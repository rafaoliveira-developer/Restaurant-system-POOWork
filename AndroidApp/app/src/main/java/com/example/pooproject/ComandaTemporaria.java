
package com.example.pooproject;

import java.util.ArrayList;
import java.util.List;

public class ComandaTemporaria {

    public static class Item {
        private final String nome;
        private final double preco;
        private final String setor;
        private int quantidade;


        public Item(String nome, double preco, String setor) {
            this.nome = nome;
            this.preco = preco;
            this.setor = setor;
            this.quantidade = 1;
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

        public int getQuantidade() {
            return quantidade;
        }

        public void aumentarQuantidade() {
            quantidade++;
        }

        public void diminuirQuantidade() {
            if (quantidade > 0) {
                quantidade--;
            }
        }

        public double calcularSubtotal() {
            return preco * quantidade;
        }
    }

    private static final List<Item>[] itensPorMesa = new ArrayList[4];

    static {
        for (int i = 0; i < itensPorMesa.length; i++) {
            itensPorMesa[i] = new ArrayList<>();
        }
    }


    public static boolean adicionarItem(
            int numeroMesa,
            String nome,
            double preco,
            String setor) {

        if (numeroMesa < 1 || numeroMesa > itensPorMesa.length) {
            return false;
        }

        if (!EstadoMesas.estaOcupada(numeroMesa)) {
            return false;
        }

        List<Item> itens = itensPorMesa[numeroMesa - 1];

        for (Item item : itens) {
            if (item.getNome().equals(nome)) {
                item.aumentarQuantidade();
                return true;
            }
        }

        itens.add(new Item(nome, preco, setor));
        return true;
    }


    public static List<Item> getItens(int numeroMesa) {
        return itensPorMesa[numeroMesa - 1];
    }

    public static double calcularTotal(int numeroMesa) {
        double total = 0;

        for (Item item : getItens(numeroMesa)) {
            total += item.calcularSubtotal();
        }

        return total;
    }
    public static boolean removerItem(int numeroMesa, String nome) {

        if (numeroMesa < 1 || numeroMesa > itensPorMesa.length) {
            return false;
        }

        List<Item> itens = itensPorMesa[numeroMesa - 1];

        for (Item item : itens) {

            if (item.getNome().equals(nome)) {

                if (item.getQuantidade() > 1) {
                    item.diminuirQuantidade();
                } else {
                    itens.remove(item);
                }

                return true;
            }
        }

        return false;
    }
    public static void limparComanda(int numeroMesa) {
        itensPorMesa[numeroMesa - 1].clear();
    }

}
