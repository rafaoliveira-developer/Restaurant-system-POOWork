package com.example.pooproject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Cardapio {

    private static final List<ProdutoMenu> produtos = new ArrayList<>();

    static {
        carregarProdutosIniciais();
    }

    private static void carregarProdutosIniciais() {
        produtos.add(new ProdutoMenu("Hambúrguer", 30.00, "Cozinha"));
        produtos.add(new ProdutoMenu("Macarrão à bolonhesa", 38.00, "Cozinha"));
        produtos.add(new ProdutoMenu("Filé à parmegiana", 45.00, "Cozinha"));

        produtos.add(new ProdutoMenu("Água", 5.00, "Bar"));
        produtos.add(new ProdutoMenu("Refrigerante", 7.00, "Bar"));
        produtos.add(new ProdutoMenu("Suco", 9.00, "Bar"));
    }

    public static boolean adicionarProduto(String nome, double preco, String setor) {

        if (nome == null || nome.trim().isEmpty()) {
            return false;
        }

        if (preco <= 0) {
            return false;
        }

        if (!setor.equals("Cozinha") && !setor.equals("Bar")) {
            return false;
        }

        produtos.add(new ProdutoMenu(nome.trim(), preco, setor));

        return true;
    }

    public static boolean removerProduto(ProdutoMenu produto) {
        return produtos.remove(produto);
    }

    public static List<ProdutoMenu> getProdutos() {
        return Collections.unmodifiableList(new ArrayList<>(produtos));
    }
}