package com.poo.modelos;

import java.util.ArrayList;

public class PedidoCozinha {

    private Comanda comanda;
    private ArrayList<ItemSolicitado> itens;
    private StatusPedidoCozinha status;

    public PedidoCozinha(Comanda comanda) {
        this.comanda = comanda;
        this.itens = new ArrayList<>();
    }
}
