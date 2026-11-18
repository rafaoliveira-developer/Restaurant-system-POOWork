package com.poo.modelos;

public class Mesa {

    private int numero;
    private StatusMesa status;

    public Mesa(int numero) {
        this.numero = numero;
        this.status = StatusMesa.LIVRE;
    }

    public StatusMesa obterStatus() {
        return status;
    }

    public void definirStatus(StatusMesa status) {
        this.status = status;
    }

    public int obterNumero() {
        return numero;
    }

    public void definirNumero(int numero) {
        this.numero = numero;
    }
}
