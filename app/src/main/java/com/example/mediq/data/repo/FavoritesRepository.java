package com.example.mediq.data.repo;

import android.content.Context;

import com.example.mediq.data.remote.SessionManager;
import com.example.mediq.data.remote.SupabaseClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * In-memory cache of the signed-in user's saved doctors / hospitals, backed by the
 * user_favorites table (column: user_id). Callbacks run on the main thread.
 */
public class FavoritesRepository {

    public interface Done { void onDone(boolean nowFavorite, String errorOrNull); }

    private static FavoritesRepository instance;
    private final Set<String> doctorIds = new HashSet<>();
    private final Set<String> hospitalIds = new HashSet<>();

    public static synchronized FavoritesRepository get() {
        if (instance == null) instance = new FavoritesRepository();
        return instance;
    }

    public boolean isDoctorFav(String id) { return id != null && doctorIds.contains(id); }
    public boolean isHospitalFav(String id) { return id != null && hospitalIds.contains(id); }

    public java.util.Set<String> idsOf(boolean doctor) { return doctor ? doctorIds : hospitalIds; }

    public void clear() { doctorIds.clear(); hospitalIds.clear(); }

    /** Refresh the cache from the server. */
    public void load(Context ctx, Runnable done) {
        String uid = SessionManager.getInstance(ctx).getUserId();
        if (uid == null || uid.isEmpty()) { if (done != null) done.run(); return; }
        SupabaseClient.getInstance().get("user_favorites?user_id=eq." + uid + "&select=doctor_id,hospital_id",
                new SupabaseClient.Callback() {
                    @Override public void onSuccess(String r) {
                        try {
                            JSONArray arr = new JSONArray(r);
                            doctorIds.clear();
                            hospitalIds.clear();
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);
                                String d = SupabaseClient.optStringSafe(o, "doctor_id");
                                String h = SupabaseClient.optStringSafe(o, "hospital_id");
                                if (d != null) doctorIds.add(d);
                                if (h != null) hospitalIds.add(h);
                            }
                        } catch (Exception ignored) { }
                        if (done != null) done.run();
                    }
                    @Override public void onError(Exception e) { if (done != null) done.run(); }
                });
    }

    public void toggleDoctor(Context ctx, String doctorId, Done cb) { toggle(ctx, "doctor_id", doctorId, doctorIds, cb); }
    public void toggleHospital(Context ctx, String hospitalId, Done cb) { toggle(ctx, "hospital_id", hospitalId, hospitalIds, cb); }

    private void toggle(Context ctx, String column, String id, Set<String> cache, Done cb) {
        String uid = SessionManager.getInstance(ctx).getUserId();
        if (uid == null || uid.isEmpty() || id == null) {
            if (cb != null) cb.onDone(false, "Sign in to save");
            return;
        }
        final boolean wasFav = cache.contains(id);
        // Optimistic update so the heart reacts instantly
        if (wasFav) cache.remove(id); else cache.add(id);

        SupabaseClient.Callback result = new SupabaseClient.Callback() {
            @Override public void onSuccess(String r) { if (cb != null) cb.onDone(!wasFav, null); }
            @Override public void onError(Exception e) {
                if (wasFav) cache.add(id); else cache.remove(id); // roll back
                if (cb != null) cb.onDone(wasFav, e.getMessage());
            }
        };

        if (wasFav) {
            SupabaseClient.getInstance().delete("user_favorites?user_id=eq." + uid + "&" + column + "=eq." + id, result);
        } else {
            try {
                JSONObject body = new JSONObject();
                body.put("user_id", uid);
                body.put(column, id);
                SupabaseClient.getInstance().post("user_favorites", body, result);
            } catch (Exception e) {
                cache.remove(id);
                if (cb != null) cb.onDone(false, e.getMessage());
            }
        }
    }
}
