package com.example.mediq.ui.staff;

import android.app.Activity;
import android.content.Intent;

import com.example.mediq.data.remote.SessionManager;
import com.example.mediq.ui.auth.LoginActivity;

import android.widget.TextView;

import com.example.mediq.data.remote.SupabaseClient;
import com.example.mediq.ui.patient.NotificationsActivity;

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class StaffUtils {
    private StaffUtils() { }

    /** Today's date in India time, matching how the database creates daily queues. */
    static String todayIst() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
        return f.format(new Date());
    }

    static String nowIso() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    static void logout(Activity a) {
        com.example.mediq.service.QueueWatcherService.stop(a);
        SessionManager.getInstance(a).clearSession();
        Intent i = new Intent(a, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        a.startActivity(i);
        a.finish();
    }

    /** Wires the 🔔 button on a staff dashboard to the notification inbox and shows the unread count. */
    static void bindBell(Activity a, TextView bell) {
        if (bell == null) return;
        bell.setOnClickListener(v -> a.startActivity(new Intent(a, NotificationsActivity.class)));
    }

    static void refreshBell(Activity a, TextView bell) {
        if (bell == null) return;
        String uid = SessionManager.getInstance(a).getUserId();
        if (uid == null || uid.isEmpty()) return;
        SupabaseClient.getInstance().get("notifications?user_id=eq." + uid + "&read_at=is.null&select=id",
                new SupabaseClient.Callback() {
                    @Override public void onSuccess(String json) {
                        try {
                            int n = new JSONArray(json).length();
                            bell.setText(n == 0 ? "🔔" : "🔔 " + (n > 9 ? "9+" : String.valueOf(n)));
                        } catch (Exception ignored) { }
                    }
                    @Override public void onError(Exception e) { }
                });
    }
}
