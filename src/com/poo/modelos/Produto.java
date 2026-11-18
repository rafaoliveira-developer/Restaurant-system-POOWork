package com.poo.modelos;

public abstract class Produto {

    private int id;
    private String nome;
    private String descricao;
    private double preco;

    public Produto(int id, String nome, String descricao, double preco) {
        this.id = id;
        this.nome = nome;
        this.descricao = descricao;
        this.preco = preco;
    }

    public int obterId() {
        return id;
    }

    public String obterNome() {
        return nome;
    }

    public String obterDescricao() {
        return descricao;
    }

    public double obterPreco() {
        return preco;
    }
}
