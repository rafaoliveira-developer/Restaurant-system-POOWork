package com.example.pooproject;

import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

public final class Cardapio {
    private Cardapio() {}
    static List<ProdutoMenu> getProdutos(SessaoUsuario session) throws Exception {
        JSONArray rows = (JSONArray)session.get("products");
        return from(rows);
    }
    static List<ProdutoMenu> from(JSONArray rows) {
        List<ProdutoMenu> products = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) products.add(new ProdutoMenu(rows.optJSONObject(i)));
        return products;
    }
}
