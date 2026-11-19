package atlas.cozinha;

import atlas.cliente.*;
import atlas.impressao.servicos.Printing;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.stage.Stage;
import java.nio.file.Path;
import java.util.Set;
import static atlas.cliente.Client.env;
import static atlas.cliente.SessionView.load;

public final class Cozinha extends Application {
    @Override public void start(Stage stage) {
        new SessionView(stage,"ATLAS · Terminal da Cozinha","COZINHA",Set.of("COZINHA"),this::open).start();
    }

    private void open(SessionView session) {
        Parent layout = load(Cozinha.class,"/fxml/cozinha.fxml");
        var printing = Printing.configured(env("ATLAS_PRINT_MODE","simulado"),env("ATLAS_PRINTER_NAME",""),
                Path.of(env("ATLAS_PRINT_OUTPUT",".local/comprovantes")));
        new KitchenPrinting(session,layout,printing);
    }

    public static void main(String[] args) { launch(args); }
}
