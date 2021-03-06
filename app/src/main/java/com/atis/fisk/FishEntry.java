package com.atis.fisk;

import java.util.Random;

public class FishEntry {
    private String name;
    private int resourceID;
    private int weight;
    private int count;

    public FishEntry(String name, int resourceID, int weight){
        this.name = name;
        this.resourceID = resourceID;
        this.weight = weight;
    }

    public int getResourceID(){
        return resourceID;
    }

    public int getWeight(){
        return weight;
    }

    public String getName(){
        return name;
    }

    public void increaseCount() {
        count++;
    }

    public int getCount() {
        return count;
    }

}