package com.poo.modelos;

import java.util.ArrayList;

public class Cozinha {

    private ArrayList<PedidoCozinha> pedidos;
    private StatusPedidoCozinha statusPedidoCozinha;

    public Cozinha() {
        this.pedidos = new ArrayList<>();
    }

    public void adicionarPedido(PedidoCozinha pedido) {
        pedidos.add(pedido);
    }

    public void removerPedido(PedidoCozinha pedido) {
        pedidos.remove(pedido);
    }
}
