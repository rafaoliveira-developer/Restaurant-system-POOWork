package com.poo.modelos;

public class Garcom {

    private int id;
    private String nome;

    public Garcom(int id, String nome) {
        this.id = id;
        this.nome = nome;
    }

    public int obterId() {
        return id;
    }

    public void definirId(int id) {
        this.id = id;
    }

    public String obterNome() {
        return nome;
    }

    public void definirNome(String nome) {
        this.nome = nome;
    }
}
