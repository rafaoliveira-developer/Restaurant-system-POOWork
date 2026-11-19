package atlas.cozinha;

import atlas.cliente.*;
import atlas.impressao.servicos.Printing;
import com.google.gson.*;
import javafx.scene.Parent;
import javafx.scene.control.*;
import java.util.*;
import java.nio.file.*;
import java.io.IOException;
import static atlas.cliente.Client.text;
import static atlas.cliente.SessionView.*;

final class KitchenPrinting {
    private final SessionView session;
    private final Printing printing;
    private final TerminalView terminal;
    private final Label status;
    private final ComboBox<String> jobs;
    private final Map<String,Button> controls = new LinkedHashMap<>();
    private final Map<String,JsonObject> byLabel = new LinkedHashMap<>();
    private final Path journal;
    private final Map<String,JsonObject> results = new LinkedHashMap<>();
    private final Map<String,String> copies = new HashMap<>();
    private boolean working;

    KitchenPrinting(SessionView session,Parent layout,Printing printing) {
        this.session = session;
        this.printing = printing;
        journal = Path.of(".local","print-results",session.client().username);
        jobs = control(layout,"printJobs");
        status = control(layout,"printStatus");
        terminal = new TerminalView(session,"COZINHA",layout,this::refresh);
        terminal.onSelection(this::showJobs);
        button(layout,"printRetry","retry");
        button(layout,"printCopy","reprint");
        button(layout,"printUncertain","uncertain");
        button(layout,"printCancel","cancel");
        jobs.valueProperty().addListener((source,old,value) -> updateControls());
        updateControls();
    }

    private void button(Parent layout,String id,String action) {
        Button button = control(layout,id);
        controls.put(action,button);
        button.setOnAction(e -> action(action));
    }

    private void showJobs() {
        JsonObject order = terminal.selected();
        var previous = byLabel.get(jobs.getValue());
        byLabel.clear();
        if (order != null) {
            for (var element : order.getAsJsonArray("prints")) {
                var job = element.getAsJsonObject();
                String id = text(job,"id");
                String kind = text(job,"parent_id").isEmpty() ? "Original" : "Cópia";
                byLabel.put(kind + " · " + text(job,"status") + " · " + id.substring(0,8)
                        + (text(job,"error").isEmpty() ? "" : " · " + text(job,"error")),job);
            }
        }
        jobs.getItems().setAll(byLabel.keySet());
        if (previous != null) byLabel.entrySet().stream()
                .filter(e -> text(e.getValue(),"id").equals(text(previous,"id"))).map(Map.Entry::getKey)
                .findFirst().ifPresent(jobs::setValue);
        if (!byLabel.containsKey(jobs.getValue())) {
            if (jobs.getItems().isEmpty()) jobs.setValue(null);
            else jobs.getSelectionModel().selectFirst();
        }
        updateControls();
    }

    private void updateControls() {
        JsonObject job = byLabel.get(jobs.getValue());
        String state = job == null ? "" : text(job,"status");
        controls.get("retry").setDisable(!state.equals("FALHA"));
        controls.get("reprint").setDisable(!Set.of("IMPRESSO","INCERTO").contains(state));
        controls.get("uncertain").setDisable(!state.equals("IMPRIMINDO"));
        controls.get("cancel").setDisable(!Set.of("PENDENTE","FALHA").contains(state));
    }

    private void action(String action) {
        var job = byLabel.get(jobs.getValue());
        if (job == null) {
            status.setText("Selecione um trabalho de impressão.");
            return;
        }
        String id = text(job,"id");
        if (action.equals("reprint") || action.equals("uncertain") || action.equals("cancel")) {
            String question = action.equals("reprint") ? "Confira a fila e o papel antes de autorizar uma cópia."
                    : action.equals("uncertain") ? "Marcar esta impressão como incerta após conferir a fila?"
                    : "Cancelar este trabalho de impressão?";
            var confirm = new Alert(Alert.AlertType.CONFIRMATION,question);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        }
        Client client = session.client();
        Map<String,String> body = action.equals("reprint")
                ? Map.of("copyId",copies.computeIfAbsent(id,key -> UUID.randomUUID().toString())) : Map.of();
        async(() -> client.post("prints/" + id + "/" + action,body), result -> {
            copies.remove(id);
            status.setText("Solicitação de impressão registrada.");
            terminal.refresh();
        }, failure -> status.setText(message(failure)));
    }

    private void refresh() {
        showJobs();
        Client client = session.client();
        if (working || client == null) return;
        working = true;
        async(() -> {
            Files.createDirectories(journal);
            try (var files = Files.list(journal)) {
                for (Path file : files.filter(p -> p.getFileName().toString().matches("[a-f0-9-]{36}\\.json")).toList()) {
                    String id = file.getFileName().toString().substring(0,36);
                    if (!results.containsKey(id)) results.put(id,JsonParser.parseString(Files.readString(file)).getAsJsonObject());
                }
            }
            for (String id : List.copyOf(results.keySet())) record(client,id);
            JsonArray pending = client.get("prints").getAsJsonArray();
            for (var element : pending) {
                if (session.client() != client) break;
                String id = text(element.getAsJsonObject(),"id");
                JsonObject job;
                try { job = client.post("prints/" + id + "/claim",Map.of()).getAsJsonObject(); }
                catch (Client.ApiError ex) { if (ex.status == 409) continue; else throw ex; }
                var outcome = printing.submit(UUID.fromString(id),text(job,"content"));
                JsonObject saved = new JsonObject();
                saved.addProperty("claimId",text(job,"claim_id"));
                saved.addProperty("status",outcome.status());
                saved.addProperty("error",outcome.error());
                results.put(id,saved);
                Path file = journal.resolve(id + ".json");
                Path temporary = Files.createTempFile(journal,"outcome-",".tmp");
                try {
                    Files.writeString(temporary,saved.toString());
                    try { Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE); }
                    catch (AtomicMoveNotSupportedException ex) { Files.move(temporary,file); }
                } catch (IOException ex) {
                    System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Falha ao guardar resultado de impressão",ex);
                } finally { Files.deleteIfExists(temporary); }
                record(client,id);
            }
            return pending.size();
        }, count -> {
            working = false;
            if (count > 0) {
                status.setText("Fila de impressão atualizada.");
                terminal.refresh();
            }
        }, failure -> {
            working = false;
            status.setText(message(failure));
        });
    }

    private void record(Client client,String id) throws Exception {
        JsonObject saved = results.get(id);
        try { client.post("prints/" + id + "/result",Map.of("claimId",text(saved,"claimId"),
                "status",text(saved,"status"),"error",text(saved,"error"))); }
        catch (Client.ApiError ex) { if (ex.status != 409) throw ex; }
        Files.deleteIfExists(journal.resolve(id + ".json"));
        results.remove(id);
    }
}
