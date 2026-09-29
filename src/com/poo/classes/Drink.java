package com.poo.classes;

public class Drink extends Product {

    private DrinkCategory category;

    public Drink(int id, String name, String description, double price, DrinkCategory category) {
        super(id, name, description, price);
        this.category = category;

    }
}
