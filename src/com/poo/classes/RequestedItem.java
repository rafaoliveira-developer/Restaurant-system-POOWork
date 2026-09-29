package com.poo.classes;

public class ResquestedItem {

    private Product product;
    private int quantity;

    public  ResquestedItem(Product product, int quantity){
        this.product = product;
        this.quantity = quantity;
    }

    public double calculateTotal(){
        return product.getPrice() * quantity;
    }




}
