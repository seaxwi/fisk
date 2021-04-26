package com.atis.fisk;

import android.graphics.drawable.AnimationDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Build;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;


public class fishing extends AppCompatActivity implements SensorEventListener {

    private SensorManager mSensorManager;
    private TextView sensorText;
    private ImageView start_waves;
    private AnimationDrawable waves;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fishing);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorText = (TextView) findViewById(R.id.sensortext);
        start_waves = (ImageView) findViewById(R.id.start_waves);
        start_waves.setBackgroundResource(R.drawable.waves);
        waves = (AnimationDrawable) start_waves.getBackground();
        waves.start();


    }

    @Override
    protected void onResume() {
        super.onResume();

        // for the system's orientation sensor registered listeners
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // to stop the listener and save battery
        mSensorManager.unregisterListener(this);
        waves.stop();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        String xStr = String.format("%.1f", x);
        String yStr = String.format("%.1f", y);
        String zStr = String.format("%.1f", z);

        sensorText.setText("x:y:z =" + xStr + ": " + yStr + ": " + zStr);
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        waves.start();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // not in use
    }

}