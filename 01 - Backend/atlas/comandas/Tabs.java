package atlas.comandas;

import atlas.modelos.*;
import atlas.mqtt.Topics;
import java.sql.*;
import java.util.*;
import static atlas.repositorios.Database.*;
import static atlas.servicos.Catalog.*;

public final class Tabs {
    private static final String BILLABLE = "o.status <> 'PENDENTE' AND d.status='OK'";

    public Map<String,Object> open(Connection c, Actor actor, long tableId) throws SQLException {
        return open(c,actor,tableId,null);
    }

    private Map<String,Object> open(Connection c, Actor actor, long tableId, Long assigned) throws SQLException {
        actor.require("GERENTE","GARCOM");
        if (assigned != null && !actor.role().equals("GERENTE"))
            throw new Problem(403,"O garçom só pode abrir a própria comanda.");
        var table = lockTable(c,tableId);
        if (!(boolean)table.get("available")) throw Problem.conflict("Mesa indisponível.");
        var tab = activeTab(c,tableId);
        if (tab != null) {
            authorize(actor,tab);
            if (assigned != null && assigned != number(tab,"employee_id"))
                throw Problem.conflict("A mesa já tem uma comanda de outro garçom.");
            if (!"ABERTA".equals(tab.get("status"))) throw Problem.conflict("A conta já foi solicitada.");
            return tab;
        }
        if (actor.role().equals("GERENTE")) {
            if (assigned == null) throw Problem.invalid("Selecione o garçom responsável.");
            if (one(c,"SELECT id FROM employees WHERE id=? AND role='GARCOM' AND active FOR SHARE",assigned) == null)
                throw Problem.conflict("Garçom indisponível.");
        }
        tab = one(c,"INSERT INTO tabs(table_id,employee_id) VALUES (?,?) RETURNING *",tableId,
                assigned == null ? actor.id() : assigned);
        change(c,actor,tab,"COMANDA_ABERTA");
        return tab;
    }

    public Object action(Connection c, Actor actor, long tableId, String action, String method, java.math.BigDecimal expectedTotal, Long assigned) throws SQLException {
        actor.require("GERENTE","GARCOM","CAIXA");
        if (action.equals("open")) return open(c,actor,tableId,assigned);
        lockTable(c,tableId);
        var tab = activeTab(c,tableId);
        if (tab == null) throw Problem.missing("Mesa sem comanda ativa.");
        long tabId = number(tab,"id");
        if (action.equals("request")) {
            actor.require("GARCOM","GERENTE","CAIXA");
            authorize(actor,tab);
            if ("CONTA_SOLICITADA".equals(tab.get("status"))) return tab;
            update(c,"UPDATE tabs SET status='CONTA_SOLICITADA', requested_at=now() WHERE id=?",tabId);
        } else if (action.equals("cancel")) {
            actor.require("GERENTE");
            if (one(c,"SELECT id FROM orders WHERE tab_id=? LIMIT 1",tabId) != null)
                throw Problem.conflict("Comanda com pedidos registrados exige fechamento.");
            update(c,"UPDATE tabs SET status='CANCELADA',closed_at=now() WHERE id=?",tabId);
        } else if (action.equals("pay")) {
            actor.require("GERENTE","CAIXA");
            if (!"CONTA_SOLICITADA".equals(tab.get("status"))) throw Problem.conflict("Solicite a conta primeiro.");
            if (one(c,"SELECT id FROM orders WHERE tab_id=? AND status='PENDENTE' LIMIT 1",tabId) != null)
                throw Problem.conflict("Aguarde o resultado de produção dos pedidos.");
            var total = total(c,tabId);
            if (expectedTotal != null && expectedTotal.compareTo(total) != 0)
                throw Problem.conflict("O total da comanda mudou. Abra os detalhes e revise novamente.");
            query(c,"SELECT pg_advisory_xact_lock(20142610)");
            update(c,"INSERT INTO payments(tab_id,employee_id,method,amount) VALUES (?,?,?,?)",
                    tabId,actor.id(),method,total);
            update(c,"UPDATE tabs SET status='FECHADA',closed_at=now() WHERE id=?",tabId);
        } else {
            throw Problem.missing("Operação de comanda desconhecida.");
        }
        tab = one(c,"SELECT * FROM tabs WHERE id=?",tabId);
        change(c,actor,tab,"COMANDA_" + action.toUpperCase(Locale.ROOT));
        return tab;
    }

    public Object view(Connection c, Actor actor, long tableId) throws SQLException {
        actor.require("GERENTE","GARCOM","CAIXA");
        var tab = one(c,"SELECT * FROM tabs WHERE table_id=? AND status IN ('ABERTA','CONTA_SOLICITADA')",tableId);
        if (tab == null) return Map.of("items",List.of(),"total",0);
        authorize(actor,tab);
        long id = number(tab,"id");
        return Map.of("tab",tab,"total",total(c,id),"items",query(c,"""
                SELECT i.name,i.category,i.station,i.quantity,i.unit_price,i.notes,o.sequence,
                  o.status AS order_status,d.status AS station_status,
                  (o.status <> 'PENDENTE' AND d.status='OK') AS billable
                FROM orders o JOIN order_items i ON i.order_id=o.id
                JOIN dispatches d ON d.order_id=o.id AND d.station=i.station
                WHERE o.tab_id=? ORDER BY o.sequence,i.id
                """,id));
    }

    public Object closingQueue(Connection c, Actor actor) throws SQLException {
        actor.require("GERENTE","CAIXA");
        var rows = query(c,"""
                SELECT m.id,m.label,t.id AS tab_id,t.requested_at,e.name AS waiter
                FROM tabs t JOIN dining_tables m ON m.id=t.table_id JOIN employees e ON e.id=t.employee_id
                WHERE t.status='CONTA_SOLICITADA' ORDER BY t.requested_at DESC
                """);
        for (var row : rows) row.put("total",total(c,number(row,"tab_id")));
        return rows;
    }

    public Object payments(Connection c, Actor actor) throws SQLException {
        actor.require("GERENTE","CAIXA");
        return query(c,"""
                SELECT p.*,m.label AS table_label,e.name AS cashier FROM payments p
                JOIN tabs t ON t.id=p.tab_id JOIN dining_tables m ON m.id=t.table_id
                JOIN employees e ON e.id=p.employee_id ORDER BY p.id DESC LIMIT 300
                """);
    }

    public Object cash(Connection c, Actor actor, boolean close) throws SQLException {
        actor.require("GERENTE","CAIXA");
        if (!close) return query(c,"SELECT * FROM cash_closings ORDER BY id DESC LIMIT 100");
        query(c,"SELECT pg_advisory_xact_lock(20142610)");
        var entry = one(c,"""
                INSERT INTO cash_closings(employee_id,amount,payment_count)
                SELECT ?,COALESCE(sum(amount),0),count(*) FROM payments WHERE cash_closing_id IS NULL
                HAVING count(*)>0 RETURNING *
                """,actor.id());
        if (entry == null) throw Problem.conflict("Nenhum pagamento pendente de fechamento de caixa.");
        update(c,"UPDATE payments SET cash_closing_id=? WHERE cash_closing_id IS NULL",entry.get("id"));
        audit(c,actor,"CAIXA_FECHADO",entry.get("id").toString(),"");
        Topics.changed(c,"tabs");
        return entry;
    }

    private static java.math.BigDecimal total(Connection c, long tabId) throws SQLException {
        return (java.math.BigDecimal)one(c,"""
                SELECT COALESCE(sum(i.unit_price*i.quantity),0) AS total
                FROM orders o JOIN order_items i ON i.order_id=o.id
                JOIN dispatches d ON d.order_id=o.id AND d.station=i.station
                WHERE o.tab_id=? AND
                """ + BILLABLE,tabId).get("total");
    }

    private static void authorize(Actor actor, Map<String,Object> tab) {
        if (actor.role().equals("GARCOM") && actor.id() != number(tab,"employee_id"))
            throw new Problem(403,"Mesa sob responsabilidade de outro garçom.");
    }

    private static void change(Connection c, Actor actor, Map<String,Object> tab, String action) throws SQLException {
        audit(c,actor,action,tab.get("id").toString(),"Mesa " + tab.get("table_id"));
        Topics.changed(c,"tabs");
    }
}
