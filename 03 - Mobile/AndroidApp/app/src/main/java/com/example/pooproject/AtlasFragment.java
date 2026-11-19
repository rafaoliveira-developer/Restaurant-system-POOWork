package com.example.pooproject;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.annotation.LayoutRes;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

abstract class AtlasFragment extends Fragment {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable pulse = new Runnable() {
        public void run() { refresh(); main.postDelayed(this,12_000); }
    };
    private final Runnable change = () -> {
        main.removeCallbacks(pulse);
        main.postDelayed(pulse,220);
    };

    AtlasFragment(@LayoutRes int layout) { super(layout); }

    @Override public void onStart() {
        super.onStart();
        if (SessaoUsuario.get() == null) { login(); return; }
        SessaoUsuario.listen(change);
        pulse.run();
    }

    @Override public void onStop() {
        SessaoUsuario.unlisten(change);
        main.removeCallbacksAndMessages(null);
        super.onStop();
    }

    protected abstract void refresh();

    void login() {
        var nav = NavHostFragment.findNavController(this);
        nav.navigate(R.id.loginFragment,null,new NavOptions.Builder()
                .setPopUpTo(R.id.nav_graph,true).build());
    }

    boolean visibleView() { return isAdded() && getView() != null; }

    void error(Exception error,TextView label) {
        if (!visibleView()) return;
        label.setText(error instanceof SessaoUsuario.ApiError ? error.getMessage() : "Conexão indisponível. Tente novamente.");
        if (error instanceof SessaoUsuario.ApiError && ((SessaoUsuario.ApiError)error).status == 401) {
            SessaoUsuario session = SessaoUsuario.get();
            if (session != null) SessaoUsuario.run(() -> { session.logout(); return true; },x -> {},x -> {});
            login();
        }
    }

    int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }

    TextView line(String value,int size) {
        TextView text = new TextView(requireContext());
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(0xff15253a);
        text.setPadding(0,dp(7),0,dp(7));
        return text;
    }

    Button button(String label) {
        Button button = new com.google.android.material.button.MaterialButton(requireContext(),null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(label);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        return button;
    }

    long tableId() { return requireArguments().getLong("tableId"); }

    static String cash(String value) {
        try { return NumberFormat.getCurrencyInstance(new Locale("pt","BR")).format(new BigDecimal(value)); }
        catch (NumberFormatException ex) { return "—"; }
    }

    static String state(String value) {
        switch (value) {
            case "LIVRE": return "Livre";
            case "OCUPADA": return "Ocupada";
            case "INDISPONIVEL": return "Indisponível";
            case "CONTA_SOLICITADA": return "Conta solicitada";
            case "PENDENTE": return "Em produção";
            case "CONFIRMADO": return "Confirmado";
            case "PARCIAL": return "Confirmação parcial";
            case "ERRO": return "Falha de produção";
            case "REJEITADO": return "Rejeitado pelo Backend";
            default: return value;
        }
    }
}
