package com.atis.fisk;

import android.util.Log;

import java.util.Random;

public class Fishes {

    public static FishEntry[] fishEntries = createFishArray();

    public static int spawnTime() {
        Random rd = new Random();
        int seconds = (15 + rd.nextInt(15)); // 15-30 second wait

        return seconds;
    }

    public static FishEntry[] getFishEntries() {
        return fishEntries;
    }

    public static int nTotal() {
        return fishEntries.length;
    }

    public static int nCaught() {
        int count = 0;
        for(FishEntry f: fishEntries) {
            if(f.getCount() > 0) {
                count++;
            }
        }
        return count;
    }

    public static FishEntry[] createFishArray(){
        //create empty fishArray
        FishEntry[] fishEntryArray = {
        new FishEntry("Pink Fish", R.drawable.pink_fish, 1),
        new FishEntry("Blue Fish", R.drawable.blue_fish, 1),
        new FishEntry("Yellow Fish", R.drawable.yellow_fish, 1),
        new FishEntry("Flounder", R.drawable.flounder, 3),
        new FishEntry("Crab", R.drawable.crab, 3),
        new FishEntry("Jellyfish", R.drawable.jellyfish, 3),
        new FishEntry("Seahorse", R.drawable.seahorse, 3),
        new FishEntry("Tin Can", R.drawable.tin_can, 5)
        };

        return fishEntryArray;
    }

    public static FishEntry catchFish(double lineLength) {
        Random rd = new Random();

        int totalWeight = 0;
        for (FishEntry f : Fishes.fishEntries) {
            totalWeight += f.getWeight();
        }

        int randomWeight = rd.nextInt(totalWeight);

        for (FishEntry f : Fishes.fishEntries) {
            int fishWeight = f.getWeight();
            if (randomWeight > fishWeight) {
                randomWeight -= fishWeight;
            } else {
                f.increaseCount();
                return f;
            }
        }
        return null;
    }
}
