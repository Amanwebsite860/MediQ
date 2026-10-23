package com.example.mediq.util;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import java.util.Locale;

public final class UiUtils {
    private UiUtils() { }

    public static int dp(View v, int dp) {
        return Math.round(dp * v.getResources().getDisplayMetrics().density);
    }

    public static int parseColor(String hex, int fallback) {
        try { return Color.parseColor(hex); } catch (Exception e) { return fallback; }
    }

    public static GradientDrawable gradient(String start, String end, float radiusPx) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{parseColor(start, 0xFF1976D2), parseColor(end, 0xFF42A5F5)});
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static GradientDrawable oval(String hex) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(parseColor(hex, 0xFFEAF2FF));
        return g;
    }

    /** "14:30:00" -> "2:30 PM" */
    public static String formatTime(String hhmmss) {
        if (hhmmss == null || hhmmss.length() < 5) return "";
        try {
            int h = Integer.parseInt(hhmmss.substring(0, 2));
            String m = hhmmss.substring(3, 5);
            String ap = h >= 12 ? "PM" : "AM";
            int h12 = h % 12 == 0 ? 12 : h % 12;
            return String.format(Locale.US, "%d:%s %s", h12, m, ap);
        } catch (Exception e) {
            return hhmmss;
        }
    }

    /** "2026-10-21" -> "21 Oct" */
    public static String formatShortDate(String iso) {
        if (iso == null || iso.length() < 10) return "";
        try {
            String[] mon = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
            int m = Integer.parseInt(iso.substring(5, 7));
            int d = Integer.parseInt(iso.substring(8, 10));
            return d + " " + mon[m - 1];
        } catch (Exception e) {
            return iso;
        }
    }

    /** "2026-10-21" -> "Today" / "Tomorrow" / "Fri, 24 Oct" (device-local calendar). */
    public static String friendlyDate(String iso) {
        if (iso == null || iso.length() < 10) return iso == null ? "" : iso;
        try {
            java.text.SimpleDateFormat in = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
            java.util.Date d = in.parse(iso.substring(0, 10));
            String day = in.format(new java.util.Date());
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.add(java.util.Calendar.DAY_OF_YEAR, 1);
            if (iso.startsWith(day)) return "Today";
            if (iso.startsWith(in.format(c.getTime()))) return "Tomorrow";
            return new java.text.SimpleDateFormat("EEE, d MMM", Locale.US).format(d);
        } catch (Exception e) {
            return iso;
        }
    }

    /** Haversine distance in km. */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
