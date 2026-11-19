package com.example.pooproject;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;
import org.json.*;

public final class HistoricoFragment extends AtlasFragment {
    private boolean loading;
    private String previous = "";

    public HistoricoFragment() { super(R.layout.fragment_historico); }

    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state) {
        previous = "";
        view.findViewById(R.id.voltarHistorico).setOnClickListener(v ->
                androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack());
    }

    @Override protected void refresh() {
        var session = SessaoUsuario.get();
        View view = getView();
        if (session == null || view == null || loading) return;
        loading = true;
        TextView status = view.findViewById(R.id.statusHistorico);
        LinearLayout list = view.findViewById(R.id.listaHistorico);
        SessaoUsuario.run(() -> (JSONArray)session.get("orders"),rows -> {
            loading = false;
            if (view != getView()) return;
            status.setText(rows.length() == 0 ? "Nenhum pedido registrado." : rows.length() + " pedidos");
            if (rows.toString().equals(previous)) return;
            previous = rows.toString();
            list.removeAllViews();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject order = rows.optJSONObject(i);
                Button row = button("PED-" + order.optString("sequence") + " · " + order.optString("table_label")
                        + "\n" + (order.isNull("delivered_at") ? state(order.optString("status")) : "Entregue"));
                row.setOnClickListener(v -> detail(session,order,status));
                list.addView(row);
            }
        }, failure -> { loading = false; if (getView() == view) error(failure,status); });
    }

    private void detail(SessaoUsuario session,JSONObject summary,TextView status) {
        String path = "orders/" + summary.optString("id");
        SessaoUsuario.run(() -> (JSONObject)session.get(path),order -> {
            if (!visibleView()) return;
            StringBuilder details = new StringBuilder(state(order.optString("status")));
            JSONArray items = order.optJSONArray("items");
            for (int i = 0; items != null && i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                details.append("\n").append(item.optString("quantity")).append(" × ").append(item.optString("name"));
            }
            JSONArray stations = order.optJSONArray("stations");
            for (int i = 0; stations != null && i < stations.length(); i++) {
                JSONObject station = stations.optJSONObject(i);
                details.append("\n").append(station.optString("station")).append(" · ").append(station.optString("status"));
                if (!station.optString("error").isEmpty()) details.append(" · ").append(station.optString("error"));
            }
            AlertDialog.Builder dialog = new AlertDialog.Builder(requireContext())
                    .setTitle("PED-" + order.optString("sequence")).setMessage(details).setNegativeButton("Voltar",null);
            if (order.isNull("delivered_at") && ("CONFIRMADO".equals(order.optString("status")) || "PARCIAL".equals(order.optString("status"))))
                dialog.setPositiveButton("Marcar entregue",(d,w) -> SessaoUsuario.run(() -> session.post(path + "/deliver",new JSONObject()),
                        response -> refresh(),failure -> error(failure,status)));
            dialog.show();
        },failure -> error(failure,status));
    }
}
