package com.poo.modelos;

import java.util.ArrayList;

public class Comanda {

    private static int proximoId = 1;

    private int id;
    private Mesa mesa;
    private Garcom garcom;
    private ArrayList<ItemSolicitado> itens;
    private StatusComanda status;

    public Comanda(Mesa mesa, Garcom garcom) {
        this.id = proximoId++;
        this.mesa = mesa;
        this.garcom = garcom;
        this.itens = new ArrayList<>();
        this.status = StatusComanda.ABERTA;
    }

    public void adicionarItem(ItemSolicitado item) {
        itens.add(item);
    }

    public double calcularTotal() {
        double total = 0;

        for (ItemSolicitado item : itens) {
            total += item.calcularTotal();
        }
        return total;
    }

    public StatusComanda obterStatus() {
        return status;
    }

    public void fechar() {
        this.status = StatusComanda.FINALIZADA;
    }

    public Mesa obterMesa() {
        return mesa;
    }

    public int obterId() {
        return id;
    }
}
