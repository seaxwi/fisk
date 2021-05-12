package com.atis.fisk;

import android.util.Log;

import java.util.Random;

public class Fishes {

    public static int spawnTime() {
        Random rd = new Random();
        int seconds = (15 + rd.nextInt(15)); // 15-30 second wait

        return seconds;
    }

    public static FishEntry[] createFishArray(){
        //create empty fishArray
        FishEntry[] fishEntryArray = new FishEntry[9];

        // Create fish objects
        int i = 0;
        fishEntryArray[i++] = new FishEntry("???", R.drawable.unknown_fish, 0);
        fishEntryArray[i++] = new FishEntry("Pink Fish", R.drawable.pink_fish, 1);
        fishEntryArray[i++] = new FishEntry("Blue Fish", R.drawable.blue_fish, 1);
        fishEntryArray[i++] = new FishEntry("Yellow Fish", R.drawable.yellow_fish, 1);
        fishEntryArray[i++] = new FishEntry("Flounder", R.drawable.flounder, 3);
        fishEntryArray[i++] = new FishEntry("Crab", R.drawable.crab, 3);
        fishEntryArray[i++] = new FishEntry("Jellyfish", R.drawable.jellyfish, 3);
        fishEntryArray[i++] = new FishEntry("Seahorse", R.drawable.seahorse, 3);
        fishEntryArray[i++] = new FishEntry("Tin Can", R.drawable.tin_can, 5);

        return fishEntryArray;
    }

    public static FishEntry determineCaughtFish(FishEntry[] fishEntryArray){
        Random rd = new Random();

        int totalWeight = 0;
        for(FishEntry f: fishEntryArray) {
            totalWeight += f.getWeight();
        }

        int randomWeight = rd.nextInt(totalWeight);

        for(FishEntry f: fishEntryArray) {
            int fishWeight = f.getWeight();
            if(randomWeight > fishWeight) {
                randomWeight -= fishWeight;
            } else {
                return f;
            }
        }

        return fishEntryArray[7];
    }
}
