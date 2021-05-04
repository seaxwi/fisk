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

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Random;


public class FishingActivity extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = "FishingActivity";

    private final double PARAMETER_PRIMING_ANGLE = Math.toRadians(45);
    private final double PARAMETER_PRIMING_ANGLE_THRESHOLD = Math.toRadians(10);
    private final float PARAMETER_CASTING_ACCELERATION_LIMIT = 30;
    private final float PARAMETER_CASTING_ACCELERATION_THRESHOLD= 10;

    private static final int CAST_MODE_LOADING = -1;
    private static final int CAST_MODE_IDLE = 0;
    private static final int CAST_MODE_PRIMED = 1;
    private static final int CAST_MODE_CASTING = 2;
    private static final int CAST_MODE_AIRBORNE = 3;
    private static final int CAST_MODE_FISHING = 4;

    private static final int FISHING_MODE_BLOCKED = -2;
    private static final int FISHING_MODE_NONE = -1;
    private static final int FISHING_MODE_SPLASH = 0;
    private static final int FISHING_MODE_WAITING = 1;
    private static final int FISHING_MODE_APPROACH = 2;
    private static final int FISHING_MODE_ACTIVE = 3;

    private static final int REED_MODE_BLOCKED = -1;
    private static final int REED_MODE_IDLE = 0;
    private static final int REED_MODE_STARTING = 1;
    private static final int REED_MODE_REELING = 2;

    // Sound
    private SoundPool soundPool;

    // Mode
    boolean debugViewOpen = false;
    boolean catchViewOpen = false;

    // Intent
    private Intent bgSoundintent;

    // sensor stuff
    private SensorManager sensorManager;
    private Sensor linearAccelerationSensor;
    private Sensor rotationVectorSensor;

    // Vibrator
    private Vibrator vibrator;

    // Views
    private ConstraintLayout debugViewLayout;
    private ConstraintLayout catchViewLayout;
    private ImageView catchImage;
    private TextView catchName;
    private TextView xzyDebug;
    private TextView totalDebug;
    private ImageView backgroundView;
    private TextView lineLengthView;
    private TextView rotationView;
    private TextView castModeView;
    private AnimationDrawable wavesAnimation;

    // Button
    private Button reelButton;

    private FishEntry[] fishEntryArray;
    private FishEntry activeFish = null;

    // Other
    DecimalFormat df = new DecimalFormat("#.#"); // debug
    private Random rd;

    // Sensor Arrays
    private double[] linear_acceleration;
    private double[] top_accelerations_split; // debugging only
    private double[] top_accelerations_total; // debugging only
    private double[] top_accelerations_cast; // top casting acceleration
    private double[] rotations;

    // Sensor loop control
    private int castMode = CAST_MODE_LOADING;
    private int reedMode = REED_MODE_BLOCKED;
    private int fishingMode = FISHING_MODE_NONE;
    private double lineLength = 0;

    // Sounds
    int sound_swosh, sound_click, sound_splash_small, sound_splash_big, sound_splash_droplet,
    sound_prime, sound_idle, sound_reel;
    int reelStreamId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fishing);

        debugViewLayout = findViewById(R.id.debug_values);
        catchViewLayout = findViewById(R.id.fish_display_layout);

        // Fish stuff
        fishEntryArray = createFishArray();

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

        xzyDebug = findViewById(R.id.acc_values_xyz);
        totalDebug = findViewById(R.id.acc_values_total);
        rotationView = findViewById(R.id.rotation_value);
        castModeView = findViewById(R.id.cast_mode);

        reelButton = findViewById(R.id.btn_reel);
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

        backgroundView = findViewById(R.id.start_waves);
        lineLengthView = findViewById(R.id.line_length);

        catchImage = findViewById(R.id.catch_image);
        catchName = findViewById(R.id.catch_name);

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
            if(debugViewOpen) {
                updateAccelerationViews();
            }

        } else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            updateRotationValues(event);
            if (debugViewOpen) {
                updateRotationViews();
            }
        }

        if (castMode == CAST_MODE_IDLE && rotations[2] > PARAMETER_PRIMING_ANGLE + PARAMETER_PRIMING_ANGLE_THRESHOLD / 2) {
            setCastMode(CAST_MODE_PRIMED);
            soundPool.play(sound_prime, 1, 1, 0, 0, 1);
            vibrator.vibrate(100);
        }

        if (castMode == CAST_MODE_PRIMED) {

            if (rotations[2] < PARAMETER_PRIMING_ANGLE - PARAMETER_PRIMING_ANGLE_THRESHOLD / 2) {

                Log.w(TAG, "Priming angle crossed with an acceleration of " + df.format(linear_acceleration[3]) + "!");
                if (linear_acceleration[3] > PARAMETER_CASTING_ACCELERATION_LIMIT + PARAMETER_CASTING_ACCELERATION_THRESHOLD / 2) {
                    Log.w(TAG, "Filling top_accelerations_cast[] with zeroes...");
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

            // Wait until accleration drops below threshold
            if(linear_acceleration[3] < PARAMETER_CASTING_ACCELERATION_LIMIT - PARAMETER_CASTING_ACCELERATION_THRESHOLD / 2) {
                vibrator.vibrate(1000);
                Log.w(TAG, "Succesful cast! Top cast acceleration: " + df.format(top_accelerations_cast[3]));

                new Thread(new Runnable() {
                    public void run() {
                        setCastMode(CAST_MODE_AIRBORNE);
                        int sound = soundPool.play(sound_click, 1, 1, 0, -1, 1.5f);
                        while (lineLength < (top_accelerations_cast[3] / 3)) { // TODO: This is prone to glitches, like counter reeling
                            lineLength += 0.1;
                            SystemClock.sleep(10);
                        }
                        soundPool.stop(sound);
                        setFishingMode(FISHING_MODE_SPLASH);
                        setCastMode(CAST_MODE_FISHING);
                    }
                }).start();
            }
        }

        if(castMode == CAST_MODE_AIRBORNE) {

            // Does nothing for now. Loop stays idle until castMode is changed

        }

        // This branches into the fishing branch (FISH_MODE_*)
        // TODO: Sensors could be paused while waiting for fish
        if(castMode == CAST_MODE_FISHING) {

            // SPLASH!
            if(fishingMode == FISHING_MODE_SPLASH) {
                soundPool.play(sound_splash_small, 1, 1, 0, 0, 1);
                setFishingMode(FISHING_MODE_NONE);
            }

            if(fishingMode == FISHING_MODE_NONE) {

                // Start random wait thread
                setFishingMode(FISHING_MODE_WAITING);
                new Thread(new Runnable() {
                    public void run() {
                        long wait = (10 + rd.nextInt(20)) * 1000; // 10-30 second wait
                        Log.w(TAG, "Waiting " + wait / 1000 + " seconds.");
                        while (castMode == CAST_MODE_FISHING && fishingMode != FISHING_MODE_BLOCKED) {

                            if (wait >= 0) {
                                // Splash at T-minus 1s
                                if (wait / 100 == 10) {
                                    soundPool.play(sound_splash_small, 1, 1, 0, 0, 1);
                                }
                                // Vibrations when t<2s
                                if (wait <= 2000 && wait % 500 == 0) {
                                    vibrator.vibrate(50);
                                }

                                SystemClock.sleep(100);
                                wait -= 100;

                                // Reset wait if reeling
                                if (reedMode == REED_MODE_REELING) {
                                    Log.w(TAG, "You reeled in to soon, fish didn't bite...");
                                } else {
                                    // FISH BITES THE HOOK
                                    setFishingMode(FISHING_MODE_ACTIVE);
                                }
                            }
                        }
                    }
                }).start();
            }

            if (fishingMode == FISHING_MODE_WAITING) {

                // setFishingMode(FISHING_MODE_ACTIVE);
                activeFish = determineCaughtFish(fishEntryArray);

                new Thread(new Runnable() {
                    public void run() {
                        long wait = (10 + rd.nextInt(20)) * 1000; // 10-30 second wait
                        Log.w(TAG, "Waiting " + wait / 1000 + " seconds.");
                        while (castMode == CAST_MODE_FISHING && fishingMode != FISHING_MODE_BLOCKED) {

                            vibrator.vibrate(1000);
                            SystemClock.sleep(1000);
                            int idleTime = 0;

                            if (reedMode != REED_MODE_REELING) {
                                idleTime += 10;
                                SystemClock.sleep(10);
                            } else {
                                vibrator.vibrate(100);
                                lineLength += 1;
                                SystemClock.sleep(100);
                                idleTime += 100;
                            }

                            if (idleTime > 1000) {
                                activeFish = null;
                                Log.w(TAG, "Fish got away...");
                            }
                            // setFishingMode(FISHING_MODE_WAITING);
                        }
                    }
                }).start();
            }

            // LINE REELED IN
            if(lineLength <= 0) {
                // Make sure line is 0 and not negative
                lineLength = 0;
                soundPool.play(sound_splash_big, 1, 1, 0, 0, 1);

                // Check for fish
                if(castMode == CAST_MODE_FISHING) {
                    if (activeFish == null) {
                        Log.w(TAG, "You didn't catch anything.");
                    } else {
                        Log.w(TAG, "You caught a " + activeFish.getName() + "!");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            catchImage.setImageDrawable(getDrawable(activeFish.getResourceID()));
                        }
                        catchName.setText(activeFish.getName());

                        setCatchViewVisibility(true);
                    }
                    setFishingMode(FISHING_MODE_BLOCKED); // ?
                    setCastMode(CAST_MODE_IDLE);
                }
            }
        }

        if (reedMode == REED_MODE_STARTING && lineLength > 0) {

            setReedMode(REED_MODE_REELING);

            new Thread(new Runnable() {
                public void run() {
                    // a potentially time consuming task
                    int sound = soundPool.play(sound_reel, 1, 1, 0, 0, 1);
                    Log.w(TAG, "Reeling...");
                    while (lineLength > 0 && reedMode == REED_MODE_REELING) {

                        lineLength -= 0.1;
                        SystemClock.sleep(10);
                    }
                    Log.w(TAG, "...stopped reeling.");
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

    public void setCastMode(int castMode) {
        Log.w(TAG, "CAST_MODE: " + this.castMode + " -> " + castMode);
        this.castMode = castMode;
        castModeView.setText(getString(R.string.cast_mode, this.castMode));
        if(castMode != CAST_MODE_FISHING) {
            setFishingMode(FISHING_MODE_BLOCKED);
        }
    }

    public void setFishingMode(int fishingMode) {
        Log.w(TAG, "FISHING_MODE: " + this.fishingMode + " -> " + fishingMode);
        this.fishingMode = fishingMode;
    }

    public void setReedMode(int reedMode) {
        // Log.w(TAG, "REEL_MODE: " + this.reedMode + " -> " + reedMode);
        this.reedMode = reedMode;
        if(reedMode == REED_MODE_STARTING) {
            reelStreamId = soundPool.play(sound_reel, 1, 1, 0, -1, 1);
            setFishingMode(FISHING_MODE_BLOCKED);
        } else {
            soundPool.stop(reelStreamId);
            setFishingMode(FISHING_MODE_NONE);
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

    private FishEntry[] createFishArray(){
        //create empty fishArray
        FishEntry[] fishEntryArray = new FishEntry[9];

        //create fish objects
        FishEntry unknown = new FishEntry("???", R.drawable.unknown_fish, 0);
        FishEntry pinkFish = new FishEntry("Pink Fish", R.drawable.pink_fish, 1);
        FishEntry blueFish = new FishEntry("Blue Fish", R.drawable.blue_fish, 1);
        FishEntry yellowFish = new FishEntry("Yellow Fish", R.drawable.yellow_fish, 1);
        FishEntry flounder = new FishEntry("Flounder", R.drawable.flounder, 3);
        FishEntry crab = new FishEntry("Crab", R.drawable.crab, 3);
        FishEntry jellyfish = new FishEntry("Jellyfish", R.drawable.jellyfish, 3);
        FishEntry seahorse = new FishEntry("Seahorse", R.drawable.seahorse, 3);
        FishEntry tinCan = new FishEntry("Tin Can", R.drawable.tin_can, 5);

        //add fish objects to array
        fishEntryArray[0] = pinkFish;
        fishEntryArray[1] = blueFish;
        fishEntryArray[2] = yellowFish;
        fishEntryArray[3] = flounder;
        fishEntryArray[4] = crab;
        fishEntryArray[5] = jellyfish;
        fishEntryArray[6] = seahorse;
        fishEntryArray[7] = tinCan;
        fishEntryArray[8] = unknown; // ???

        return fishEntryArray;
    }

    private FishEntry determineCaughtFish(FishEntry[] fishEntryArray){

        int totalWeight = 0;
        for(FishEntry f: fishEntryArray) {
            totalWeight += f.getWeight();
        }

        int randomWeight = rd.nextInt(totalWeight);

        for(FishEntry f: fishEntryArray) {
            int fishWeight = f.getWeight();
            if(randomWeight > fishWeight) {
                randomWeight -= fishWeight;
            } else {
                return f;
            }
        }
        Log.w(TAG, "Fail");
        return fishEntryArray[7];
    }
}
