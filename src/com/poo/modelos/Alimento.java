package com.poo.modelos;

public class Alimento extends Produto {

    private CategoriaAlimento categoria;
    private int quantidadePessoasAtendidas;

    public Alimento(int id, String nome, String descricao, double preco,
                    CategoriaAlimento categoria, int quantidadePessoasAtendidas) {
        super(id, nome, descricao, preco);
        this.categoria = categoria;
        this.quantidadePessoasAtendidas = quantidadePessoasAtendidas;
    }
}
