package com.atis.fisk;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Vibrator;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.util.ArrayList;


public class FishingActivity extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = "FishingActivity";
    private SharedPreferences settings;

    private static final int CAST_MODE_IDLE = 0;
    private static final int CAST_MODE_CASTING = 1;
    private static final int CAST_MODE_FISHING = 2;

    private static final int REEL_MODE_IDLE = 0;
    private static final int REEL_MODE_REELING = 1;

    /* Declare sensors and vibrator */
    private SensorManager sensorManager;
    private Sensor linearAccelerationSensor;
    private Sensor rotationVectorSensor;
    private Vibrator vibrator;

    /* Declare views */
    private ConstraintLayout mainLayout;
    private ConstraintLayout UILayout;
    private ConstraintLayout debugViewLayout;
    private ImageView backgroundView;
    private ImageView floatView;
    private TextView xzyDebug;
    private TextView totalDebug;
    private TextView lineLengthView;
    private TextView rotationView;
    private TextView castModeView;
    private AnimationDrawable wavesAnimation;

    /* Sounds */
    SoundPool soundPool;
    int sound_swosh;
    int sound_click;
    int sound_splash_small;
    int sound_splash_big;
    int sound_prime;
    int sound_idle;
    int sound_reel;
    int sound_line_tension;
    int sound_line_break;

    /* Declare sensor variables */
    private double[] linear_acceleration;
    private double[] top_accelerations_split; // debugging only
    private double[] top_accelerations_total; // debugging only
    private double[] top_accelerations_cast; // top casting acceleration
    private double rotX, rotY, rotZ;
    private ArrayList<Double> delta = new ArrayList<Double>();
    private double velocity = 0;
    private double castVelocity = 0;

    /* Tutorial */
    boolean displayWelcomeTip;
    boolean displayCastTip;
    boolean displayWaitTip;
    boolean displayCloseTip;
    boolean displayReelInTip;
    boolean displayTooSoonTip;
    boolean displayTooLateTip;
    boolean displaySuccessTip;
    PopupData[] previousPopupData;

    // Other
    private DecimalFormat df = new DecimalFormat("#.#"); // debug
    private Intent bgSoundintent;
    private Intent waitingSoundintent;

    /* Game loop */
    private boolean paused = false;
    private final int fps = 60;
    private final long delay = 1000 / fps;
    private final Handler handler = new Handler();
    Runnable game = new Runnable() {
        public void run() {
            if(!paused) {
                tick();
                handler.postDelayed(this, delay);
            } else {
                handler.post(this);
            }


        }
    };

    /* Game states and variables */
    private boolean debugViewOpen = false;
    private volatile int castMode = CAST_MODE_IDLE;
    private int reelMode = REEL_MODE_IDLE;
    private boolean reelEnabled = true;
    private volatile double lineLength = 0;
    private double targetLength;
    private Fish activeFish = null;
    private boolean castDone;
    private float lineLengthCastAdd;
    private float lineLengthReelAdd;
    private float lineLengthPullAdd;
    private float lineDurability = 4000;
    private double lineTension;

    /* Reel variables */
    private volatile float currentReelSpin;
    private int reelSoundId;
    private int lineSoundId;

    /* Popup Window */
    private PopupWindow popupWindow;

    @SuppressLint("ClickableViewAccessibility") // TODO: ?
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fishing);

        initializeViews();

        if (MainActivity.tutorialEnabled) {
            displayWelcomeTip = true;
            displayCastTip = true; // will be set to true after welcome tip is displayed
            displayWaitTip = true;
            displayCloseTip = true;
            displayReelInTip = false; // distracting
            displayTooSoonTip = true;
            displayTooLateTip = true;
            displaySuccessTip = true;
        } else {
            disableTutorial();
        }

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
        sound_line_tension = soundPool.load(this, R.raw.rod_tension, 1);
        sound_line_break = soundPool.load(this, R.raw.rod_snap, 1);

        /* Create REEL IN button */
        mainLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                // TODO: Hmmm
                if(reelEnabled) {

                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        lineLengthReelAdd = 0.75f;
                        setReelMode(REEL_MODE_REELING);
                    }
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        lineLengthReelAdd = 0f;
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

        floatView.setBackgroundResource(R.drawable.float_animated);
        floatView.setImageDrawable(null);
        ((AnimationDrawable) floatView.getBackground()).start();

        /* Start background audio */
        bgSoundintent = new Intent(this, BackgroundSoundService.class);
        startService(bgSoundintent);

        waitingSoundintent = new Intent(this, WaitingSoundService.class);
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
        soundPool.autoPause();
        vibrator.cancel();
        stopService(bgSoundintent);
        stopService(waitingSoundintent);

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
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // not in use
    }

    private void tick() {

        if (displayWelcomeTip) {
            popup(
                    new PopupData(R.string.how_to_play, R.drawable.ic_launcher_foreground, R.string.popup_tip_welcome),
                    new PopupData(R.string.how_to_play, R.drawable.cast_animation, R.string.popup_tip_casting)
            );
            displayWelcomeTip = false;
            displayCastTip = false;
        } else if (displayCastTip) {
            // ...
        }

        /* Velocity */
        double newV = linear_acceleration[3] * (delay / 1000.0);
        delta.add(newV);
        velocity += newV;
        if(delta.size() > fps / 2) {
            double oldV = delta.remove(0);
            velocity -= oldV;
        }

        if (castMode == CAST_MODE_IDLE) {
            reelMode = REEL_MODE_IDLE;
            setReelEnabled(false);

            /* Velocity method */
            if (velocity > 8) {
                castVelocity = 8;
                targetLength = 8;

                // Start the cast
                Log.w(TAG, "Starting cast...");
                lineLengthCastAdd = 1;
                setReelEnabled(false);
                castDone = false;
                soundPool.play(sound_swosh, 1, 1, 0, 0, 1);
                vibrator.vibrate(1000);
                setCastMode(CAST_MODE_CASTING);
            }
        }

        if (castMode == CAST_MODE_CASTING) {

            if(!castDone) {
                // Wait until velocity drops below threshold
                if (velocity < 9) {
                    Log.w(TAG, "Successful cast! Velocity: " + df.format(velocity) + "m/s");
                    castDone = true;
                    targetLength = castVelocity; // TODO: Better math
                    // lineLengthCastAdd = 1;

                } else {
                    // Updates castVelocity
                    if (velocity > castVelocity) {
                        castVelocity = velocity;
                    }
                }
            }

            if (lineLength < targetLength) { // TODO: Better math
                // ...
            } else {
                // Splash
                lineLengthCastAdd = 0;
                setReelEnabled(true);
                soundPool.play(sound_splash_small, 1, 1, 0, 0, 1);
                floatView.setVisibility(View.VISIBLE);
                if(displayWaitTip) {
                    popup(new PopupData(R.string.how_to_play, R.drawable.phone_press, R.string.popup_tip_wait));
                }
                setCastMode(CAST_MODE_FISHING);
            }
        }

        // TODO: Sensors could be paused while waiting for fish
        if(castMode == CAST_MODE_FISHING) {

            if(activeFish == null || activeFish.escaped()) {
                activeFish = new Fish();
            } else {
                // Do one fish tick
                activeFish.tick(delay);
            }
        }

        // TODO: Delta can be changed even if sum is the same, might cause problems.
        float sum = lineLengthCastAdd - lineLengthReelAdd + lineLengthPullAdd;
        // REEL STUFF
        if (sum != currentReelSpin) {

            lineTension = Math.min(
                    (lineLengthCastAdd + lineLengthPullAdd),
                    lineLengthReelAdd);

            Log.w(TAG, "ReelSpin: " + currentReelSpin + " -> " + sum + " (" + lineTension + ")");

            if (lineTension > 0) {
                soundPool.stop(lineSoundId);
                lineSoundId = soundPool.play(sound_line_tension, 1, 1, 0, 0, 1);
                long[] pattern = {100, 100};
                vibrator.vibrate(pattern, 0);
            } else {
                soundPool.stop(lineSoundId);
                vibrator.cancel();
            }


            currentReelSpin = sum;

            soundPool.stop(reelSoundId);

            float rate = Math.abs(currentReelSpin); // (-1.0) - 1.0

            if (rate > 0) {
                reelSoundId = soundPool.play(sound_reel, 1, 1, 0, -1, rate);
            } else if (rate < 0) {
                reelSoundId = soundPool.play(sound_reel, 1, 1, 0, -1, rate);
            }
        }

        lineLength +=  currentReelSpin * 5 * (1.0 / fps);

        if(lineTension > 0) {
            lineDurability -= delay;

            if (lineDurability < 0) {
                soundPool.stop(lineSoundId);
                soundPool.play(sound_line_break, 1, 1, 0, 0, 1);
                vibrator.cancel();
                popup(
                        new PopupData(R.string.how_to_play, R.drawable.line_snap, R.string.popup_tip_snap),
                        new PopupData(R.string.how_to_play, R.drawable.pull_animation_2, R.string.popup_tip_pull)
                        );
                activeFish = null;
                lineLengthPullAdd = 0;
                lineLength = -1;
                lineDurability = 4000;
            }
        } else {
            lineDurability = 4000;
        }

        // LINE REELED IN
        if(lineLength < 0) {
            soundPool.stop(reelSoundId); // Make sure reelSound stops playing
            setCastMode(CAST_MODE_IDLE);
            // Reset line to 0
            lineLength = 0;
            delta.clear();
            velocity = 0;
            Log.w(TAG, "FX: Big splash");
            soundPool.play(sound_splash_big, 1, 1, 0, 0, 1);



            if (activeFish != null && activeFish.isHooked()) {

                FishEntry entry = Fishes.catchFish(activeFish.spawnDistance);
                Log.w(TAG, "You caught a " + entry.getName() + "!");

                if(displaySuccessTip) {
                    popup(
                        new PopupData(R.string.fish_was_caught, entry.getResourceID(), getString(R.string.caught_message, entry.getName())),
                        new PopupData(R.string.how_to_play, R.drawable.tutorial_complete, R.string.popup_tip_success)
                    );
                    disableTutorial();
                } else {
                    popup(new PopupData(R.string.fish_was_caught, entry.getResourceID(), getString(R.string.caught_message, entry.getName())));
                }

                // Release fish
                activeFish = null;
            } else {
                Log.w(TAG, "You didn't catch anything.");
            }
        }

        // Update views
        updateAccelerationViews();
        updateRotationViews();

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
                        Math.toDegrees(rotX),
                        Math.toDegrees(rotY),
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

        this.reelMode = reelMode;
    }

    public void setReelEnabled(boolean reelEnabled) {

        // Check if there's been a change
        if(this.reelEnabled != reelEnabled) {

            if (reelEnabled == true) {
                Log.w(TAG, "Reel enabled.");
                this.reelEnabled = reelEnabled;
                mainLayout.setEnabled(true);
            } else {
                lineLengthReelAdd = 0; // Making sure this is zero
                Log.w(TAG, "Reel disabled.");
                this.reelEnabled = reelEnabled;
                mainLayout.setEnabled(false);
            }
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
            backgroundView.setVisibility(View.VISIBLE);

            startService(bgSoundintent);
        }
    }

    public void disableTutorial(View view) {
        disableTutorial();
    }

    public void disableTutorial() {
        // Disable hints
        displayWelcomeTip = false;
        displayCastTip = false;
        displayWaitTip = false;
        displayCloseTip = false;
        displayReelInTip = false;
        displayTooSoonTip = false;
        displayTooLateTip = false;
        displaySuccessTip = false;

        MainActivity.tutorialEnabled = false;

        ImageButton helpButton = (ImageButton) findViewById(R.id.help_button);
        helpButton.setVisibility(View.GONE);
    }

    public void viewPreviousPopup(View view) {
        popup(previousPopupData);
    }

    public void finishActivity(View view) {
        Log.w(TAG, "Activity finished.");
        finish();
    }

    public void popup(final PopupData... popupData) {

        popup(0, popupData);

    }

    public void popup(final int i, final PopupData... popupData) {
        Log.w(TAG, "Popup opened.");

        if (i == 0) {
            previousPopupData = popupData;
            paused = true;
        }

        final PopupData pd = popupData[i];
        final boolean last = (i == popupData.length - 1);

        // Initialize popup window if null
        if (popupWindow == null) {
            // inflate the layout of the popup window
            LayoutInflater inflater =
                    (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            View popupView = inflater.inflate(R.layout.popup, (ViewGroup) findViewById(R.id.main_layout), false);

            // create the popup window
            int width = LinearLayout.LayoutParams.MATCH_PARENT;
            int height = LinearLayout.LayoutParams.MATCH_PARENT;
            boolean focusable = false; // disables ability to tap outside the popup to dismiss
            popupWindow = new PopupWindow(popupView, width, height, focusable);
        }

        TextView popupTitle = popupWindow.getContentView().findViewById(R.id.popup_title);
        TextView popupMessage = popupWindow.getContentView().findViewById(R.id.popup_tip);
        ImageView popupDrawable = popupWindow.getContentView().findViewById(R.id.popup_image);
        Button popupButton = popupWindow.getContentView().findViewById(R.id.popup_button);

        popupTitle.setText(pd.title);
        Drawable drawable = getResources().getDrawable(pd.image);

        // Remove any current drawables
        popupDrawable.setBackground(null);
        popupDrawable.setImageDrawable(null);

        if (drawable instanceof AnimationDrawable) {
            popupDrawable.setBackground(drawable);
            popupDrawable.setImageDrawable(null);
            ((AnimationDrawable) popupDrawable.getBackground()).start();
        } else {
            popupDrawable.setImageDrawable(drawable);
        }
        popupMessage.setText(pd.message);

        if (last) {
            popupButton.setText(R.string.ok);
        } else {
            popupButton.setText(R.string.next);
        }

        ConstraintLayout mainLayout = findViewById(R.id.main_layout);

        // if(prevPopupWindow != null) {
        //     prevPopupWindow.dismiss();
        // }

        if(i == 0) {
            popupWindow.showAtLocation(mainLayout, Gravity.CENTER, 0, 0);
            soundPool.pause(reelSoundId);
            UILayout.setVisibility(View.GONE);
        }

        // dismiss the popup window when touched
        popupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (last) {
                    // resume game
                    UILayout.setVisibility(View.VISIBLE);
                    soundPool.resume(reelSoundId);
                    popupWindow.dismiss();
                    paused = false;
                } else {
                    // next popup
                    popup((i + 1), popupData);
                }
            }
        });
    }

    private void initializeViews() {
        mainLayout                    = findViewById(R.id.main_layout);
        UILayout                      = findViewById(R.id.UI_layout);
        debugViewLayout               = findViewById(R.id.debug_values);

        backgroundView   = (ImageView) findViewById(R.id.start_waves);
        lineLengthView   = (TextView) findViewById(R.id.line_length);
        xzyDebug         = (TextView) findViewById(R.id.acc_values_xyz);
        totalDebug       = (TextView) findViewById(R.id.acc_values_total);
        rotationView     = (TextView) findViewById(R.id.rotation_value);
        castModeView     = (TextView) findViewById(R.id.cast_mode);

        floatView = findViewById(R.id.float_animated);
    }

    private class PopupData {
        final int title;
        final int image;
        final String message;

        public PopupData(int title, int image, String message) {
            this.title = title;
            this.image = image;
            this.message = message;
        }

        public PopupData(int title, int image, int message) {
            this.title = title;
            this.image = image;
            this.message = getResources().getString(message);
        }
    }

    private class Fish {
        double spawnDistance = lineLength;

        long vibrationLength = 80; // Find shortest duration that works
        long nextSplash = 10000;
        long nextVibration = 4000;
        float vibrationFactor = 1f;
        long initWait;
        long wait;
        boolean escaped = false;
        boolean startedEating = false;
        boolean hooked = false;
        int countdown;
        double stunTime;

        public Fish() {
            wait = Fishes.spawnTime() * 1000;
            initWait = wait;
            countdown = (int) (wait / 1000);
        }

        public boolean escaped() {
            return escaped;
        }

        public boolean isHooked() {
            return hooked;
        }

        public void tick(long delay) {

            if (!escaped) {

                if(!hooked) {

                    if (wait <= countdown * 1000) {
                        Log.w(TAG, "Fish will approach in " + countdown + " seconds.");
                        countdown--;
                    }

                    if (4000 <= wait && wait < 10000)
                        if(displayCloseTip) {
                            popup(
                                new PopupData(R.string.how_to_play, R.drawable.unknown_fish, R.string.popup_tip_close),
                                new PopupData(R.string.how_to_play, R.drawable.man_reel_simple, R.string.popup_tip_reel),
                                new PopupData(R.string.how_to_play, R.drawable.pull_animation_2, R.string.popup_tip_pull),
                                new PopupData(R.string.how_to_play, R.drawable.pull_animation_3, R.string.popup_tip_repeat)
                            );
                            displayCloseTip = false;
                        }

                    // Start vibrating
                    if (1000 <= wait && wait < 4000) {
                        if (wait <= nextVibration) {
                            vibrator.vibrate(vibrationLength);
                            nextVibration -= vibrationLength + 1000 * vibrationFactor;
                            vibrationFactor -= 0.2f;
                        }
                    }

                    // Bite
                    if (0 <= wait && wait < 1000) {
                        if(displayReelInTip) {
                            popup(new PopupData(R.string.how_to_play, R.drawable.unknown_fish, R.string.popup_tip_now));
                            displayReelInTip = false;

                        // Making sure that the tip is displayed before vibration starts
                        } else {
                            if (!startedEating) {
                                startedEating = true;
                                floatView.setVisibility(View.GONE);
                                Log.w(TAG, "Reel in now!");
                                vibrator.vibrate(2000);
                            }
                        }

                    }

                    // Reset wait if reeling
                    if (reelMode == REEL_MODE_REELING) {


                        if (startedEating) {

                            if (wait >= -1000) {
                                Log.w(TAG, "Fish hooked!");
                                hooked = true;
                                vibrator.cancel();
                            } else {
                                if (displayTooSoonTip) {
                                    popup(new PopupData(R.string.how_to_play, R.drawable.unknown_fish, R.string.popup_tip_too_soon_close));
                                }
                                Log.w(TAG, "You reeled in too soon, the fish didn't bite...");
                                floatView.setVisibility(View.VISIBLE);
                                escaped = true;
                            }
                        } else if (initWait - wait > 3000 || wait < 10000) {
                            if (displayTooSoonTip) {
                                popup(new PopupData(R.string.how_to_play, R.drawable.unknown_fish, R.string.popup_tip_too_soon_far));
                            }
                            Log.w(TAG, "You reeled in before a fish had time to approach...");
                            escaped = true;
                        }
                    }

                    if (wait < -1000) {
                        if(displayTooLateTip) {
                            popup(new PopupData(R.string.how_to_play, R.drawable.unknown_fish, R.string.popup_tip_too_late));
                        }
                        Log.w(TAG, "You reeled in too late, the fish got away with the bait...");
                        floatView.setVisibility(View.VISIBLE);
                        escaped = true;
                    }

                    wait -= delay;

                } else {
                    // IF HOOKED

                    if(wait < -2000) {
                        if(displayTooLateTip) {
                            popup(new PopupData(R.string.how_to_play, R.drawable.unknown_fish, R.string.popup_tip_too_late));
                        }
                        Log.w(TAG, "Fish got away!");
                        escaped = true;
                        lineLengthPullAdd = 0f;
                    } else {
                        if (reelMode != REEL_MODE_REELING) {
                            wait -= delay;
                        }

                        if (stunTime > 0) {
                            stunTime -= delay;
                        } else {

                            if (velocity > 4) {
                                vibrator.vibrate(100);
                                soundPool.play(sound_swosh, 1, 1, 0, 0, 1);
                                soundPool.play(sound_splash_small, 1, 1, 0, 0, 1);
                                lineLengthPullAdd = 0f;
                                stunTime = 1500;
                            } else {
                                lineLengthPullAdd = 0.9f;
                            }
                        }
                    }

                }

            }
        }
    }

}
