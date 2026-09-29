import com.poo.classes.*;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);


        Restaurant restaurant = new Restaurant();

        Table table1 = new Table(01);
        Waiter waiter = new Waiter(02, "Gabriel");
        Table table2 = new Table(02);
        Waiter waiter2 = new Waiter(02, "Rafael");

        Food hamburguer = new Food(01,"hamburguer", "hamburguer", 30, FoodCategory.HAMBURGUER, 01);
        Drink water= new Drink(01, "Water", "Only Water", 5, DrinkCategory.WATER);



        restaurant.getMenu().addProduct(hamburguer);
        restaurant.getMenu().addProduct(water);










        int option;

        do {

            System.out.println("""
                    RESTAURANTE UFG
                    
                    1- Abrir Comanda
                    2- Ver Menu
                    3- Pedir Produto
                    4- Ver Comanda
                    5- Fechar Comanda
                    0- Sair
                    """);

            option = scanner.nextInt();

            switch (option) {

                case 0:
                    System.out.println("Encerrando...");
                    break;

                case 1:
                    BarTab tab = restaurant.openTab(table1,waiter);
                    System.out.println("Comanda #" + tab.getId() + " aberta!");
                    break;



                case 2:
                    restaurant.getMenu().showProducts();

                case 3:
                    restaurant.getMenu().showProducts();
                    System.out.println("Qual produto você gostaria de pedir? ");
                    int productChoice = scanner.nextInt();
                    System.out.println("Qual a quantidade? ");
                    int quantity = scanner.nextInt();
                    Product productSearch = restaurant.getMenu().searchProduct(productChoice);
                    RequestedItem requestedItem = new RequestedItem(productSearch,quantity);
                    System.out.println("Qual a sua comanda?");
                    restaurant.searchBarTab(scanner.nextInt());




            }

        }while(option!=0);

        scanner.close();


















    }
}