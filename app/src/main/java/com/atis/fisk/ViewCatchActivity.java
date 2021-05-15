package com.atis.fisk;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;

public class ViewCatchActivity extends AppCompatActivity {

    FiskRecyclerViewAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_catch);

        // set up the RecyclerView
        RecyclerView recyclerView = findViewById(R.id.caught_fish_view);
        recyclerView.setNestedScrollingEnabled(false);
        int numberOfColumns = 2;

        // Disable vertical scroll
        GridLayoutManager manager = new GridLayoutManager(this, numberOfColumns); /* {
            @Override
            public boolean canScrollVertically() {
                //Similarly you can customize "canScrollHorizontally()" for managing horizontal scroll
                return false;
            }
        }; */

        recyclerView.setLayoutManager(manager);
        adapter = new FiskRecyclerViewAdapter(this, Fishes.getFishEntries());
        // adapter.setClickListener(this);
        recyclerView.setAdapter(adapter);
    }
}