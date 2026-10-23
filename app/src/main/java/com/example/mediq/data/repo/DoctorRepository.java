package com.example.mediq.data.repo;

import com.example.mediq.R;
import com.example.mediq.data.model.Doctor;
import com.example.mediq.data.remote.SupabaseClient;
import com.example.mediq.util.UiUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads doctors (with their hospital name) and decorates each with its next free slot
 * from the doctor_next_slot view (created by sql/05_feature_upgrade.sql).
 * If that view doesn't exist yet, doctors still load — just without the slot text.
 */
public final class DoctorRepository {
    private DoctorRepository() { }

    public interface Result {
        void onResult(List<Doctor> doctors);
        void onError(Exception e);
    }

    /** @param filterQuery extra PostgREST filters, e.g. "&hospital_id=eq.123" (may be empty) */
    public static void load(String filterQuery, Result cb) {
        String q = "doctors?select=*,hospitals(name)&active=eq.true&order=rating.desc"
                + (filterQuery == null ? "" : filterQuery);
        SupabaseClient.getInstance().get(q, new SupabaseClient.Callback() {
            @Override public void onSuccess(String json) {
                final List<Doctor> list;
                try {
                    list = parse(new JSONArray(json));
                } catch (Exception e) {
                    cb.onError(e);
                    return;
                }
                attachSlots(list, cb);
            }
            @Override public void onError(Exception e) { cb.onError(e); }
        });
    }

    private static void attachSlots(List<Doctor> list, Result cb) {
        SupabaseClient.getInstance().get("doctor_next_slot?select=doctor_id,schedule_date,slot_time,is_today",
                new SupabaseClient.Callback() {
                    @Override public void onSuccess(String json) {
                        try {
                            Map<String, JSONObject> byDoc = new HashMap<>();
                            JSONArray arr = new JSONArray(json);
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);
                                byDoc.put(o.optString("doctor_id"), o);
                            }
                            String tomorrow = tomorrowIso();
                            for (Doctor d : list) {
                                JSONObject o = byDoc.get(d.getId());
                                if (o == null) {
                                    d.setAvailableToday(false);
                                    d.setNextSlotText(null);
                                    continue;
                                }
                                boolean today = o.optBoolean("is_today", false);
                                String time = UiUtils.formatTime(o.optString("slot_time", ""));
                                String date = o.optString("schedule_date", "");
                                d.setAvailableToday(today);
                                if (today) d.setNextSlotText("Today \u00B7 " + time);
                                else if (date.equals(tomorrow)) d.setNextSlotText("Tomorrow \u00B7 " + time);
                                else d.setNextSlotText("Next: " + UiUtils.formatShortDate(date));
                            }
                        } catch (Exception ignored) { }
                        cb.onResult(list);
                    }
                    @Override public void onError(Exception e) { cb.onResult(list); }
                });
    }

    public static List<Doctor> parse(JSONArray arr) throws Exception {
        List<Doctor> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            String specialty = SupabaseClient.optStringSafe(o, "specialty");
            String sp = specialty == null ? "" : specialty.toLowerCase(Locale.ROOT);
            int avatarRes = (sp.contains("derma") || sp.contains("gyne"))
                    ? R.drawable.ic_doctor_female_avatar : R.drawable.ic_doctor_male_avatar;
            String hospitalName = null;
            JSONObject h = o.optJSONObject("hospitals");
            if (h != null) hospitalName = SupabaseClient.optStringSafe(h, "name");

            Doctor d = new Doctor(
                    SupabaseClient.optStringSafe(o, "id"),
                    SupabaseClient.optStringSafe(o, "name"),
                    specialty,
                    SupabaseClient.optStringSafe(o, "bio"),
                    o.optInt("experience_years", 5),
                    (int) o.optDouble("consultation_fee", 300),
                    o.optInt("consultation_duration_min", 15),
                    SupabaseClient.optStringSafe(o, "avatar_url"),
                    avatarRes,
                    o.optDouble("rating", 0),
                    o.optInt("review_count", 0),
                    hospitalName,
                    SupabaseClient.optStringSafe(o, "hospital_id"),
                    true);
            d.setQualification(SupabaseClient.optStringSafe(o, "qualification"));
            out.add(d);
        }
        return out;
    }

    private static String tomorrowIso() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, 1);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }
}
