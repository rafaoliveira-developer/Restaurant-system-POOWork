package atlas.bar;

import atlas.cliente.SessionView;
import atlas.cliente.TerminalView;
import javafx.application.Application;
import javafx.stage.Stage;
import java.util.Set;

public final class Bar extends Application {
    @Override public void start(Stage stage) {
        new SessionView(stage,"ATLAS · Terminal do Bar","BAR",Set.of("BAR"),session ->
                new TerminalView(session,"BAR",SessionView.load(Bar.class,"/fxml/bar.fxml"),() -> {})).start();
    }

    public static void main(String[] args) { launch(args); }
}
