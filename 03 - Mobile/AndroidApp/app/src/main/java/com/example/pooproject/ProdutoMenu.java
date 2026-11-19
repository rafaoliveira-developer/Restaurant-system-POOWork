package com.example.pooproject;

import org.json.JSONObject;
import java.math.BigDecimal;

public final class ProdutoMenu {
    final long id;
    final String nome;
    final BigDecimal preco;
    final String categoria;
    final String descricao;
    final String imagem;

    ProdutoMenu(JSONObject data) {
        this(data.optLong("id"),data.optString("name"),new BigDecimal(data.optString("price")),
                data.optString("category"),data.optString("description"),data.optString("image"));
    }

    ProdutoMenu(long id,String name,BigDecimal price,String category,String description,String image) {
        this.id = id;
        nome = name;
        preco = price;
        categoria = category;
        descricao = description;
        imagem = image;
    }
}
