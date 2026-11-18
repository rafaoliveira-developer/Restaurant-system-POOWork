package com.poo.modelos;

import java.util.ArrayList;

public class Cardapio {

    private ArrayList<Produto> produtos;

    public Cardapio() {
        produtos = new ArrayList<>();
    }

    public void adicionarProduto(Produto produto) {
        produtos.add(produto);
    }

    public void removerProduto(Produto produto) {
        produtos.remove(produto);
    }

    public Produto buscarProduto(int id) {
        for (Produto produto : produtos) {
            if (produto.obterId() == id) {
                return produto;
            }
        }
        return null;
    }

    public ArrayList<Produto> obterProdutos() {
        return produtos;
    }

    public void exibirProdutos() {
        for (Produto produto : produtos) {
            System.out.println(
                    produto.obterId() + " - " +
                            produto.obterNome() + " - R$ " +
                            produto.obterPreco()
            );
        }
    }
}
