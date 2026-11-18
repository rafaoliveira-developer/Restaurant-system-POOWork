
package com.example.pooproject;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

public class MesaFragment extends Fragment {

    @Override
    public void onResume() {
        super.onResume();

        if (getView() != null) {
            atualizarMesas(getView());
        }
    }

    private void atualizarMesas(View view) {

        int[] ids = {
                R.id.mesa01,
                R.id.mesa02,
                R.id.mesa03,
                R.id.mesa04
        };

        for (int i = 0; i < ids.length; i++) {

            int numeroMesa = i + 1;
            Button botao = view.findViewById(ids[i]);

            String estado = EstadoMesas.estaOcupada(numeroMesa)
                    ? "Ocupada"
                    : "Livre";

            botao.setText("Mesa "
                    + String.format("%02d", numeroMesa)
                    + "\n" + estado);
        }
    }

    private void atualizarPerfil(View view) {

        TextView perfilFuncionario =
                view.findViewById(R.id.perfilFuncionario);

        Button botaoAdministracao =
                view.findViewById(R.id.botaoAdministracao);

        String id = SessaoUsuario.getIdUsuario();
        String perfil = SessaoUsuario.getPerfil();

        perfilFuncionario.setText(
                "Funcionário: " + id + " | Perfil: " + perfil
        );

        if (SessaoUsuario.ehGerente()) {
            botaoAdministracao.setVisibility(View.VISIBLE);
        } else {
            botaoAdministracao.setVisibility(View.GONE);
        }
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        return inflater.inflate(
                R.layout.fragment_mesas,
                container,
                false
        );
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        atualizarMesas(view);
        atualizarPerfil(view);

        Button botaoLogout =
                view.findViewById(R.id.botaoLogout);

        botaoLogout.setOnClickListener(v -> {

            SessaoUsuario.encerrarSessao();

            NavOptions opcoes = new NavOptions.Builder()
                    .setPopUpTo(R.id.loginFragment, true)
                    .build();

            NavHostFragment.findNavController(this)
                    .navigate(R.id.loginFragment, null, opcoes);
        });

        view.findViewById(R.id.botaoAdministracao)
                .setOnClickListener(v ->
                        NavHostFragment.findNavController(this)
                                .navigate(R.id.action_mesas_to_administracao)
                );

        view.findViewById(R.id.mesa01)
                .setOnClickListener(v -> abrirMesa(1));

        view.findViewById(R.id.mesa02)
                .setOnClickListener(v -> abrirMesa(2));

        view.findViewById(R.id.mesa03)
                .setOnClickListener(v -> abrirMesa(3));

        view.findViewById(R.id.mesa04)
                .setOnClickListener(v -> abrirMesa(4));
    }

    private void abrirMesa(int numeroMesa) {

        Bundle args = new Bundle();
        args.putInt("numeroMesa", numeroMesa);

        NavHostFragment.findNavController(this)
                .navigate(
                        R.id.action_mesas_to_comanda,
                        args
                );
    }
}
