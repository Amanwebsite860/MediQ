package com.example.mediq.ui.staff;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.example.mediq.R;
import com.example.mediq.data.remote.SessionManager;
import com.example.mediq.data.remote.SupabaseClient;
import com.example.mediq.util.ToastUtils;
import com.example.mediq.util.UiUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Receptionist dashboard: hospital profile + photos, live queues (call next), today's appointments
 * (check patients in) and patient reviews (reply).
 */
public class ReceptionistHomeActivity extends AppCompatActivity {

    private static final long REFRESH_MS = 10000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() { if (hospitalId != null) loadOperations(); handler.postDelayed(this, REFRESH_MS); }
    };

    private SwipeRefreshLayout swipe;
    private TextView tvName, tvMeta;
    private ImageView ivCover;
    private LinearLayout layoutQueues, layoutAppointments, layoutReviews;
    private String hospitalId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receptionist_home);
        swipe = findViewById(R.id.swipeStaff);
        tvName = findViewById(R.id.tvHospName);
        tvMeta = findViewById(R.id.tvHospMeta);
        ivCover = findViewById(R.id.ivCover);
        layoutQueues = findViewById(R.id.layoutQueues);
        layoutAppointments = findViewById(R.id.layoutAppointments);
        layoutReviews = findViewById(R.id.layoutReviews);

        findViewById(R.id.btnLogout).setOnClickListener(v -> StaffUtils.logout(this));
        StaffUtils.bindBell(this, findViewById(R.id.btnBell));
        findViewById(R.id.btnEditHospital).setOnClickListener(v -> {
            if (hospitalId == null) return;
            Intent i = new Intent(this, HospitalFormActivity.class);
            i.putExtra(HospitalFormActivity.EXTRA_MODE, HospitalFormActivity.MODE_EDIT);
            i.putExtra(HospitalFormActivity.EXTRA_HOSPITAL_ID, hospitalId);
            startActivity(i);
        });
        swipe.setOnRefreshListener(this::loadAll);
    }

    @Override protected void onResume() {
        super.onResume();
        StaffUtils.refreshBell(this, findViewById(R.id.btnBell));
        loadAll();
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, REFRESH_MS);
    }

    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(ticker); }

    private void loadAll() {
        String uid = SessionManager.getInstance(this).getUserId();
        SupabaseClient.getInstance().get("profiles?id=eq." + uid + "&select=hospital_id", new SupabaseClient.Callback() {
            @Override public void onSuccess(String json) {
                try {
                    JSONArray arr = new JSONArray(json);
                    if (arr.length() > 0) hospitalId = SupabaseClient.optStringSafe(arr.getJSONObject(0), "hospital_id");
                } catch (Exception ignored) { }
                if (hospitalId == null) { swipe.setRefreshing(false); tvName.setText("No hospital linked"); return; }
                loadHospital();
                loadOperations();
            }
            @Override public void onError(Exception e) { swipe.setRefreshing(false); }
        });
    }

    private void loadHospital() {
        SupabaseClient.getInstance().get("hospitals?id=eq." + hospitalId + "&select=name,city,image_url,rating,review_count",
                new SupabaseClient.Callback() {
                    @Override public void onSuccess(String json) {
                        try {
                            JSONArray arr = new JSONArray(json);
                            if (arr.length() == 0) return;
                            JSONObject h = arr.getJSONObject(0);
                            tvName.setText(h.optString("name"));
                            tvMeta.setText(String.format(java.util.Locale.US, "%s  ·  ★ %.1f (%d reviews)",
                                    h.optString("city", ""), h.optDouble("rating", 0), h.optInt("review_count", 0)));
                            String img = SupabaseClient.optStringSafe(h, "image_url");
                            if (img != null) Glide.with(ReceptionistHomeActivity.this).load(img).into(ivCover);
                        } catch (Exception ignored) { }
                    }
                    @Override public void onError(Exception e) { }
                });
    }

    private void loadOperations() {
        String today = StaffUtils.todayIst();
        loadQueues(today);
        loadAppointments(today);
        loadReviews();
    }

    // ------------------------------------------------------------------ queues
    private void loadQueues(String today) {
        SupabaseClient.getInstance().get("queues?hospital_id=eq." + hospitalId + "&queue_date=eq." + today
                + "&select=id,current_token,status,doctors(name)", new SupabaseClient.Callback() {
            @Override public void onSuccess(String json) {
                swipe.setRefreshing(false);
                try {
                    JSONArray arr = new JSONArray(json);
                    layoutQueues.removeAllViews();
                    if (arr.length() == 0) { layoutQueues.addView(note("No queues started today yet")); return; }
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject q = arr.getJSONObject(i);
                        JSONObject d = q.optJSONObject("doctors");
                        View row = inflateRow(layoutQueues);
                        ((TextView) row.findViewById(R.id.tvRowTitle)).setText(d != null ? d.optString("name") : "Doctor");
                        String cur = SupabaseClient.optStringSafe(q, "current_token");
                        ((TextView) row.findViewById(R.id.tvRowSub)).setText(cur != null ? "Now serving " + cur : "No patient inside");
                        MaterialButton b = row.findViewById(R.id.btnRowAction);
                        b.setVisibility(View.VISIBLE);
                        b.setText("Call next");
                        final String qid = q.optString("id");
                        b.setOnClickListener(v -> callNext(qid));
                        layoutQueues.addView(row);
                    }
                } catch (Exception ignored) { }
            }
            @Override public void onError(Exception e) { swipe.setRefreshing(false); }
        });
    }

    private void callNext(String queueId) {
        try {
            JSONObject body = new JSONObject();
            body.put("p_queue_id", queueId);
            SupabaseClient.getInstance().rpc("advance_queue", body, new SupabaseClient.Callback() {
                @Override public void onSuccess(String r) {
                    try {
                        JSONObject o = new JSONObject(r);
                        if (!o.optBoolean("success", false)) ToastUtils.show(ReceptionistHomeActivity.this, o.optString("error", "Couldn't advance"));
                        else if (o.has("message")) ToastUtils.show(ReceptionistHomeActivity.this, o.optString("message"));
                        else ToastUtils.show(ReceptionistHomeActivity.this, "Now serving " + o.optString("current_token"));
                    } catch (Exception ignored) { }
                    loadOperations();
                }
                @Override public void onError(Exception e) { ToastUtils.show(ReceptionistHomeActivity.this, "Couldn't advance the queue"); }
            });
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ appointments
    private void loadAppointments(String today) {
        SupabaseClient.getInstance().get("appointments?hospital_id=eq." + hospitalId + "&appointment_date=eq." + today
                + "&status=in.(pending,confirmed)&select=id,appointment_time,doctors(name),queue_entries(id,token_number,status)"
                + "&order=appointment_time", new SupabaseClient.Callback() {
            @Override public void onSuccess(String json) {
                try {
                    JSONArray arr = new JSONArray(json);
                    layoutAppointments.removeAllViews();
                    if (arr.length() == 0) { layoutAppointments.addView(note("No appointments today")); return; }
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject a = arr.getJSONObject(i);
                        JSONObject d = a.optJSONObject("doctors");
                        JSONArray qe = a.optJSONArray("queue_entries");
                        String token = "—", status = "", entryId = null;
                        if (qe != null && qe.length() > 0) {
                            JSONObject e = qe.getJSONObject(0);
                            token = e.optString("token_number", "—");
                            status = e.optString("status", "");
                            entryId = SupabaseClient.optStringSafe(e, "id");
                        }
                        View row = inflateRow(layoutAppointments);
                        ((TextView) row.findViewById(R.id.tvRowTitle)).setText(
                                UiUtils.formatTime(a.optString("appointment_time")) + "  ·  Token " + token);
                        ((TextView) row.findViewById(R.id.tvRowSub)).setText((d != null ? d.optString("name") : "") + "  ·  " + pretty(status));
                        if ("booked".equals(status) && entryId != null) {
                            MaterialButton b = row.findViewById(R.id.btnRowAction);
                            b.setVisibility(View.VISIBLE);
                            b.setText("Check in");
                            final String eid = entryId;
                            b.setOnClickListener(v -> checkIn(eid));
                        }
                        layoutAppointments.addView(row);
                    }
                } catch (Exception ignored) { }
            }
            @Override public void onError(Exception e) { }
        });
    }

    private void checkIn(String entryId) {
        try {
            JSONObject body = new JSONObject();
            body.put("status", "checked_in");
            body.put("checked_in_at", StaffUtils.nowIso());
            SupabaseClient.getInstance().patch("queue_entries?id=eq." + entryId, body, new SupabaseClient.Callback() {
                @Override public void onSuccess(String r) { ToastUtils.show(ReceptionistHomeActivity.this, "Patient checked in"); loadOperations(); }
                @Override public void onError(Exception e) { ToastUtils.show(ReceptionistHomeActivity.this, "Couldn't check in"); }
            });
        } catch (Exception ignored) { }
    }

    private String pretty(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.replace('_', ' ').substring(0, 1).toUpperCase() + s.replace('_', ' ').substring(1);
    }

    // ------------------------------------------------------------------ reviews
    private void loadReviews() {
        SupabaseClient.getInstance().get("hospital_reviews?hospital_id=eq." + hospitalId
                + "&select=id,patient_name,rating,comment,reply&order=created_at.desc&limit=15", new SupabaseClient.Callback() {
            @Override public void onSuccess(String json) {
                try {
                    JSONArray arr = new JSONArray(json);
                    layoutReviews.removeAllViews();
                    if (arr.length() == 0) { layoutReviews.addView(note("No reviews yet")); return; }
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject r = arr.getJSONObject(i);
                        View row = inflateRow(layoutReviews);
                        ((TextView) row.findViewById(R.id.tvRowTitle)).setText(String.format(java.util.Locale.US, "★ %d  ·  %s",
                                r.optInt("rating"), r.optString("patient_name", "Patient")));
                        ((TextView) row.findViewById(R.id.tvRowSub)).setText(r.optString("comment", ""));
                        String reply = SupabaseClient.optStringSafe(r, "reply");
                        TextView extra = row.findViewById(R.id.tvRowExtra);
                        if (reply != null) { extra.setVisibility(View.VISIBLE); extra.setText("↪ Your reply: " + reply); }
                        MaterialButton b = row.findViewById(R.id.btnRowAction);
                        b.setVisibility(View.VISIBLE);
                        b.setText(reply == null ? "Reply" : "Edit");
                        final String id = r.optString("id");
                        b.setOnClickListener(v -> replyDialog(id, reply));
                        layoutReviews.addView(row);
                    }
                } catch (Exception ignored) { }
            }
            @Override public void onError(Exception e) { }
        });
    }

    private void replyDialog(String reviewId, String existing) {
        EditText et = new EditText(this);
        et.setHint("Write a reply patients will see");
        et.setMaxLines(4);
        if (existing != null) et.setText(existing);
        int pad = UiUtils.dp(et, 20);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(pad, pad / 2, pad, 0);
        wrap.addView(et, new LinearLayout.LayoutParams(-1, -2));
        new MaterialAlertDialogBuilder(this).setTitle("Reply to review").setView(wrap)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Post", (d, w) -> {
                    try {
                        JSONObject body = new JSONObject();
                        body.put("reply", et.getText().toString().trim());
                        SupabaseClient.getInstance().patch("hospital_reviews?id=eq." + reviewId, body, new SupabaseClient.Callback() {
                            @Override public void onSuccess(String r) { loadReviews(); }
                            @Override public void onError(Exception e) { ToastUtils.show(ReceptionistHomeActivity.this, "Couldn't post reply"); }
                        });
                    } catch (Exception ignored) { }
                }).show();
    }

    // ------------------------------------------------------------------ helpers
    private View inflateRow(LinearLayout parent) {
        return LayoutInflater.from(this).inflate(R.layout.item_staff_row, parent, false);
    }

    private TextView note(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(getColor(R.color.colorTextSecondary));
        t.setTextSize(14);
        return t;
    }
}
