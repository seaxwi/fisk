package com.atis.fisk;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Handler;
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

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Random;


public class FishingActivity extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = "FishingActivity";

    private final double PARAMETER_PRIMING_ANGLE_UPPER = Math.toRadians(50);
    private final double PARAMETER_PRIMING_ANGLE_LOWER = Math.toRadians(40);
    private final float PARAMETER_CASTING_ACCELERATION_LIMIT = 30;

    private static final int CAST_MODE_THREAD = -1;
    private static final int CAST_MODE_IDLE = 0;
    private static final int CAST_MODE_PRIMED = 1;
    private static final int CAST_MODE_CASTING = 2;
    private static final int CAST_MODE_AIRBORNE = 3;
    private static final int CAST_MODE_FISHING = 4;

    private static final int FISHING_MODE_IDLE = -1;
    private static final int FISHING_MODE_SPLASH = 0;

    private static final int REEL_MODE_THREAD = -1;
    private static final int REEL_MODE_IDLE = 0;
    private static final int REEL_MODE_REELING = 1;

    /* Declare sensors and vibrator */
    private SensorManager sensorManager;
    private Sensor linearAccelerationSensor;
    private Sensor rotationVectorSensor;
    private Vibrator vibrator;

    /* Declare views */
    private ConstraintLayout debugViewLayout;
    private ConstraintLayout catchViewLayout;
    private ConstraintLayout castingInstructionsViewLayout;
    private ConstraintLayout fishingInstructionsViewLayout;
    private ConstraintLayout currentTutorialStep = null;
    private ImageView backgroundView;
    private ImageView instructionsView;
    private ImageView catchImage;
    private TextView catchName;
    private TextView xzyDebug;
    private TextView totalDebug;
    private TextView lineLengthView;
    private TextView rotationView;
    private TextView castModeView;
    private AnimationDrawable wavesAnimation;
    private AnimationDrawable instructionsAnimation;
    private Button reelButton;

    /* Sounds */
    SoundPool soundPool;
    int sound_swosh;
    int sound_click;
    int sound_splash_small;
    int sound_splash_big;
    int sound_prime;
    int sound_idle;
    int sound_reel;
    int reelStreamId;

    /* Declare sensor variables */
    private double[] linear_acceleration;
    private double[] top_accelerations_split; // debugging only
    private double[] top_accelerations_total; // debugging only
    private double[] top_accelerations_cast; // top casting acceleration
    private double rotX, rotY, rotZ;

    // Other
    private DecimalFormat df = new DecimalFormat("#.#"); // debug
    private Intent bgSoundintent;
    private Random rd;

    /* Game loop */
    private final int fps = 60;
    private final long delay = 1000 / fps;
    private final Handler handler = new Handler();
    Runnable game = new Runnable() {
        public void run() {
            tick();
            handler.postDelayed(this, delay);
        }
    };
    Thread wait;

    /* Game states and variables */
    private boolean debugViewOpen = false;
    private boolean catchViewOpen = false;
    private volatile int castMode = CAST_MODE_IDLE;
    private int reelMode = REEL_MODE_IDLE;
    private boolean reelEnabled = true;
    private volatile double lineLength = 0;
    private double castVelocity;
    private double targetLength;
    private FishEntry[] fishEntryArray;
    private Fish activeFish = null;
    private long activeFishTimer;

    /* Reel variables */
    private volatile float nextReelSpin = 0;
    private volatile float currentReelSpin;
    private int reelSoundId;

    @SuppressLint("ClickableViewAccessibility") // TODO: ?
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fishing);

        initializeViews();

        /* Get services and sensors */
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        /* Create SoundPool and load sounds */
        soundPool = Sounds.createSoundPool();
        sound_swosh = soundPool.load(this, R.raw.megaswosh1, 1);
        sound_click = soundPool.load(this, R.raw.click, 1);
        sound_splash_small = soundPool.load(this, R.raw.splash1, 1);
        sound_splash_big = soundPool.load(this, R.raw.splash2, 1);
        sound_prime = soundPool.load(this, R.raw.cursor_select, 1);
        sound_idle = soundPool.load(this, R.raw.cursor_back, 1);
        sound_reel = soundPool.load(this, R.raw.reel, 1);

        /* Create REEL IN button */
        reelButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if(reelEnabled) {

                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        setReelMode(REEL_MODE_REELING);
                    }
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        setReelMode(REEL_MODE_IDLE);
                    }

                }
                return true;
            }

        });

        /* Declare sensor arrays */
        linear_acceleration = new double[4];
        top_accelerations_split = new double[4];
        top_accelerations_total = new double[4];
        top_accelerations_cast = new double[4];

        /* Start background animation */
        backgroundView.setBackgroundResource(R.drawable.waves);
        wavesAnimation = (AnimationDrawable) backgroundView.getBackground();
        backgroundView.setImageDrawable(null); // Remove placeholder drawable
        wavesAnimation.start();


        /* Start tutorial */
        instructionsView.setBackgroundResource(R.drawable.instructions);
        instructionsAnimation = (AnimationDrawable) instructionsView.getBackground();
        instructionsView.setImageDrawable(null); // Remove placeholder drawable
        instructionsAnimation.start();
        nextTutorialStep(null);

        /* Start background audio */
        bgSoundintent = new Intent(this, BackgroundSoundService.class);
        startService(bgSoundintent);

        /* Other initialization */
        fishEntryArray = Fishes.createFishArray();
        rd = new Random();

        // Wait for sensor to calibrate or something (TODO: Check if needed)
        // SystemClock.sleep(100);
    }

    @Override
    protected void onResume() {
        super.onResume();

        /* Register listeners and resume audio */
        sensorManager.registerListener(this, linearAccelerationSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_NORMAL);
        soundPool.autoResume();

        /* Start game loop */
        handler.postDelayed(game, delay);
    }

    @Override
    protected void onPause() {
        super.onPause();

        sensorManager.unregisterListener(this);
        wavesAnimation.stop();
        instructionsAnimation.stop();
        soundPool.autoPause();
        stopService(bgSoundintent);

        /* Pause game loop */
        handler.removeCallbacks(game);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {

            updateAccelerationValues(event);

        } else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {

            updateRotationValues(event);

        }
    }

    // TODO: Do we need this?
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        wavesAnimation.start();
        instructionsAnimation.start();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // not in use
    }

    private void tick() {

        if(castMode == CAST_MODE_THREAD) {
            // Wait for castMode to be changed by thread
        }

        if (castMode == CAST_MODE_IDLE) {
            reelMode = REEL_MODE_IDLE;
            setReelEnabled(false);

            if (rotZ > PARAMETER_PRIMING_ANGLE_UPPER) {

                // Hide tutorial if visable
                Log.w(TAG, "HIDE ANIMATION");
                castingInstructionsViewLayout.setVisibility(View.GONE);

                // Set castMode to PRIMED
                setCastMode(CAST_MODE_PRIMED);
                soundPool.play(sound_prime, 1, 1, 0, 0, 1);
                vibrator.vibrate(100);

            }

        }

        if (castMode == CAST_MODE_PRIMED) {

            // Wait for rotation to pass lower angle
            if (rotZ < PARAMETER_PRIMING_ANGLE_LOWER) {


                if (linear_acceleration[3] > PARAMETER_CASTING_ACCELERATION_LIMIT) {

                    // Reset array
                    Arrays.fill(top_accelerations_cast, 0);

                    soundPool.play(sound_swosh, 1, 1, 0, 0, 1);

                    setCastMode(CAST_MODE_CASTING);

                } else {

                    Log.w(TAG, "The acceleration (" + df.format(linear_acceleration[3]) + " m/s) was below the limit (" + df.format(PARAMETER_CASTING_ACCELERATION_LIMIT) + " m/s)");

                    soundPool.play(sound_idle, 1, 1, 0, 0, 1);
                    vibrator.vibrate(100);

                    setCastMode(CAST_MODE_IDLE);

                }
            }
        }

        if (castMode == CAST_MODE_CASTING) {

            // Wait until accleration drops below threshold
            if(linear_acceleration[3] < PARAMETER_CASTING_ACCELERATION_LIMIT) {
                castVelocity = top_accelerations_cast[3]; // TODO: Better math
                targetLength = castVelocity / 3; // TODO: Better math
                vibrator.vibrate(1000);
                Log.w(TAG, "Succesful cast! Acceleration: " + df.format(top_accelerations_cast[3]) + "m/s");

                setReelEnabled(false);
                setCastMode(CAST_MODE_AIRBORNE);
            }
        }

        if (castMode == CAST_MODE_AIRBORNE) {

            if (lineLength < targetLength) { // TODO: Better math
                nextReelSpin += 1;
            } else {
                // Splash
                setReelEnabled(true);
                soundPool.play(sound_splash_small, 1, 1, 0, 0, 1);
                setCastMode(CAST_MODE_FISHING);
            }
        }

        // TODO: Sensors could be paused while waiting for fish
        if(castMode == CAST_MODE_FISHING) {

            if(activeFish == null) {
                activeFish = new Fish();
            } else if (activeFish.escaped()) {
                activeFish = null;
            } else {
                activeFish.tick(delay);
            }
        }

        // REEL STUFF

        if(reelMode == REEL_MODE_REELING) {
            nextReelSpin -= 0.75;
        }

        if (nextReelSpin != currentReelSpin) {

            currentReelSpin = nextReelSpin;

            Log.w(TAG, "Reel spin: " + currentReelSpin);

            soundPool.stop(reelSoundId);

            float rate = Math.abs(nextReelSpin); // (-1.0) - 1.0

            if (rate > 0) {
                reelSoundId = soundPool.play(sound_reel, 1, 1, 0, -1, rate);
            } else if (rate < 0) {
                reelSoundId = soundPool.play(sound_reel, 1, 1, 0, -1, rate);
            }
        }
        nextReelSpin = 0;

        lineLength +=  currentReelSpin * 5 * (1.0 / fps);

        // LINE REELED IN
        if(lineLength < 0) {
            setCastMode(CAST_MODE_IDLE);
            // Reset line to 0
            lineLength = 0;
            Log.w(TAG, "FX: Big splash");
            soundPool.play(sound_splash_big, 1, 1, 0, 0, 1);

            if (activeFish == null) {
                Log.w(TAG, "You didn't catch anything.");
            } else {
                FishEntry entry = Fishes.determineCaughtFish(fishEntryArray);
                Log.w(TAG, "You caught a " + entry.getName() + "!");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    catchImage.setImageDrawable(getDrawable(entry.getResourceID()));
                }
                catchName.setText(entry.getName());
                setCatchViewVisibility(true);
                // Release fish
                activeFish = null;
            }
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
                        0,
                        0,
                        Math.toDegrees(rotZ)
                )
        );
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
        rotX = Math.atan2(2.0 * (qx*qy + qz*qw),(sqx - sqy - sqz + sqw));
        rotY = Math.atan2(2.0 * (qy*qz + qx*qw),(-sqx - sqy + sqz + sqw));
        rotZ = Math.asin(-2.0 * (qx*qz - qy*qw));
    }

    public void resetHighestAccs(View view) {
        // reset accelerations
        for(int i = 0; i<4; i++) {
            top_accelerations_split[i] = 0;
            top_accelerations_total[i] = 0;
        }
    }

    public void setCastMode(int castMode) {
        Log.w(TAG, "CAST_MODE: " + this.castMode + " -> " + castMode);
        this.castMode = castMode;
        castModeView.setText(getString(R.string.cast_mode, this.castMode));
    }

    public void setReelMode(int reelMode) {
        // Log.w(TAG, "REEL_MODE: " + this.reedMode + " -> " + reedMode);
        this.reelMode = reelMode;
    }

    public void setReelEnabled(boolean reelEnabled) {

        // Check if there's been a change
        if(this.reelEnabled != reelEnabled) {

            if (reelEnabled == true) {
                Log.w(TAG, "Reel enabled.");
                this.reelEnabled = reelEnabled;
                reelButton.setEnabled(true);
                reelButton.setAlpha(1f);
            } else {
                Log.w(TAG, "Reel disabled.");
                this.reelEnabled = reelEnabled;
                reelButton.setEnabled(false);
                reelButton.setAlpha(0.5f); // TODO: Does not work, hides button completely
            }
        }

    }

    public void hideView(View view) {
        view.setVisibility(View.GONE);
    }

    public void toggleDebug(View view) {
        debugViewOpen = !debugViewOpen;

        if(debugViewOpen) {
            // set VISIBLE
            debugViewLayout.setVisibility(View.VISIBLE);
            // set GONE
            // lineLengthView.setVisibility(View.GONE);
            backgroundView.setVisibility(View.GONE);

            stopService(bgSoundintent);
        } else {
            // set GONE
            debugViewLayout.setVisibility(View.GONE);
            // set VISIBLE
            // lineLengthView.setVisibility(View.VISIBLE);
            backgroundView.setVisibility(View.VISIBLE);

            startService(bgSoundintent);
        }
    }

    public void setCatchViewVisibility(boolean visible) {
        catchViewOpen = visible;

        if(catchViewOpen) {
            // set VISIBLE
            catchViewLayout.setVisibility(View.VISIBLE);

            stopService(bgSoundintent);
        } else {
            // set GONE
            catchViewLayout.setVisibility(View.GONE);

            startService(bgSoundintent);
        }
    }

    public void closeCatchView(View view) {

        setCatchViewVisibility(false);
    }

    public void nextTutorialStep(View view) {
        if(currentTutorialStep == null) {
            castingInstructionsViewLayout.setVisibility(View.VISIBLE);
            currentTutorialStep = castingInstructionsViewLayout;
        } else if(currentTutorialStep == castingInstructionsViewLayout) {
            castingInstructionsViewLayout.setVisibility(View.GONE);
            fishingInstructionsViewLayout.setVisibility(View.VISIBLE);
            currentTutorialStep = fishingInstructionsViewLayout;
        } else {
            fishingInstructionsViewLayout.setVisibility(View.GONE);
        }
    }

    private void initializeViews() {
        debugViewLayout               = findViewById(R.id.debug_values);
        catchViewLayout               = findViewById(R.id.fish_display_layout);
        castingInstructionsViewLayout = findViewById(R.id.instructions_casting_layout);
        fishingInstructionsViewLayout = findViewById(R.id.instructions_fishing_layout);

        backgroundView   = (ImageView) findViewById(R.id.start_waves);
        lineLengthView   = (TextView) findViewById(R.id.line_length);
        instructionsView = (ImageView) findViewById(R.id.instructions);
        xzyDebug         = (TextView) findViewById(R.id.acc_values_xyz);
        totalDebug       = (TextView) findViewById(R.id.acc_values_total);
        rotationView     = (TextView) findViewById(R.id.rotation_value);
        castModeView     = (TextView) findViewById(R.id.cast_mode);
        catchImage       = (ImageView) findViewById(R.id.catch_image);
        catchName        = (TextView) findViewById(R.id.catch_name);

        reelButton       = findViewById(R.id.btn_reel);
    }

    private class Fish {
        Random rd = new Random();

        long nextSplash = 10000;
        long nextVibration = 4000;
        float vibrationIntensity = 0.1f;
        long wait;
        boolean escaped = false;
        boolean startedEating = false;
        boolean hooked = false;

        public Fish() {
            wait = Fishes.spawnTime() * 1000;
            Log.w(TAG, "Fish will approach in " + (wait / 1000) + " seconds.");
        }

        public boolean escaped() {
            return escaped;
        }

        public void tick(long delay) {

            if (!escaped) {

                if(!hooked) {

                    if (4000 <= wait && wait < 10000)
                        // Start splashing
                        if (wait <= nextSplash) {
                            soundPool.play(sound_splash_small, 1, 1, 0, 0, 1);
                            nextSplash -= Math.round(1500 - (rd.nextFloat() * 1000) );
                            Log.w(TAG, "FX: Splash!");
                        }

                    // Start vibrating
                    if (1000 <= wait && wait < 4000) {
                        long vibrationLength = Math.round(50);
                        vibrator.vibrate(vibrationLength);
                        nextVibration -= Math.round((300 - (vibrationIntensity * 200)));
                        vibrationIntensity += 0.1f;
                    }

                    // Reset wait if reeling
                    if (nextReelSpin < 0) {
                        Log.w(TAG, "You reeled in too soon, the fish didn't bite...");
                        escaped = true;
                    }

                    // Bite
                    if (0 <= wait && wait < 1000) {
                        if (!startedEating) {
                            startedEating = true;
                            Log.w(TAG, "Reel in now!");
                            vibrator.vibrate(1000);
                        }

                        if (currentReelSpin < 0) {
                            Log.w(TAG, "Fish hooked!");
                            hooked = true;
                            vibrator.cancel();
                        }
                    }

                    if (wait < 0) {
                        Log.w(TAG, "You reeled in too late, the fish got away with the bait...");
                        escaped = true;
                    }
                    wait -= delay;
                } else {

                    if(wait < -1000) {

                        escaped = true;
                    }

                    if (reelMode != REEL_MODE_REELING) {
                        wait -= delay;
                    }
                    nextReelSpin += 0.25;
                }

            }
        }
    }

}
