package com.atis.fisk;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.SystemClock;
import android.os.Vibrator;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Arrays;
import java.util.Random;


public class FishingActivity extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = "FishingActivity";

    private static int CAST_MODE_LOADING = -1;
    private static int CAST_MODE_IDLE = 0;
    private static int CAST_MODE_PRIMED = 1;
    private static int CAST_MODE_CASTING = 2;
    private static int CAST_MODE_AIRBORNE = 3;
    private static int CAST_MODE_IN_WATER = 4;

    private static int REED_MODE_BLOCKED = -1;
    private static int REED_MODE_IDLE = 0;
    private static int REED_MODE_STARTING = 1;
    private static int REED_MODE_REELING = 2;

    // Sound
    private SoundPool soundPool;

    // Mode
    boolean debug = false;

    // Intent
    private Intent bgSoundintent;

    // sensor stuff
    private SensorManager sensorManager;
    private Sensor linearAccelerationSensor;
    private Sensor rotationVectorSensor;

    // Vibrator
    private Vibrator vibrator;

    // Views
    private ConstraintLayout debugLayout;
    private TextView xzyDebug;
    private TextView totalDebug;
    private ImageView backgroundView;
    private TextView lineLengthView;
    private TextView rotationView;
    private TextView castModeView;
    private AnimationDrawable wavesAnimation;

    // Button
    private Button reelButton;

    // final MediaPlayer mp = MediaPlayer.create(this, R.raw.);
    private AudioManager am;


    // Other
    private Random rd;

    // Sensor Arrays
    private double[] linear_acceleration;
    private double[] top_accelerations_split;
    private double[] top_accelerations_total;
    private double[] top_accelerations_cast;
    private double[] rotations;

    // Cast values
    private int castMode = CAST_MODE_LOADING;
    private int reedMode = REED_MODE_BLOCKED;
    private double castPrimeThresholdAngle = Math.toRadians(45);
    private double castPrimeThresholdAngleBuffer = Math.toRadians(10);
    private double lineLength = 0;

    // Sounds
    int sound_swosh, sound_click, sound_splash_small, sound_splash_big, sound_splash_droplet,
    sound_prime, sound_idle, sound_reel;
    int reelStreamId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fishing);

        debugLayout = (ConstraintLayout) findViewById(R.id.debug_values);

        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        /* Setup sensor */
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        // Loud sounds (TODO: what about onResume?)
        soundPool = createSoundPool();

        sound_swosh = soundPool.load(this, R.raw.megaswosh1, 1);
        sound_click = soundPool.load(this, R.raw.click, 1);
        sound_splash_small = soundPool.load(this, R.raw.splash1, 1);
        sound_splash_big = soundPool.load(this, R.raw.splash2, 1);
        sound_splash_droplet = soundPool.load(this, R.raw.droplet, 1);
        sound_prime = soundPool.load(this, R.raw.cursor_select, 1);
        sound_idle = soundPool.load(this, R.raw.cursor_back, 1);
        sound_reel = soundPool.load(this, R.raw.reel, 1);

        // Wait for sensor to calibrate or something (TODO: Check if needed)
        SystemClock.sleep(100);

        xzyDebug = (TextView) findViewById(R.id.acc_values_xyz);
        totalDebug = (TextView) findViewById(R.id.acc_values_total);
        rotationView = (TextView) findViewById(R.id.rotation_value);
        castModeView = (TextView) findViewById(R.id.cast_mode);

        reelButton = findViewById(R.id.btn_reel);
        int reelStreamId;
        // https://stackoverflow.com/questions/47107105/android-button-has-setontouchlistener-called-on-it-but-does-not-override-perform
        reelButton.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN){
                    setReedMode(REED_MODE_STARTING);
                }
                if(event.getAction() == MotionEvent.ACTION_UP){
                    setReedMode(REED_MODE_IDLE);
                }
                return true;
            }

        });

        rd = new Random();

        /* Declare arrays */
        linear_acceleration = new double[4];
        top_accelerations_split = new double[4];
        top_accelerations_total = new double[4];
        top_accelerations_cast = new double[4];
        rotations = new double[3];

        backgroundView = (ImageView) findViewById(R.id.start_waves);
        lineLengthView = (TextView) findViewById(R.id.line_length);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // Prepare animation
        backgroundView.setBackgroundResource(R.drawable.waves);
        wavesAnimation = (AnimationDrawable) backgroundView.getBackground();

        // Remove placeholder background
        backgroundView.setImageDrawable(null);

        // Intent?
        bgSoundintent = new Intent(this, BackgroundSoundService.class);
        startService(bgSoundintent);

        // Start animation
        wavesAnimation.start();

        // Making sure
        setCastMode(CAST_MODE_IDLE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // for the system's orientation sensor registered listeners
        sensorManager.registerListener(this, linearAccelerationSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_NORMAL);

        // resume
        soundPool.autoResume();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // to stop the listener and save battery
        sensorManager.unregisterListener(this);

        // stop animation
        wavesAnimation.stop();

        // stop other
        stopService(bgSoundintent);
        soundPool.autoPause();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long time = SystemClock.elapsedRealtime();
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

        if (castMode == CAST_MODE_IDLE && rotations[2] > castPrimeThresholdAngle + castPrimeThresholdAngleBuffer / 2) {
            setCastMode(CAST_MODE_PRIMED);
            soundPool.play(sound_prime, 1, 1, 0, 0, 1);
            vibrator.vibrate(100);
        }

        if (castMode == CAST_MODE_PRIMED) {

            if (rotations[2] < castPrimeThresholdAngle - castPrimeThresholdAngleBuffer / 2) {

                if (linear_acceleration[3] > 30 + 10) {
                    Arrays.fill(top_accelerations_cast, 0);
                    setCastMode(CAST_MODE_CASTING);
                    soundPool.play(sound_swosh, 1, 1, 0, 0, 1);
                } else {
                    setCastMode(CAST_MODE_IDLE);
                    soundPool.play(sound_idle, 1, 1, 0, 0, 1);
                    vibrator.vibrate(100);
                }

            }


        }

        if (castMode == CAST_MODE_CASTING) {

            if(linear_acceleration[3] < 30) {
                vibrator.vibrate(1000);
                Log.w(TAG, "Cast: " + Arrays.toString(top_accelerations_cast));

                new Thread(new Runnable() {
                    public void run() {
                        // a potentially time consuming task
                        int sound = soundPool.play(sound_click, 1, 1, 0, -1, 1.5f);
                        while (lineLength < (top_accelerations_cast[3] / 3)) { // TODO: This is prone to glitches, like counter reeling
                            lineLength += 0.1;
                            SystemClock.sleep(10);
                        }
                        soundPool.stop(sound);
                    }
                }).start();

                setCastMode(CAST_MODE_AIRBORNE);
            }
        }

        if(castMode == CAST_MODE_AIRBORNE) {

            if(lineLength > (top_accelerations_cast[3] / 3)) {
                soundPool.play(sound_splash_small, 1, 1, 0, 0, 1);
                setCastMode(CAST_MODE_IN_WATER);
            }
        }

        if(castMode == CAST_MODE_IN_WATER) {
            if(lineLength <= 0) {
                lineLength = 0;
                setCastMode(CAST_MODE_IDLE);
                soundPool.play(sound_splash_big, 1, 1, 0, 0, 1);
            }

            // TODO: Fish stuff?
        }

        if (reedMode == REED_MODE_STARTING && lineLength > 0) {

            setReedMode(REED_MODE_REELING);

            new Thread(new Runnable() {
                public void run() {
                    // a potentially time consuming task
                    int sound = soundPool.play(sound_reel, 1, 1, 0, 0, 1);
                    while (lineLength > 0 && reedMode == REED_MODE_REELING) {

                        lineLength -= 0.1;
                        SystemClock.sleep(10);
                    }
                    soundPool.stop(sound);
                }
            }).start();
        }

        // Update line length
        lineLengthView.setText(getString(R.string.line_length, lineLength));
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
                        Math.toDegrees(rotations[2])
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
        if(Math.abs(linear_acceleration[3]) > Math.abs(top_accelerations_cast[3])) {
            System.arraycopy(linear_acceleration, 0, top_accelerations_cast, 0, 4);
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
        }
    }

    public void toggleDebug(View view) {
        debug = !debug;

        if(debug) {
            // set VISIBLE
            debugLayout.setVisibility(View.VISIBLE);
            // set GONE
            // lineLengthView.setVisibility(View.GONE);
            backgroundView.setVisibility(View.GONE);

            stopService(bgSoundintent);
        } else {
            // set GONE
            debugLayout.setVisibility(View.GONE);
            // set VISIBLE
            // lineLengthView.setVisibility(View.VISIBLE);
            backgroundView.setVisibility(View.VISIBLE);

            startService(bgSoundintent);
        }
    }

    public void setCastMode(int castMode) {
        Log.w(TAG, "CAST_MODE: " + this.castMode + " -> " + castMode);
        this.castMode = castMode;
        castModeView.setText(getString(R.string.cast_mode, this.castMode));
    }

    public void setReedMode(int reedMode) {
        Log.w(TAG, "REEL_MODE: " + this.reedMode + " -> " + reedMode);
        this.reedMode = reedMode;
        if(reedMode == REED_MODE_STARTING) {
            reelStreamId = soundPool.play(sound_reel, 1, 1, 0, -1, 1);
        } else {
            soundPool.stop(reelStreamId);
        }
    }

    // Stafford Williams https://stackoverflow.com/a/27552576
    protected SoundPool createSoundPool() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return createNewSoundPool();
        } else {
            return createOldSoundPool();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    protected SoundPool createNewSoundPool(){
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        return new SoundPool.Builder()
                .setAudioAttributes(attributes)
                .setMaxStreams(5)
                .build();
    }

    @SuppressWarnings("deprecation")
    protected SoundPool createOldSoundPool(){
        return new SoundPool(5, AudioManager.STREAM_MUSIC, 0);
    }
}
