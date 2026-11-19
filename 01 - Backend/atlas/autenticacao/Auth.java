package atlas.autenticacao;

import atlas.modelos.Actor;
import atlas.modelos.Problem;
import atlas.repositorios.Database;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import static atlas.repositorios.Database.*;

public final class Auth {
    private static final int ITERATIONS = 600_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final String DUMMY = hashPassword("verification-placeholder");
    private final Database database;

    public Auth(Database database) { this.database = database; }

    public Map<String,Object> login(String username, String password) throws SQLException {
        return database.tx(c -> {
            Map<String,Object> employee = one(c, "SELECT * FROM employees WHERE username=? AND active FOR SHARE", username);
            String stored = employee == null ? DUMMY : (String) employee.get("password_hash");
            if (!verify(password, stored) || employee == null) throw unauthorized();
            Actor actor = actor(employee);
            String token = random(32);
            update(c, "DELETE FROM sessions WHERE expires_at <= now()");
            update(c, "INSERT INTO sessions VALUES (?,?,now()+interval '12 hours')", sha(token), actor.id());
            return Map.of("token", token, "employee", actor);
        });
    }

    public Actor authenticate(Connection c, String token) throws SQLException {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw unauthorized();
        var row = one(c, """
                SELECT e.id,e.username,e.name,e.role FROM employees e
                JOIN sessions s ON s.employee_id=e.id
                WHERE s.token_hash=? AND s.expires_at>now() AND e.active
                """, sha(token));
        if (row == null) throw unauthorized();
        return actor(row);
    }

    public void logout(String token) throws SQLException {
        database.tx(c -> {
            authenticate(c, token);
            update(c, "DELETE FROM sessions WHERE token_hash=?", sha(token));
            return null;
        });
    }

    public void bootstrap(String username, String password) throws SQLException {
        if (username == null || password == null) return;
        validateUsername(username);
        validatePassword(password);
        database.tx(c -> {
            if (one(c, "SELECT id FROM employees WHERE role='GERENTE' LIMIT 1") == null)
                update(c, "INSERT INTO employees(username,name,role,password_hash) VALUES (?,?,'GERENTE',?)",
                        username, "Gerência ATLAS", hashPassword(password));
            return null;
        });
    }

    private static Actor actor(Map<String,Object> row) {
        return new Actor(((Number)row.get("id")).longValue(), (String)row.get("username"),
                (String)row.get("name"), (String)row.get("role"));
    }

    public static void validateUsername(String username) {
        if (!username.matches("[a-z0-9_-]{3,48}"))
            throw Problem.invalid("O código deve ter de 3 a 48 caracteres: a–z, 0–9, - ou _.");
    }

    public static void validatePassword(String password) {
        if (password.length() < 12 || password.length() > 160)
            throw Problem.invalid("A senha deve ter de 12 a 160 caracteres.");
    }

    public static String hashPassword(String password) {
        String salt = random(16);
        return salt + ":" + ENCODER.encodeToString(derive(password, Base64.getUrlDecoder().decode(salt)));
    }

    private static boolean verify(String password, String stored) {
        String[] pieces = stored.split(":", 2);
        return MessageDigest.isEqual(derive(password, Base64.getUrlDecoder().decode(pieces[0])),
                Base64.getUrlDecoder().decode(pieces[1]));
    }

    private static byte[] derive(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        } finally {
            spec.clearPassword();
        }
    }

    public static String sha(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String random(int size) {
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    private static Problem unauthorized() { return new Problem(401, "Credenciais inválidas ou sessão expirada."); }
}
