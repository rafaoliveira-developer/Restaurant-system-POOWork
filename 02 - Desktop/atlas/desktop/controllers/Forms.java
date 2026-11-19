package atlas.desktop.controllers;

import atlas.desktop.Desktop;
import com.google.gson.JsonObject;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;
import static atlas.cliente.Client.text;
import static atlas.cliente.SessionView.*;

final class Forms {
    private final Dialog<Map<String,Object>> dialog = new Dialog<>();
    private final VBox fields;
    private final Map<String,Supplier<?>> values = new LinkedHashMap<>();
    private final JsonObject existing;

    private Forms(String title,JsonObject existing) {
        this.existing = existing == null ? new JsonObject() : existing;
        dialog.setTitle(title);
        DialogPane pane = (DialogPane)load(Desktop.class,"/fxml/editar.fxml");
        dialog.setDialogPane(pane);
        fields = control(pane,"fields");
        pane.getButtonTypes().addAll(ButtonType.CANCEL,ButtonType.OK);
        Label feedback = new Label();
        feedback.setWrapText(true);
        pane.setHeader(feedback);
        pane.lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION,e -> {
            try { read(); }
            catch (NumberFormatException ex) { feedback.setText("Informe um preço válido."); e.consume(); }
        });
        dialog.setResultConverter(btn -> btn == ButtonType.OK ? read() : null);
    }

    private Map<String,Object> read() {
        Map<String,Object> body = new LinkedHashMap<>();
        values.forEach((key,supplier) -> body.put(key,supplier.get()));
        return body;
    }

    static Optional<Map<String,Object>> edit(String page,JsonObject row) {
        Forms form = new Forms(row == null ? "Novo registro" : "Editar registro",row);
        switch (page) {
            case "cardapio" -> {
                form.field("name","Nome",false);
                form.field("description","Descrição",false);
                form.field("category","Categoria comercial",false);
                form.options("station","Estação de produção","COZINHA","BAR");
                form.field("price","Preço em reais",true);
                form.field("image","URL HTTPS da imagem",false);
                form.check("available","Disponível para pedidos");
            }
            case "salao" -> {
                form.field("label","Identificação",false);
                form.field("area","Área do salão",false);
                form.check("available","Mesa disponível");
            }
            case "funcionarios" -> {
                form.field("name","Nome",false);
                form.field("username","Código de acesso",false);
                form.options("role","Cargo","GARCOM","GERENTE","CAIXA","BAR","COZINHA");
                form.field("schedule","Expediente",false);
                PasswordField password = new PasswordField();
                form.add(row == null ? "Senha inicial" : "Nova senha (opcional)",password);
                form.values.put("password",password::getText);
                form.check("active","Acesso ativo");
            }
            case "configuracoes" -> form.field("restaurant","Nome exibido",false);
            default -> throw new IllegalArgumentException(page);
        }
        return form.dialog.showAndWait();
    }

    private void field(String key,String label,boolean number) {
        TextField field = new TextField(text(existing,key));
        add(label,field);
        values.put(key,number ? () -> new BigDecimal(field.getText().strip().replace(',','.')) : field::getText);
    }

    private void options(String key,String label,String... options) {
        ComboBox<String> selection = new ComboBox<>();
        selection.getItems().setAll(options);
        String current = text(existing,key);
        selection.setValue(current.isBlank() ? options[0] : current);
        add(label,selection);
        values.put(key,selection::getValue);
    }

    private void check(String key,String label) {
        CheckBox check = new CheckBox(label);
        check.setSelected(!existing.has(key) || existing.get(key).getAsBoolean());
        fields.getChildren().add(check);
        values.put(key,check::isSelected);
    }

    private void add(String name,Node node) { fields.getChildren().addAll(new Label(name),node); }

    static boolean confirm(String title,String message) {
        Alert prompt = new Alert(Alert.AlertType.CONFIRMATION,message);
        prompt.setTitle(title);
        return prompt.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    static Optional<String> payment() {
        ChoiceDialog<String> prompt = new ChoiceDialog<>("PIX","PIX","DINHEIRO","CARTAO");
        prompt.setTitle("Forma de pagamento");
        prompt.setHeaderText("Registre a forma de pagamento recebida.");
        return prompt.showAndWait();
    }

    static void show(String title,String message) { detail(title,message,null,null); }

    static int detail(String title,String description,String primary,String secondary) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        DialogPane pane = (DialogPane)load(Desktop.class,"/fxml/detalhes.fxml");
        ((Label)control(pane,"summary")).setText(title);
        ((TextArea)control(pane,"body")).setText(description.strip());
        dialog.setDialogPane(pane);
        ButtonType first = primary == null ? null : new ButtonType(primary,ButtonBar.ButtonData.OK_DONE);
        ButtonType second = secondary == null ? null : new ButtonType(secondary,ButtonBar.ButtonData.OTHER);
        if (first != null) pane.getButtonTypes().add(first);
        if (second != null) pane.getButtonTypes().add(second);
        pane.getButtonTypes().add(ButtonType.CLOSE);
        ButtonType chosen = dialog.showAndWait().orElse(ButtonType.CLOSE);
        return chosen == first ? 1 : chosen == second ? 2 : 0;
    }
}
