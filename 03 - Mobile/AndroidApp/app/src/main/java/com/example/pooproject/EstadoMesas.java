package com.example.pooproject;

import org.json.JSONArray;

public final class EstadoMesas {
    private EstadoMesas() {}
    static JSONArray getMesas(SessaoUsuario session) throws Exception { return (JSONArray)session.get("tables"); }
}
