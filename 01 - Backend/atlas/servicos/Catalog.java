package atlas.servicos;

import atlas.autenticacao.Auth;
import atlas.modelos.*;
import atlas.mqtt.Topics;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import static atlas.repositorios.Database.*;
import static atlas.util.Json.*;

public final class Catalog {
    public Object products(Connection c, Actor actor) throws SQLException {
        actor.require("GARCOM","GERENTE","CAIXA");
        return query(c, "SELECT * FROM products WHERE available OR ? ORDER BY category,name", actor.role().equals("GERENTE"));
    }

    public Object product(Connection c, Actor actor, Long id, JsonObject body) throws SQLException {
        actor.require("GERENTE");
        String name = str(body,"name",120,true);
        String description = str(body,"description",600,false);
        String category = str(body,"category",60,true);
        String station = choice(body,"station","BAR","COZINHA");
        String image = str(body,"image",1000,false);
        if (!image.isEmpty()) {
            try {
                var uri = java.net.URI.create(image);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null)
                    throw new IllegalArgumentException();
            } catch (IllegalArgumentException ex) {
                throw Problem.invalid("A imagem deve usar um endereço HTTPS válido.");
            }
        }
        BigDecimal price = decimal(body,"price");
        if (price.signum() <= 0 || price.compareTo(new BigDecimal("9999999999.99")) > 0
                || price.stripTrailingZeros().scale() > 2)
            throw Problem.invalid("Preço positivo com até duas casas decimais.");
        boolean available = flag(body,"available",true);
        update(c,"INSERT INTO categories VALUES (?) ON CONFLICT DO NOTHING",category);
        Map<String,Object> saved = id == null
                ? one(c,"INSERT INTO products(name,description,price,category,station,image,available) VALUES (?,?,?,?,?,?,?) RETURNING *",
                      name,description,price,category,station,image,available)
                : one(c,"UPDATE products SET name=?,description=?,price=?,category=?,station=?,image=?,available=? WHERE id=? RETURNING *",
                      name,description,price,category,station,image,available,id);
        return changed(c,actor,"PRODUTO","products",saved);
    }

    public Object tables(Connection c, Actor actor) throws SQLException {
        actor.require("GERENTE","GARCOM","CAIXA");
        return query(c,"""
                SELECT m.*,t.id AS tab_id,t.employee_id,e.name AS waiter,
                  CASE WHEN t.status='ABERTA' THEN 'OCUPADA' WHEN t.id IS NOT NULL THEN t.status
                       WHEN m.available THEN 'LIVRE' ELSE 'INDISPONIVEL' END AS status
                FROM dining_tables m LEFT JOIN tabs t ON t.table_id=m.id AND t.status IN ('ABERTA','CONTA_SOLICITADA')
                LEFT JOIN employees e ON e.id=t.employee_id ORDER BY m.area,m.id
                """);
    }

    public Object table(Connection c, Actor actor, Long id, JsonObject body) throws SQLException {
        actor.require("GERENTE");
        String label = str(body,"label",60,true);
        String area = str(body,"area",60,true);
        boolean available = flag(body,"available",true);
        if (id != null) {
            lockTable(c,id);
            if (!available && activeTab(c,id) != null) throw Problem.conflict("Encerre a comanda antes de desativar a mesa.");
        }
        try {
            Map<String,Object> saved = id == null
                    ? one(c,"INSERT INTO dining_tables(label,area,available) VALUES (?,?,?) RETURNING *",label,area,available)
                    : one(c,"UPDATE dining_tables SET label=?,area=?,available=? WHERE id=? RETURNING *",label,area,available,id);
            return changed(c,actor,"MESA","tables",saved);
        } catch (SQLException ex) {
            if ("23505".equals(ex.getSQLState())) throw Problem.conflict("Já existe mesa com essa identificação.");
            throw ex;
        }
    }

    public Object employees(Connection c, Actor actor) throws SQLException {
        actor.require("GERENTE");
        return query(c,"SELECT id,username,name,role,schedule,active FROM employees ORDER BY name");
    }

    public Object employee(Connection c, Actor actor, Long id, JsonObject body) throws SQLException {
        actor.require("GERENTE");
        String username = str(body,"username",48,true);
        Auth.validateUsername(username);
        String password = str(body,"password",160,id == null);
        if (!password.isEmpty()) Auth.validatePassword(password);
        String name = str(body,"name",120,true);
        String role = choice(body,"role","GERENTE","GARCOM","CAIXA","BAR","COZINHA");
        String schedule = str(body,"schedule",160,false);
        boolean active = flag(body,"active",true);
        String hash = password.isEmpty() ? null : Auth.hashPassword(password);
        if (id != null && id == actor.id() && (!active || !role.equals("GERENTE") || !username.equals(actor.username())))
            throw Problem.conflict("Sua conta de gerente deve permanecer ativa com o mesmo código.");
        try {
            Map<String,Object> saved = id == null
                    ? one(c,"INSERT INTO employees(username,name,role,schedule,password_hash,active) VALUES (?,?,?,?,?,?) RETURNING id,username,name,role,schedule,active",
                          username,name,role,schedule,hash,active)
                    : one(c,"UPDATE employees SET username=?,name=?,role=?,schedule=?,password_hash=COALESCE(?,password_hash),active=? WHERE id=? RETURNING id,username,name,role,schedule,active",
                          username,name,role,schedule,hash,active,id);
            if (id != null) update(c,"DELETE FROM sessions WHERE employee_id=?",id);
            return changed(c,actor,"FUNCIONARIO","employees",saved);
        } catch (SQLException ex) {
            if ("23505".equals(ex.getSQLState())) throw Problem.conflict("Código de funcionário já cadastrado.");
            throw ex;
        }
    }

    public Object settings(Connection c, Actor actor, JsonObject body) throws SQLException {
        actor.require("GERENTE");
        if (body != null) {
            String restaurant = str(body,"restaurant",120,true);
            update(c, "UPDATE operational_settings SET value=? WHERE key='restaurant'", restaurant);
            audit(c,actor,"CONFIGURACAO","restaurant","");
            Topics.changed(c,"settings");
        }
        return query(c,"SELECT key,value FROM operational_settings ORDER BY key");
    }

    public static Map<String,Object> lockTable(Connection c, long id) throws SQLException {
        var table = one(c,"SELECT * FROM dining_tables WHERE id=? FOR UPDATE",id);
        if (table == null) throw Problem.missing("Mesa inexistente.");
        return table;
    }

    public static Map<String,Object> activeTab(Connection c, long id) throws SQLException {
        return one(c,"SELECT * FROM tabs WHERE table_id=? AND status IN ('ABERTA','CONTA_SOLICITADA') FOR UPDATE",id);
    }

    public static long number(Map<String,Object> row, String key) { return ((Number) row.get(key)).longValue(); }

    public static void audit(Connection c, Actor actor, String action, String reference, String details)
            throws SQLException {
        update(c,"INSERT INTO audit_events(actor,operation,reference,details) VALUES (?,?,?,?)",
                actor == null ? null : actor.id(),action,reference,details);
    }

    private static Object changed(Connection c, Actor actor, String action, String resource, Map<String,Object> saved)
            throws SQLException {
        if (saved == null) throw Problem.missing("Registro não encontrado.");
        audit(c,actor,action,saved.get("id").toString(),"");
        Topics.changed(c,resource);
        return saved;
    }
}
