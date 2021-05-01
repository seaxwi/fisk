package com.atis.fisk;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.os.Vibrator;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Random;


public class FishingActivity extends AppCompatActivity implements SensorEventListener {

    // Mode
    boolean debug = false;

    // Intent
    private Intent intent;

    // sensor stuff
    private SensorManager sensorManager;
    private Sensor linearAccelerationSensor;
    private Sensor rotationVectorSensor;

    private Vibrator vibrator;

    // Views
    private ConstraintLayout debugLayout;
    private TextView xzyDebug;
    private TextView totalDebug;
    private ImageView backgroundView;
    private TextView fishResultView;
    private TextView catchCountView;
    private TextView rotationView;
    private AnimationDrawable wavesAnimation;

    // Other
    private Random rd;

    // Sensor Arrays
    private double[] linear_acceleration;
    private double[] top_accelerations_split;
    private double[] top_accelerations_total;
    private double[] rotations;
    private double[] top_rotations;

    // Values
    private int nCaught = 0;
    boolean vBlock = false;
    long lastVibration = 0;
    double totAcc = 0;
    double highestTotAcc = 0;
    float highestXAcc = 0;
    float highestYAcc = 0;
    float highestZAcc = 0;
    float highestXRot = 0;
    double angle = 0;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fishing);

        debugLayout = (ConstraintLayout) findViewById(R.id.debug_values);

        /* Setup sensor */
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);;
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        // Wait for sensor to calibrate or something
        SystemClock.sleep(100);

        xzyDebug = (TextView) findViewById(R.id.acc_values_xyz);
        totalDebug = (TextView) findViewById(R.id.acc_values_total);
        rotationView = (TextView) findViewById(R.id.rotation_value);
        rd = new Random();

        /* Declare arrays */
        linear_acceleration = new double[4];
        top_accelerations_split = new double[4];
        top_accelerations_total = new double[4];
        rotations = new double[3];
        top_rotations = new double[3];

        backgroundView = (ImageView) findViewById(R.id.start_waves);
        fishResultView = (TextView) findViewById(R.id.fish_result);
        catchCountView= (TextView) findViewById(R.id.catch_count);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // Prepare animation
        backgroundView.setBackgroundResource(R.drawable.waves);
        wavesAnimation = (AnimationDrawable) backgroundView.getBackground();

        // Remove placeholder background
        backgroundView.setImageDrawable(null);

        // Intent?
        intent = new Intent(this, BackgroundSoundService.class);
        startService(intent);

        // Start animation
        wavesAnimation.start();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // for the system's orientation sensor registered listeners
        sensorManager.registerListener(this, linearAccelerationSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // to stop the listener and save battery
        sensorManager.unregisterListener(this);

        // stop animation
        wavesAnimation.stop();

        // stop other
        stopService(intent);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            updateAccelerationValues(event);
            if(debug) {
                updateAccelerationViews();
            }

        } else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            updateRotationValues(event);
            if (debug) {
                updateRotationViews();
            }
        }

        // Debug: Vibrate unless there was a recent vibration
        long currTime = System.currentTimeMillis();
        if(debug && currTime > lastVibration + 1000) {
            lastVibration = currTime;
            // sensorText.setText("cool!");

            lastVibration = currTime;
            //deprecated in API 26 (!)
            // vibrator.vibrate(500);
        }
    }

    private void updateAccelerationViews() {
        // Update xzy textViews

        xzyDebug.setText(
                getString(R.string.acceleration_xyz,
                        top_accelerations_split[0],
                        top_accelerations_split[1],
                        top_accelerations_split[2])
        );

        // update total textViews
        totalDebug.setText(
                getString(R.string.acceleration_total,
                        top_accelerations_total[3],
                        top_accelerations_total[0],
                        top_accelerations_total[1],
                        top_accelerations_total[2])
        );
    }

    private void updateRotationViews() {
        rotationView.setText(
                getString(R.string.rotations,
                        Math.toDegrees(rotations[0]),
                        Math.toDegrees(rotations[1]),
                        Math.toDegrees(rotations[2]),
                        Math.toDegrees(top_rotations[0]),
                        Math.toDegrees(top_rotations[1]),
                        Math.toDegrees(top_rotations[2])
                )
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

    private void updateAccelerationValues(SensorEvent event) {
        linear_acceleration[0] = event.values[0];
        linear_acceleration[1] = event.values[1];
        linear_acceleration[2] = event.values[2];
        // Getting total magnitude sqrt(x^2 + y^2 + z^2)
        linear_acceleration[3] = Math.sqrt(Math.pow(linear_acceleration[0], 2)
                + Math.pow(linear_acceleration[1], 2)
                + Math.pow(linear_acceleration[2], 2)
        );

        // checking x, y, z
        for(int i = 0; i < 3; i++) {
            if(Math.abs(linear_acceleration[i]) > Math.abs(top_accelerations_split[i])) {
                top_accelerations_split[i] = linear_acceleration[i];
            }
        }

        // checking |t|
        if(Math.abs(linear_acceleration[3]) > Math.abs(top_accelerations_split[3])) {
            System.arraycopy(linear_acceleration, 0, top_accelerations_total, 0, 4);
            top_accelerations_split[3] = linear_acceleration[3];
        }

    }

    private void updateRotationValues(SensorEvent event) {
        // https://www.euclideanspace.com/maths/geometry/rotations/conversions/quaternionToEuler/indexLocal.htm

        // Converting quaternions to euler
        float qx = event.values[0];
        float qy = event.values[1];
        float qz = event.values[2];
        float qw = event.values[3];

        // square
        double sqw = qw*qw;
        double sqx = qx*qx;
        double sqy = qy*qy;
        double sqz = qz*qz;

        // solve for x, y , z
        rotations[0] = Math.atan2(2.0 * (qx*qy + qz*qw),(sqx - sqy - sqz + sqw));
        rotations[1] = Math.atan2(2.0 * (qy*qz + qx*qw),(-sqx - sqy + sqz + sqw));
        rotations[2] = Math.asin(-2.0 * (qx*qz - qy*qw));

        // update top rotations
        for(int i = 0; i < 3; i++) {
            if(Math.abs(rotations[i]) > Math.abs(top_rotations[i])) {
                top_rotations[i] = rotations[i];
            }
        }
    }

    public void cast(View view){

        // Simple placeholder
        if(rd.nextBoolean()) {
            nCaught++;
            fishResultView.setText(R.string.fish_was_caught);
            catchCountView.setText(
                    getString(R.string.catch_count, nCaught)
            );

        } else {
            fishResultView.setText(R.string.fish_got_away);
        }
    }

    public void resetHighestAccs(View view) {
        // reset accelerations
        for(int i = 0; i<4; i++) {
            top_accelerations_split[i] = 0;
            top_accelerations_total[i] = 0;
        }

        // reset rotations
        for(int i = 0; i<3; i++) {
            rotations[i] = 0;
            top_rotations[i] = 0;
        }
    }

    public void toggleDebug(View view) {
        debug = !debug;

        if(debug) {
            // set VISIBLE
            debugLayout.setVisibility(View.VISIBLE);
            // set GONE
            fishResultView.setVisibility(View.GONE);
            catchCountView.setVisibility(View.GONE);
            backgroundView.setVisibility(View.GONE);
        } else {
            // set GONE
            debugLayout.setVisibility(View.GONE);
            // set VISIBLE
            fishResultView.setVisibility(View.VISIBLE);
            catchCountView.setVisibility(View.VISIBLE);
            backgroundView.setVisibility(View.VISIBLE);
        }
    }
}
