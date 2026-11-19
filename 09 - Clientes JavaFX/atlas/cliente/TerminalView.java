package atlas.cliente;

import com.google.gson.*;
import javafx.scene.Parent;
import javafx.scene.control.*;
import java.time.*;
import java.util.*;
import static atlas.cliente.Client.text;
import static atlas.cliente.SessionView.*;

public final class TerminalView {
    private final SessionView session;
    private final String station;
    private final ListView<JsonObject> queue;
    private final TextArea details;
    private final Label status;
    private final Runnable onRefresh;
    private final List<Button> actions;
    private boolean loading;
    private boolean busy;

    public TerminalView(SessionView session,String station,Parent layout,Runnable onRefresh) {
        this.session = session;
        this.station = station;
        this.onRefresh = onRefresh;
        queue = control(layout,"queue");
        details = control(layout,"details");
        status = control(layout,"status");
        Label account = control(layout,"account");
        account.setText(session.client().name);
        session.showConnection(control(layout,"connection"));
        Button reload = control(layout,"reload");
        reload.setOnAction(e -> refresh());
        Button logout = control(layout,"logout");
        logout.setOnAction(e -> session.logout());
        Button start = control(layout,"start");
        Button finish = control(layout,"finish");
        Button fail = control(layout,"fail");
        actions = List.of(start,finish,fail);
        start.setOnAction(e -> command("PREPARANDO",""));
        finish.setOnAction(e -> command("OK",""));
        fail.setOnAction(e -> {
            if (queue.getSelectionModel().getSelectedItem() == null) return;
            var dialog = new TextInputDialog();
            dialog.setTitle("Falha de produção");
            dialog.setHeaderText("Informe o motivo para o garçom.");
            dialog.showAndWait().filter(value -> !value.isBlank()).ifPresent(value -> command("ERRO",value));
        });
        queue.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(JsonObject item, boolean empty) {
                super.updateItem(item,empty);
                setText(empty || item == null ? null : "PED-" + text(item,"sequence") + "  •  "
                        + text(item,"table_label") + "  ·  " + state(text(item,"status")));
            }
        });
        queue.setPlaceholder(new Label("Nenhum pedido na fila."));
        queue.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> detail(b));
        detail(null);
        session.listen(this::refresh);
        session.root(layout);
        refresh();
    }

    public JsonObject selected() { return queue.getSelectionModel().getSelectedItem(); }
    public void show(String message) { status.setText(message); }
    public void onSelection(Runnable action) {
        queue.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> action.run());
    }

    public void refresh() {
        Client client = session.client();
        if (client == null || loading) return;
        loading = true;
        if (queue.getItems().isEmpty()) status.setText("Carregando fila…");
        async(() -> client.get("queue/" + station).getAsJsonArray(), rows -> {
            loading = false;
            if (session.client() != client) return;
            JsonObject selection = selected();
            String key = selection == null ? "" : text(selection,"order_id");
            List<JsonObject> items = new ArrayList<>();
            for (var row : rows) items.add(row.getAsJsonObject());
            if (!queue.getItems().equals(items)) {
                queue.getItems().setAll(items);
                items.stream().filter(row -> key.equals(text(row,"order_id"))).findFirst()
                        .ifPresent(queue.getSelectionModel()::select);
            }
            detail(selected());
            if (!busy) status.setText(items.isEmpty() ? "Fila vazia" : items.size() + " pedidos na fila e no turno recente");
            onRefresh.run();
        }, error -> {
            loading = false;
            if (session.client() == client) status.setText(message(error));
        });
    }

    private void command(String state,String error) {
        JsonObject current = selected();
        if (current == null || busy) return;
        if (error.length() > 300) {
            status.setText("Use até 300 caracteres para informar a falha.");
            return;
        }
        busy = true;
        detail(current);
        String event = "BAR".equals(station) ? "CONFIRMABAR" : "CONFIRMACOZINHA";
        status.setText("Enviando ao broker…");
        async(() -> {
            session.publish(event,Map.of("orderId",text(current,"order_id"),"sequence",current.get("sequence").getAsLong(),
                    "status",state,"error",error));
            return true;
        }, result -> {
            busy = false;
            status.setText("Aguardando registro pelo Backend…");
            detail(selected());
        }, failure -> {
            busy = false;
            status.setText(message(failure));
            detail(selected());
        });
    }

    private void detail(JsonObject item) {
        boolean actionable = item != null && List.of("PENDENTE","PREPARANDO").contains(text(item,"status"));
        actions.forEach(button -> button.setDisable(!actionable || busy));
        if (item == null) {
            details.setText("Selecione um pedido para ver os itens da estação.");
            return;
        }
        if (text(item,"status").equals("PREPARANDO")) actions.getFirst().setDisable(true);
        StringBuilder text = new StringBuilder("PED-").append(text(item,"sequence")).append("  ·  ")
                .append(text(item,"table_label")).append("\nComanda ").append(text(item,"tab_id"))
                .append("  •  ").append(text(item,"waiter")).append("  •  ")
                .append(Duration.between(Instant.parse(text(item,"created_at")),Instant.now()).toMinutes()).append(" min\n")
                .append(state(text(item,"status"))).append("\n\n");
        for (var value : item.getAsJsonArray("items")) {
            var row = value.getAsJsonObject();
            text.append(text(row,"quantity")).append(" × ").append(text(row,"name")).append("\n");
            if (!text(row,"notes").isBlank()) text.append("   ").append(text(row,"notes")).append("\n");
        }
        if (!text(item,"error").isEmpty()) text.append("\nFalha: ").append(text(item,"error"));
        details.setText(text.toString());
    }

    private static String state(String state) {
        return switch (state) { case "PENDENTE" -> "Novo"; case "PREPARANDO" -> "Em preparo";
            case "OK" -> "Pronto"; case "ERRO" -> "Falha"; default -> state; };
    }
}
