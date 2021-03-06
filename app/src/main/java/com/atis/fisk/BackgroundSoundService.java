package com.atis.fisk;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.widget.Toast;
import android.support.annotation.Nullable;

public class BackgroundSoundService extends Service {
    MediaPlayer mediaPlayer;
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    @Override
    public void onCreate() {
        super.onCreate();
        mediaPlayer = MediaPlayer.create(this, R.raw.ocean_waves_trimmed);
        mediaPlayer.setLooping(true); // Set looping
        mediaPlayer.setVolume(0.7f, 0.7f);
    }
    public int onStartCommand(Intent intent, int flags, int startId) {
        mediaPlayer.start();
        return startId;
    }
    public void onStart(Intent intent, int startId) {
    }
    @Override
    public void onDestroy() {
        mediaPlayer.stop();
        mediaPlayer.release();
    }


    @Override
    public void onLowMemory() {
    }
}