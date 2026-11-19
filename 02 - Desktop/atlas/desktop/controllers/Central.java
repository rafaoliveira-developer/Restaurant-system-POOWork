package atlas.desktop.controllers;

import atlas.cliente.*;
import atlas.desktop.Desktop;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import java.util.*;
import static atlas.cliente.SessionView.*;

public final class Central {
    private final Client client;
    private final StackPane content;
    private final VBox sidebar;
    private final ImageView logo;
    private final Map<String,Button> links = new LinkedHashMap<>();
    private final Map<String,Workspace> pages = new HashMap<>();
    private String current;
    private boolean collapsed;
    private Panorama panorama;

    public Central(SessionView session) {
        client = session.client();
        Parent layout = load(Desktop.class,"/fxml/central.fxml");
        content = control(layout,"content");
        sidebar = control(layout,"sidebar");
        logo = control(layout,"logo");
        logo.setImage(image("imgMarcaAtlasLogoCompletaPretaSem"));
        ((Label)control(layout,"user")).setText(client.name);
        ((Label)control(layout,"role")).setText(client.role.equals("GERENTE") ? "Gerência" : "Caixa");
        ((Label)control(layout,"badge")).setText(client.name.substring(0,1).toUpperCase(Locale.ROOT));
        session.showConnection(control(layout,"connection"));
        Button toggle = control(layout,"toggle");
        toggle.setGraphic(icon("imgPainelLateralPhosphorSidebarSimple",20));
        toggle.setOnAction(e -> collapse());
        Button exit = control(layout,"logout");
        exit.setGraphic(icon("imgPhosphorRegularSignOutCinzaAtlas",20));
        exit.setOnAction(e -> session.logout());
        if (client.role.equals("GERENTE")) {
            link("inicio","Início","imgPhosphorRegularHouseAzulAtlas");
            link("pedidos","Pedidos","imgPhosphorRegularReceiptCinzaAtlas");
            link("salao","Salão","imgPhosphorRegularChairCinzaAtlas");
            link("cardapio","Menu","imgPhosphorRegularBookOpenTextCinzaAtlas");
            link("funcionarios","Funcionários","imgPhosphorRegularUsersThreeCinzaAtlas");
            link("relatorios","Relatórios","imgPhosphorRegularChartBarCinzaAtlas");
            link("historico","Histórico","imgPhosphorRegularClockCounterClockwiseCinzaAtlas");
            link("configuracoes","Configurações","imgPhosphorRegularGearSixCinzaAtlas");
        }
        link("caixa","Contas solicitadas","imgPhosphorDuotoneCashRegisterAzulAtlas");
        session.root(layout);
        select(client.role.equals("GERENTE") ? "inicio" : "caixa");
        session.listen(() -> {
            if ("inicio".equals(current)) panorama.refresh();
            else if (pages.containsKey(current)) pages.get(current).refresh();
        });
    }

    private void select(String key) {
        current = key;
        links.values().forEach(button -> button.getStyleClass().remove("active"));
        links.get(key).getStyleClass().add("active");
        if (key.equals("inicio")) {
            if (panorama == null) panorama = new Panorama(client,this::select);
            content.getChildren().setAll(panorama.root);
            panorama.refresh();
        } else {
            Workspace page = pages.computeIfAbsent(key,k -> new Workspace(client,k));
            content.getChildren().setAll(page.root);
            page.refresh();
        }
    }

    private void link(String id,String label,String asset) {
        Button button = new Button(label,icon(asset,20));
        button.setMaxWidth(Double.MAX_VALUE);
        button.setTooltip(new Tooltip(label));
        button.setAccessibleText(label);
        button.getStyleClass().add("nav");
        button.setOnAction(e -> select(id));
        VBox destination = control(sidebar,id.equals("configuracoes") ? "bottomLinks" : "links");
        destination.getChildren().add(button);
        links.put(id,button);
    }

    private void collapse() {
        collapsed = !collapsed;
        double width = collapsed ? 82 : 240;
        sidebar.setPrefWidth(width);
        sidebar.setMinWidth(width);
        sidebar.setMaxWidth(width);
        logo.setVisible(!collapsed);
        logo.setManaged(!collapsed);
        links.values().forEach(button -> button.setText(collapsed ? "" : button.getAccessibleText()));
    }

    private static Image image(String name) {
        return new Image(resource(Desktop.class,"/icons/" + name + ".png").toExternalForm());
    }

    static ImageView icon(String name,int width) {
        var view = new ImageView(image(name));
        view.setFitWidth(width);
        view.setPreserveRatio(true);
        return view;
    }
}
