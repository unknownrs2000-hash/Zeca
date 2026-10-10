// lang: Java, file: AdminReceiver.java, target: Android 8+ API 26
// device admin stub — being active blocks standard uninstall path

package com.example.zeca.stealer;

import android.app.admin.DeviceAdminReceiver;
import android.content.*;

public class AdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context ctx, Intent intent) {}
    @Override
    public void onDisabled(Context ctx, Intent intent) {}
}