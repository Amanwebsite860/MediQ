package com.example.mediq.ui.staff;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.mediq.R;
import com.example.mediq.data.remote.SessionManager;
import com.example.mediq.data.remote.SupabaseClient;
import com.example.mediq.util.ToastUtils;

import org.json.JSONArray;
import org.json.JSONObject;

/** Doctor dashboard: today's queue with a one-tap "Call next patient". */
public class DoctorHomeActivity extends AppCompatActivity {

    private static final long REFRESH_MS = 8000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() { if (queueId != null) loadEntries(); handler.postDelayed(this, REFRESH_MS); }
    };

    private SwipeRefreshLayout swipe;
    private TextView tvName, tvMeta, tvNow, tvWaiting, tvEmpty, tvDocGenderBadge, tvDocPhoneBadge;
    private ImageView ivDocAvatar;
    private LinearLayout layoutEntries;
    private View btnCallNext;
    private String doctorId, queueId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_home);
        swipe = findViewById(R.id.swipeStaff);
        tvName = findViewById(R.id.tvDocName);
        tvMeta = findViewById(R.id.tvDocMeta);
        tvDocGenderBadge = findViewById(R.id.tvDocGenderBadge);
        tvDocPhoneBadge = findViewById(R.id.tvDocPhoneBadge);
        ivDocAvatar = findViewById(R.id.ivDocAvatar);
        tvNow = findViewById(R.id.tvNowServing);
        tvWaiting = findViewById(R.id.tvWaitingCount);
        tvEmpty = findViewById(R.id.tvDocEmpty);
        layoutEntries = findViewById(R.id.layoutEntries);
        btnCallNext = findViewById(R.id.btnCallNext);

        findViewById(R.id.btnLogout).setOnClickListener(v -> StaffUtils.logout(this));
        StaffUtils.bindBell(this, findViewById(R.id.btnBell));
        View.OnClickListener openEditAction = v ->
                com.example.mediq.ui.common.ProfileEditSheet.show(this, "doctor", this::loadDoctor);
        View btnEdit = findViewById(R.id.btnEditDoctorProfile);
        if (btnEdit != null) btnEdit.setOnClickListener(openEditAction);
        if (ivDocAvatar != null) ivDocAvatar.setOnClickListener(openEditAction);

        btnCallNext.setOnClickListener(v -> callNext());
        swipe.setOnRefreshListener(this::loadDoctor);
    }

    @Override protected void onResume() {
        super.onResume();
        StaffUtils.refreshBell(this, findViewById(R.id.btnBell));
        loadDoctor();
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, REFRESH_MS);
    }

    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(ticker); }

    private void loadDoctor() {
        String uid = SessionManager.getInstance(this).getUserId();
        updateAvatar(SessionManager.getInstance(this).getUserAvatarUrl());
        bindPersonalBadges();
        SupabaseClient.getInstance().get("doctors?profile_id=eq." + uid + "&select=id,name,specialty,avatar_url,hospitals(name),profiles(gender,phone,blood_group,emergency_contact)",
                new SupabaseClient.Callback() {
                    @Override public void onSuccess(String json) {
                        try {
                            JSONArray arr = new JSONArray(json);
                            if (arr.length() == 0) { swipe.setRefreshing(false); tvName.setText("Doctor profile not found"); return; }
                            JSONObject d = arr.getJSONObject(0);
                            doctorId = d.optString("id");
                            tvName.setText(d.optString("name"));

                            JSONObject p = d.optJSONObject("profiles");
                            if (p != null) {
                                String g = SupabaseClient.optStringSafe(p, "gender");
                                if (g != null && !g.isEmpty()) SessionManager.getInstance(DoctorHomeActivity.this).setUserGender(g);
                                String ph = SupabaseClient.optStringSafe(p, "phone");
                                if (ph != null && !ph.isEmpty()) SessionManager.getInstance(DoctorHomeActivity.this).setUserPhone(ph);
                                String bg = SupabaseClient.optStringSafe(p, "blood_group");
                                if (bg != null && !bg.isEmpty()) SessionManager.getInstance(DoctorHomeActivity.this).setUserBloodGroup(bg);
                                String em = SupabaseClient.optStringSafe(p, "emergency_contact");
                                if (em != null && !em.isEmpty()) SessionManager.getInstance(DoctorHomeActivity.this).setUserEmergencyContact(em);
                            }
                            bindPersonalBadges();

                            String avatar = SupabaseClient.optStringSafe(d, "avatar_url");
                            if (avatar != null && !avatar.isEmpty()) {
                                SessionManager.getInstance(DoctorHomeActivity.this).setUserAvatarUrl(avatar);
                                updateAvatar(avatar);
                            } else {
                                fetchProfileAvatar(uid);
                            }
                            JSONObject h = d.optJSONObject("hospitals");
                            tvMeta.setText(d.optString("specialty") + (h != null ? "  ·  " + h.optString("name") : ""));
                            loadQueue();
                        } catch (Exception e) { swipe.setRefreshing(false); }
                    }
                    @Override public void onError(Exception e) { swipe.setRefreshing(false); }
                });
    }

    private void bindPersonalBadges() {
        SessionManager sm = SessionManager.getInstance(this);
        String gender = sm.getUserGender();
        if (tvDocGenderBadge != null) {
            if (gender != null && !gender.trim().isEmpty() && !"Not set".equalsIgnoreCase(gender)) {
                tvDocGenderBadge.setVisibility(View.VISIBLE);
                tvDocGenderBadge.setText(gender);
            } else {
                tvDocGenderBadge.setVisibility(View.GONE);
            }
        }
        String phone = sm.getUserPhone();
        if (tvDocPhoneBadge != null) {
            if (phone != null && !phone.trim().isEmpty()) {
                tvDocPhoneBadge.setVisibility(View.VISIBLE);
                tvDocPhoneBadge.setText("📞 " + phone);
            } else {
                tvDocPhoneBadge.setVisibility(View.GONE);
            }
        }
    }

    private void fetchProfileAvatar(String uid) {
        SupabaseClient.getInstance().get("profiles?id=eq." + uid + "&select=avatar_url,gender", new SupabaseClient.Callback() {
            @Override
            public void onSuccess(String json) {
                try {
                    JSONArray arr = new JSONArray(json);
                    if (arr.length() > 0) {
                        JSONObject obj = arr.getJSONObject(0);
                        String url = SupabaseClient.optStringSafe(obj, "avatar_url");
                        String g = SupabaseClient.optStringSafe(obj, "gender");
                        if (g != null && !g.isEmpty()) {
                            SessionManager.getInstance(DoctorHomeActivity.this).setUserGender(g);
                        }
                        if (url != null && !url.isEmpty()) {
                            SessionManager.getInstance(DoctorHomeActivity.this).setUserAvatarUrl(url);
                        }
                        runOnUiThread(() -> {
                            bindPersonalBadges();
                            updateAvatar(SessionManager.getInstance(DoctorHomeActivity.this).getUserAvatarUrl());
                        });
                    }
                } catch (Exception ignored) {}
            }
            @Override public void onError(Exception e) {}
        });
    }

    private void updateAvatar(String url) {
        if (ivDocAvatar == null || isFinishing()) return;
        String gender = SessionManager.getInstance(this).getUserGender();
        boolean isFemale = isFemaleGender(gender);
        int placeholder = isFemale ? R.drawable.ic_doctor_female_avatar : R.drawable.ic_doctor_male_avatar;

        if (url != null && !url.isEmpty()) {
            com.bumptech.glide.Glide.with(this)
                    .load(url.startsWith("content://") || url.startsWith("file://") ? android.net.Uri.parse(url) : url)
                    .placeholder(placeholder)
                    .error(placeholder)
                    .circleCrop()
                    .into(ivDocAvatar);
        } else {
            ivDocAvatar.setImageResource(placeholder);
        }
    }

    private boolean isFemaleGender(String gender) {
        if (gender == null) return false;
        String g = gender.trim().toLowerCase(java.util.Locale.ROOT);
        return g.startsWith("f") || g.contains("female") || g.contains("woman");
    }

    private void loadQueue() {
        SupabaseClient.getInstance().get("queues?doctor_id=eq." + doctorId + "&queue_date=eq." + StaffUtils.todayIst()
                + "&select=id,current_token", new SupabaseClient.Callback() {
            @Override public void onSuccess(String json) {
                swipe.setRefreshing(false);
                try {
                    JSONArray arr = new JSONArray(json);
                    if (arr.length() == 0) { queueId = null; render(null, new JSONArray()); return; }
                    queueId = arr.getJSONObject(0).optString("id");
                    loadEntries();
                } catch (Exception ignored) { }
            }
            @Override public void onError(Exception e) { swipe.setRefreshing(false); }
        });
    }

    private void loadEntries() {
        SupabaseClient.getInstance().get("queue_entries?queue_id=eq." + queueId + "&select=token_number,token_sequence,status&order=token_sequence",
                new SupabaseClient.Callback() {
                    @Override public void onSuccess(String json) {
                        try { render(queueId, new JSONArray(json)); } catch (Exception ignored) { }
                    }
                    @Override public void onError(Exception e) { }
                });
    }

    private void render(String qid, JSONArray entries) throws org.json.JSONException {
        layoutEntries.removeAllViews();
        String serving = "—";
        int waiting = 0;
        LayoutInflater inf = LayoutInflater.from(this);
        for (int i = 0; i < entries.length(); i++) {
            JSONObject e = entries.getJSONObject(i);
            String st = e.optString("status");
            if ("in_progress".equals(st)) serving = e.optString("token_number");
            if ("waiting".equals(st) || "checked_in".equals(st)) waiting++;
            if ("cancelled".equals(st)) continue;
            View row = inf.inflate(R.layout.item_staff_row, layoutEntries, false);
            ((TextView) row.findViewById(R.id.tvRowTitle)).setText("Token " + e.optString("token_number"));
            String label = st.replace('_', ' ');
            ((TextView) row.findViewById(R.id.tvRowSub)).setText(label.isEmpty() ? "" : label.substring(0, 1).toUpperCase() + label.substring(1));
            layoutEntries.addView(row);
        }
        tvNow.setText(serving);
        tvWaiting.setText(waiting + (waiting == 1 ? " patient waiting" : " patients waiting"));
        tvEmpty.setVisibility(layoutEntries.getChildCount() == 0 ? View.VISIBLE : View.GONE);
        btnCallNext.setEnabled(qid != null);
    }

    private void callNext() {
        if (queueId == null) return;
        try {
            JSONObject body = new JSONObject();
            body.put("p_queue_id", queueId);
            SupabaseClient.getInstance().rpc("advance_queue", body, new SupabaseClient.Callback() {
                @Override public void onSuccess(String r) {
                    try {
                        JSONObject o = new JSONObject(r);
                        if (!o.optBoolean("success", false)) ToastUtils.show(DoctorHomeActivity.this, o.optString("error", "Couldn't advance"));
                        else if (o.has("message")) ToastUtils.show(DoctorHomeActivity.this, o.optString("message"));
                    } catch (Exception ignored) { }
                    loadEntries();
                }
                @Override public void onError(Exception e) { ToastUtils.show(DoctorHomeActivity.this, "Couldn't advance the queue"); }
            });
        } catch (Exception ignored) { }
    }
}
