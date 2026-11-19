package atlas.desktop.controllers;

import atlas.cliente.Client;
import atlas.desktop.Desktop;
import com.google.gson.*;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static atlas.cliente.Client.text;
import static atlas.cliente.SessionView.*;

final class Workspace {
    private static final Locale PT = new Locale("pt","BR");
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm",PT);
    final Parent root;
    private final Client client;
    private final String key;
    private final TableView<JsonObject> table;
    private final Label status;
    private final TextField search;
    private final ComboBox<String> filter;
    private final ComboBox<String> station;
    private final DatePicker from;
    private final DatePicker to;
    private final ToggleButton alternate;
    private final Button details;
    private JsonArray current = new JsonArray();
    private JsonObject secondary = new JsonObject();
    private boolean loading;
    private boolean refreshAgain;
    private boolean writing;
    private List<JsonObject> cashRendered;

    Workspace(Client client,String key) {
        this.client = client;
        this.key = key;
        root = load(Desktop.class,"/fxml/" + key + "/" + key + ".fxml");
        table = control(root,"table");
        status = control(root,"status");
        search = control(root,"search");
        details = control(root,"details");
        filter = optional("filter");
        station = optional("station");
        from = optional("from");
        to = optional("to");
        alternate = optional("alternate");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Nenhum registro para os filtros selecionados."));
        table.setOnMouseClicked(e -> { if (e.getClickCount()==2) detail(table.getSelectionModel().getSelectedItem()); });
        details.setDisable(true);
        details.setOnAction(e -> detail(table.getSelectionModel().getSelectedItem()));
        table.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> details.setDisable(b == null || writing));
        search.textProperty().addListener((o,a,b) -> render());
        if (filter != null) { filter.setValue("Todos"); filter.valueProperty().addListener((o,a,b) -> render()); }
        if (station != null) {
            station.getItems().setAll("Todas as estações","COZINHA","BAR");
            station.setValue("Todas as estações");
            station.valueProperty().addListener((o,a,b) -> render());
        }
        if (from != null) {
            if (key.equals("relatorios")) { from.setValue(LocalDate.now(ZONE).minusDays(6)); to.setValue(LocalDate.now(ZONE)); }
            from.setOnAction(e -> dates());
            to.setOnAction(e -> dates());
        }
        if (alternate != null) alternate.setOnAction(e -> {
            if (key.equals("salao")) render();
            else { columns(); refresh(); }
        });
        Button primary = optional("primary");
        if (primary != null) primary.setOnAction(e -> primary());
        Button closings = optional("closings");
        if (closings != null) closings.setOnAction(e -> async(() -> client.get("cash-closings").getAsJsonArray(),rows -> {
            StringBuilder summary = new StringBuilder();
            for (var element : rows) {
                var row = element.getAsJsonObject();
                summary.append(dateTime(text(row,"created_at"))).append(" · ").append(text(row,"payment_count"))
                       .append(" pagamentos · ").append(money(text(row,"amount"))).append("\n");
            }
            Forms.show("Fechamentos de caixa",summary.isEmpty() ? "Nenhum fechamento registrado." : summary.toString());
        },error -> status.setText(message(error))));
        ((Button)control(root,"reload")).setOnAction(e -> refresh());
        columns();
    }

    @SuppressWarnings("unchecked") private <T extends javafx.scene.Node> T optional(String id) {
        return (T)root.lookup("#" + id);
    }

    private void dates() { if (key.equals("relatorios")) refresh(); else render(); }

    private boolean payments() { return key.equals("caixa") && alternate.isSelected(); }

    private void columns() {
        table.getColumns().clear();
        switch (key) {
            case "pedidos" -> fields("Pedido:sequence","Mesa:table_label","Garçom:waiter","Estações:stations","Situação:@status","Entrada:created_at");
            case "salao" -> fields("Mesa:label","Área:area","Situação:@status","Comanda:tab_id","Responsável:waiter");
            case "cardapio" -> fields("Produto:name","Categoria:category","Preço:price","Setor:station","Disponibilidade:available");
            case "funcionarios" -> fields("Nome:name","Código:username","Cargo:role","Expediente:schedule","Situação:active");
            case "caixa" -> {
                if (payments()) fields("Mesa:table_label","Comanda:tab_id","Pagamento:method","Valor:amount","Caixa:cashier","Registro:created_at");
                else fields("Mesa:label","Comanda:tab_id","Garçom:waiter","Solicitada:requested_at","Total:total");
            }
            case "relatorios" -> fields("Produto:name","Categoria:category","Setor:station","Garçom:waiter","Mesa:table_label","Qtd.:quantity","Valor:total");
            case "historico" -> fields("Data:created_at","Operação:operation","Mesa:table_label","Funcionário:employee",
                    "Pedido:order_sequence","Comanda:tab_id","Detalhes:details");
            case "configuracoes" -> fields("Configuração:key","Valor:value");
            default -> throw new IllegalArgumentException(key);
        }
    }

    private void fields(String... columns) {
        for (String entry : columns) {
            String[] parts = entry.split(":",2);
            TableColumn<JsonObject,String> column = new TableColumn<>(parts[0]);
            column.setMinWidth(75);
            column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(display(cell.getValue(),parts[1])));
            table.getColumns().add(column);
        }
    }

    void refresh() {
        if (loading) { refreshAgain = true; return; }
        loading = true;
        if (current.isEmpty()) status.setText("Carregando…");
        LocalDate first = from == null ? null : from.getValue();
        LocalDate last = to == null ? null : to.getValue();
        String endpoint = switch (key) {
            case "pedidos" -> "orders";
            case "salao" -> "tables";
            case "cardapio" -> "products";
            case "funcionarios" -> "employees";
            case "caixa" -> payments() ? "payments" : "closing-queue";
            case "relatorios" -> "reports" + query(first,last);
            case "historico" -> "history";
            case "configuracoes" -> "settings";
            default -> throw new IllegalArgumentException(key);
        };
        async(() -> {
            JsonElement data = client.get(endpoint);
            JsonObject detail = key.equals("caixa") ? client.get("dashboard").getAsJsonObject() : new JsonObject();
            if (key.equals("configuracoes")) detail = client.get("health").getAsJsonObject();
            return new Object[]{data,detail};
        }, values -> {
            loading = false;
            if (key.equals("relatorios")) {
                JsonObject result = (JsonObject)values[0];
                current = result.getAsJsonArray("items");
                secondary = result.getAsJsonObject("summary");
            } else {
                current = (JsonArray)values[0];
                secondary = (JsonObject)values[1];
                if (key.equals("configuracoes")) {
                    JsonObject row = new JsonObject();
                    row.addProperty("key","Conexão MQTT");
                    row.addProperty("value",text(secondary,"mqtt"));
                    current.add(row);
                    JsonObject database = new JsonObject();
                    database.addProperty("key","PostgreSQL");
                    database.addProperty("value",text(secondary,"database"));
                    current.add(database);
                }
            }
            filters();
            render();
            if (refreshAgain) { refreshAgain = false; refresh(); }
        },error -> {
            loading = false;
            status.setText(message(error));
            if (refreshAgain) { refreshAgain = false; refresh(); }
        });
    }

    private void filters() {
        if (filter == null) return;
        String selected = filter.getValue();
        Set<String> choices = new LinkedHashSet<>();
        choices.add("Todos");
        for (var row : current) choices.add(filterValue(row.getAsJsonObject()));
        choices.remove("");
        if (!filter.getItems().equals(new ArrayList<>(choices))) filter.getItems().setAll(choices);
        filter.setValue(choices.contains(selected) ? selected : "Todos");
    }

    private String filterValue(JsonObject row) {
        return switch (key) {
            case "pedidos","salao" -> state(row);
            case "cardapio","relatorios" -> text(row,"category");
            case "funcionarios" -> text(row,"role");
            case "historico" -> text(row,"operation");
            default -> "";
        };
    }

    private void render() {
        String chosen = filter == null ? null : filter.getValue();
        String selectedStation = station == null ? "" : station.getValue();
        String query = search.getText().strip().toLowerCase(PT);
        List<JsonObject> visible = new ArrayList<>();
        for (var item : current) {
            JsonObject row = item.getAsJsonObject();
            if (chosen != null && !chosen.equals("Todos") && !filterValue(row).equals(chosen)) continue;
            if (station != null && !selectedStation.startsWith("Todas") && !text(row,"stations").contains(selectedStation)) continue;
            if (!query.isBlank() && !matches(row,query)) continue;
            if (from != null && !key.equals("relatorios") && !withinDates(row)) continue;
            visible.add(row);
        }
        if (!table.getItems().equals(visible)) {
            JsonObject selected = table.getSelectionModel().getSelectedItem();
            table.getItems().setAll(visible);
            if (selected != null) visible.stream().filter(r -> same(r,selected)).findFirst().ifPresent(table.getSelectionModel()::select);
        }
        if (key.equals("salao")) showMap(visible);
        if (key.equals("caixa")) {
            showAccounts(visible);
            ((Label)control(root,"balance")).setText(money(text(secondary,"balance")));
            ((Label)control(root,"counts")).setText(text(secondary,"closing") + " comandas aguardando fechamento");
        }
        status.setText(key.equals("relatorios") ? visible.size() + " linhas · " + text(secondary,"payments")
                + " pagamentos · " + money(text(secondary,"total")) : visible.size() + " registros");
    }

    private void showAccounts(List<JsonObject> rows) {
        ScrollPane scroll = control(root,"accountScroll");
        boolean history = payments();
        table.setManaged(history);
        table.setVisible(history);
        scroll.setManaged(!history);
        scroll.setVisible(!history);
        details.setManaged(history);
        details.setVisible(history);
        if (history || rows.equals(cashRendered)) return;
        cashRendered = List.copyOf(rows);
        VBox accounts = control(root,"accounts");
        accounts.getChildren().clear();
        if (rows.isEmpty()) {
            accounts.getChildren().add(new Label("Nenhuma conta solicitada."));
            return;
        }
        for (JsonObject row : rows) {
            Parent card = load(Desktop.class,"/fxml/caixa/linha-conta.fxml");
            ((Label)control(card,"tableName")).setText(text(row,"label"));
            ((Label)control(card,"tabName")).setText("Comanda " + text(row,"tab_id") + " · " + text(row,"waiter"));
            ((Label)control(card,"time")).setText("Solicitada em " + dateTime(text(row,"requested_at")));
            ((Label)control(card,"total")).setText(money(text(row,"total")));
            ((Button)control(card,"open")).setOnAction(e -> detail(row));
            accounts.getChildren().add(card);
        }
    }

    private void showMap(List<JsonObject> rows) {
        ScrollPane map = control(root,"map");
        boolean mode = alternate.isSelected();
        table.setManaged(!mode);
        table.setVisible(!mode);
        map.setManaged(mode);
        map.setVisible(mode);
        alternate.setText(mode ? "Lista" : "Mapa");
        if (!mode) return;
        VBox areas = control(root,"areas");
        areas.getChildren().clear();
        Map<String,TilePane> sections = new LinkedHashMap<>();
        for (JsonObject row : rows) {
            String area = text(row,"area");
            TilePane section = sections.computeIfAbsent(area,name -> {
                areas.getChildren().add(new Label(name));
                TilePane tiles = new TilePane(10,10);
                areas.getChildren().add(tiles);
                return tiles;
            });
            Button place = new Button(text(row,"label") + "\n" + state(row));
            place.getStyleClass().addAll("map-button",text(row,"status").toLowerCase(PT));
            place.setOnAction(e -> detail(row));
            section.getChildren().add(place);
        }
    }

    private boolean withinDates(JsonObject row) {
        String field = text(row,"created_at");
        if (field.isEmpty()) return true;
        LocalDate date = Instant.parse(field).atZone(ZONE).toLocalDate();
        return (from.getValue() == null || !date.isBefore(from.getValue())) && (to.getValue() == null || !date.isAfter(to.getValue()));
    }

    private boolean matches(JsonObject row,String query) {
        if (key.equals("pedidos") && ("PED-" + text(row,"sequence")).toLowerCase(PT).contains(query)) return true;
        return row.entrySet().stream().filter(e -> e.getValue().isJsonPrimitive())
                .anyMatch(e -> e.getValue().getAsString().toLowerCase(PT).contains(query));
    }

    private static boolean same(JsonObject a,JsonObject b) {
        return a.equals(b) || a.has("id") && b.has("id") && text(a,"id").equals(text(b,"id"));
    }

    private void primary() {
        if (writing) return;
        switch (key) {
            case "caixa" -> {
                if (Forms.confirm("Fechar caixa","Consolidar todos os pagamentos ainda sem fechamento?"))
                    mutate("cash-closings",Map.of(),false);
            }
            case "configuracoes" -> {
                JsonObject existing = new JsonObject();
                for (var value : current) {
                    JsonObject row = value.getAsJsonObject();
                    if (text(row,"key").equals("restaurant")) existing.add("restaurant",row.get("value"));
                }
                Forms.edit(key,existing).ifPresent(values -> mutate("settings",values,true));
            }
            default -> edit(null);
        }
    }

    private void edit(JsonObject row) {
        String endpoint = switch (key) {
            case "salao" -> "tables";
            case "cardapio" -> "products";
            case "funcionarios" -> "employees";
            default -> throw new IllegalArgumentException(key);
        };
        Forms.edit(key,row).ifPresent(values -> mutate(endpoint + (row == null ? "" : "/" + text(row,"id")),values,row != null));
    }

    private void detail(JsonObject row) {
        if (row == null || writing) return;
        switch (key) {
            case "cardapio","funcionarios" -> edit(row);
            case "pedidos" -> loadOrder(row);
            case "salao" -> loadTab(row,false);
            case "caixa" -> {
                if (payments()) Forms.show("Pagamento",generic(row));
                else loadTab(row,true);
            }
            default -> Forms.show("Detalhes",generic(row));
        }
    }

    private void loadOrder(JsonObject row) {
        String endpoint = "orders/" + text(row,"id");
        async(() -> client.get(endpoint).getAsJsonObject(),order -> {
            StringBuilder description = new StringBuilder(text(order,"table_label") + " · " + text(order,"waiter")
                    + "\n" + state(order) + "\n\n");
            for (var item : order.getAsJsonArray("items")) description.append(line(item.getAsJsonObject()));
            description.append("\nProdução\n");
            for (var item : order.getAsJsonArray("stations")) {
                JsonObject sector = item.getAsJsonObject();
                description.append(text(sector,"station")).append(" · ").append(text(sector,"status")).append(" ")
                           .append(text(sector,"error")).append("\n");
            }
            if (order.has("prints") && !order.getAsJsonArray("prints").isEmpty()) {
                description.append("\nImpressão\n");
                for (var item : order.getAsJsonArray("prints")) {
                    JsonObject job = item.getAsJsonObject();
                    description.append(text(job,"status")).append(" · ").append(text(job,"attempts"))
                            .append(" tentativa(s) · ").append(text(job,"error")).append("\n");
                }
            }
            description.append("\nHistórico\n");
            for (var item : order.getAsJsonArray("history")) {
                JsonObject event = item.getAsJsonObject();
                description.append(dateTime(text(event,"created_at"))).append(" · ").append(text(event,"operation"))
                           .append("  ").append(text(event,"details")).append("\n");
            }
            boolean deliver = text(order,"delivered_at").isEmpty() && Set.of("CONFIRMADO","PARCIAL").contains(text(order,"status"));
            if (Forms.detail("PED-" + text(row,"sequence"),description.toString(),deliver ? "Marcar entregue" : null,null)==1)
                mutate(endpoint + "/deliver",Map.of(),false);
        },error -> status.setText(message(error)));
    }

    private void loadTab(JsonObject row,boolean checkout) {
        long tableId = row.get("id").getAsLong();
        String endpoint = "tabs/" + tableId;
        async(() -> client.get(endpoint).getAsJsonObject(),tab -> {
            StringBuilder lines = new StringBuilder();
            JsonObject active = tab.getAsJsonObject("tab");
            if (active == null) lines.append("Mesa sem comanda ativa.");
            else {
                lines.append("Comanda ").append(text(active,"id")).append(" · ").append(text(row,"waiter")).append("\n\n");
                for (var item : tab.getAsJsonArray("items")) lines.append(line(item.getAsJsonObject()));
                lines.append("\nTotal confirmado  ").append(money(text(tab,"total")));
            }
            String title = text(row,"label");
            if (checkout) {
                if (Forms.detail(title,lines.toString(),"Revisar fechamento",null)!=1) return;
                Forms.payment().ifPresent(method -> {
                    if (Forms.confirm("Confirmar fechamento",title + " · " + money(text(tab,"total")) + " · " + method))
                        mutate(endpoint + "/pay",Map.of("method",method,"expectedTotal",tab.get("total").getAsBigDecimal()),false);
                });
            } else {
                String action = active == null ? "Abrir comanda" : text(active,"status").equals("ABERTA") ? "Solicitar conta" : null;
                if (active == null && !row.get("available").getAsBoolean()) action = null;
                int choice = Forms.detail(title,lines.toString(),action,"Editar mesa");
                if (choice==1) {
                    if (active == null) chooseWaiter(endpoint);
                    else mutate(endpoint + "/request",Map.of(),false);
                }
                if (choice==2) edit(row);
            }
        },error -> status.setText(message(error)));
    }

    private void chooseWaiter(String route) {
        async(() -> client.get("employees").getAsJsonArray(), employees -> {
            Map<String,Long> available = new LinkedHashMap<>();
            for (var item : employees) {
                var employee = item.getAsJsonObject();
                if ("GARCOM".equals(text(employee,"role")) && employee.get("active").getAsBoolean())
                    available.put(text(employee,"name") + " · " + text(employee,"username"),employee.get("id").getAsLong());
            }
            if (available.isEmpty()) {
                status.setText("Cadastre um garçom ativo para abrir a comanda.");
                return;
            }
            ChoiceDialog<String> choose = new ChoiceDialog<>(available.keySet().iterator().next(),available.keySet());
            choose.setTitle("Abrir comanda");
            choose.setHeaderText("Selecione o garçom responsável.");
            choose.showAndWait().ifPresent(name -> mutate(route + "/open",Map.of("waiterId",available.get(name)),false));
        },error -> status.setText(message(error)));
    }

    private void mutate(String route,Map<String,?> fields,boolean update) {
        if (writing) return;
        writing = true;
        status.setText("Registrando…");
        details.setDisable(true);
        async(() -> update ? client.put(route,fields) : client.post(route,fields),result -> {
            writing = false;
            details.setDisable(table.getSelectionModel().getSelectedItem()==null);
            refresh();
        },error -> {
            writing = false;
            details.setDisable(table.getSelectionModel().getSelectedItem()==null);
            status.setText(message(error));
            new Alert(Alert.AlertType.ERROR,message(error)).show();
        });
    }

    private static String line(JsonObject row) {
        String note = text(row,"notes");
        return text(row,"quantity") + " × " + text(row,"name") + "  ·  " + money(text(row,"unit_price"))
                + (note.isBlank() ? "" : "  ·  " + note) + "\n";
    }

    private static String generic(JsonObject row) {
        StringBuilder result = new StringBuilder();
        row.entrySet().forEach(e -> {
            if (e.getValue().isJsonPrimitive()) result.append(e.getKey()).append(" · ").append(e.getValue().getAsString()).append("\n");
        });
        return result.toString();
    }

    private static String display(JsonObject row,String key) {
        if (key.equals("@status")) return state(row);
        String value = text(row,key);
        if (value.isEmpty()) return "—";
        return switch (key) {
            case "sequence","order_sequence" -> "PED-" + value;
            case "price","total","amount" -> money(value);
            case "created_at","requested_at" -> dateTime(value);
            case "available" -> value.equals("true") ? "Disponível" : "Indisponível";
            case "active" -> value.equals("true") ? "Ativo" : "Inativo";
            default -> value;
        };
    }

    private static String state(JsonObject row) {
        if (!text(row,"delivered_at").isEmpty()) return "Entregue";
        return switch (text(row,"status")) {
            case "PENDENTE" -> "true".equals(text(row,"preparing")) ? "Em preparo" : "Novo";
            case "CONFIRMADO" -> "Pronto";
            case "PARCIAL" -> "Parcial";
            case "ERRO" -> "Falha";
            case "LIVRE" -> "Livre";
            case "OCUPADA" -> "Ocupada";
            case "INDISPONIVEL" -> "Indisponível";
            case "CONTA_SOLICITADA" -> "Conta solicitada";
            default -> text(row,"status");
        };
    }

    static String money(String raw) {
        if (raw.isBlank()) return "—";
        return NumberFormat.getCurrencyInstance(PT).format(new BigDecimal(raw));
    }

    private static String dateTime(String date) {
        return date.isBlank() ? "—" : Instant.parse(date).atZone(ZONE).format(DATE_TIME);
    }

    private static String query(LocalDate from,LocalDate to) {
        List<String> values = new ArrayList<>();
        if (from != null) values.add("from=" + from);
        if (to != null) values.add("to=" + to);
        return values.isEmpty() ? "" : "?" + String.join("&",values);
    }
}
