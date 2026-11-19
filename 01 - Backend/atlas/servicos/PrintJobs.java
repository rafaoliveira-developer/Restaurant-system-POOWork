package atlas.servicos;

import atlas.modelos.*;
import atlas.mqtt.Topics;
import com.google.gson.JsonObject;
import java.sql.*;
import java.util.*;
import static atlas.repositorios.Database.*;
import static atlas.servicos.Catalog.audit;
import static atlas.util.Json.*;

public final class PrintJobs {
    public Object pending(Connection c, Actor actor) throws SQLException {
        actor.require("COZINHA");
        return query(c,"SELECT id,order_id FROM print_jobs WHERE status='PENDENTE' ORDER BY created_at LIMIT 50");
    }

    public Object action(Connection c, Actor actor, UUID id, String action, JsonObject body) throws SQLException {
        actor.require("COZINHA");
        var existing = one(c,"SELECT * FROM print_jobs WHERE id=? FOR UPDATE",id);
        if (existing == null) throw Problem.missing("Impressão não encontrada.");
        String state = (String) existing.get("status");
        Map<String,Object> row;
        switch (action) {
            case "claim" -> {
                if (!state.equals("PENDENTE")) throw Problem.conflict("Trabalho já assumido.");
                row = one(c,"""
                        UPDATE print_jobs SET status='IMPRIMINDO',attempts=attempts+1,claim_id=?,claimed_by=?,
                          claimed_at=now(),updated_at=now() WHERE id=? RETURNING *
                        """,UUID.randomUUID(),actor.id(),id);
            }
            case "result" -> {
                UUID claim = uuid(body,"claimId");
                if (!claim.toString().equals(existing.get("claim_id")) || !Objects.equals(existing.get("claimed_by"),actor.id()))
                    throw new Problem(403,"Impressão atribuída a outro operador.");
                String status = choice(body,"status","IMPRESSO","FALHA","INCERTO");
                String error = str(body,"error",300,!status.equals("IMPRESSO"));
                if (state.equals(status)) return existing;
                if (!state.equals("IMPRIMINDO")) throw Problem.conflict("O trabalho já teve seu estado alterado.");
                row = one(c,"UPDATE print_jobs SET status=?,error=?,updated_at=now() WHERE id=? RETURNING *",status,error,id);
            }
            case "retry" -> {
                if (!state.equals("FALHA")) throw Problem.conflict("Somente falhas confirmadas podem ser tentadas novamente.");
                row = one(c,"""
                        UPDATE print_jobs SET status='PENDENTE',claim_id=NULL,claimed_by=NULL,claimed_at=NULL,
                          error='',updated_at=now() WHERE id=? RETURNING *
                        """,id);
            }
            case "uncertain" -> {
                if (!state.equals("IMPRIMINDO")) throw Problem.conflict("O trabalho não está em impressão.");
                row = one(c,"""
                        UPDATE print_jobs SET status='INCERTO',error='Verificação manual após interrupção',updated_at=now()
                        WHERE id=? AND claimed_at<now()-interval '2 minutes' RETURNING *
                        """,id);
                if (row == null) throw Problem.conflict("Aguarde dois minutos antes de marcar a impressão como incerta.");
            }
            case "reprint" -> {
                if (!List.of("IMPRESSO","INCERTO").contains(state))
                    throw Problem.conflict("Confira o resultado e o papel antes de reimprimir.");
                UUID copy = uuid(body,"copyId");
                var previous = one(c,"SELECT * FROM print_jobs WHERE id=? AND parent_id=?",copy,id);
                if (previous != null) return previous;
                row = one(c,"INSERT INTO print_jobs(id,order_id,parent_id,content) VALUES (?,?,?,?) RETURNING *",
                        copy,UUID.fromString(existing.get("order_id").toString()),id,"REIMPRESSÃO\n" + existing.get("content"));
            }
            case "cancel" -> {
                if (!List.of("PENDENTE","FALHA").contains(state)) throw Problem.conflict("O trabalho já foi encaminhado.");
                row = one(c,"UPDATE print_jobs SET status='CANCELADO',updated_at=now() WHERE id=? RETURNING *",id);
            }
            default -> throw Problem.missing("Operação de impressão desconhecida.");
        }
        audit(c,actor,"IMPRESSAO_" + action.toUpperCase(Locale.ROOT),existing.get("order_id").toString(),
                row.get("id") + " · " + row.get("status") + " · " + row.get("error"));
        Topics.changed(c,"prints");
        return row;
    }
}
