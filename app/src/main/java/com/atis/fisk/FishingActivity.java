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
import android.util.Log;
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
    private float[] gravity;
    private float[] linear_acceleration;
    private double[] rotation_vector;
    private double[] top_rotation_vector;

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
        gravity = new float[3];
        linear_acceleration = new float[3];
        rotation_vector = new double[3];
        top_rotation_vector = new double[3];

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
            // Update values
            try {
                updateAccelerationValues(event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            updateRotationValues(event);
            if(Math.abs(rotation_vector[0]) > Math.abs(top_rotation_vector[0])) {
                top_rotation_vector[0] = rotation_vector[0];
            }
            if(Math.abs(rotation_vector[1]) > Math.abs(top_rotation_vector[1])) {
                top_rotation_vector[1] = rotation_vector[1];
            }
            if(Math.abs(rotation_vector[2]) > Math.abs(top_rotation_vector[2])) {
                top_rotation_vector[2] = rotation_vector[2];
            }
            rotationView.setText(
                    getString(R.string.rotations,
                            Math.toDegrees(rotation_vector[0]),
                            Math.toDegrees(rotation_vector[1]),
                            Math.toDegrees(rotation_vector[2]),
                            Math.toDegrees(top_rotation_vector[0]),
                            Math.toDegrees(top_rotation_vector[1]),
                            Math.toDegrees(top_rotation_vector[2])
                    )
            );
        }

        boolean change = false;

        if(Math.abs(totAcc) > Math.abs(highestTotAcc)) {
            highestTotAcc = totAcc;
            change = true;
            totalDebug.setText(
                    getString(R.string.acceleration_total, highestTotAcc, linear_acceleration[0],
                            linear_acceleration[1], linear_acceleration[2])
            );
        }
        if(Math.abs(linear_acceleration[0]) > Math.abs(highestXAcc)) {
            highestXAcc = linear_acceleration[0];
            change = true;
        }
        if(Math.abs(linear_acceleration[1]) > Math.abs(highestYAcc)) {
            highestYAcc = linear_acceleration[1];
            change = true;
        }
        if(Math.abs(linear_acceleration[2]) > Math.abs(highestZAcc)) {
            highestZAcc = linear_acceleration[2];
            change = true;
        }

        if(change){
            // Update acc textViews
            xzyDebug.setText(
                    getString(R.string.acceleration_xyz, highestXAcc,
                            highestYAcc, highestZAcc)
            );

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

    private void updateAccelerationValues(SensorEvent event) throws Exception {

        int sensorType = linearAccelerationSensor.getType();

        if(sensorType == Sensor.TYPE_ACCELEROMETER) {
            // https://developer.android.com/guide/topics/sensors/sensors_motion#sensors-motion-accel

            // In this example, alpha is calculated as t / (t + dT),
            // where t is the low-pass filter's time-constant and
            // dT is the event delivery rate.

            float alpha = 0.8f;

            // Isolate the force of gravity with the low-pass filter.
            gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
            gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
            gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

            // Remove the gravity contribution with the high-pass filter.
            linear_acceleration[0] = event.values[0] - gravity[0];
            linear_acceleration[1] = event.values[1] - gravity[1];
            linear_acceleration[2] = event.values[2] - gravity[2];
        } else if(sensorType == Sensor.TYPE_LINEAR_ACCELERATION) {
            linear_acceleration[0] = event.values[0];
            linear_acceleration[1] = event.values[1];
            linear_acceleration[2] = event.values[2];

            float aX = event.values[0];
            float aY = event.values[1];
            //aZ = event.values[2];
            angle = Math.atan2(aX, aY)/(Math.PI/180);
        } else {
            throw new Exception("Error: Wrong sensor type");
        }

        totAcc = Math.sqrt(linear_acceleration[0] * linear_acceleration[0]
                + linear_acceleration[1] * linear_acceleration[1]
                + linear_acceleration[2] * linear_acceleration[2]
        );
    }

    private void updateRotationValues(SensorEvent event) {
        // https://www.euclideanspace.com/maths/geometry/rotations/conversions/quaternionToEuler/indexLocal.htm

        float qx = event.values[0];
        float qy = event.values[1];
        float qz = event.values[2];
        float qw = event.values[3];

        double sqw = qw*qw;
        double sqx = qx*qx;
        double sqy = qy*qy;
        double sqz = qz*qz;

        rotation_vector[0] = Math.atan2(2.0 * (qx*qy + qz*qw),(sqx - sqy - sqz + sqw));
        rotation_vector[1] = Math.atan2(2.0 * (qy*qz + qx*qw),(-sqx - sqy + sqz + sqw));
        rotation_vector[2] = Math.asin(-2.0 * (qx*qz - qy*qw));
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
        highestXAcc = 0;
        highestYAcc = 0;
        highestZAcc = 0;
        highestTotAcc = 0;
        highestXRot = 0;
        top_rotation_vector[0] = 0;
        top_rotation_vector[1] = 0;
        top_rotation_vector[2] = 0;
    }

    public void toggleDebug(View view) {
        debug = !debug;

        if(debug) {
            debugLayout.setVisibility(View.VISIBLE);

            fishResultView.setVisibility(View.GONE);
            catchCountView.setVisibility(View.GONE);
            backgroundView.setVisibility(View.GONE);
        } else {
            debugLayout.setVisibility(View.GONE);

            fishResultView.setVisibility(View.VISIBLE);
            catchCountView.setVisibility(View.VISIBLE);
            backgroundView.setVisibility(View.VISIBLE);
        }
    }
}
