package atlas.cliente;

import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

public final class Client {
    private static final Gson GSON = new Gson();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final String server;
    private final String token;
    public final String username;
    public final String name;
    public final String role;

    private Client(String server, String token, String username, String name, String role) {
        this.server = server;
        this.token = token;
        this.username = username;
        this.name = name;
        this.role = role;
    }

    public static Client login(String server, String username, String password) throws Exception {
        String url = server.strip().replaceAll("/+$","");
        URI uri = URI.create(url);
        if (uri.getHost() == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getRawQuery() != null || uri.getRawUserInfo() != null || uri.getRawFragment() != null)
            throw new IOException("Informe um endereço HTTP(S) válido para o Backend.");
        Client anonymous = new Client(url,"","","","");
        var login = anonymous.post("login",Map.of("username",username.strip(),"password",password)).getAsJsonObject();
        var employee = login.getAsJsonObject("employee");
        return new Client(url,text(login,"token"),text(employee,"username"),text(employee,"name"),text(employee,"role"));
    }

    public JsonElement get(String route) throws Exception { return send("GET",route,null); }
    public JsonElement post(String route, Map<String,?> body) throws Exception { return send("POST",route,body); }
    public JsonElement put(String route, Map<String,?> body) throws Exception { return send("PUT",route,body); }

    private JsonElement send(String method, String route, Map<String,?> body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(server + "/api/v1/" + route))
                .timeout(Duration.ofSeconds(12)).header("Accept","application/json");
        if (!token.isEmpty()) builder.header("Authorization","Bearer " + token);
        if (body == null) builder.GET();
        else builder.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
        var response = http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
        JsonElement json = JsonParser.parseString(response.body());
        if (response.statusCode() != 200) throw new ApiError(response.statusCode(),text(json.getAsJsonObject(),"error"));
        return json;
    }

    public JsonObject command(String event, Map<String,?> fields) {
        JsonObject json = GSON.toJsonTree(fields).getAsJsonObject();
        json.addProperty("event",event);
        json.addProperty("version",1);
        json.addProperty("token",token);
        return json;
    }

    public static String text(JsonObject row, String field) {
        var value = row.get(field);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    public static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public static final class ApiError extends IOException {
        public final int status;
        public ApiError(int status,String message) { super(message); this.status=status; }
    }
}
