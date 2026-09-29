package com.poo.classes;

public class Food extends Product{

    private FoodCategory category;
    private int numberOfPeopleServes;

    public Food(int id, String name, String description, double price,
                FoodCategory category, int numberOfPeopleServes ) {
        super(id, name, description, price);
        this.category = category;
        this.numberOfPeopleServes = numberOfPeopleServes;
    }




}
