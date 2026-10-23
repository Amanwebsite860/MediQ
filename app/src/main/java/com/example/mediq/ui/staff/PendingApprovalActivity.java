package com.example.mediq.ui.staff;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.mediq.R;
import com.example.mediq.data.remote.SessionManager;
import com.example.mediq.data.remote.SupabaseClient;
import com.example.mediq.ui.auth.LoginActivity;
import com.example.mediq.ui.auth.RoleRouter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/** "Waiting for admin approval" — checks the application every few seconds and moves on when approved. */
public class PendingApprovalActivity extends AppCompatActivity {

    private static final long POLL_MS = 8000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, POLL_MS); }
    };

    private TextView tvEmoji, tvTitle, tvSub, tvRole, tvHospital, tvStatus, tvReason;
    private View btnEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pending_approval);
        tvEmoji = findViewById(R.id.tvPendEmoji);
        tvTitle = findViewById(R.id.tvPendTitle);
        tvSub = findViewById(R.id.tvPendSub);
        tvRole = findViewById(R.id.tvPendRole);
        tvHospital = findViewById(R.id.tvPendHospital);
        tvStatus = findViewById(R.id.tvPendStatus);
        tvReason = findViewById(R.id.tvPendReason);
        btnEdit = findViewById(R.id.btnPendEdit);

        findViewById(R.id.btnPendRefresh).setOnClickListener(v -> refresh());
        btnEdit.setOnClickListener(v -> startActivity(new Intent(this, StaffOnboardingActivity.class)));
        findViewById(R.id.btnPendLogout).setOnClickListener(v -> {
            SessionManager.getInstance(this).clearSession();
            Intent i = new Intent(this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });
    }

    @Override protected void onResume() { super.onResume(); handler.post(poll); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(poll); }

    private void refresh() {
        String uid = SessionManager.getInstance(this).getUserId();
        if (uid == null) return;
        SupabaseClient.getInstance().get(
                "staff_applications?user_id=eq." + uid + "&select=status,rejection_reason,requested_role,hospitals(name)",
                new SupabaseClient.Callback() {
                    @Override public void onSuccess(String json) {
                        try {
                            JSONArray arr = new JSONArray(json);
                            if (arr.length() == 0) {
                                startActivity(new Intent(PendingApprovalActivity.this, StaffOnboardingActivity.class));
                                finish();
                                return;
                            }
                            JSONObject a = arr.getJSONObject(0);
                            String status = a.optString("status", "pending");
                            String role = a.optString("requested_role", "");
                            JSONObject h = a.optJSONObject("hospitals");
                            tvRole.setText("Applying as " + role);
                            tvHospital.setText(h != null ? h.optString("name") : "");
                            render(status, SupabaseClient.optStringSafe(a, "rejection_reason"));
                            if ("approved".equals(status)) {
                                handler.removeCallbacks(poll);
                                RoleRouter.routeAfterLogin(PendingApprovalActivity.this); // role is now doctor/receptionist
                            }
                        } catch (Exception ignored) { }
                    }
                    @Override public void onError(Exception e) { }
                });
    }

    private void render(String status, String reason) {
        boolean rejected = "rejected".equals(status);
        boolean approved = "approved".equals(status);
        tvEmoji.setText(rejected ? "⚠️" : approved ? "🎉" : "⏳");
        tvTitle.setText(rejected ? "Application needs changes" : approved ? "You're approved!" : "Waiting for admin approval");
        tvSub.setText(rejected ? "Please update your details and submit again."
                : approved ? "Opening your dashboard…"
                : "We've received your details. An admin will review them and activate your account — usually within a day.");
        tvStatus.setText(rejected ? "Rejected" : approved ? "Approved" : "Pending review");
        tvStatus.setTextColor(ContextCompat.getColor(this, rejected ? R.color.colorError : R.color.colorPrimary));
        btnEdit.setVisibility(rejected ? View.VISIBLE : View.GONE);
        tvReason.setVisibility(rejected && reason != null && !reason.isEmpty() ? View.VISIBLE : View.GONE);
        if (reason != null) tvReason.setText(String.format(Locale.US, "Reason: %s", reason));
    }
}
