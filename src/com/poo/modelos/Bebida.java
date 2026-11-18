package com.poo.modelos;

public class Bebida extends Produto {

    private CategoriaBebida categoria;

    public Bebida(int id, String nome, String descricao, double preco,
                  CategoriaBebida categoria) {
        super(id, nome, descricao, preco);
        this.categoria = categoria;
    }
}
