package com.example.pooproject;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.annotation.*;
import androidx.navigation.fragment.NavHostFragment;
import org.json.*;

public final class ComandaFragment extends AtlasFragment {
    private TextView status;
    private TextView total;
    private LinearLayout list;
    private Button open;
    private Button menu;
    private Button request;
    private boolean loading;
    private boolean submitting;

    public ComandaFragment() { super(R.layout.fragment_comanda); }

    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state) {
        ((TextView)view.findViewById(R.id.tituloComanda)).setText(requireArguments().getString("tableLabel"));
        status = view.findViewById(R.id.statusComanda);
        total = view.findViewById(R.id.totalComanda);
        list = view.findViewById(R.id.listaItensComanda);
        open = view.findViewById(R.id.botaoAbrirComanda);
        menu = view.findViewById(R.id.botaoCardapio);
        request = view.findViewById(R.id.botaoPagamento);
        view.findViewById(R.id.botaoCancelarComanda).setVisibility(View.GONE);
        request.setText("Solicitar conta");
        open.setEnabled(false);
        menu.setEnabled(false);
        request.setEnabled(false);
        menu.setOnClickListener(v -> NavHostFragment.findNavController(this)
                .navigate(R.id.action_comanda_to_cardapio,requireArguments()));
        open.setOnClickListener(v -> action("open"));
        request.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Solicitar conta").setMessage("A comanda será encaminhada ao caixa e ficará fechada para novos pedidos.")
                .setNegativeButton("Voltar",null).setPositiveButton("Solicitar",(d,w) -> action("request")).show());
    }

    @Override protected void refresh() {
        if (!visibleView() || loading || submitting || SessaoUsuario.get() == null) return;
        loading = true;
        var session = SessaoUsuario.get();
        View view = getView();
        long table = tableId();
        SessaoUsuario.run(() -> (JSONObject)session.get("tabs/" + table),order -> {
            loading = false;
            if (getView() != view) return;
            JSONObject tab = order.optJSONObject("tab");
            boolean opened = tab != null && "ABERTA".equals(tab.optString("status"));
            boolean pending = false;
            try { pending = session.pending(table) != null; } catch (JSONException ignored) {}
            open.setEnabled(tab == null);
            menu.setEnabled(tab == null || opened || pending);
            request.setEnabled(opened);
            status.setText(tab == null ? "Nenhuma comanda aberta. Você também pode começar pelo cardápio."
                    : "Comanda " + tab.optString("id") + " · " + (opened ? "Aberta" : "Conta solicitada"));
            total.setText("Total confirmado " + cash(order.optString("total")));
            list.removeAllViews();
            JSONArray items = order.optJSONArray("items");
            if (items == null || items.length() == 0) list.addView(line("Nenhum item registrado.",14));
            else for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                String text = item.optInt("quantity") + " × " + item.optString("name") + " · " + cash(item.optString("unit_price"))
                        + "\n" + item.optString("station") + " · " + state(item.optString("order_status"));
                list.addView(line(text,15));
            }
        }, failure -> { loading = false; if (getView() == view) error(failure,status); });
    }

    private void action(String action) {
        if (submitting || SessaoUsuario.get() == null) return;
        submitting = true;
        var session = SessaoUsuario.get();
        long table = tableId();
        View view = getView();
        open.setEnabled(false);
        request.setEnabled(false);
        status.setText("Enviando solicitação…");
        SessaoUsuario.run(() -> session.post("tabs/" + table + "/" + action,new JSONObject()),
                ok -> { submitting = false; if (getView() == view) refresh(); },
                failure -> { submitting = false; if (getView() == view) { error(failure,status); request.setEnabled(true); open.setEnabled(true); } });
    }
}
