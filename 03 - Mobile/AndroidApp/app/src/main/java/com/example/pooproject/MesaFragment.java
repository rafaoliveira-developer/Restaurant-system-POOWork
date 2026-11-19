package com.example.pooproject;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.annotation.*;
import androidx.navigation.fragment.NavHostFragment;
import org.json.*;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;

public final class MesaFragment extends AtlasFragment {
    private LinearLayout area;
    private TextView status;
    private boolean loading;
    private String last = "";

    public MesaFragment() { super(R.layout.fragment_mesas); }

    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state) {
        area = view.findViewById(R.id.listaMesas);
        status = view.findViewById(R.id.subtituloMesas);
        last = "";
        view.findViewById(R.id.botaoHistorico).setOnClickListener(v -> NavHostFragment.findNavController(this)
                .navigate(R.id.historicoFragment));
        view.findViewById(R.id.botaoLogout).setOnClickListener(v -> {
            SessaoUsuario session = SessaoUsuario.get();
            if (session != null) SessaoUsuario.run(() -> { session.logout(); return true; },x -> {},x -> {});
            login();
        });
        view.findViewById(R.id.botaoAtualizar).setOnClickListener(v -> {
            var session = SessaoUsuario.get();
            if (session != null && !session.connected()) SessaoUsuario.run(() -> { session.connect(); return true; },
                    x -> refresh(),err -> error(err,status));
            refresh();
        });
    }

    @Override protected void refresh() {
        var session = SessaoUsuario.get();
        View current = getView();
        if (current == null || session == null || loading) return;
        loading = true;
        SessaoUsuario.run(() -> new Object[]{EstadoMesas.getMesas(session),session.get("restaurant")},value -> {
            loading = false;
            if (getView() != current) return;
            JSONArray tables = (JSONArray)value[0];
            ((TextView)current.findViewById(R.id.tituloMesas)).setText(((JSONObject)value[1]).optString("name","ATLAS"));
            ((TextView)current.findViewById(R.id.perfilFuncionario)).setText(session.name);
            status.setText(tables.length() == 0 ? "Nenhuma mesa cadastrada."
                    : session.connected() ? "Selecione uma mesa" : "MQTT desconectado · toque em Atualizar");
            if (!tables.toString().equals(last)) { render(tables,session); last = tables.toString(); }
        }, err -> { loading = false; if (getView() == current) error(err,status); });
    }

    private void render(JSONArray tables,SessaoUsuario session) {
        area.removeAllViews();
        var groups = new LinkedHashMap<String,List<JSONObject>>();
        for (int i = 0; i < tables.length(); i++) {
            JSONObject table = tables.optJSONObject(i);
            groups.computeIfAbsent(table.optString("area"),key -> new ArrayList<>()).add(table);
        }
        groups.forEach((name,entries) -> {
            area.addView(line(name,18));
            for (int i = 0; i < entries.size(); i += 2) {
                LinearLayout row = new LinearLayout(requireContext());
                for (int k = i; k < Math.min(i+2,entries.size()); k++) {
                    JSONObject table = entries.get(k);
                    String state = table.optString("status");
                    Button action = button(table.optString("label") + "\n" + state(state));
                    action.setMinHeight(dp(78));
                    action.setEnabled(!state.equals("INDISPONIVEL") && (table.isNull("tab_id") || table.optLong("employee_id") == session.employeeId));
                    action.setOnClickListener(v -> {
                        Bundle args = new Bundle();
                        args.putLong("tableId",table.optLong("id"));
                        args.putString("tableLabel",table.optString("label"));
                        NavHostFragment.findNavController(this).navigate(R.id.action_mesas_to_comanda,args);
                    });
                    var params = new LinearLayout.LayoutParams(0,-2,1);
                    params.setMargins(0,0,dp(8),dp(8));
                    row.addView(action,params);
                }
                if (row.getChildCount() == 1) row.addView(new View(requireContext()),new LinearLayout.LayoutParams(0,1,1));
                area.addView(row);
            }
        });
    }
}
