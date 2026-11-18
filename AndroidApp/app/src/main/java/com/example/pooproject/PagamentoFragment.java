
package com.example.pooproject;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import java.text.NumberFormat;
import java.util.Locale;

public class PagamentoFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        return inflater.inflate(
                R.layout.fragment_pagamento,
                container,
                false
        );
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        int numeroMesa = requireArguments()
                .getInt("numeroMesa", -1);

        TextView titulo =
                view.findViewById(R.id.tituloPagamento);

        TextView valor =
                view.findViewById(R.id.valorPagamento);

        TextView status =
                view.findViewById(R.id.statusPagamento);

        RadioGroup grupo =
                view.findViewById(R.id.grupoPagamento);

        Button confirmar =
                view.findViewById(R.id.botaoConfirmarPagamento);

        Button cancelar =
                view.findViewById(R.id.botaoCancelarPagamento);

        NumberFormat moeda =
                NumberFormat.getCurrencyInstance(
                        new Locale("pt", "BR")
                );

        double total =
                ComandaTemporaria.calcularTotal(numeroMesa);

        titulo.setText("Pagamento — Mesa " + numeroMesa);
        valor.setText("Total: " + moeda.format(total));


        confirmar.setOnClickListener(v -> {

            int opcao = grupo.getCheckedRadioButtonId();

            if (opcao == -1) {
                status.setText(
                        "Selecione uma forma de pagamento."
                );
                return;
            }

             double totalAtual = ComandaTemporaria.calcularTotal(numeroMesa);

            if (totalAtual <= 0) {
                status.setText(
                        "Não é possível pagar uma comanda vazia."
                );
                return;
            }

            String metodo;

            if (opcao == R.id.opcaoDinheiro) {
                metodo = "Dinheiro";
            } else if (opcao == R.id.opcaoCartao) {
                metodo = "Cartão";
            } else {
                metodo = "Pix";
            }

            // Encerra a comanda e libera a mesa.
            ComandaTemporaria.limparComanda(numeroMesa);
            EstadoMesas.fecharComanda(numeroMesa);

            // Retorna à tela de mesas.
            boolean voltou = NavHostFragment.findNavController(this)
                    .popBackStack(R.id.mesasFragment, false);

            if (!voltou) {
                status.setText(
                        "Pagamento simulado, mas não foi possível "
                                + "retornar à tela de mesas."
                );
            }
        });

        cancelar.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .popBackStack()
        );
    }
}
