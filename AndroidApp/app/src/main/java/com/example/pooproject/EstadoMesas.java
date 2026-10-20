package com.example.pooproject;

public class EstadoMesas {

    private static final boolean[] ocupadas = {
            false, false, true, false
    };

    public static boolean estaOcupada(int numeroMesa) {
        return ocupadas[numeroMesa - 1];
    }

    public static boolean abrirComanda(int numeroMesa) {
        int indice = numeroMesa - 1;

        if (ocupadas[indice]) {
            return false;
        }

        ocupadas[indice] = true;
        return true;
    }

    public static void fecharComanda(int numeroMesa) {
        int indice = numeroMesa - 1;
        ocupadas[indice] = false;
    }

}