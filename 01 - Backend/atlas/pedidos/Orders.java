package atlas.pedidos;

import atlas.autenticacao.Auth;
import atlas.comandas.Tabs;
import atlas.modelos.*;
import atlas.mqtt.Topics;
import atlas.util.Json;
import com.google.gson.*;
import java.sql.*;
import java.util.*;
import static atlas.repositorios.Database.*;
import static atlas.servicos.Catalog.*;
import static atlas.util.Json.*;

public final class Orders {
    private final Tabs tabs;

    public Orders(Tabs tabs) { this.tabs = tabs; }

    private record Item(long productId, int quantity, String notes) {}

    public void accept(Connection c, Actor actor, JsonObject body) throws SQLException {
        actor.require("GARCOM");
        UUID id = uuid(body,"orderId");
        query(c,"SELECT pg_advisory_xact_lock(hashtextextended(?,0))",id.toString());
        var rejection = one(c,"SELECT error FROM order_rejections WHERE employee_id=? AND order_id=?",actor.id(),id);
        if (rejection != null) {
            publishResult(c,actor.username(),id,Map.of("status","REJEITADO","error",rejection.get("error")));
            return;
        }
        Savepoint beforeOrder = c.setSavepoint();
        try {
            store(c,actor,body,id);
        } catch (Problem ex) {
            c.rollback(beforeOrder);
            if (one(c,"SELECT id FROM orders WHERE id=? AND employee_id=?",id,actor.id()) == null) {
                update(c,"INSERT INTO order_rejections(employee_id,order_id,error) VALUES (?,?,?)",actor.id(),id,ex.getMessage());
                audit(c,actor,"PEDIDO_REJEITADO",id.toString(),ex.getMessage());
            }
            publishResult(c,actor.username(),id,Map.of("status","REJEITADO","error",ex.getMessage()));
        }
        c.releaseSavepoint(beforeOrder);
    }

    private void store(Connection c,Actor actor,JsonObject body,UUID id) throws SQLException {
        UUID key = uuid(body,"idempotencyKey");
        long tableId = id(body,"tableId");
        List<Item> items = items(body);
        String hash = Auth.sha(Json.encode(List.of(id.toString(),tableId,items)));
        query(c,"SELECT pg_advisory_xact_lock(hashtextextended(?,0))",actor.id() + ":" + key);
        var previous = one(c,"SELECT id,request_hash,status FROM orders WHERE employee_id=? AND idempotency_key=?",
                actor.id(),key);
        if (previous != null) {
            if (!hash.equals(previous.get("request_hash"))) throw Problem.conflict("Chave de pedido já utilizada.");
            publishResult(c,actor.username(),id,result(c,id));
            return;
        }
        var tab = tabs.open(c,actor,tableId);
        Map<Long,Map<String,Object>> catalog = new HashMap<>();
        for (long productId : items.stream().map(Item::productId).distinct().sorted().toList()) {
            var product = one(c,"SELECT * FROM products WHERE id=? FOR SHARE",productId);
            if (product == null || !Boolean.TRUE.equals(product.get("available")))
                throw Problem.conflict("Produto indisponível: " + productId);
            catalog.put(productId,product);
        }
        Map<String,Object> order;
        try {
            order = one(c,"INSERT INTO orders(id,idempotency_key,request_hash,employee_id,tab_id) VALUES (?,?,?,?,?) RETURNING *",
                    id,key,hash,actor.id(),tab.get("id"));
        } catch (SQLException ex) {
            if ("23505".equals(ex.getSQLState())) throw Problem.conflict("Identificador de pedido já utilizado.");
            throw ex;
        }
        Set<String> sectors = new TreeSet<>();
        for (Item item : items) {
            var product = catalog.get(item.productId());
            sectors.add((String) product.get("station"));
            update(c,"""
                    INSERT INTO order_items(order_id,product_id,name,category,station,unit_price,quantity,notes)
                    VALUES (?,?,?,?,?,?,?,?)
                    """,id,item.productId(),product.get("name"),product.get("category"),product.get("station"),
                    product.get("price"),item.quantity(),item.notes());
        }
        for (String station : sectors) {
            update(c,"INSERT INTO dispatches(order_id,station) VALUES (?,?)",id,station);
            var dispatch = dispatch(c,id,station);
            if (station.equals("COZINHA")) createPrintJob(c,id,dispatch);
            dispatch.put("version",1);
            dispatch.put("event", station.equals("COZINHA") ? "ENVIACOZINHA" : "ENVIABAR");
            dispatch.put("orderId",id.toString());
            Topics.enqueue(c,Topics.station(station),dispatch);
        }
        audit(c,actor,"NOVOPEDIDO",id.toString(),"Mesa " + tableId);
        publishResult(c,actor.username(),id,Map.of("status","PENDENTE","sequence", order.get("sequence")));
        Topics.changed(c,"orders");
    }

    private static List<Item> items(JsonObject body) {
        JsonElement value = body.get("items");
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().isEmpty() || value.getAsJsonArray().size() > 100)
            throw Problem.invalid("Informe entre 1 e 100 itens.");
        List<Item> result = new ArrayList<>();
        for (JsonElement entry : value.getAsJsonArray()) {
            if (!entry.isJsonObject()) throw Problem.invalid("Item inválido.");
            JsonObject item = entry.getAsJsonObject();
            long quantity = id(item,"quantity");
            if (quantity > 99) throw Problem.invalid("Quantidade máxima por item: 99.");
            result.add(new Item(id(item,"productId"),(int)quantity,str(item,"notes",300,false)));
        }
        return result;
    }

    public void process(Connection c, Actor actor, String station, JsonObject body) throws SQLException {
        actor.require(station);
        UUID orderId = uuid(body,"orderId");
        String status = choice(body,"status","PREPARANDO","OK","ERRO");
        String error = str(body,"error",300,status.equals("ERRO"));
        long sequence = id(body,"sequence");
        var order = one(c,"""
                SELECT o.*, e.username FROM orders o JOIN employees e ON e.id=o.employee_id
                WHERE o.id=? FOR UPDATE OF o
                """,orderId);
        if (order == null || number(order,"sequence") != sequence) throw Problem.missing("Pedido ou sequência inválidos.");
        var dispatch = one(c,"SELECT * FROM dispatches WHERE order_id=? AND station=?",orderId,station);
        if (dispatch == null) throw Problem.missing("Pedido sem itens para este setor.");
        String present = (String)dispatch.get("status");
        if (present.equals(status) || present.equals("OK") || present.equals("ERRO")) return;
        update(c,"UPDATE dispatches SET status=?,result_by=?,result_at=now(),error=? WHERE order_id=? AND station=?",
                status,actor.id(),error,orderId,station);
        audit(c,actor,"PRODUCAO_" + status,orderId.toString(),station + (error.isEmpty() ? "" : ": " + error));
        Map<String,String> confirmations = new LinkedHashMap<>();
        for (var row : query(c,"SELECT station,status FROM dispatches WHERE order_id=? ORDER BY station",orderId))
            confirmations.put((String)row.get("station"),(String)row.get("status"));
        var finalState = Consolidation.from(confirmations);
        if (finalState.code() != null) {
            update(c,"UPDATE orders SET status=?,code=? WHERE id=?",finalState.status(),finalState.code(),orderId);
            publishResult(c,(String)order.get("username"),orderId,result(c,orderId));
            audit(c,actor,"CONFIRMAREPEDIDO",orderId.toString(),finalState.code());
        }
        Topics.changed(c,"orders");
    }

    public Object list(Connection c, Actor actor) throws SQLException {
        actor.require("GERENTE","GARCOM","CAIXA");
        return query(c, """
                SELECT o.id,o.sequence,o.status,o.code,o.created_at,o.delivered_at,m.id AS table_id,
                  m.label AS table_label,e.name AS waiter,t.id AS tab_id,
                  (SELECT string_agg(station,', ' ORDER BY station) FROM dispatches WHERE order_id=o.id) AS stations,
                  EXISTS(SELECT 1 FROM dispatches d WHERE d.order_id=o.id AND d.status='PREPARANDO') AS preparing
                FROM orders o JOIN employees e ON e.id=o.employee_id
                JOIN tabs t ON t.id=o.tab_id JOIN dining_tables m ON m.id=t.table_id
                WHERE (? OR o.employee_id=?) ORDER BY o.sequence DESC LIMIT 500
                """,!actor.role().equals("GARCOM"),actor.id());
    }

    public Object view(Connection c, Actor actor, UUID id) throws SQLException {
        actor.require("GERENTE","GARCOM","CAIXA");
        var order = one(c, """
                SELECT o.id,o.sequence,o.employee_id,o.status,o.code,o.created_at,o.delivered_at,
                       m.label AS table_label,e.name AS waiter
                FROM orders o JOIN tabs t ON t.id=o.tab_id JOIN dining_tables m ON m.id=t.table_id
                JOIN employees e ON e.id=o.employee_id WHERE o.id=?
                """,id);
        if (order == null || (actor.role().equals("GARCOM") && actor.id() != number(order,"employee_id"))) {
            var rejection = one(c,"SELECT error FROM order_rejections WHERE employee_id=? AND order_id=?",
                    actor.id(),id);
            if (rejection != null) return Map.of("id",id.toString(),"status","REJEITADO","error",rejection.get("error"));
            throw Problem.missing("Pedido não encontrado.");
        }
        order.put("items",query(c,"SELECT * FROM order_items WHERE order_id=? ORDER BY id",id));
        order.put("stations",query(c,"SELECT station,status,error,result_at FROM dispatches WHERE order_id=? ORDER BY station",id));
        if (actor.role().equals("GERENTE")) order.put("prints",query(c,
                "SELECT id,status,attempts,error,updated_at FROM print_jobs WHERE order_id=? ORDER BY created_at DESC",id));
        order.put("history",query(c,"SELECT operation,details,created_at FROM audit_events WHERE reference=? ORDER BY id",id.toString()));
        return order;
    }

    public Object deliver(Connection c, Actor actor, UUID id) throws SQLException {
        actor.require("GARCOM","GERENTE");
        var order = one(c,"SELECT * FROM orders WHERE id=? FOR UPDATE",id);
        if (order == null) throw Problem.missing("Pedido não encontrado.");
        if (actor.role().equals("GARCOM") && actor.id() != number(order,"employee_id"))
            throw new Problem(403,"Pedido vinculado a outro garçom.");
        if (!List.of("CONFIRMADO","PARCIAL").contains(order.get("status")))
            throw Problem.conflict("A entrega exige itens confirmados.");
        if (order.get("delivered_at") == null) {
            update(c,"UPDATE orders SET delivered_at=now() WHERE id=?",id);
            audit(c,actor,"PEDIDO_ENTREGUE",id.toString(),"");
            Topics.changed(c,"orders");
        }
        return view(c,actor,id);
    }

    public Object queue(Connection c, Actor actor, String station) throws SQLException {
        actor.require(station,"GERENTE");
        var summaries = query(c,"""
                SELECT d.order_id FROM dispatches d JOIN orders o ON o.id=d.order_id WHERE d.station=?
                 AND (d.status IN ('PENDENTE','PREPARANDO') OR d.result_at>now()-interval '12 hours')
                 ORDER BY d.status IN ('PENDENTE','PREPARANDO') DESC,o.sequence
                """,station);
        List<Map<String,Object>> queue = new ArrayList<>();
        for (var row : summaries) queue.add(dispatch(c,UUID.fromString((String)row.get("order_id")),station));
        return queue;
    }

    private static Map<String,Object> dispatch(Connection c, UUID id, String station) throws SQLException {
        var result = one(c,"""
                SELECT o.id AS order_id,o.sequence,o.created_at,m.label AS table_label,e.name AS waiter,
                       d.status,d.station,d.error,t.id AS tab_id
                FROM dispatches d JOIN orders o ON o.id=d.order_id JOIN tabs t ON t.id=o.tab_id
                JOIN dining_tables m ON m.id=t.table_id JOIN employees e ON e.id=o.employee_id
                WHERE o.id=? AND d.station=?
                """,id,station);
        result.put("items",query(c,"SELECT name,quantity,notes FROM order_items WHERE order_id=? AND station=? ORDER BY id",id,station));
        if (station.equals("COZINHA")) result.put("prints",query(c,"""
                SELECT id,parent_id,status,attempts,error,created_at,updated_at FROM print_jobs
                WHERE order_id=? ORDER BY created_at DESC
                """,id));
        return result;
    }

    private static Map<String,Object> result(Connection c, UUID id) throws SQLException {
        var row = one(c,"SELECT sequence,status,code FROM orders WHERE id=?",id);
        row.put("stations",query(c,"SELECT station,status,error FROM dispatches WHERE order_id=? ORDER BY station",id));
        return row;
    }

    public static void publishResult(Connection c, String username, UUID id, Map<String,Object> fields)
            throws SQLException {
        var payload = new LinkedHashMap<>(fields);
        payload.put("event","CONFIRMAREPEDIDO");
        payload.put("version",1);
        payload.put("orderId",id.toString());
        Topics.enqueue(c,Topics.result(username),payload);
    }

    private static void createPrintJob(Connection c, UUID id, Map<String,Object> dispatch) throws SQLException {
        StringBuilder slip = new StringBuilder("ATLAS  |  COZINHA\n");
        slip.append("PED-").append(dispatch.get("sequence")).append("  •  ").append(dispatch.get("table_label"))
            .append("\nComanda ").append(dispatch.get("tab_id")).append("  •  ").append(dispatch.get("waiter"))
            .append("\n").append(dispatch.get("created_at")).append("\n\n");
        for (Object element : (List<?>)dispatch.get("items")) {
            var item = (Map<?,?>)element;
            slip.append(item.get("quantity")).append(" × ").append(item.get("name")).append("\n");
            if (!"".equals(item.get("notes"))) slip.append("    ").append(item.get("notes")).append("\n");
        }
        UUID job = UUID.randomUUID();
        update(c,"INSERT INTO print_jobs(id,order_id,content) VALUES (?,?,?)",job,id,slip.toString());
        dispatch.put("printId",job.toString());
    }
}
