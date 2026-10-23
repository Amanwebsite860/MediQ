package com.example.mediq.util;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.example.mediq.R;
import com.google.android.material.snackbar.Snackbar;

/**
 * Modern notification utility that displays clean Material Snackbars without
 * the Android 12+ system icon, and with extended duration for easy readability.
 */
public class ToastUtils {

    /**
     * Displays a notification with extended duration (3.5s) and no Android logo.
     */
    public static void show(Activity activity, String message) {
        if (activity == null || activity.isFinishing() || message == null || message.trim().isEmpty()) {
            return;
        }

        activity.runOnUiThread(() -> {
            View rootView = activity.findViewById(android.R.id.content);
            if (rootView != null) {
                Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);
                snackbar.setDuration(4000); // 4 seconds for easy reading
                snackbar.show();
            } else {
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    public static void show(Context context, String message) {
        if (context instanceof Activity) {
            show((Activity) context, message);
        } else if (context != null && message != null && !message.trim().isEmpty()) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
    }
}
