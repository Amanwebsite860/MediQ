package com.example.mediq.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mediq.R;
import com.example.mediq.data.remote.SessionManager;
import com.example.mediq.data.remote.SupabaseClient;
import com.example.mediq.util.ToastUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

/**
 * ResetPasswordActivity provides a complete password recovery screen:
 * - Enter registered email
 * - Request / Resend 6-digit recovery OTP
 * - Enter 6-digit OTP
 * - Enter and confirm new password
 * - Verifies OTP via Supabase type="recovery" and updates password via PUT /auth/v1/user
 */
public class ResetPasswordActivity extends AppCompatActivity {

    private static final String TAG = "ResetPasswordActivity";

    public static final String EXTRA_EMAIL = "extra_email";
    public static final String EXTRA_AUTO_SENT = "extra_auto_sent";

    private TextInputLayout tilEmail;
    private TextInputEditText etEmail;
    private TextInputLayout tilOtpCode;
    private TextInputEditText etOtpCode;
    private TextInputLayout tilNewPassword;
    private TextInputEditText etNewPassword;
    private TextInputLayout tilConfirmPassword;
    private TextInputEditText etConfirmPassword;
    private TextView tvResendOtp;
    private MaterialButton btnResetPassword;
    private ProgressBar progressBar;

    private CountDownTimer resendTimer;
    private boolean canResend = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        initViews();
        setupListeners();

        // Handle incoming email from Login screen
        if (getIntent().hasExtra(EXTRA_EMAIL)) {
            String email = getIntent().getStringExtra(EXTRA_EMAIL);
            if (email != null && !email.isEmpty()) {
                etEmail.setText(email);
            }
        }

        // If the code was just dispatched from Login screen, start resend timer immediately
        boolean autoSent = getIntent().getBooleanExtra(EXTRA_AUTO_SENT, false);
        if (autoSent) {
            startResendTimer();
        }
    }

    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btnBack);
        tilEmail = findViewById(R.id.tilEmail);
        etEmail = findViewById(R.id.etEmail);
        tilOtpCode = findViewById(R.id.tilOtpCode);
        etOtpCode = findViewById(R.id.etOtpCode);
        tilNewPassword = findViewById(R.id.tilNewPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        tvResendOtp = findViewById(R.id.tvResendOtp);
        btnResetPassword = findViewById(R.id.btnResetPassword);
        progressBar = findViewById(R.id.progressBar);
        TextView tvBackToLogin = findViewById(R.id.tvBackToLogin);

        btnBack.setOnClickListener(v -> finish());
        tvBackToLogin.setOnClickListener(v -> finish());
    }

    private void setupListeners() {
        tvResendOtp.setOnClickListener(v -> {
            if (canResend) {
                sendRecoveryCode();
            } else {
                ToastUtils.show(this, "Please wait before requesting another code");
            }
        });

        btnResetPassword.setOnClickListener(v -> attemptResetPassword());
    }

    private void startResendTimer() {
        canResend = false;
        tvResendOtp.setEnabled(false);

        if (resendTimer != null) {
            resendTimer.cancel();
        }

        resendTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                tvResendOtp.setText("Resend in " + seconds + "s");
            }

            @Override
            public void onFinish() {
                canResend = true;
                tvResendOtp.setEnabled(true);
                tvResendOtp.setText("Resend OTP");
            }
        }.start();
    }

    private void sendRecoveryCode() {
        tilEmail.setError(null);
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Please enter a valid registered email address");
            etEmail.requestFocus();
            return;
        }

        setLoading(true);
        SupabaseClient.getInstance().recoverPassword(email, new SupabaseClient.Callback() {
            @Override
            public void onSuccess(String response) {
                setLoading(false);
                ToastUtils.show(ResetPasswordActivity.this, "6-digit OTP sent to " + email);
                startResendTimer();
            }

            @Override
            public void onError(Exception error) {
                setLoading(false);
                String msg = error.getMessage() != null ? error.getMessage() : "Failed to send code";
                tilEmail.setError(msg);
                ToastUtils.show(ResetPasswordActivity.this, msg);
            }
        });
    }

    private void attemptResetPassword() {
        tilEmail.setError(null);
        tilOtpCode.setError(null);
        tilNewPassword.setError(null);
        tilConfirmPassword.setError(null);

        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String code = etOtpCode.getText() != null ? etOtpCode.getText().toString().trim() : "";
        String newPassword = etNewPassword.getText() != null ? etNewPassword.getText().toString().trim() : "";
        String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString().trim() : "";

        boolean hasError = false;

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Please enter a valid email address");
            hasError = true;
        }

        if (TextUtils.isEmpty(code)) {
            tilOtpCode.setError("Please enter the 6-digit OTP verification code");
            hasError = true;
        } else if (code.length() != 6) {
            tilOtpCode.setError("OTP code must be 6 digits");
            hasError = true;
        }

        if (TextUtils.isEmpty(newPassword)) {
            tilNewPassword.setError("Please enter a new password");
            hasError = true;
        } else if (newPassword.length() < 6) {
            tilNewPassword.setError("Password must be at least 6 characters");
            hasError = true;
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            tilConfirmPassword.setError("Please confirm your new password");
            hasError = true;
        } else if (!newPassword.equals(confirmPassword)) {
            tilConfirmPassword.setError("Passwords do not match");
            hasError = true;
        }

        if (hasError) {
            return;
        }

        setLoading(true);

        // Step 1: Verify recovery OTP code to get an authorized session
        SupabaseClient.getInstance().verifyOtp(email, code, "recovery", new SupabaseClient.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject json = new JSONObject(response);
                    String accessToken = SupabaseClient.optStringSafe(json, "access_token");
                    if (accessToken == null || accessToken.isEmpty()) {
                        setLoading(false);
                        tilOtpCode.setError("Verification succeeded but no session token was received.");
                        return;
                    }

                    String refreshToken = SupabaseClient.optStringSafe(json, "refresh_token");
                    long expiresIn = json.optLong("expires_in", 3600);

                    JSONObject user = json.optJSONObject("user");
                    String userId = user != null ? SupabaseClient.optStringSafe(user, "id") : "";
                    String userEmail = user != null ? SupabaseClient.optStringSafe(user, "email") : email;
                    if (userEmail == null || userEmail.isEmpty()) userEmail = email;

                    String name = "Patient";
                    if (user != null && user.has("user_metadata")) {
                        JSONObject meta = user.getJSONObject("user_metadata");
                        String metaName = SupabaseClient.optStringSafe(meta, "full_name");
                        if (metaName != null && !metaName.isEmpty()) name = metaName;
                    }

                    // Step 2: Update password using the authenticated recovery session
                    final String fEmail = userEmail;
                    final String fUserId = userId;
                    final String fName = name;
                    final String fAccess = accessToken;
                    final String fRefresh = refreshToken != null ? refreshToken : "";
                    final long fExpires = expiresIn;

                    SupabaseClient.getInstance().updateUserPassword(newPassword, accessToken, new SupabaseClient.Callback() {
                        @Override
                        public void onSuccess(String updateResp) {
                            setLoading(false);
                            // Save session with updated credentials
                            SessionManager.getInstance(ResetPasswordActivity.this).saveSession(
                                    fAccess, fRefresh, fExpires, fUserId, fEmail, fName, "patient"
                            );

                            ToastUtils.show(ResetPasswordActivity.this, "Password updated successfully! Logging you in...");
                            RoleRouter.routeAfterLogin(ResetPasswordActivity.this);
                            finish();
                        }

                        @Override
                        public void onError(Exception updateErr) {
                            setLoading(false);
                            String msg = updateErr.getMessage() != null ? updateErr.getMessage() : "Failed to update password";
                            tilNewPassword.setError(msg);
                            ToastUtils.show(ResetPasswordActivity.this, msg);
                        }
                    });

                } catch (Exception e) {
                    setLoading(false);
                    Log.e(TAG, "Failed to parse OTP recovery response", e);
                    tilOtpCode.setError("Failed to parse verification session: " + e.getMessage());
                }
            }

            @Override
            public void onError(Exception error) {
                setLoading(false);
                String msg = error.getMessage() != null ? error.getMessage() : "Invalid or expired OTP code";
                tilOtpCode.setError(msg);
                ToastUtils.show(ResetPasswordActivity.this, msg);
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnResetPassword.setEnabled(!loading);
        tvResendOtp.setEnabled(!loading && canResend);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (resendTimer != null) {
            resendTimer.cancel();
        }
    }
}
