package atlas.controladores;

import atlas.autenticacao.Auth;
import atlas.comandas.Tabs;
import atlas.modelos.*;
import atlas.pedidos.Orders;
import atlas.repositorios.Database;
import atlas.servicos.*;
import atlas.util.Json;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;
import static atlas.util.Json.*;
import static atlas.repositorios.Database.one;

public final class Api implements HttpHandler {
    private static final System.Logger LOG = System.getLogger(Api.class.getName());
    private final Database database;
    private final Auth auth;
    private final Orders orders;
    private final Tabs tabs;
    private final Catalog catalog = new Catalog();
    private final PrintJobs printing = new PrintJobs();
    private final Reports reports = new Reports();
    private final BooleanSupplier broker;
    private final Semaphore logins = new Semaphore(4);
    private final Semaphore requests = new Semaphore(40);

    public Api(Database database, Auth auth, Orders orders, Tabs tabs, BooleanSupplier broker) {
        this.database = database;
        this.auth = auth;
        this.orders = orders;
        this.tabs = tabs;
        this.broker = broker;
    }

    @Override public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!requests.tryAcquire()) {
                send(exchange,503,Map.of("error","Servidor ocupado. Tente novamente."));
                return;
            }
            try {
                send(exchange,200,route(exchange));
            } catch (Problem ex) {
                send(exchange,ex.status(),Map.of("error",ex.getMessage()));
            } catch (SQLException ex) {
                LOG.log(System.Logger.Level.ERROR,"Falha no PostgreSQL",ex);
                send(exchange,503,Map.of("error","Operação indisponível no banco de dados."));
            } catch (RuntimeException ex) {
                LOG.log(System.Logger.Level.ERROR,"Falha na API",ex);
                send(exchange,500,Map.of("error","Erro interno do ATLAS."));
            } finally {
                requests.release();
            }
        }
    }

    private Object route(HttpExchange exchange) throws SQLException, IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        if (!path.startsWith("/api/v1/")) throw Problem.missing("Rota inexistente.");
        String[] parts = path.substring(8).split("/",-1);
        String resource = parts[0];
        String token = bearer(exchange);
        if (parts.length == 1 && resource.equals("health") && method.equals("GET"))
            return database.tx(c -> Map.of("database","OK","mqtt",broker.getAsBoolean() ? "OK" : "OFFLINE"));
        if (parts.length == 1 && resource.equals("login") && method.equals("POST")) {
            JsonObject body = read(exchange);
            if (!logins.tryAcquire()) throw new Problem(429,"Muitas autenticações simultâneas. Tente novamente.");
            try { return auth.login(str(body,"username",48,true),str(body,"password",160,true)); }
            finally { logins.release(); }
        }
        if (parts.length == 1 && resource.equals("logout") && method.equals("POST")) {
            auth.logout(token);
            return Map.of("status","OK");
        }
        JsonObject body = method.equals("POST") || method.equals("PUT") ? read(exchange) : null;
        return database.tx(c -> authorized(c,auth.authenticate(c,token),method,parts,body,params(exchange)));
    }

    private Object authorized(Connection c, Actor actor, String method, String[] p, JsonObject body,
                              Map<String,String> params) throws SQLException {
        boolean get = method.equals("GET");
        boolean post = method.equals("POST");
        boolean put = method.equals("PUT");
        if (p.length == 1 && get) {
            return switch (p[0]) {
                case "me" -> actor;
                case "products" -> catalog.products(c,actor);
                case "tables" -> catalog.tables(c,actor);
                case "employees" -> catalog.employees(c,actor);
                case "orders" -> orders.list(c,actor);
                case "payments" -> tabs.payments(c,actor);
                case "closing-queue" -> tabs.closingQueue(c,actor);
                case "cash-closings" -> tabs.cash(c,actor,false);
                case "dashboard" -> reports.dashboard(c,actor);
                case "history" -> reports.history(c,actor);
                case "settings" -> catalog.settings(c,actor,null);
                case "restaurant" -> one(c,"SELECT value AS name FROM operational_settings WHERE key='restaurant'");
                case "prints" -> printing.pending(c,actor);
                case "reports" -> reports.sales(c,actor,date(params,"from",LocalDate.now().minusDays(6)),
                        date(params,"to",LocalDate.now()));
                default -> throw Problem.missing("Rota inexistente.");
            };
        }
        if (p.length == 1 && (post || put)) {
            return switch (p[0]) {
                case "products" -> catalog.product(c,actor,null,body);
                case "tables" -> catalog.table(c,actor,null,body);
                case "employees" -> catalog.employee(c,actor,null,body);
                case "settings" -> catalog.settings(c,actor,body);
                case "cash-closings" -> tabs.cash(c,actor,true);
                default -> throw Problem.missing("Rota inexistente.");
            };
        }
        if (p.length == 2 && get) {
            return switch (p[0]) {
                case "orders" -> orders.view(c,actor,uuid(p[1]));
                case "queue" -> orders.queue(c,actor,station(p[1]));
                case "tabs" -> tabs.view(c,actor,number(p[1]));
                default -> throw Problem.missing("Rota inexistente.");
            };
        }
        if (p.length == 2 && put) {
            return switch (p[0]) {
                case "products" -> catalog.product(c,actor,number(p[1]),body);
                case "tables" -> catalog.table(c,actor,number(p[1]),body);
                case "employees" -> catalog.employee(c,actor,number(p[1]),body);
                default -> throw Problem.missing("Rota inexistente.");
            };
        }
        if (p.length == 3 && post) {
            return switch (p[0]) {
                case "tabs" -> tabs.action(c,actor,number(p[1]),p[2],
                        p[2].equals("pay") ? choice(body,"method","PIX","DINHEIRO","CARTAO") : "",
                        p[2].equals("pay") && body.has("expectedTotal") ? decimal(body,"expectedTotal") : null,
                        p[2].equals("open") && body.has("waiterId") ? id(body,"waiterId") : null);
                case "prints" -> printing.action(c,actor,uuid(p[1]),p[2],body);
                case "orders" -> {
                    if (!p[2].equals("deliver")) throw Problem.missing("Rota inexistente.");
                    yield orders.deliver(c,actor,uuid(p[1]));
                }
                default -> throw Problem.missing("Rota inexistente.");
            };
        }
        throw Problem.missing("Rota inexistente.");
    }

    private static LocalDate date(Map<String,String> params, String name, LocalDate fallback) {
        if (!params.containsKey(name)) return fallback;
        try {
            var value = LocalDate.parse(params.get(name));
            if (value.getYear() >= 2000 && value.getYear() <= 2100) return value;
        } catch (DateTimeException ex) {
            throw Problem.invalid("Data inválida em " + name);
        }
        throw Problem.invalid("Data fora do período suportado.");
    }

    private static String station(String station) {
        if (!Set.of("BAR","COZINHA").contains(station)) throw Problem.missing("Estação inexistente.");
        return station;
    }

    private static long number(String part) {
        try {
            long result = Long.parseLong(part);
            if (result > 0) return result;
        } catch (NumberFormatException ex) {
            throw Problem.invalid("Identificador inválido.");
        }
        throw Problem.invalid("Identificador inválido.");
    }

    private static Map<String,String> params(HttpExchange exchange) {
        String query = exchange.getRequestURI().getRawQuery();
        Map<String,String> values = new HashMap<>();
        if (query == null) return values;
        for (String entry : query.split("&")) {
            String[] p = entry.split("=",2);
            if (p.length == 2) values.put(p[0],URLDecoder.decode(p[1],StandardCharsets.UTF_8));
        }
        return values;
    }

    private static String bearer(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }

    private static JsonObject read(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(65_537);
        if (bytes.length > 65_536) throw new Problem(413,"Requisição muito grande.");
        return bytes.length == 0 ? new JsonObject() : Json.parse(new String(bytes,StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, Object content) throws IOException {
        byte[] response = Json.encode(content).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control","no-store");
        exchange.sendResponseHeaders(status,response.length);
        exchange.getResponseBody().write(response);
    }
}
