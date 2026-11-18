
package com.example.pooproject;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

public class LoginFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        return inflater.inflate(
                R.layout.fragment_login,
                container,
                false
        );
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        EditText campoCodigo =
                view.findViewById(R.id.campoCodigo);

        EditText campoSenha =
                view.findViewById(R.id.campoSenha);

        TextView mensagem =
                view.findViewById(R.id.mensagemLogin);

        View botaoEntrar =
                view.findViewById(R.id.botaoEntrar);

        botaoEntrar.setOnClickListener(v -> {

            String id = campoCodigo.getText().toString().trim();
            String senha = campoSenha.getText().toString();

            boolean autenticado =
                    SessaoUsuario.autenticar(id, senha);

            if (autenticado) {

                String perfil = SessaoUsuario.getPerfil();

                Toast.makeText(
                        requireContext(),
                        "Login realizado como: " + perfil,
                        Toast.LENGTH_LONG
                ).show();

                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_login_to_mesas);

            }
            else {

                mensagem.setText(
                        "Preencha o código e a senha."
                );
            }
        });
    }
}
