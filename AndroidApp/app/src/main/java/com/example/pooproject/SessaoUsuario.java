
package com.example.pooproject;

public class SessaoUsuario {

    private static String idUsuario;
    private static String perfil;

    public static boolean autenticar(String id, String senha) {

        if (id == null || senha == null ||
                id.trim().isEmpty() || senha.trim().isEmpty()) {
            return false;
        }

        id = id.trim();

        if (id.equals("gerente")) {

            if (!senha.equals("123456")) {
                return false;
            }

            idUsuario = id;
            perfil = "GERENTE";

        } else {

            if (!id.matches("\\d+")) {
                return false;
            }

            idUsuario = id;
            perfil = "GARCOM";
        }

        return true;
    }

    public static String getIdUsuario() {
        return idUsuario;
    }

    public static String getPerfil() {
        return perfil;
    }

    public static boolean ehGerente() {
        return "GERENTE".equals(perfil);
    }

    public static void encerrarSessao() {
        idUsuario = null;
        perfil = null;
    }
}
