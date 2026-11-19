package atlas.cliente;

import com.google.gson.JsonObject;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.function.Consumer;

public final class Events implements AutoCloseable {
    private static final String ROOT = "atlas/v1/";
    private final MqttAsyncClient mqtt;
    private final MqttConnectOptions options = new MqttConnectOptions();
    private final Client user;

    public Events(Client user,String address,String password,String station,Consumer<Boolean> connection,Runnable update)
            throws Exception {
        this.user = user;
        String identity = user.username + "-" + (station == null ? "desktop" : station.toLowerCase());
        Path directory = Path.of(".local","clients",identity);
        Files.createDirectories(directory);
        Path device = directory.resolve("device-id");
        if (!Files.exists(device)) {
            try { Files.writeString(device,java.util.UUID.randomUUID().toString(),StandardOpenOption.CREATE_NEW); }
            catch (FileAlreadyExistsException ignored) {}
        }
        mqtt = new MqttAsyncClient(address,"afx-" + Files.readString(device).strip(),new MqttDefaultFilePersistence(directory.toString()));
        options.setCleanSession(false);
        options.setAutomaticReconnect(true);
        options.setUserName(user.username);
        options.setPassword(password.toCharArray());
        options.setConnectionTimeout(4);
        options.setKeepAliveInterval(15);
        mqtt.setCallback(new MqttCallbackExtended() {
            public void connectComplete(boolean reconnect, String uri) {
                try {
                    mqtt.subscribe(ROOT + "updates",1);
                    if (station != null) mqtt.subscribe(ROOT + "stations/" + station + "/+",1);
                    connection.accept(true);
                    update.run();
                } catch (MqttException ex) { connection.accept(false); }
            }
            public void connectionLost(Throwable cause) { connection.accept(false); }
            public void deliveryComplete(IMqttDeliveryToken token) {}
            public void messageArrived(String topic,MqttMessage message) {
                if (!message.isRetained()) update.run();
            }
        });
    }

    public void connect() throws MqttException {
        if (!mqtt.isConnected()) mqtt.connect(options).waitForCompletion(5_000);
    }

    public void publish(String event, JsonObject body) throws MqttException {
        if (!mqtt.isConnected()) throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        mqtt.publish(ROOT + "commands/" + user.username + "/" + event,
                body.toString().getBytes(StandardCharsets.UTF_8),1,false).waitForCompletion(5_000);
    }

    @Override public void close() {
        try {
            if (mqtt.isConnected()) mqtt.disconnect().waitForCompletion(2_000);
            mqtt.close();
        } catch (MqttException ignored) { }
    }
}
