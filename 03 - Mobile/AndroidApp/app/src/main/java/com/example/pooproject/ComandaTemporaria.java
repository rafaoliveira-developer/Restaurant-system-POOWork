package com.example.pooproject;

import org.json.*;
import java.math.BigDecimal;
import java.util.*;

public final class ComandaTemporaria {
    static final class Item {
        ProdutoMenu product;
        int quantity;
        String notes = "";
        Item(ProdutoMenu product) { this.product = product; }
        BigDecimal subtotal() { return product.preco.multiply(BigDecimal.valueOf(quantity)); }
    }
    private final Map<Long,Item> selected = new LinkedHashMap<>();

    void adjust(ProdutoMenu product,int difference) {
        Item item = selected.computeIfAbsent(product.id,key -> new Item(product));
        item.quantity = Math.max(0,Math.min(99,item.quantity + difference));
        if (item.quantity == 0) selected.remove(product.id);
    }

    void update(List<ProdutoMenu> catalog) {
        Map<Long,ProdutoMenu> latest = new HashMap<>();
        for (ProdutoMenu product : catalog) latest.put(product.id,product);
        selected.keySet().retainAll(latest.keySet());
        selected.forEach((id,item) -> item.product = latest.get(id));
    }

    int quantity(long product) {
        Item item = selected.get(product);
        return item == null ? 0 : item.quantity;
    }

    List<Item> items() { return new ArrayList<>(selected.values()); }

    BigDecimal total() {
        BigDecimal total = BigDecimal.ZERO;
        for (Item item : selected.values()) total = total.add(item.subtotal());
        return total;
    }

    JSONArray payload() throws JSONException {
        JSONArray result = new JSONArray();
        for (Item item : selected.values())
            result.put(new JSONObject().put("productId",item.product.id).put("quantity",item.quantity).put("notes",item.notes));
        return result;
    }

    void clear() { selected.clear(); }
}
