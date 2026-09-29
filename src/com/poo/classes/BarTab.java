package com.poo.classes;

import java.util.ArrayList;

public class BarTab {

    private static int nextId = 1;

    private int id;
    private Table table;
    private Waiter waiter;
    private ArrayList<ResquestedItem> items;
    private TabStatus status;

    public BarTab(Table table, Waiter waiter) {
        this.id = nextId++;
        this.table = table;
        this.waiter = waiter;
        this.items = new ArrayList<>();
        this.status = TabStatus.OPEN;
    }

    public void addItem(ResquestedItem item){
        items.add(item);
    }

    public double calculateTotal(){
        double total = 0;

        for(ResquestedItem item: items){
            total += item.calculateTotal();
        }
        return total;
    }

    public TabStatus getStatus() {
        return status;
    }

    public void close() {
        this.status = TabStatus.FINISH;
    }

    public Table getTable() {
        return table;
    }

    public int getId(){
        return id;
    }
}
