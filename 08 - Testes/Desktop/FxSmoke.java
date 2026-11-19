import atlas.bar.Bar;
import atlas.cliente.SessionView;
import atlas.cliente.Client;
import com.google.gson.JsonObject;
import org.eclipse.paho.client.mqttv3.*;
import java.nio.charset.StandardCharsets;
import atlas.cozinha.Cozinha;
import atlas.desktop.Desktop;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import static atlas.cliente.SessionView.control;

public final class FxSmoke {
    private static final String PASSWORD = System.getenv("ATLAS_IT_PASSWORD");
    private static final String BROKER = System.getenv("ATLAS_IT_BROKER");
    private static final String URL = System.getenv("ATLAS_IT_URL");
    private static final List<Stage> WINDOWS = new ArrayList<>();
    private static final Path IMAGES = Path.of(System.getenv("ATLAS_IT_OUTPUT")).getParent();

    public static void main(String[] args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((thread,error) -> {
            error.printStackTrace();
            System.exit(2);
        });
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            fx(() -> {
                for (String page : List.of("pedidos","salao","cardapio","funcionarios","caixa","relatorios","historico","configuracoes"))
                    SessionView.load(Desktop.class,"/fxml/" + page + "/" + page + ".fxml");
                for (String page : List.of("login","central","inicio/inicio","inicio/indicador","detalhes","editar","caixa/linha-conta"))
                    SessionView.load(Desktop.class,"/fxml/" + page + ".fxml");
                SessionView.load(Bar.class,"/fxml/bar.fxml");
                SessionView.load(Cozinha.class,"/fxml/cozinha.fxml");
                return true;
            });
            Stage desktop = open(new Desktop(),"gerencia","desktop-login.png");
            await(desktop,root -> root.lookup("#content") != null);
            snapshot(desktop,"desktop-inicio.png");
            for (String item : List.of("Pedidos","Salão","Menu","Funcionários","Relatórios","Histórico","Configurações","Contas solicitadas")) {
                fx(() -> {
                    desktop.getScene().getRoot().lookupAll(".nav").stream().map(Button.class::cast)
                            .filter(button -> button.getAccessibleText().equals(item)).findFirst().orElseThrow().fire();
                    return true;
                });
                await(desktop,root -> root.lookup("#table") instanceof TableView<?> table && !table.getItems().isEmpty());
            }
            snapshot(desktop,"desktop-caixa.png");
            fx(() -> { ((Button)desktop.getScene().getRoot().lookup("#toggle")).fire(); return true; });
            Stage bar = open(new Bar(),"bar",null);
            await(bar,root -> root.lookup("#queue") instanceof ListView<?> queue && !queue.getItems().isEmpty());
            snapshot(bar,"terminal-bar.png");
            Stage kitchen = open(new Cozinha(),"cozinha",null);
            await(kitchen,root -> root.lookup("#queue") instanceof ListView<?> queue && !queue.getItems().isEmpty());
            liveOrder(bar,kitchen);
            snapshot(kitchen,"terminal-cozinha.png");
            System.out.println("PASSOU: 18 FXML, navegação, pedido misto e confirmação nos terminais JavaFX.");
        } finally {
            fx(() -> {
                WINDOWS.forEach(stage -> {
                    if (stage.getOnCloseRequest() != null) stage.getOnCloseRequest().handle(null);
                    stage.hide();
                });
                return true;
            });
            Platform.exit();
        }
    }

    private static void liveOrder(Stage bar,Stage kitchen) throws Exception {
        await(bar,root -> hasConnection(root));
        await(kitchen,root -> hasConnection(root));
        Client waiter = Client.login(URL,"garcom",PASSWORD);
        Client manager = Client.login(URL,"gerencia",PASSWORD);
        var table = manager.post("tables",Map.of("label","Mesa Verificação UI","area","Salão"))
                .getAsJsonObject().get("id").getAsLong();
        List<Map<String,Long>> items = new ArrayList<>();
        for (var product : waiter.get("products").getAsJsonArray()) {
            items.add(Map.of("productId",product.getAsJsonObject().get("id").getAsLong(),"quantity",1L));
        }
        String order = UUID.randomUUID().toString();
        var payload = waiter.command("NOVOPEDIDO",Map.of("orderId",order,"idempotencyKey",order,"tableId",table,"items",items));
        MqttClient mqtt = new MqttClient(BROKER,"it-fx-waiter",new org.eclipse.paho.client.mqttv3.persist.MemoryPersistence());
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName("garcom");
            options.setPassword(PASSWORD.toCharArray());
            mqtt.connect(options);
            mqtt.publish("atlas/v1/commands/garcom/NOVOPEDIDO",payload.toString().getBytes(StandardCharsets.UTF_8),1,false);
        } finally {
            if (mqtt.isConnected()) mqtt.disconnect();
            mqtt.close();
        }
        selectOrder(bar,order);
        selectOrder(kitchen,order);
        fire(bar,"start");
        await(bar,root -> ((TextArea)root.lookup("#details")).getText().contains("Em preparo"));
        fire(kitchen,"finish");
        fire(bar,"finish");
        var result = waiter.get("orders/" + order).getAsJsonObject();
        for (int i=0; i<35 && !"CONFIRMADO".equals(Client.text(result,"status")); i++) {
            Thread.sleep(300);
            result = waiter.get("orders/" + order).getAsJsonObject();
        }
        if (!"11".equals(Client.text(result,"code"))) throw new AssertionError("O pedido misto não foi confirmado pelas duas interfaces.");
        Path receipts = Path.of(System.getenv("ATLAS_PRINT_OUTPUT"));
        for (int i=0; i<30; i++) {
            if (Files.isDirectory(receipts)) {
                try (var files = Files.list(receipts)) {
                    if (files.anyMatch(path -> path.toString().endsWith(".txt"))) return;
                }
            }
            Thread.sleep(250);
        }
        throw new AssertionError("O terminal não gerou o comprovante da cozinha.");
    }

    private static boolean hasConnection(Parent root) {
        return root.lookup("#connection") instanceof Label label && label.getText().equals("MQTT conectado");
    }

    @SuppressWarnings("unchecked")
    private static void selectOrder(Stage stage,String id) throws Exception {
        await(stage,root -> root.lookup("#queue") instanceof ListView<?> q && q.getItems().stream()
                .anyMatch(row -> id.equals(Client.text((JsonObject)row,"order_id"))));
        fx(() -> {
            ListView<JsonObject> queue = (ListView<JsonObject>)stage.getScene().getRoot().lookup("#queue");
            queue.getItems().stream().filter(row -> id.equals(Client.text(row,"order_id"))).findFirst()
                    .ifPresent(queue.getSelectionModel()::select);
            return true;
        });
    }

    private static void fire(Stage stage,String id) throws Exception {
        await(stage,root -> root.lookup("#" + id) instanceof Button button && !button.isDisabled());
        fx(() -> { ((Button)stage.getScene().getRoot().lookup("#" + id)).fire(); return true; });
    }

    private static Stage open(javafx.application.Application app,String identity,String screenshot) throws Exception {
        Stage stage = fx(() -> {
            Stage window = new Stage();
            WINDOWS.add(window);
            app.start(window);
            return window;
        });
        if (screenshot != null) snapshot(stage,screenshot);
        fx(() -> {
            Parent root = stage.getScene().getRoot();
            ((TextField)control(root,"server")).setText(URL);
            ((TextField)control(root,"broker")).setText(BROKER);
            ((TextField)control(root,"username")).setText(identity);
            ((PasswordField)control(root,"password")).setText(PASSWORD);
            ((PasswordField)control(root,"mqtt")).setText(PASSWORD);
            ((Button)control(root,"submit")).fire();
            return true;
        });
        return stage;
    }

    private static void await(Stage stage,Predicate<Parent> condition) throws Exception {
        for (int i=0; i<40; i++) {
            if (fx(() -> condition.test(stage.getScene().getRoot()))) return;
            Thread.sleep(250);
        }
        throw new AssertionError("Interface não chegou ao estado esperado: " + stage.getTitle());
    }

    private static void snapshot(Stage stage,String filename) throws Exception {
        WritableImage image = fx(() -> stage.getScene().snapshot(null));
        ImageIO.write(SwingFXUtils.fromFXImage(image,null),"png",IMAGES.resolve(filename).toFile());
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(20,TimeUnit.SECONDS);
    }
}
