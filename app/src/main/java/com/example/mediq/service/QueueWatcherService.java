package com.example.mediq.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.example.mediq.MainActivity;
import com.example.mediq.R;
import com.example.mediq.data.remote.SessionManager;
import com.example.mediq.data.remote.SupabaseClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Keeps patients informed while the app is closed on the day of a visit.
 *
 * It runs only while the patient has an active appointment TODAY (MainActivity starts it), checks for new
 * rows in the notifications table every 15 s and shows each one as a normal phone notification
 * ("You're next", "It's your turn", "Queue moved"...). It stops by itself when the visit day is over,
 * the appointment is finished/cancelled, or the user logs out.
 *
 * Note: this is polling, not push. For reminders when nothing is booked for today, or after the phone
 * has force-stopped the app, you would need Firebase Cloud Messaging.
 */
public class QueueWatcherService extends Service {

    private static final String CH_ALERTS = "mediq_alerts";
    private static final String CH_TRACK = "mediq_tracking";
    private static final int FGS_ID = 7001;
    private static final long POLL_MS = 15_000L;
    private static final long APPT_CHECK_MS = 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String uid;
    private String lastSeen;          // created_at of the newest notification already shown
    private long lastApptCheck = 0;
    private boolean running = false;

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (!running) return;
            poll();
            handler.postDelayed(this, POLL_MS);
        }
    };

    public static void start(Context c) {
        ContextCompat.startForegroundService(c, new Intent(c, QueueWatcherService.class));
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, QueueWatcherService.class));
    }

    public static boolean canNotify(Context c) {
        return Build.VERSION.SDK_INT < 33
                || ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        uid = SessionManager.getInstance(this).getUserId();
        if (uid == null || uid.isEmpty()) { stopSelf(); return START_NOT_STICKY; }

        createChannels();
        Notification n = new NotificationCompat.Builder(this, CH_TRACK)
                .setSmallIcon(R.drawable.ic_stat_bell)
                .setContentTitle("MediQ is tracking your queue")
                .setContentText("We'll alert you when your turn is near")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(openAppIntent())
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(this, FGS_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(FGS_ID, n);
        }

        if (!running) {
            running = true;
            lastSeen = nowUtcIso();      // only alert about things that happen from now on
            lastApptCheck = System.currentTimeMillis();
            handler.postDelayed(loop, POLL_MS);
        }
        return START_NOT_STICKY;
    }

    private void poll() {
        if (!canNotify(this)) { stopSelf(); return; }
        String current = SessionManager.getInstance(this).getUserId();
        if (current == null || !current.equals(uid)) { stopSelf(); return; }

        SupabaseClient.getInstance().get(
                "notifications?user_id=eq." + uid + "&read_at=is.null&created_at=gt." + Uri.encode(lastSeen)
                        + "&select=id,type,title,message,created_at&order=created_at.asc&limit=10",
                new SupabaseClient.Callback() {
                    @Override public void onSuccess(String json) {
                        try {
                            JSONArray arr = new JSONArray(json);
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);
                                show(o.optString("id"), o.optString("title"), o.optString("message"));
                                lastSeen = o.optString("created_at", lastSeen);
                            }
                        } catch (Exception ignored) { }
                    }
                    @Override public void onError(Exception e) { /* try again next cycle */ }
                });

        if (System.currentTimeMillis() - lastApptCheck >= APPT_CHECK_MS) {
            lastApptCheck = System.currentTimeMillis();
            stopIfNoVisitToday();
        }
    }

    private void stopIfNoVisitToday() {
        SupabaseClient.getInstance().get(
                "appointments?patient_id=eq." + uid + "&appointment_date=eq." + todayIst()
                        + "&status=in.(pending,confirmed)&select=id&limit=1",
                new SupabaseClient.Callback() {
                    @Override public void onSuccess(String json) {
                        try { if (new JSONArray(json).length() == 0) stopSelf(); } catch (Exception ignored) { }
                    }
                    @Override public void onError(Exception e) { }
                });
    }

    private void show(String id, String title, String message) {
        if (!canNotify(this)) return;
        Notification n = new NotificationCompat.Builder(this, CH_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_bell)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent())
                .build();
        try {
            NotificationManagerCompat.from(this).notify(id.hashCode(), n);
        } catch (SecurityException ignored) { }
    }

    private PendingIntent openAppIntent() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH_ALERTS, "Queue & appointment alerts", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(CH_TRACK, "Queue tracking", NotificationManager.IMPORTANCE_LOW));
    }

    private static String nowUtcIso() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    private static String todayIst() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
        return f.format(new Date());
    }

    // Android 14/15 end "data sync" services after a time limit; stop cleanly instead of crashing.
    public void onTimeout(int startId) { stopSelf(); }
    public void onTimeout(int startId, int fgsType) { stopSelf(); }

    @Override public void onDestroy() { running = false; handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
