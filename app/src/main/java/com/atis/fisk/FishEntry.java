package com.atis.fisk;

public class FishEntry {
    private String name;
    private int resourceID;
    private int weight;

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
}
