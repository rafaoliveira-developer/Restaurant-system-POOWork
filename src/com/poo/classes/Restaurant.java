package com.poo.classes;

import java.util.ArrayList;

public class Restaurant {

    private ArrayList<Table> tables;
    private ArrayList<Waiter> waiters;
    private ArrayList<BarTab> tabs;
    private Menu menu;

    public Restaurant() {
        this.tables = new ArrayList<>();
        this.waiters = new ArrayList<>();
        this.tabs = new ArrayList<>();
        this.menu = new Menu();
    }

    public Menu getMenu(){
        return menu;
    }

    public BarTab openTab(Table table, Waiter waiter) {

        if (table.getStatus() == TableStatus.OCCUPIED) {
            throw new IllegalStateException("Table is already occupied.");
        }

        BarTab tab = new BarTab(table, waiter);

        table.setStatus(TableStatus.OCCUPIED);

        tabs.add(tab);

        return tab;
    }

    public void closeTab(BarTab tab) {
        tab.close();
        tab.getTable().setStatus(TableStatus.FREE);
    }

    public BarTab searchBarTab(int id){
        for(BarTab barTab: tabs){
            if(barTab.getId()== id){
                return barTab;
            }
        }

        return null;
    }
}

