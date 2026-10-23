package com.example.mediq.ui.staff;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
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
    private TextView tvName, tvMeta, tvNow, tvWaiting, tvEmpty;
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
        tvNow = findViewById(R.id.tvNowServing);
        tvWaiting = findViewById(R.id.tvWaitingCount);
        tvEmpty = findViewById(R.id.tvDocEmpty);
        layoutEntries = findViewById(R.id.layoutEntries);
        btnCallNext = findViewById(R.id.btnCallNext);

        findViewById(R.id.btnLogout).setOnClickListener(v -> StaffUtils.logout(this));
        StaffUtils.bindBell(this, findViewById(R.id.btnBell));
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
        SupabaseClient.getInstance().get("doctors?profile_id=eq." + uid + "&select=id,name,specialty,hospitals(name)",
                new SupabaseClient.Callback() {
                    @Override public void onSuccess(String json) {
                        try {
                            JSONArray arr = new JSONArray(json);
                            if (arr.length() == 0) { swipe.setRefreshing(false); tvName.setText("Doctor profile not found"); return; }
                            JSONObject d = arr.getJSONObject(0);
                            doctorId = d.optString("id");
                            tvName.setText(d.optString("name"));
                            JSONObject h = d.optJSONObject("hospitals");
                            tvMeta.setText(d.optString("specialty") + (h != null ? "  ·  " + h.optString("name") : ""));
                            loadQueue();
                        } catch (Exception e) { swipe.setRefreshing(false); }
                    }
                    @Override public void onError(Exception e) { swipe.setRefreshing(false); }
                });
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
