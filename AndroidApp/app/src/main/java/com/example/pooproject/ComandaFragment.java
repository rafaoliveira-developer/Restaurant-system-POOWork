
package com.example.pooproject;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class ComandaFragment extends Fragment {

    private int numeroMesa;
    private TextView status;
    private TextView total;
    private LinearLayout listaItens;
    private Button botaoAbrir;
    private Button botaoCardapio;

    private Button botaoPagamento;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        return inflater.inflate(
                R.layout.fragment_comanda,
                container,
                false
        );
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        numeroMesa = requireArguments()
                .getInt("numeroMesa", -1);

        TextView titulo =
                view.findViewById(R.id.tituloComanda);

        status = view.findViewById(R.id.statusComanda);
        total = view.findViewById(R.id.totalComanda);

        listaItens =
                view.findViewById(R.id.listaItensComanda);

        botaoAbrir =
                view.findViewById(R.id.botaoAbrirComanda);

        botaoCardapio =
                view.findViewById(R.id.botaoCardapio);

        Button botaoPagamento =
                view.findViewById(R.id.botaoPagamento);

        titulo.setText("Mesa " + numeroMesa);

        botaoAbrir.setOnClickListener(v -> {
            boolean abriu =
                    EstadoMesas.abrirComanda(numeroMesa);

            if (abriu) {
                atualizarTela();
            } else {
                status.setText("Esta mesa já está ocupada");
                botaoAbrir.setEnabled(false);
            }
        });

        botaoPagamento.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putInt("numeroMesa", numeroMesa);

            NavHostFragment.findNavController(this)
                    .navigate(
                            R.id.action_comanda_to_pagamento,
                            args
                    );
        });

        botaoCardapio.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putInt("numeroMesa", numeroMesa);

            NavHostFragment.findNavController(this)
                    .navigate(
                            R.id.action_comanda_to_cardapio,
                            args
                    );
        });

        atualizarTela();
    }

    @Override
    public void onResume() {
        super.onResume();

        atualizarTela();
    }

    private void atualizarTela() {

        boolean ocupada =
                EstadoMesas.estaOcupada(numeroMesa);

        if (ocupada) {
            status.setText("Comanda aberta");
            botaoAbrir.setEnabled(false);
            botaoCardapio.setEnabled(true);
        } else {
            status.setText("Nenhuma comanda aberta");
            botaoAbrir.setEnabled(true);
            botaoCardapio.setEnabled(false);
        }

        atualizarItens();
    }

    private void atualizarItens() {

        listaItens.removeAllViews();

        List<ComandaTemporaria.Item> itens =
                ComandaTemporaria.getItens(numeroMesa);

        if (itens.isEmpty()) {
            TextView mensagem = new TextView(requireContext());
            mensagem.setText("Nenhum item registrado.");
            mensagem.setTextSize(15);
            mensagem.setPadding(8, 8, 8, 8);

            listaItens.addView(mensagem);
        } else {
            NumberFormat moeda =
                    NumberFormat.getCurrencyInstance(
                            new Locale("pt", "BR")
                    );

            for (ComandaTemporaria.Item item : itens) {

                TextView linha = new TextView(requireContext());

                String texto =
                        item.getNome()
                                + " — " + item.getQuantidade()
                                + " x " + moeda.format(item.getPreco())
                                + "\n" + item.getSetor()
                                + " | Subtotal: "
                                + moeda.format(item.calcularSubtotal());

                linha.setText(texto);
                linha.setTextSize(15);
                linha.setPadding(8, 12, 8, 12);

                listaItens.addView(linha);
            }
        }

        NumberFormat moeda =
                NumberFormat.getCurrencyInstance(
                        new Locale("pt", "BR")
                );

        total.setText(
                "Total previsto: "
                        + moeda.format(
                        ComandaTemporaria.calcularTotal(numeroMesa)
                )
        );
    }
}
