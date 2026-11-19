package com.example.pooproject;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

public final class LoginFragment extends Fragment {
    public LoginFragment() { super(R.layout.fragment_login); }

    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state) {
        var settings = requireContext().getSharedPreferences("atlas-session-settings",0);
        EditText server = view.findViewById(R.id.campoServidor);
        EditText broker = view.findViewById(R.id.campoBroker);
        EditText code = view.findViewById(R.id.campoCodigo);
        EditText secret = view.findViewById(R.id.campoSenha);
        EditText mqtt = view.findViewById(R.id.campoSenhaMqtt);
        TextView status = view.findViewById(R.id.mensagemLogin);
        Button enter = view.findViewById(R.id.botaoEntrar);
        server.setText(settings.getString("server",""));
        broker.setText(settings.getString("broker",""));
        var context = requireContext().getApplicationContext();
        enter.setOnClickListener(v -> {
            String url = server.getText().toString();
            String endpoint = broker.getText().toString();
            String username = code.getText().toString();
            String password = secret.getText().toString();
            String mqttSecret = mqtt.getText().toString();
            if (url.trim().isEmpty() || endpoint.trim().isEmpty() || username.trim().isEmpty() || password.isEmpty() || mqttSecret.isEmpty()) {
                status.setText("Preencha o acesso e os endereços da operação.");
                return;
            }
            enter.setEnabled(false);
            status.setText("Entrando…");
            SessaoUsuario.run(() -> SessaoUsuario.login(context,url,endpoint,username,password,mqttSecret),account -> {
                if (getView() != view) return;
                secret.setText("");
                mqtt.setText("");
                NavHostFragment.findNavController(this).navigate(R.id.action_login_to_mesas,null,
                        new NavOptions.Builder().setPopUpTo(R.id.loginFragment,true).build());
            }, error -> {
                if (getView() != view) return;
                enter.setEnabled(true);
                status.setText(error instanceof SessaoUsuario.ApiError || error instanceof IllegalArgumentException
                        ? error.getMessage() : error instanceof java.io.IOException && error.getMessage() != null
                        && error.getMessage().startsWith("Informe") ? error.getMessage() : "Não foi possível entrar. Confira a rede e os dados.");
            });
        });
    }
}
