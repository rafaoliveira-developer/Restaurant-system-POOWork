
package com.example.pooproject;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class CardapioFragment extends Fragment {

    private String categoriaAtual = "Cozinha";

    private EditText campoPesquisa;
    private TextView tituloCategoria;
    private TextView textoSelecao;
    private TextView mensagemListaVazia;
    private LinearLayout listaProdutos;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        return inflater.inflate(
                R.layout.fragment_cardapio,
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

        campoPesquisa = view.findViewById(R.id.campoPesquisa);
        tituloCategoria = view.findViewById(R.id.tituloCategoria);
        textoSelecao = view.findViewById(R.id.textoSelecao);
        listaProdutos = view.findViewById(R.id.listaProdutos);
        mensagemListaVazia =
                view.findViewById(R.id.mensagemListaVazia);

        Button botaoCozinha =
                view.findViewById(R.id.botaoCozinha);
        Button botaoBar =
                view.findViewById(R.id.botaoBar);
        Button botaoVoltar =
                view.findViewById(R.id.botaoVoltarComanda);

        botaoCozinha.setOnClickListener(v -> {
            categoriaAtual = "Cozinha";
            tituloCategoria.setText(categoriaAtual);
            atualizarLista(numeroMesa);
        });

        botaoBar.setOnClickListener(v -> {
            categoriaAtual = "Bar";
            tituloCategoria.setText(categoriaAtual);
            atualizarLista(numeroMesa);
        });

        campoPesquisa.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {
                atualizarLista(numeroMesa);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        botaoVoltar.setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack()
        );

        atualizarLista(numeroMesa);
    }

    private void atualizarLista(int numeroMesa) {
        listaProdutos.removeAllViews();

        String pesquisa = normalizarTexto(
                campoPesquisa.getText().toString()
        );

        int encontrados = 0;

        List<ProdutoMenu> produtos = Cardapio.getProdutos();

        for (ProdutoMenu produto : produtos) {

            if (!produto.getSetor().equals(categoriaAtual)) {
                continue;
            }

            if (!normalizarTexto(produto.getNome()).contains(pesquisa)) {
                continue;
            }

            encontrados++;

            Button botaoProduto = new Button(requireContext());

            NumberFormat moeda =
                    NumberFormat.getCurrencyInstance(
                            new Locale("pt", "BR"));

            botaoProduto.setText(
                    produto.getNome() + " — " +
                            moeda.format(produto.getPreco()));

            botaoProduto.setOnClickListener(v -> {
                boolean adicionado = ComandaTemporaria.adicionarItem(
                        numeroMesa,
                        produto.getNome(),
                        produto.getPreco(),
                        produto.getSetor()
                );

                if (adicionado) {
                    textoSelecao.setText(
                            produto.getNome() + " adicionado à comanda");
                } else {
                    textoSelecao.setText(
                            "Abra uma comanda antes de adicionar produtos.");
                }
            });

            listaProdutos.addView(botaoProduto);
        }

        mensagemListaVazia.setVisibility(
                encontrados == 0 ? View.VISIBLE : View.GONE);
    }

    private String normalizarTexto(String texto) {
        return java.text.Normalizer
                .normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
