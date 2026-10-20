package com.example.pooproject;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;

import androidx.fragment.app.Fragment;

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

            botao.setText("Mesa " + String.format("%02d", numeroMesa)
                    + "\n" + estado);
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

        atualizarMesas(view);

        super.onViewCreated(view, savedInstanceState);

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