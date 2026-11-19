package atlas;

import atlas.autenticacao.Auth;
import atlas.comandas.Tabs;
import atlas.controladores.Api;
import atlas.mqtt.Broker;
import atlas.pedidos.Orders;
import atlas.repositorios.Database;
import com.sun.net.httpserver.*;
import javax.net.ssl.*;
import java.io.InputStream;
import java.net.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.concurrent.Executors;

public final class Backend {
    private Backend() {}

    public static void main(String[] args) throws Exception {
        var db = new Database(env("ATLAS_DB_URL","jdbc:postgresql://127.0.0.1:5432/atlas?connectTimeout=4"),
                env("ATLAS_DB_USER","atlas"),required("ATLAS_DB_PASSWORD"));
        db.migrate(Path.of(env("ATLAS_MIGRATIONS","07 - Infraestrutura/Banco de Dados")));
        var auth = new Auth(db);
        auth.bootstrap(System.getenv("ATLAS_ADMIN_USER"),System.getenv("ATLAS_ADMIN_PASSWORD"));
        var tabs = new Tabs();
        var orders = new Orders(tabs);
        Path mqttData = Path.of(env("ATLAS_MQTT_STATE",".local/backend-mqtt"));
        Files.createDirectories(mqttData);
        var broker = new Broker(db,auth,orders,env("ATLAS_MQTT_URI","tcp://127.0.0.1:1883"),
                env("ATLAS_MQTT_CLIENT_ID","atlas-backend"),env("ATLAS_MQTT_USER","atlas-backend"),
                required("ATLAS_MQTT_PASSWORD"),mqttData);
        String bind = env("ATLAS_BIND","127.0.0.1");
        int port = Integer.parseInt(env("ATLAS_PORT","8080"));
        HttpServer server = server(new InetSocketAddress(bind,port));
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/api/v1/",new Api(db,auth,orders,tabs,broker::connected));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            broker.close();
            executor.shutdown();
        }));
        broker.start();
        server.start();
        System.getLogger(Backend.class.getName()).log(System.Logger.Level.INFO,
                "API ATLAS em {0}:{1} ({2})",bind,port,server instanceof HttpsServer ? "HTTPS" : "HTTP");
    }

    private static HttpServer server(InetSocketAddress bind) throws Exception {
        String keystore = System.getenv("ATLAS_TLS_KEYSTORE");
        if (keystore == null || keystore.isBlank()) {
            if (!bind.getAddress().isLoopbackAddress() && !"true".equals(System.getenv("ATLAS_ALLOW_LAN_HTTP")))
                throw new IllegalArgumentException("Para rede local configure HTTPS ou autorize HTTP de desenvolvimento.");
            return HttpServer.create(bind,64);
        }
        char[] password = required("ATLAS_TLS_PASSWORD").toCharArray();
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream bytes = Files.newInputStream(Path.of(keystore))) { store.load(bytes,password); }
        var factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        factory.init(store,password);
        var ssl = SSLContext.getInstance("TLS");
        ssl.init(factory.getKeyManagers(),null,null);
        var server = HttpsServer.create(bind,64);
        server.setHttpsConfigurator(new HttpsConfigurator(ssl));
        return server;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String required(String key) {
        String value = env(key,"");
        if (value.isEmpty()) throw new IllegalArgumentException("Defina " + key);
        return value;
    }
}
