package atlas.repositorios;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;

public final class Database {
    private final String url;
    private final String user;
    private final String password;

    public Database(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    @FunctionalInterface
    public interface Work<T> { T run(Connection connection) throws SQLException; }

    public <T> T tx(Work<T> work) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException ex) {
                try { connection.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                throw ex;
            }
        }
    }

    public void migrate(Path directory) throws SQLException, IOException {
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.getFileName().toString().matches("V\\d+__.+\\.sql")).sorted().toList()) {
                String version = file.getFileName().toString().split("__", 2)[0];
                String sql = Files.readString(file);
                tx(connection -> {
                    query(connection, "SELECT pg_advisory_xact_lock(20142609)");
                    update(connection, "CREATE TABLE IF NOT EXISTS schema_versions (version text PRIMARY KEY, applied_at timestamptz DEFAULT now())");
                    if (one(connection, "SELECT version FROM schema_versions WHERE version=?", version) == null) {
                        try (Statement statement = connection.createStatement()) { statement.execute(sql); }
                        update(connection, "INSERT INTO schema_versions(version) VALUES (?)", version);
                    }
                    return null;
                });
            }
        }
        tx(c -> {
            if (one(c, "SELECT to_regclass('public.employees') AS relation").get("relation") == null)
                throw new SQLException("As migrations do ATLAS não foram encontradas em " + directory);
            return null;
        });
    }

    public static List<Map<String,Object>> query(Connection connection, String sql, Object... parameters)
            throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, parameters);
             ResultSet rows = statement.executeQuery()) {
            List<Map<String,Object>> result = new ArrayList<>();
            ResultSetMetaData columns = rows.getMetaData();
            while (rows.next()) {
                Map<String,Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columns.getColumnCount(); i++) {
                    Object value = rows.getObject(i);
                    if (value instanceof Timestamp) value = rows.getObject(i, OffsetDateTime.class).toInstant().toString();
                    if (value instanceof UUID || value instanceof java.sql.Date) value = value.toString();
                    if (value instanceof org.postgresql.util.PGobject pg) value = pg.getValue();
                    row.put(columns.getColumnLabel(i), value);
                }
                result.add(row);
            }
            return result;
        }
    }

    public static Map<String,Object> one(Connection connection, String sql, Object... parameters)
            throws SQLException {
        List<Map<String,Object>> rows = query(connection, sql, parameters);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public static int update(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, parameters)) {
            return statement.executeUpdate();
        }
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object... parameters)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            statement.setQueryTimeout(12);
            for (int i = 0; i < parameters.length; i++) statement.setObject(i + 1, parameters[i]);
            return statement;
        } catch (SQLException ex) {
            try { statement.close(); } catch (SQLException close) { ex.addSuppressed(close); }
            throw ex;
        }
    }
}
