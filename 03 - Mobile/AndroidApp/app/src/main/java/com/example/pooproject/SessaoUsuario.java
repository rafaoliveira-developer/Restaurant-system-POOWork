package com.example.pooproject;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;

public final class SessaoUsuario {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService TASKS = Executors.newSingleThreadExecutor();
    private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile SessaoUsuario active;
    private final SharedPreferences data;
    private final String server;
    private final MqttAsyncClient mqtt;
    private final MqttConnectOptions options;
    private final String token;
    final String username;
    final String name;
    final long employeeId;
    private volatile boolean connected;

    private SessaoUsuario(SharedPreferences data,String server,String broker,String mqttSecret,JSONObject login) throws Exception {
        this.data = data;
        this.server = server;
        token = login.getString("token");
        JSONObject employee = login.getJSONObject("employee");
        if (!employee.getString("role").equals("GARCOM")) {
            http(server,token,"logout",new JSONObject());
            throw new IOException("Entre com uma conta de garçom.");
        }
        username = employee.getString("username");
        name = employee.getString("name");
        employeeId = employee.getLong("id");
        String key = "device:" + server + ":" + username;
        String id = data.getString(key,null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            data.edit().putString(key,id).apply();
        }
        mqtt = new MqttAsyncClient(broker,"atlas-android-" + id,new MemoryPersistence());
        options = new MqttConnectOptions();
        options.setUserName(username);
        options.setPassword(mqttSecret.toCharArray());
        options.setCleanSession(false);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(5);
        options.setKeepAliveInterval(18);
        mqtt.setCallback(new MqttCallbackExtended() {
            public void connectComplete(boolean reconnect,String uri) {
                try {
                    mqtt.subscribe(new String[]{"atlas/v1/updates","atlas/v1/users/" + username + "/CONFIRMAREPEDIDO"},
                            new int[]{1,1});
                    connected = true;
                    update();
                } catch (MqttException ignored) {
                    connected = false;
                    update();
                }
            }
            public void connectionLost(Throwable cause) { connected = false; update(); }
            public void deliveryComplete(IMqttDeliveryToken token) {}
            public void messageArrived(String topic,MqttMessage message) {
                if (!message.isRetained()) update();
            }
        });
    }

    static SessaoUsuario login(Context context,String url,String broker,String username,String password,String mqttSecret)
            throws Exception {
        String server = url.trim().replaceAll("/+$","");
        URI api = URI.create(server);
        boolean debug = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (api.getHost() == null || !(api.getScheme().equals("https") || debug && api.getScheme().equals("http"))
                || api.getRawUserInfo() != null || api.getRawQuery() != null || api.getRawFragment() != null)
            throw new IOException("Informe um endereço HTTPS válido para o ATLAS.");
        URI endpoint = URI.create(broker.trim());
        if (endpoint.getHost() == null || !("ssl".equals(endpoint.getScheme()) || debug && "tcp".equals(endpoint.getScheme())))
            throw new IOException("Informe o broker no formato ssl://servidor:8883.");
        if (mqttSecret.isEmpty()) throw new IOException("Informe a senha MQTT individual.");
        var prefs = context.getSharedPreferences("atlas-session-settings",Context.MODE_PRIVATE);
        JSONObject body = new JSONObject().put("username",username.trim()).put("password",password);
        var session = new SessaoUsuario(prefs,server,broker.trim(),mqttSecret,(JSONObject)http(server,"","login",body));
        prefs.edit().putString("server",server).putString("broker",broker.trim()).apply();
        active = session;
        try { session.connect(); } catch (MqttException ignored) { update(); }
        return session;
    }

    static SessaoUsuario get() { return active; }
    boolean connected() { return connected; }
    Object get(String route) throws Exception { return http(server,token,route,null); }
    JSONObject post(String route,JSONObject body) throws Exception { return (JSONObject) http(server,token,route,body); }

    void connect() throws MqttException {
        if (!mqtt.isConnected()) mqtt.connect(options).waitForCompletion(5_500);
    }

    void logout() {
        if (active == this) active = null;
        try { if (mqtt.isConnected()) mqtt.disconnect().waitForCompletion(1_000); mqtt.close(); }
        catch (MqttException ignored) {}
        try { post("logout",new JSONObject()); } catch (Exception ignored) {}
    }

    private String key(long table) { return "order:" + server + ":" + username + ":" + table; }

    JSONObject pending(long table) throws JSONException {
        String value = data.getString(key(table),null);
        return value == null ? null : new JSONObject(value);
    }

    void clear(long table) { data.edit().remove(key(table)).apply(); }

    void send(long table, JSONArray items) throws Exception {
        JSONObject command = pending(table);
        if (command == null) {
            if (items == null || items.length() == 0) throw new IOException("Adicione um produto ao pedido.");
            command = new JSONObject().put("event","NOVOPEDIDO").put("version",1)
                    .put("orderId",UUID.randomUUID().toString()).put("idempotencyKey",UUID.randomUUID().toString())
                    .put("tableId",table).put("items",items);
            if (!data.edit().putString(key(table),command.toString()).commit())
                throw new IOException("O aplicativo não conseguiu guardar o pedido para recuperação.");
        }
        if (!mqtt.isConnected()) connect();
        command.put("token",token);
        mqtt.publish("atlas/v1/commands/" + username + "/NOVOPEDIDO",
                command.toString().getBytes(StandardCharsets.UTF_8),1,false).waitForCompletion(5_000);
    }

    private static Object http(String server,String token,String route,JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)new URL(server + "/api/v1/" + route).openConnection();
        try {
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(9_000);
            connection.setInstanceFollowRedirects(false);
            if (!token.isEmpty()) connection.setRequestProperty("Authorization","Bearer " + token);
            if (body != null) {
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type","application/json; charset=UTF-8");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int result = connection.getResponseCode();
            InputStream stream = result == 200 ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) throw new ApiError(result,"Servidor indisponível.");
            try (InputStream source = stream; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int size;
                while ((size = source.read(buffer)) != -1) bytes.write(buffer,0,size);
                Object value = new JSONTokener(bytes.toString("UTF-8")).nextValue();
                if (result != 200) throw new ApiError(result,((JSONObject)value).optString("error","Falha na solicitação."));
                return value;
            }
        } finally { connection.disconnect(); }
    }

    static void listen(Runnable callback) { LISTENERS.add(callback); }
    static void unlisten(Runnable callback) { LISTENERS.remove(callback); }
    private static void update() { MAIN.post(() -> LISTENERS.forEach(Runnable::run)); }

    interface Response<T> { void accept(T value); }
    static <T> void run(Callable<T> work,Response<T> success,Response<Exception> error) {
        TASKS.execute(() -> {
            try {
                T result = work.call();
                MAIN.post(() -> success.accept(result));
            } catch (Exception failure) { MAIN.post(() -> error.accept(failure)); }
        });
    }

    static final class ApiError extends IOException {
        final int status;
        ApiError(int status,String message) { super(message); this.status = status; }
    }
}
