package atlas.mqtt;

import atlas.util.Json;
import java.sql.*;
import java.util.Map;
import static atlas.repositorios.Database.update;

public final class Topics {
    public static final String ROOT = "atlas/v1/";
    public static final String ORDER_COMMAND = ROOT + "commands/+/NOVOPEDIDO";
    public static final String STATION_COMMAND = ROOT + "commands/+/+";
    public static final String UPDATES = ROOT + "updates";
    private Topics() {}

    public static String result(String username) { return ROOT + "users/" + username + "/CONFIRMAREPEDIDO"; }
    public static String station(String station) { return ROOT + "stations/" + station + (station.equals("BAR") ? "/ENVIABAR" : "/ENVIACOZINHA"); }

    public static void enqueue(Connection c, String topic, Object payload) throws SQLException {
        update(c, "INSERT INTO outbox(topic,payload) VALUES (?,?)", topic, Json.encode(payload));
    }

    public static void changed(Connection c, String resource) throws SQLException {
        enqueue(c, UPDATES, Map.of("event", "ATUALIZACAO", "version", 1, "resource", resource));
    }
}
