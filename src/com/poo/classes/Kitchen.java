package com.poo.classes;

import java.util.ArrayList;

public class Kitchen {

    private ArrayList<KitchenOrder> orders;
    private KitchenOrderStatus kitchenOrderStatus;

    public Kitchen(){
        this.orders = new ArrayList<>();
    }

    public void addOrder(KitchenOrder order) {
        orders.add(order);
    }

    public void removeOrder(KitchenOrder order) {
        orders.remove(order);
    }

}
