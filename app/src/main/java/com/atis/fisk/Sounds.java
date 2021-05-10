package com.atis.fisk;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;

public class Sounds {

    public static void load(Context context, int ...sounds) {

    }

    // Stafford Williams https://stackoverflow.com/a/27552576
    public static SoundPool createSoundPool() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return createNewSoundPool();
        } else {
            return createOldSoundPool();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static SoundPool createNewSoundPool(){
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
    private static SoundPool createOldSoundPool(){
        return new SoundPool(5, AudioManager.STREAM_MUSIC, 0);
    }

}
