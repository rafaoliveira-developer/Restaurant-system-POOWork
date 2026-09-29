package com.poo.classes;

public class Table {

    private int number;
    private TableStatus status;

    public Table(int number){
        this.number = number;
        this.status = TableStatus.FREE;
    }

    public TableStatus getStatus(){
        return status;
    }

    public void setStatus(TableStatus status){
        this.status = status;
    }










}
