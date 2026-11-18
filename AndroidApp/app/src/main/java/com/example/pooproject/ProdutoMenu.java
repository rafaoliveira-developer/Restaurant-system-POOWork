package com.example.pooproject;

public class ProdutoMenu {

    private String nome;
    private double preco;
    private String setor;

    public ProdutoMenu(String nome, double preco, String setor) {
        this.nome = nome;
        this.preco = preco;
        this.setor = setor;
    }

    public String getNome() {
        return nome;
    }

    public double getPreco() {
        return preco;
    }

    public String getSetor() {
        return setor;
    }
}