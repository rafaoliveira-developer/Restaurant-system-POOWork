package atlas.mqtt;

import atlas.autenticacao.Auth;
import atlas.modelos.Problem;
import atlas.pedidos.Orders;
import atlas.repositorios.Database;
import com.google.gson.JsonObject;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.*;
import static atlas.repositorios.Database.*;
import static atlas.util.Json.*;

public final class Broker implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(Broker.class.getName());
    private final Database database;
    private final Auth auth;
    private final Orders orders;
    private final MqttAsyncClient client;
    private final MqttConnectOptions options = new MqttConnectOptions();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean connecting;

    public Broker(Database database, Auth auth, Orders orders, String uri, String id, String username,
                  String password, Path persistence) throws MqttException {
        this.database = database;
        this.auth = auth;
        this.orders = orders;
        client = new MqttAsyncClient(uri,id,new MqttDefaultFilePersistence(persistence.toString()));
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        options.setAutomaticReconnect(true);
        options.setCleanSession(false);
        options.setConnectionTimeout(5);
        options.setKeepAliveInterval(15);
        client.setCallback(new MqttCallbackExtended() {
            public void connectComplete(boolean reconnect, String serverURI) {
                try { client.subscribe(Topics.STATION_COMMAND,1); }
                catch (MqttException ex) { LOG.log(System.Logger.Level.WARNING,"Assinatura MQTT indisponível",ex); }
            }
            public void connectionLost(Throwable cause) {
                LOG.log(System.Logger.Level.WARNING,"Conexão MQTT interrompida");
            }
            public void deliveryComplete(IMqttDeliveryToken token) {}
            public void messageArrived(String topic, MqttMessage message) throws SQLException {
                if (!message.isRetained()) receive(topic,message.getPayload());
            }
        });
    }

    public void start() {
        worker.scheduleWithFixedDelay(this::tick,0,400,TimeUnit.MILLISECONDS);
    }

    public boolean connected() { return client.isConnected(); }

    private void receive(String topic, byte[] bytes) throws SQLException {
        if (bytes.length > 65_536) return;
        String[] bits = topic.split("/", -1);
        if (bits.length != 5) return;
        String event = bits[4];
        if (!event.equals("NOVOPEDIDO") && !event.equals("CONFIRMABAR") && !event.equals("CONFIRMACOZINHA")) return;
        JsonObject body;
        try {
            body = parse(new String(bytes,StandardCharsets.UTF_8));
            if (!event.equals(str(body,"event",30,true)) || id(body,"version") != 1) return;
        } catch (Problem ex) {
            LOG.log(System.Logger.Level.WARNING,"Mensagem MQTT inválida em {0}",event);
            return;
        }
        try {
            database.tx(c -> {
                var actor = auth.authenticate(c,str(body,"token",43,true));
                if (!actor.username().equals(bits[3])) throw new Problem(403,"Tópico e sessão incompatíveis.");
                if (event.equals("NOVOPEDIDO")) orders.accept(c,actor,body);
                else orders.process(c,actor,event.equals("CONFIRMABAR") ? "BAR" : "COZINHA",body);
                return null;
            });
        } catch (Problem ex) {
            LOG.log(System.Logger.Level.WARNING,"Comando MQTT recusado: {0} ({1})",event,ex.status());
        }
    }

    private void tick() {
        try {
            if (!client.isConnected()) {
                if (!connecting) {
                    connecting = true;
                    try {
                        client.connect(options,null,new IMqttActionListener() {
                            public void onSuccess(IMqttToken token) { connecting = false; }
                            public void onFailure(IMqttToken token, Throwable error) { connecting = false; }
                        });
                    } catch (MqttException ex) {
                        connecting = false;
                        if (ex.getReasonCode() != MqttException.REASON_CODE_CONNECT_IN_PROGRESS) throw ex;
                    }
                }
                return;
            }
            database.tx(c -> {
                var pending = query(c,"SELECT id,topic,payload FROM outbox WHERE published_at IS NULL ORDER BY id LIMIT 30 FOR UPDATE SKIP LOCKED");
                for (var row : pending) {
                    try {
                        var message = new MqttMessage(row.get("payload").toString().getBytes(StandardCharsets.UTF_8));
                        message.setQos(1);
                        message.setRetained(false);
                        client.publish(row.get("topic").toString(),message).waitForCompletion(4_000);
                    } catch (MqttException ex) {
                        throw new SQLException("Publicação MQTT indisponível",ex);
                    }
                    update(c,"UPDATE outbox SET published_at=now() WHERE id=?",row.get("id"));
                }
                return null;
            });
        } catch (SQLException | MqttException ex) {
            LOG.log(System.Logger.Level.WARNING,"Fila MQTT aguardando recuperação: {0}",ex.getMessage());
        }
    }

    @Override public void close() {
        worker.shutdownNow();
        try {
            if (client.isConnected()) client.disconnect().waitForCompletion(1_500);
            client.close();
        } catch (MqttException ex) {
            LOG.log(System.Logger.Level.WARNING,"Erro ao fechar MQTT: {0}",ex.getMessage());
        }
    }
}
