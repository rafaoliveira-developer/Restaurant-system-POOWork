package com.poo.classes;

import java.util.ArrayList;

public class KitchenOrder {

    private BarTab tab;
    private ArrayList<RequestedItem> items;
    private KitchenOrderStatus status;

    public KitchenOrder(BarTab tab) {
        this.tab = tab;
        this.items = new ArrayList<>();
    }
}