package com.example.mediq.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.SparseArray;

/**
 * Draws the map markers in code (no image files needed):
 *  - PARTNER   : blue pin with a white cross  (hospital listed on MediQ, bookable)
 *  - EMERGENCY : red pin with a white cross   (listed on MediQ AND open 24x7)
 *  - CLINIC    : amber round dot              (OpenStreetMap clinic / hospital)
 *  - PHARMACY  : green round dot with "P"     (OpenStreetMap pharmacy)
 * The selected marker is drawn bigger so it stands out from the rest.
 */
public final class MapIcons {
    public static final int PARTNER = 0, EMERGENCY = 1, CLINIC = 2, PHARMACY = 3;

    private static final SparseArray<Drawable> CACHE = new SparseArray<>();

    private MapIcons() { }

    public static Drawable get(Context c, int kind, boolean selected) {
        int key = kind * 2 + (selected ? 1 : 0);
        Drawable d = CACHE.get(key);
        if (d == null) {
            float density = c.getResources().getDisplayMetrics().density;
            Bitmap b = (kind == PARTNER || kind == EMERGENCY)
                    ? pin(density, kind == EMERGENCY ? 0xFFE53935 : 0xFF1976D2, selected)
                    : dot(density, kind == PHARMACY ? 0xFF1BA672 : 0xFFF59E0B, kind == PHARMACY, selected);
            d = new BitmapDrawable(c.getResources(), b);
            CACHE.put(key, d);
        }
        return d;
    }

    private static Bitmap pin(float dp, int color, boolean selected) {
        float w = (selected ? 46 : 34) * dp;
        float h = w * 1.3f;
        Bitmap bmp = Bitmap.createBitmap(Math.round(w + 4 * dp), Math.round(h + 4 * dp), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        float cx = bmp.getWidth() / 2f, r = w / 2f - 2 * dp, cy = 2 * dp + r + 1 * dp;

        Path path = new Path();
        path.addCircle(cx, cy, r, Path.Direction.CW);
        path.moveTo(cx - r * 0.62f, cy + r * 0.78f);
        path.lineTo(cx, h + 1 * dp);
        path.lineTo(cx + r * 0.62f, cy + r * 0.78f);
        path.close();

        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(0x33000000);
        shadow.setShadowLayer(3 * dp, 0, 1.5f * dp, 0x66000000);
        cv.drawPath(path, shadow);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(color);
        cv.drawPath(path, fill);

        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth((selected ? 2.5f : 2f) * dp);
        ring.setColor(Color.WHITE);
        cv.drawPath(path, ring);

        cross(cv, cx, cy, r * 0.55f, dp);
        return bmp;
    }

    private static Bitmap dot(float dp, int color, boolean pharmacy, boolean selected) {
        float d = (selected ? 32 : 24) * dp;
        Bitmap bmp = Bitmap.createBitmap(Math.round(d + 4 * dp), Math.round(d + 4 * dp), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        float c = bmp.getWidth() / 2f, r = d / 2f;

        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(0x33000000);
        shadow.setShadowLayer(2.5f * dp, 0, 1 * dp, 0x66000000);
        cv.drawCircle(c, c, r, shadow);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(color);
        cv.drawCircle(c, c, r, fill);

        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2 * dp);
        ring.setColor(Color.WHITE);
        cv.drawCircle(c, c, r, ring);

        if (pharmacy) {
            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
            t.setColor(Color.WHITE);
            t.setTextAlign(Paint.Align.CENTER);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setTextSize(d * 0.55f);
            cv.drawText("P", c, c + d * 0.2f, t);
        } else {
            cross(cv, c, c, r * 0.55f, dp);
        }
        return bmp;
    }

    private static void cross(Canvas cv, float cx, float cy, float arm, float dp) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        float t = arm * 0.36f;
        cv.drawRoundRect(new RectF(cx - arm, cy - t, cx + arm, cy + t), t / 2, t / 2, p);
        cv.drawRoundRect(new RectF(cx - t, cy - arm, cx + t, cy + arm), t / 2, t / 2, p);
    }
}
