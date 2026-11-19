package atlas.desktop.controllers;

import atlas.cliente.*;
import atlas.desktop.Desktop;
import com.google.gson.*;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import static atlas.cliente.SessionView.*;
import static atlas.cliente.Client.text;

final class Panorama {
    final Parent root = load(Desktop.class,"/fxml/inicio/inicio.fxml");
    private final Client client;
    private final List<Card> cards = new ArrayList<>();
    private boolean loading;
    private record Card(Parent node,String[] metrics,String[] labels,String note) {}

    Panorama(Client client,Consumer<String> select) {
        this.client = client;
        var now = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo"));
        String salutation = now.getHour() < 12 ? "Bom dia" : now.getHour() < 18 ? "Boa tarde" : "Boa noite";
        ((Label)control(root,"greeting")).setText(salutation + ", " + client.name.split(" ")[0] + ".");
        ((Label)control(root,"date")).setText(now.format(DateTimeFormatter.ofPattern(
                "EEEE, d 'de' MMMM 'de' yyyy · HH:mm",new Locale("pt","BR"))));
        add("salon","Salão","Ocupação das mesas","salao","ChairAzulAtlas","blue",
                "occupied,free","Ocupadas,Livres","closing",select);
        add("orders","Pedidos","Fila de pedidos","pedidos","ReceiptVioleta","violet",
                "fresh,preparing,ready","Novos,Em preparo,Prontos","today",select);
        add("service","Atendimento","Pedidos prontos para entrega","pedidos","BellRingingAmbar","amber",
                "ready","A entregar","",select);
        add("cash","Financeiro","Contas solicitadas","caixa","CashRegisterAzulAtlas","blue",
                "closing,balance","Comandas,Em aberto","",select);
        add("production","Produção","Pedidos em andamento por estação","pedidos","CookingPotVioleta","violet",
                "kitchen,bar","Cozinha,Bar","",select);
        add("printing","Impressão","Acompanhamento da cozinha","pedidos","BroomVerde","green",
                "prints","Falhas ou resultado incerto","",select);
    }

    private void add(String slot,String title,String subtitle,String page,String artwork,String color,String metrics,
                     String labels,String note,Consumer<String> select) {
        VBox box = control(root,slot);
        VBox card = (VBox)load(Desktop.class,"/fxml/inicio/indicador.fxml");
        box.getChildren().setAll(card);
        ((Label)control(card,"title")).setText(title);
        ((Label)control(card,"subtitle")).setText(subtitle);
        StackPane icon = control(card,"icon");
        icon.getStyleClass().add(color);
        icon.getChildren().setAll(Central.icon("imgPhosphorDuotone" + artwork,24));
        Button open = control(card,"open");
        open.setAccessibleText("Abrir " + title);
        open.setGraphic(Central.icon("imgPhosphorRegularArrowRightAzulAtlas",17));
        open.setOnAction(e -> select.accept(page));
        cards.add(new Card(card,metrics.split(","),labels.split(","),note));
    }

    void refresh() {
        if (loading) return;
        loading = true;
        Label status = control(root,"status");
        async(() -> client.get("dashboard").getAsJsonObject(), data -> {
            loading = false;
            status.setText("");
            cards.forEach(card -> render(card,data));
        },error -> { loading = false; status.setText(message(error)); });
    }

    private static void render(Card card,JsonObject data) {
        HBox metrics = control(card.node,"metrics");
        metrics.getChildren().clear();
        for (int i = 0; i < card.metrics.length; i++) {
            String key = card.metrics[i];
            Label value = new Label(key.equals("balance") ? Workspace.money(text(data,key)) : text(data,key));
            value.getStyleClass().addAll("metric",key);
            Label label = new Label(card.labels[i]);
            label.getStyleClass().add("muted");
            label.setWrapText(true);
            metrics.getChildren().add(new VBox(3,value,label));
        }
        ((Label)control(card.node,"note")).setText(switch (card.note) {
            case "closing" -> text(data,"closing") + " com conta solicitada";
            case "today" -> text(data,"today") + " pedidos hoje";
            default -> "";
        });
    }
}
