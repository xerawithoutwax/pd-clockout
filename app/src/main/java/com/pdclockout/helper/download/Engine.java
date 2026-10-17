package com.pdclockout.helper.download;

import android.content.Context;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;

public final class Engine {
    private static volatile boolean initialized = false;
    private static final Object LOCK = new Object();

    private Engine() {}

    public static void ensureInitialized(Context context) throws Exception {
        if (initialized) return;
        synchronized (LOCK) {
            if (initialized) return;
            Context app = context.getApplicationContext();
            YoutubeDL.getInstance().init(app);
            FFmpeg.getInstance().init(app);
            initialized = true;
        }
    }
}
