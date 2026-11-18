package com.poo.modelos;

public class ItemSolicitado {

    private Produto produto;
    private int quantidade;

    public ItemSolicitado(Produto produto, int quantidade) {
        this.produto = produto;
        this.quantidade = quantidade;
    }

    public double calcularTotal() {
        return produto.obterPreco() * quantidade;
    }
}
