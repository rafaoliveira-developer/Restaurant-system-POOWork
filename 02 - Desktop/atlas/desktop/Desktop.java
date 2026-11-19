package atlas.desktop;

import atlas.cliente.SessionView;
import atlas.desktop.controllers.Central;
import javafx.application.Application;
import javafx.stage.Stage;
import java.util.Set;

public final class Desktop extends Application {
    @Override public void start(Stage stage) {
        new SessionView(stage,"ATLAS Central",null,Set.of("GERENTE","CAIXA"),Central::new,
                SessionView.resource(Desktop.class,"/fxml/login.fxml")).start();
    }
    public static void main(String[] args) { launch(args); }
}
