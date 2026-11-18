
package com.example.pooproject;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class AdministracaoFragment extends Fragment {

    private EditText campoNomeProduto;
    private EditText campoPrecoProduto;
    private Spinner spinnerSetor;
    private LinearLayout listaAdministracao;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        return inflater.inflate(
                R.layout.fragment_administracao,
                container,
                false
        );
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        if (!SessaoUsuario.ehGerente()) {
            Toast.makeText(
                    requireContext(),
                    "Acesso permitido apenas ao gerente.",
                    Toast.LENGTH_SHORT
            ).show();

            NavHostFragment.findNavController(this)
                    .popBackStack();

            return;
        }

        campoNomeProduto = view.findViewById(R.id.campoNomeProduto);
        campoPrecoProduto = view.findViewById(R.id.campoPrecoProduto);
        spinnerSetor = view.findViewById(R.id.spinnerSetor);
        listaAdministracao = view.findViewById(R.id.listaAdministracao);

        Button botaoAdicionar =
                view.findViewById(R.id.botaoAdicionarProduto);

        Button botaoVoltar =
                view.findViewById(R.id.botaoVoltar);

        configurarSpinner();
        atualizarLista();

        botaoAdicionar.setOnClickListener(v -> adicionarProduto());

        botaoVoltar.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_administracao_to_mesas)
        );
    }

    private void configurarSpinner() {

        String[] setores = {"Cozinha", "Bar"};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                setores
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerSetor.setAdapter(adapter);
    }

    private void adicionarProduto() {

        String nome = campoNomeProduto.getText()
                .toString().trim();

        String precoTexto = campoPrecoProduto.getText()
                .toString().trim()
                .replace(",", ".");

        if (nome.isEmpty() || precoTexto.isEmpty()) {
            Toast.makeText(
                    requireContext(),
                    "Preencha o nome e o preço.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        double preco;

        try {
            preco = Double.parseDouble(precoTexto);
        } catch (NumberFormatException e) {
            Toast.makeText(
                    requireContext(),
                    "Informe um preço válido.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String setor = spinnerSetor.getSelectedItem().toString();

        boolean adicionado = Cardapio.adicionarProduto(
                nome,
                preco,
                setor
        );

        if (!adicionado) {
            Toast.makeText(
                    requireContext(),
                    "Não foi possível cadastrar o produto.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        campoNomeProduto.setText("");
        campoPrecoProduto.setText("");

        atualizarLista();

        Toast.makeText(
                requireContext(),
                "Produto cadastrado.",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void atualizarLista() {

        listaAdministracao.removeAllViews();

        List<ProdutoMenu> produtos = Cardapio.getProdutos();

        NumberFormat moeda = NumberFormat.getCurrencyInstance(
                new Locale("pt", "BR")
        );

        for (ProdutoMenu produto : produtos) {

            LinearLayout linha = new LinearLayout(requireContext());

            linha.setOrientation(LinearLayout.HORIZONTAL);
            linha.setGravity(android.view.Gravity.CENTER_VERTICAL);
            linha.setPadding(0, 8, 0, 8);

            LinearLayout informacoes = new LinearLayout(requireContext());

            informacoes.setOrientation(LinearLayout.VERTICAL);

            TextView nome = new TextView(requireContext());
            nome.setText(produto.getNome());
            nome.setTextSize(16);

            TextView detalhes = new TextView(requireContext());
            detalhes.setText(
                    produto.getSetor() + " • " +
                            moeda.format(produto.getPreco())
            );

            informacoes.addView(nome);
            informacoes.addView(detalhes);

            Button botaoRemover = new Button(requireContext());
            botaoRemover.setText("Remover");

            botaoRemover.setOnClickListener(v -> {

                boolean removido = Cardapio.removerProduto(produto);

                if (removido) {
                    atualizarLista();

                    Toast.makeText(
                            requireContext(),
                            "Produto removido do cardápio.",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });

            LinearLayout.LayoutParams parametrosInformacoes =
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1
                    );

            linha.addView(informacoes, parametrosInformacoes);
            linha.addView(botaoRemover);

            listaAdministracao.addView(linha);

            View separador = new View(requireContext());

            separador.setBackgroundColor(
                    android.graphics.Color.LTGRAY
            );

            listaAdministracao.addView(
                    separador,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            1
                    )
            );
        }
    }
}
