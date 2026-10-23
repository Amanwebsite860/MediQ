package com.example.mediq.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public final class ImageUtils {
    private ImageUtils() { }

    /** Decode a picked image, scale so the longest side is <= maxPx, return JPEG bytes (or null on failure). */
    public static byte[] toJpeg(Context ctx, Uri src, int maxPx, int quality) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = ctx.getContentResolver().openInputStream(src)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            while (bounds.outWidth / sample > maxPx * 2 || bounds.outHeight / sample > maxPx * 2) sample *= 2;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bmp;
            try (InputStream in = ctx.getContentResolver().openInputStream(src)) {
                bmp = BitmapFactory.decodeStream(in, null, opts);
            }
            if (bmp == null) return null;

            float scale = Math.min(1f, (float) maxPx / Math.max(bmp.getWidth(), bmp.getHeight()));
            if (scale < 1f) {
                bmp = Bitmap.createScaledBitmap(bmp, Math.round(bmp.getWidth() * scale),
                        Math.round(bmp.getHeight() * scale), true);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
