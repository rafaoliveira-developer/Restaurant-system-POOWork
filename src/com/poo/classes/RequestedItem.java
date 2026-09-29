package com.poo.classes;

public class RequestedItem {

    private Product product;
    private int quantity;

    public RequestedItem(Product product, int quantity){
        this.product = product;
        this.quantity = quantity;
    }

    public double calculateTotal(){
        return product.getPrice() * quantity;
    }




}
