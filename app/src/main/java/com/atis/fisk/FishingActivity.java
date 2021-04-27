package com.atis.fisk;

import android.graphics.drawable.AnimationDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;


public class FishingActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager mSensorManager;
    private TextView sensorText;
    private ImageView backgroundView;
    private AnimationDrawable wavesAnimation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fishing);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorText = (TextView) findViewById(R.id.acceleration_values);
        backgroundView = (ImageView) findViewById(R.id.start_waves);

        // Prepare animation
        backgroundView.setBackgroundResource(R.drawable.waves);
        wavesAnimation = (AnimationDrawable) backgroundView.getBackground();

        // Remove placeholder background
        backgroundView.setImageDrawable(null);

        // Start animation
        wavesAnimation.start();
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
        wavesAnimation.stop();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        sensorText.setText(
                getString(R.string.acceleration_xyz, x, y, z)
        );
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        wavesAnimation.start();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // not in use
    }

}