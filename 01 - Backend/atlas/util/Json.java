package atlas.util;

import atlas.modelos.Problem;
import com.google.gson.*;
import java.math.BigDecimal;
import java.util.UUID;

public final class Json {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private Json() {}

    public static JsonObject parse(String value) {
        try {
            return JsonParser.parseString(value).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException ex) {
            throw Problem.invalid("Objeto JSON inválido.");
        }
    }

    public static String encode(Object value) { return GSON.toJson(value); }

    public static String str(JsonObject body, String key, int limit, boolean required) {
        JsonElement value = body.get(key);
        if ((value == null || value.isJsonNull()) && !required) return "";
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw Problem.invalid("Campo inválido: " + key);
        String text = value.getAsString().strip();
        if (text.length() > limit || text.indexOf(0) >= 0 || (required && text.isBlank()))
            throw Problem.invalid("Campo inválido: " + key);
        return text;
    }

    public static BigDecimal decimal(JsonObject body, String key) {
        JsonElement value = body.get(key);
        try {
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                throw new NumberFormatException();
            return value.getAsBigDecimal();
        } catch (NumberFormatException ex) {
            throw Problem.invalid("Informe um número em " + key);
        }
    }

    public static long id(JsonObject body, String key) {
        try {
            long id = decimal(body, key).longValueExact();
            if (id > 0) return id;
        } catch (ArithmeticException ex) {
            throw Problem.invalid("Identificador inválido: " + key);
        }
        throw Problem.invalid("Identificador inválido: " + key);
    }

    public static UUID uuid(JsonObject body, String key) {
        return uuid(str(body, key, 36, true));
    }

    public static UUID uuid(String raw) {
        try {
            UUID id = UUID.fromString(raw);
            if (!id.toString().equalsIgnoreCase(raw)) throw new IllegalArgumentException();
            return id;
        } catch (IllegalArgumentException ex) {
            throw Problem.invalid("UUID inválido.");
        }
    }

    public static boolean flag(JsonObject body, String key, boolean fallback) {
        JsonElement value = body.get(key);
        if (value == null) return fallback;
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean())
            return value.getAsBoolean();
        throw Problem.invalid("Campo booleano inválido: " + key);
    }

    public static String choice(JsonObject body, String key, String... allowed) {
        String value = str(body, key, 32, true);
        for (String option : allowed) if (option.equals(value)) return value;
        throw Problem.invalid("Opção inválida: " + key);
    }
}
