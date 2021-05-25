package com.atis.fisk;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    public static Boolean tutorialEnabled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void clickedStart (View view){
        Intent intent = new Intent (this , FishingActivity.class);
        startActivity(intent);

    }
    public void clickedViewCatch (View view){
        Intent intent = new Intent (this , ViewCatchActivity.class);
        startActivity(intent);

    }
}
