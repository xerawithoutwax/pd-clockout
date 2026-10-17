package com.pdclockout.helper;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.pdclockout.helper.download.Engine;

import java.util.concurrent.Executors;

public class PdClockoutApp extends Application {
    public static final String CHANNEL_DOWNLOAD = "pdclockout_download";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Engine.ensureInitialized(this);
            } catch (Exception ignored) {
                // Worker/MainActivity will surface the actual initialization error.
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_DOWNLOAD,
                    "PD칼퇴도우미 다운로드",
                    NotificationManager.IMPORTANCE_LOW
            );
            ch.setDescription("영상 다운로드 진행 상태");
            nm.createNotificationChannel(ch);
        }
    }
}
