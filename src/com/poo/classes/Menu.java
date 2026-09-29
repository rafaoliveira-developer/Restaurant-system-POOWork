package com.poo.classes;

import java.util.ArrayList;

public class Menu {

    private ArrayList<Product> products;

    public Menu() {
        products = new ArrayList<>();
    }

    public void addProduct(Product product) {
        products.add(product);
    }

    public void deleteProduct(Product product) {
        products.remove(product);
    }

    public Product searchProduct(int id) {
        for (Product product : products) {
            if (product.getId() == id) {
                return product;
            }
        }
        return null;
    }

    public ArrayList<Product> getProducts() {
        return products;
    }

    public void showProducts() {
        for (Product product : products) {
            System.out.println(
                    product.getId() + " - " +
                            product.getName() + " - R$ " +
                            product.getPrice()
            );
        }
    }
}
