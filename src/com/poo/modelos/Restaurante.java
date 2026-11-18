package com.poo.modelos;

import java.util.ArrayList;

public class Restaurante {

    private ArrayList<Mesa> mesas;
    private ArrayList<Garcom> garcons;
    private ArrayList<Comanda> comandas;
    private Cardapio cardapio;
    private Cozinha cozinha;

    public Restaurante() {
        this.mesas = new ArrayList<>();
        this.garcons = new ArrayList<>();
        this.comandas = new ArrayList<>();
        this.cardapio = new Cardapio();
        this.cozinha = new Cozinha();
    }

    public Cardapio obterCardapio() {
        return cardapio;
    }

    public Comanda abrirComanda(Mesa mesa, Garcom garcom) {
        if (mesa.obterStatus() == StatusMesa.OCUPADA) {
            throw new IllegalStateException("A mesa já está ocupada.");
        }

        Comanda comanda = new Comanda(mesa, garcom);
        mesa.definirStatus(StatusMesa.OCUPADA);
        comandas.add(comanda);
        return comanda;
    }

    public void fecharComanda(Comanda comanda) {
        comanda.fechar();
        comanda.obterMesa().definirStatus(StatusMesa.LIVRE);
    }

    public Comanda buscarComanda(int id) {
        for (Comanda comanda : comandas) {
            if (comanda.obterId() == id) {
                return comanda;
            }
        }
        return null;
    }
}
