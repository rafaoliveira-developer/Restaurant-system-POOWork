package atlas.servicos;

import atlas.modelos.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import static atlas.repositorios.Database.*;

public final class Reports {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");

    public Object dashboard(Connection c, Actor actor) throws SQLException {
        actor.require("GERENTE","CAIXA");
        return one(c,"""
                SELECT
                  (SELECT count(*) FROM dining_tables m WHERE m.available AND NOT EXISTS
                    (SELECT 1 FROM tabs t WHERE t.table_id=m.id AND status IN ('ABERTA','CONTA_SOLICITADA'))) AS free,
                  (SELECT count(*) FROM tabs WHERE status IN ('ABERTA','CONTA_SOLICITADA')) AS occupied,
                  (SELECT count(*) FROM tabs WHERE status='CONTA_SOLICITADA') AS closing,
                  (SELECT count(*) FROM orders o WHERE status='PENDENTE' AND NOT EXISTS
                    (SELECT 1 FROM dispatches d WHERE d.order_id=o.id AND d.status='PREPARANDO')) AS fresh,
                  (SELECT count(DISTINCT o.id) FROM orders o JOIN dispatches d ON d.order_id=o.id
                    WHERE o.status='PENDENTE' AND d.status='PREPARANDO') AS preparing,
                  (SELECT count(*) FROM orders WHERE status IN ('CONFIRMADO','PARCIAL') AND delivered_at IS NULL) AS ready,
                  (SELECT count(*) FROM dispatches WHERE station='BAR' AND status IN ('PENDENTE','PREPARANDO')) AS bar,
                  (SELECT count(*) FROM dispatches WHERE station='COZINHA' AND status IN ('PENDENTE','PREPARANDO')) AS kitchen,
                  (SELECT count(*) FROM print_jobs WHERE status IN ('FALHA','INCERTO')) AS prints,
                  (SELECT count(*) FROM orders WHERE created_at >= ?) AS today,
                  (SELECT COALESCE(sum(i.unit_price*i.quantity),0) FROM tabs t JOIN orders o ON o.tab_id=t.id
                    JOIN order_items i ON i.order_id=o.id JOIN dispatches d ON d.order_id=o.id AND d.station=i.station
                    WHERE t.status='CONTA_SOLICITADA' AND o.status<>'PENDENTE' AND d.status='OK') AS balance
                """,LocalDate.now(BUSINESS_ZONE).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime());
    }

    public Object sales(Connection c, Actor actor, LocalDate start, LocalDate end) throws SQLException {
        actor.require("GERENTE","CAIXA");
        if (end.isBefore(start) || start.plusYears(2).isBefore(end)) throw Problem.invalid("Período de até dois anos.");
        var from = start.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        var until = end.plusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        return Map.of(
                "summary",one(c,"SELECT count(*) AS payments,COALESCE(sum(amount),0) AS total FROM payments WHERE created_at>=? AND created_at<?",from,until),
                "items",query(c,"""
                        SELECT i.name,i.category,i.station,e.name AS waiter,m.label AS table_label,
                               sum(i.quantity) AS quantity,sum(i.quantity*i.unit_price) AS total
                        FROM payments p JOIN tabs t ON t.id=p.tab_id JOIN orders o ON o.tab_id=t.id
                        JOIN order_items i ON i.order_id=o.id
                        JOIN dispatches d ON d.order_id=o.id AND d.station=i.station AND d.status='OK'
                        JOIN employees e ON e.id=o.employee_id JOIN dining_tables m ON m.id=t.table_id
                        WHERE p.created_at>=? AND p.created_at<?
                        GROUP BY i.name,i.category,i.station,e.name,m.label ORDER BY total DESC LIMIT 1000
                        """,from,until));
    }

    public Object history(Connection c, Actor actor) throws SQLException {
        actor.require("GERENTE");
        return query(c,"""
                SELECT a.id,a.operation,a.reference,a.details,a.created_at,e.name AS employee,
                       COALESCE(om.label,tm.label,dm.label) AS table_label,
                       COALESCE(ot.id,dt.id) AS tab_id,o.sequence AS order_sequence
                FROM audit_events a LEFT JOIN employees e ON e.id=a.actor
                LEFT JOIN orders o ON o.id::text=a.reference
                LEFT JOIN tabs ot ON ot.id=o.tab_id LEFT JOIN dining_tables om ON om.id=ot.table_id
                LEFT JOIN tabs dt ON a.operation LIKE 'COMANDA_%' AND dt.id::text=a.reference
                LEFT JOIN dining_tables dm ON dm.id=dt.table_id
                LEFT JOIN dining_tables tm ON a.operation='MESA' AND tm.id::text=a.reference
                ORDER BY a.id DESC LIMIT 1000
                """);
    }
}
