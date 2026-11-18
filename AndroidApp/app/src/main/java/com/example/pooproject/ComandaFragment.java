
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
import android.app.AlertDialog;
import android.widget.Toast;

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

    private boolean ehGerente;

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

        ehGerente = SessaoUsuario.ehGerente();

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

        Button botaoCancelarComanda = view.findViewById(R.id.botaoCancelarComanda);
        botaoCancelarComanda.setVisibility(
                ehGerente ? View.VISIBLE : View.GONE
        );
        botaoCancelarComanda.setOnClickListener(v ->
                confirmarCancelamento()
        );
    }

    private void confirmarCancelamento() {

        if (!ehGerente) {
            return;
        }

        if (!EstadoMesas.estaOcupada(numeroMesa)) {
            Toast.makeText(
                    requireContext(),
                    "Não existe uma comanda aberta nesta mesa.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Cancelar comanda")
                .setMessage(
                        "Deseja realmente cancelar a comanda desta mesa? " +
                                "Todos os itens registrados serão removidos."
                )
                .setNegativeButton("Voltar", null)
                .setPositiveButton("Cancelar comanda", (dialog, which) -> {

                    ComandaTemporaria.limparComanda(numeroMesa);

                    EstadoMesas.fecharComanda(numeroMesa);

                    atualizarTela();

                    Toast.makeText(
                            requireContext(),
                            "Comanda cancelada.",
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .show();
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

                LinearLayout linha = new LinearLayout(requireContext());

                linha.setOrientation(LinearLayout.HORIZONTAL);
                linha.setGravity(android.view.Gravity.CENTER_VERTICAL);
                linha.setPadding(8, 12, 8, 12);

                TextView informacoes = new TextView(requireContext());

                String texto =
                        item.getNome()
                                + " — " + item.getQuantidade()
                                + " x " + moeda.format(item.getPreco())
                                + "\n" + item.getSetor()
                                + " | Subtotal: "
                                + moeda.format(item.calcularSubtotal());

                informacoes.setText(texto);
                informacoes.setTextSize(15);

                LinearLayout.LayoutParams parametrosTexto =
                        new LinearLayout.LayoutParams(
                                0,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                1
                        );

                linha.addView(informacoes, parametrosTexto);

                if (ehGerente) {

                    Button botaoRemover = new Button(requireContext());
                    botaoRemover.setText("Remover");

                    botaoRemover.setOnClickListener(v -> {

                        ComandaTemporaria.removerItem(
                                numeroMesa,
                                item.getNome()
                        );

                        atualizarTela();
                    });

                    linha.addView(botaoRemover);
                }

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
