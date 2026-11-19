package atlas.cliente;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public final class SessionView {
    private final Stage stage;
    private final String station;
    private final URL loginLayout;
    private final String title;
    private final Set<String> roles;
    private final Consumer<SessionView> opened;
    private final List<Runnable> listeners = new ArrayList<>();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(180));
    private final Timeline reconcile = new Timeline(new KeyFrame(Duration.seconds(12),e -> update()));
    private volatile Client client;
    private volatile Events events;
    private Label connection;
    private String address;
    private String mqttPassword;
    private boolean connected;
    private boolean connecting;
    private boolean disposed;

    public SessionView(Stage stage, String title, String station, Set<String> roles, Consumer<SessionView> opened) {
        this(stage,title,station,roles,opened,null);
    }

    public SessionView(Stage stage,String title,String station,Set<String> roles,Consumer<SessionView> opened,URL loginLayout) {
        this.loginLayout = loginLayout;
        this.stage = stage;
        this.title = title;
        this.station = station;
        this.roles = roles;
        this.opened = opened;
        debounce.setOnFinished(e -> List.copyOf(listeners).forEach(Runnable::run));
        reconcile.setCycleCount(Timeline.INDEFINITE);
        stage.setTitle(title);
        stage.setOnCloseRequest(e -> dispose());
    }

    public void start() { login(""); }
    public Client client() { return client; }

    public void root(Parent content) {
        Scene scene = stage.getScene();
        if (scene == null) {
            scene = new Scene(content,1280,800);
            scene.getStylesheets().add(resource(SessionView.class,"/atlasfx/atlas.css").toExternalForm());
            stage.setScene(scene);
            stage.setMinWidth(850);
            stage.setMinHeight(590);
        } else scene.setRoot(content);
        stage.show();
    }

    private void login(String message) {
        Parent screen = load(loginLayout == null ? resource(SessionView.class,"/atlasfx/login.fxml") : loginLayout);
        TextField server = control(screen,"server");
        TextField broker = control(screen,"broker");
        TextField username = control(screen,"username");
        PasswordField password = control(screen,"password");
        PasswordField mqtt = control(screen,"mqtt");
        Button submit = control(screen,"submit");
        Label status = control(screen,"message");
        Label app = control(screen,"app");
        if (loginLayout != null) {
            javafx.scene.layout.VBox rail = control(screen,"brandPanel");
            javafx.scene.layout.StackPane frame = control(screen,"brandFrame");
            javafx.scene.image.ImageView mark = control(screen,"brandMark");
            var available = javafx.beans.binding.Bindings.min(450,rail.widthProperty().subtract(64));
            mark.fitWidthProperty().bind(available);
            frame.prefHeightProperty().bind(available);
            frame.maxHeightProperty().bind(available);
            TextField visible = control(screen,"passwordVisible");
            Button reveal = control(screen,"showPassword");
            visible.textProperty().bindBidirectional(password.textProperty());
            reveal.setOnAction(e -> {
                boolean show = !visible.isVisible();
                visible.setVisible(show);
                visible.setManaged(show);
                password.setVisible(!show);
                password.setManaged(!show);
            });
        }
        app.setText(title);
        server.setText(Client.env("ATLAS_API_URL","http://127.0.0.1:8080"));
        broker.setText(Client.env("ATLAS_CLIENT_MQTT_URI","tcp://127.0.0.1:1883"));
        status.setText(message);
        submit.setOnAction(e -> {
            String apiUrl = server.getText();
            String brokerUrl = broker.getText().strip();
            String user = username.getText();
            String secret = password.getText();
            String mqttSecret = mqtt.getText();
            submit.setDisable(true);
            status.setText("Entrando…");
            async(() -> {
                if (brokerUrl.isBlank() || mqttSecret.isBlank()) throw new IllegalArgumentException("Informe o broker e a credencial MQTT.");
                var account = Client.login(apiUrl,user,secret);
                if (!roles.contains(account.role)) {
                    account.post("logout",Map.of());
                    throw new IllegalArgumentException("O perfil não tem acesso a este módulo.");
                }
                return account;
            }, account -> {
                submit.setDisable(false);
                password.clear();
                mqtt.clear();
                address = brokerUrl;
                mqttPassword = mqttSecret;
                client = account;
                opened.accept(this);
                connect();
                reconcile.play();
            }, error -> {
                submit.setDisable(false);
                status.setText(message(error));
            });
        });
        root(screen);
    }

    public void showConnection(Label label) {
        connection = label;
        label.setText(connected ? "MQTT conectado" : "MQTT aguardando conexão");
    }

    public void listen(Runnable callback) { listeners.add(callback); }

    public void reconnect() { connect(); }

    private void update() {
        if (client == null) return;
        if (!connected) connect();
        debounce.playFromStart();
    }

    private void connect() {
        if (connecting || connected || client == null || disposed) return;
        connecting = true;
        Client account = client;
        Events previous = events;
        String url = address;
        String secret = mqttPassword;
        async(() -> {
            Events link = previous == null ? new Events(account,url,secret,station,
                    up -> Platform.runLater(() -> {
                        if (client != account) return;
                        connected = up;
                        if (connection != null) connection.setText(up ? "MQTT conectado" : "MQTT desconectado");
                    }), () -> Platform.runLater(() -> {
                        if (client == account) debounce.playFromStart();
                    })) : previous;
            try { link.connect(); }
            catch (Exception ex) {
                if (previous == null) link.close();
                throw ex;
            }
            return link;
        }, link -> {
            connecting = false;
            if (client == account) events = link;
            else async(() -> { link.close(); return true; }, ignored -> {}, ignored -> {});
        }, error -> {
            connecting = false;
            if (client == account && connection != null) connection.setText("MQTT indisponível");
        });
    }

    public void publish(String event, Map<String,?> fields) throws Exception {
        Events link = events;
        Client account = client;
        if (link == null || account == null) throw new IllegalStateException("Broker MQTT desconectado.");
        link.publish(event,account.command(event,fields));
    }

    public void logout() {
        Client account = client;
        closeConnection();
        login("");
        if (account != null) async(() -> account.post("logout",Map.of()), ignored -> {}, ignored -> {});
    }

    public void dispose() { disposed = true; closeConnection(); }

    private void closeConnection() {
        reconcile.stop();
        debounce.stop();
        client = null;
        connected = false;
        listeners.clear();
        connection = null;
        mqttPassword = null;
        Events link = events;
        events = null;
        if (link != null) async(() -> { link.close(); return true; }, ignored -> {}, ignored -> {});
    }

    public static <T> void async(Callable<T> work, Consumer<T> success, Consumer<Throwable> failure) {
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return work.call(); } };
        task.setOnSucceeded(e -> success.accept(task.getValue()));
        task.setOnFailed(e -> failure.accept(task.getException()));
        Thread thread = Thread.ofVirtual().name("atlas-client").unstarted(task);
        thread.start();
    }

    public static Parent load(Class<?> owner,String path) {
        return load(resource(owner,path));
    }

    private static Parent load(URL location) {
        try {
            FXMLLoader loader = new FXMLLoader(location);
            Parent root = loader.load();
            root.getProperties().put("atlas.fxml.controls",new HashMap<>(loader.getNamespace()));
            return root;
        }
        catch (Exception ex) { throw new IllegalStateException("Erro ao abrir FXML " + location,ex); }
    }

    public static URL resource(Class<?> owner,String path) {
        URL url = owner.getResource(path);
        if (url != null) return url;
        for (String folder : List.of("02 - Desktop","04 - Terminal Bar","05 - Terminal Cozinha","09 - Clientes JavaFX",".")) {
            Path file = Path.of(folder,"resources",path.substring(1));
            if (Files.exists(file)) {
                try { return file.toUri().toURL(); }
                catch (java.net.MalformedURLException ex) { throw new IllegalStateException(ex); }
            }
        }
        throw new IllegalStateException("Recurso não encontrado: " + path);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Node> T control(Parent root, String id) {
        Object controls = root.getProperties().get("atlas.fxml.controls");
        Node node = controls instanceof Map<?,?> entries && entries.get(id) instanceof Node loaded
                ? loaded : root.lookup("#" + id);
        if (node == null) throw new IllegalStateException("Controle FXML ausente: " + id);
        return (T)node;
    }

    public static String message(Throwable error) {
        if (error instanceof Client.ApiError || error instanceof IllegalArgumentException
                || error instanceof IllegalStateException) return error.getMessage();
        return "Comunicação indisponível. Confira o endereço e tente novamente.";
    }
}
