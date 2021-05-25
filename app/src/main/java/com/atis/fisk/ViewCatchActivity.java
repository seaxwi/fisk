package com.atis.fisk;


import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.util.Log;
import android.view.MenuItem;

public class ViewCatchActivity extends AppCompatActivity {

    FiskRecyclerViewAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_catch);

        ActionBar actionBarToolbar = (ActionBar) getSupportActionBar();
        if (actionBarToolbar != null) {
            actionBarToolbar.setTitle(getString(R.string.discover_progress, Fishes.nCaught(), Fishes.nTotal()));
            actionBarToolbar.setDisplayHomeAsUpEnabled(true);
        }

        // set up the RecyclerView
        RecyclerView recyclerView = findViewById(R.id.caught_fish_view);
        recyclerView.setNestedScrollingEnabled(false);
        int numberOfColumns = 2;

        // Disable vertical scroll
        GridLayoutManager manager = new GridLayoutManager(this, numberOfColumns);

        recyclerView.setLayoutManager(manager);
        adapter = new FiskRecyclerViewAdapter(this, Fishes.getFishEntries());
        // adapter.setClickListener(this);
        recyclerView.setAdapter(adapter);
    }
}