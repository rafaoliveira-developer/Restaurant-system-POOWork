package com.example.pooproject;

import android.os.Bundle;
import android.text.*;
import android.view.View;
import android.widget.*;
import androidx.annotation.*;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import org.json.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CardapioFragment extends AtlasFragment {
    private static final ExecutorService IMAGES = Executors.newFixedThreadPool(3);
    private ComandaTemporaria draft;
    private List<ProdutoMenu> products = new ArrayList<>();
    private String version = null;
    private String category = "";
    private JSONObject pending;
    private JSONObject result;
    private LinearLayout categories;
    private LinearLayout rows;
    private TextView status;
    private EditText search;
    private Button review;
    private boolean loading;
    private boolean sending;

    public static final class DraftModel extends ViewModel { final ComandaTemporaria draft = new ComandaTemporaria(); }

    public CardapioFragment() { super(R.layout.fragment_cardapio); }

    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state) {
        draft = new ViewModelProvider(this).get(DraftModel.class).draft;
        version = null;
        status = view.findViewById(R.id.textoSelecao);
        categories = view.findViewById(R.id.categoriasCardapio);
        rows = view.findViewById(R.id.listaProdutos);
        search = view.findViewById(R.id.campoPesquisa);
        review = view.findViewById(R.id.botaoRevisar);
        ((TextView)view.findViewById(R.id.tituloCardapio)).setText(requireArguments().getString("tableLabel") + " · Cardápio");
        view.findViewById(R.id.botaoVoltarComanda).setOnClickListener(v ->
                androidx.navigation.fragment.NavHostFragment.findNavController(this).popBackStack());
        review.setOnClickListener(v -> { if (pending != null) pendingDialog(); else review(); });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { renderRows(); }
            public void afterTextChanged(Editable s) {}
        });
        buttonState();
    }

    @Override protected void refresh() {
        var session = SessaoUsuario.get();
        View view = getView();
        if (session == null || view == null || loading || sending) return;
        loading = true;
        long table = tableId();
        SessaoUsuario.run(() -> {
            JSONArray catalog = (JSONArray)session.get("products");
            JSONObject queued = session.pending(table);
            JSONObject outcome = null;
            if (queued != null) {
                try { outcome = (JSONObject)session.get("orders/" + queued.getString("orderId")); }
                catch (SessaoUsuario.ApiError ex) { if (ex.status != 404) throw ex; }
            }
            return new Object[]{catalog,queued,outcome};
        }, values -> {
            loading = false;
            if (getView() != view) return;
            JSONArray catalog = (JSONArray)values[0];
            pending = (JSONObject)values[1];
            result = (JSONObject)values[2];
            if (!catalog.toString().equals(version)) {
                version = catalog.toString();
                products = Cardapio.from(catalog);
                draft.update(products);
                renderCategories();
                renderRows();
            }
            buttonState();
        },failure -> { loading = false; if (getView() == view) error(failure,status); });
    }

    private void renderCategories() {
        categories.removeAllViews();
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.add("");
        for (var product : products) names.add(product.categoria);
        if (!names.contains(category)) category = "";
        for (String name : names) {
            Button filter = button(name.isEmpty() ? "Todas" : name);
            filter.setSelected(category.equals(name));
            filter.setEnabled(!category.equals(name));
            filter.setOnClickListener(v -> { category = name; renderCategories(); renderRows(); });
            categories.addView(filter);
        }
    }

    private void renderRows() {
        rows.removeAllViews();
        String query = search.getText().toString().toLowerCase(Locale.ROOT);
        for (var product : products) {
            if ((!category.isEmpty() && !category.equals(product.categoria)) || !product.nome.toLowerCase(Locale.ROOT).contains(query)) continue;
            LinearLayout row = new LinearLayout(requireContext());
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            if (!product.imagem.isEmpty()) {
                ImageView photo = new ImageView(requireContext());
                photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
                row.addView(photo,new LinearLayout.LayoutParams(dp(48),dp(48)));
                loadImage(photo,product.imagem);
            }
            TextView name = line(product.nome + "\n" + cash(product.preco.toPlainString())
                    + (product.descricao.isEmpty() ? "" : "\n" + product.descricao),15);
            row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            Button less = button("−");
            Button more = button("+");
            less.setContentDescription("Diminuir " + product.nome);
            more.setContentDescription("Adicionar " + product.nome);
            TextView quantity = line(Integer.toString(draft.quantity(product.id)),17);
            less.setOnClickListener(v -> { if (pending == null) draft.adjust(product,-1); quantity.setText("" + draft.quantity(product.id)); buttonState(); });
            more.setOnClickListener(v -> { if (pending == null) draft.adjust(product,1); quantity.setText("" + draft.quantity(product.id)); buttonState(); });
            row.addView(less,new LinearLayout.LayoutParams(dp(52),-2));
            row.addView(quantity,new LinearLayout.LayoutParams(dp(23),-2));
            row.addView(more,new LinearLayout.LayoutParams(dp(52),-2));
            rows.addView(row);
        }
        if (rows.getChildCount() == 0) rows.addView(line("Nenhum produto encontrado.",15));
    }

    private void buttonState() {
        if (review == null) return;
        var session = SessaoUsuario.get();
        if (pending == null) {
            review.setText("Revisar pedido · " + cash(draft.total().toPlainString()));
            review.setEnabled(!draft.items().isEmpty() && !sending);
            status.setText(session != null && session.connected() ? "Adicione os itens e revise o pedido."
                    : "MQTT indisponível. Você pode montar o pedido.");
        } else {
            review.setText("Acompanhar pedido");
            review.setEnabled(!sending);
            status.setText(result == null ? "Envio pendente · confira ou reenvie com o mesmo identificador."
                    : "PED-" + result.optString("sequence","—") + " · " + state(result.optString("status")));
        }
    }

    private void review() {
        final BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View panel = getLayoutInflater().inflate(R.layout.painel_revisao,null);
        dialog.setContentView(panel);
        LinearLayout items = panel.findViewById(R.id.revisaoItens);
        TextView total = panel.findViewById(R.id.revisaoTotal);
        Button submit = panel.findViewById(R.id.revisaoEnviar);
        Runnable render = new Runnable() {
            public void run() {
                items.removeAllViews();
                for (var item : draft.items()) {
                    LinearLayout row = new LinearLayout(requireContext());
                    row.setOrientation(LinearLayout.VERTICAL);
                    row.addView(line(item.quantity + " × " + item.product.nome + " · " + cash(item.subtotal().toPlainString()),16));
                    EditText notes = new EditText(requireContext());
                    notes.setHint("Observação");
                    notes.setFilters(new InputFilter[]{new InputFilter.LengthFilter(300)});
                    notes.setText(item.notes);
                    notes.addTextChangedListener(new TextWatcher() {
                        public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
                        public void onTextChanged(CharSequence s,int start,int before,int count) { item.notes = s.toString(); }
                        public void afterTextChanged(Editable s) {}
                    });
                    row.addView(notes);
                    LinearLayout actions = new LinearLayout(requireContext());
                    Button duplicate = button("Adicionar 1");
                    Button remove = button("Remover");
                    duplicate.setOnClickListener(v -> { draft.adjust(item.product,1); run(); });
                    remove.setOnClickListener(v -> { draft.adjust(item.product,-item.quantity); run(); });
                    actions.addView(duplicate);
                    actions.addView(remove);
                    row.addView(actions);
                    items.addView(row);
                }
                total.setText("Total estimado  " + cash(draft.total().toPlainString()));
                submit.setEnabled(!draft.items().isEmpty());
                renderRows();
                buttonState();
            }
        };
        submit.setOnClickListener(v -> {
            try { JSONArray payload = draft.payload(); dialog.dismiss(); send(payload); }
            catch (JSONException failure) { error(failure,status); }
        });
        render.run();
        dialog.show();
    }

    private void pendingDialog() {
        var dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("Acompanhamento")
                .setNegativeButton("Voltar",null);
        if (result == null) {
            dialog.setMessage("O Backend ainda não registrou a resposta.")
                    .setPositiveButton("Reenviar",(d,w) -> send(null));
        } else if ("PENDENTE".equals(result.optString("status"))) {
            dialog.setMessage("O Backend recebeu o pedido. Aguardando a produção.");
        } else {
            StringBuilder detail = new StringBuilder(state(result.optString("status")));
            if (!result.optString("error").isEmpty()) detail.append("\n").append(result.optString("error"));
            JSONArray stations = result.optJSONArray("stations");
            for (int i = 0; stations != null && i < stations.length(); i++) {
                JSONObject row = stations.optJSONObject(i);
                detail.append("\n").append(row.optString("station")).append(" · ").append(row.optString("status"));
                if (!row.optString("error").isEmpty()) detail.append(" · ").append(row.optString("error"));
            }
            dialog.setMessage(detail).setPositiveButton("Concluir",(d,w) -> {
                SessaoUsuario.get().clear(tableId());
                pending = null;
                result = null;
                draft.clear();
                renderRows();
                buttonState();
            });
        }
        dialog.show();
    }

    private void send(JSONArray payload) {
        var session = SessaoUsuario.get();
        if (session == null || sending) return;
        sending = true;
        review.setEnabled(false);
        status.setText("Enviando ao broker…");
        long table = tableId();
        View view = getView();
        SessaoUsuario.run(() -> { session.send(table,payload); return true; }, ok -> {
            sending = false;
            if (getView() == view) { status.setText("Aguardando o Backend…"); refresh(); }
        },failure -> {
            sending = false;
            if (getView() == view) { error(failure,status); refresh(); }
        });
    }

    private static void loadImage(ImageView target,String address) {
        IMAGES.execute(() -> {
            try {
                var url = new java.net.URL(address).openConnection();
                url.setConnectTimeout(2_500);
                url.setReadTimeout(3_000);
                if (url instanceof java.net.HttpURLConnection) ((java.net.HttpURLConnection)url).setInstanceFollowRedirects(false);
                try (var source = url.getInputStream()) {
                    byte[] image = new byte[512_001];
                    int offset = 0, read;
                    while (offset < image.length && (read = source.read(image,offset,image.length-offset)) != -1) offset += read;
                    if (offset == image.length) return;
                    var bounds = new android.graphics.BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    android.graphics.BitmapFactory.decodeByteArray(image,0,offset,bounds);
                    if (bounds.outWidth < 1 || bounds.outHeight < 1 || bounds.outWidth > 4096 || bounds.outHeight > 4096) return;
                    var options = new android.graphics.BitmapFactory.Options();
                    options.inSampleSize = Math.max(1,Math.max(bounds.outWidth,bounds.outHeight)/160);
                    var bitmap = android.graphics.BitmapFactory.decodeByteArray(image,0,offset,options);
                    if (bitmap != null) target.post(() -> target.setImageBitmap(bitmap));
                }
            } catch (java.io.IOException ignored) {}
        });
    }
}
