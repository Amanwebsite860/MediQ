package com.example.mediq.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mediq.MainActivity;
import com.example.mediq.R;
import com.example.mediq.data.remote.SessionManager;
import com.example.mediq.data.remote.SupabaseClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

public class OtpVerificationActivity extends AppCompatActivity {

    private static final String TAG = "OtpVerificationActivity";

    public static final String EXTRA_EMAIL = "extra_email";
    public static final String EXTRA_MODE = "extra_mode";
    public static final String EXTRA_FULL_NAME = "extra_full_name";
    public static final String EXTRA_PHONE = "extra_phone";

    public static final String MODE_REGISTER = "mode_register";
    public static final String MODE_LOGIN = "mode_login";

    private String userEmail = "";
    private String mode = MODE_REGISTER;
    private String fullName = "";
    private String phone = "";

    private TextInputLayout tilOtpCode;
    private TextInputEditText etOtpCode;
    private TextView tvTargetEmail;
    private TextView tvResendOtp;
    private ProgressBar progressBar;
    private MaterialButton btnVerifyOtp;

    private CountDownTimer resendTimer;
    private boolean canResend = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_verification);

        ImageButton btnBack = findViewById(R.id.btnBack);
        tilOtpCode = findViewById(R.id.tilOtpCode);
        etOtpCode = findViewById(R.id.etOtpCode);
        tvTargetEmail = findViewById(R.id.tvTargetEmail);
        tvResendOtp = findViewById(R.id.tvResendOtp);
        progressBar = findViewById(R.id.progressBar);
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp);

        if (getIntent().hasExtra(EXTRA_EMAIL)) {
            userEmail = getIntent().getStringExtra(EXTRA_EMAIL);
        }
        if (getIntent().hasExtra(EXTRA_MODE)) {
            mode = getIntent().getStringExtra(EXTRA_MODE);
        }
        if (getIntent().hasExtra(EXTRA_FULL_NAME)) {
            fullName = getIntent().getStringExtra(EXTRA_FULL_NAME);
        }
        if (getIntent().hasExtra(EXTRA_PHONE)) {
            phone = getIntent().getStringExtra(EXTRA_PHONE);
        }

        if (TextUtils.isEmpty(userEmail)) {
            userEmail = "user@example.com";
        }

        tvTargetEmail.setText(userEmail);

        btnBack.setOnClickListener(v -> finish());
        btnVerifyOtp.setOnClickListener(v -> verifyOtp());

        etOtpCode.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                tilOtpCode.setError(null);
                if (s != null && s.length() == 6) {
                    verifyOtp();
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        tvResendOtp.setOnClickListener(v -> {
            if (canResend) {
                sendOtp();
            } else {
                com.example.mediq.util.ToastUtils.show(this, "Please wait before requesting a new code");
            }
        });

        startResendTimer();
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
                tvResendOtp.setText(R.string.text_resend_otp);
            }
        }.start();
    }

    private void sendOtp() {
        progressBar.setVisibility(View.VISIBLE);
        tvResendOtp.setEnabled(false);

        SupabaseClient.Callback callback = new SupabaseClient.Callback() {
            @Override
            public void onSuccess(String response) {
                progressBar.setVisibility(View.GONE);
                com.example.mediq.util.ToastUtils.show(OtpVerificationActivity.this, "New code sent to " + userEmail);
                startResendTimer();
            }

            @Override
            public void onError(Exception error) {
                progressBar.setVisibility(View.GONE);
                tvResendOtp.setEnabled(true);
                com.example.mediq.util.ToastUtils.show(OtpVerificationActivity.this, "Failed to resend: " + error.getMessage());
            }
        };

        if (MODE_REGISTER.equals(mode)) {
            // In register mode, use POST /auth/v1/resend with type=signup
            SupabaseClient.getInstance().resendSignupOtp(userEmail, callback);
        } else {
            // In login mode, use signInWithOtp with create_user=false
            SupabaseClient.getInstance().signInWithOtp(userEmail, false, callback);
        }
    }

    private void verifyOtp() {
        tilOtpCode.setError(null);
        String code = etOtpCode.getText() != null ? etOtpCode.getText().toString().trim() : "";

        if (TextUtils.isEmpty(code)) {
            tilOtpCode.setError("Please enter the verification code");
            return;
        }

        if (code.length() != 6) {
            tilOtpCode.setError("Verification code must be 6 digits");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnVerifyOtp.setEnabled(false);

        // For registration OTP verification, use "signup". For login OTP verification, use "email".
        String type = MODE_REGISTER.equals(mode) ? "signup" : "email";

        SupabaseClient.getInstance().verifyOtp(userEmail, code, type, new SupabaseClient.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject json = new JSONObject(response);
                    String accessToken = SupabaseClient.optStringSafe(json, "access_token");
                    if (accessToken == null || accessToken.isEmpty()) {
                        progressBar.setVisibility(View.GONE);
                        btnVerifyOtp.setEnabled(true);
                        tilOtpCode.setError("Verification succeeded but no session token received.");
                        return;
                    }

                    String refreshToken = SupabaseClient.optStringSafe(json, "refresh_token");
                    long expiresIn = json.optLong("expires_in", 3600);

                    JSONObject user = json.optJSONObject("user");
                    String userId = user != null ? SupabaseClient.optStringSafe(user, "id") : "";
                    String email = user != null ? SupabaseClient.optStringSafe(user, "email") : userEmail;
                    if (email == null) email = userEmail;

                    String name = (fullName != null && !fullName.isEmpty()) ? fullName : "Patient";
                    if (user != null && user.has("user_metadata")) {
                        JSONObject meta = user.getJSONObject("user_metadata");
                        String metaName = SupabaseClient.optStringSafe(meta, "full_name");
                        if (metaName != null && !metaName.isEmpty()) name = metaName;
                    }

                    SessionManager.getInstance(OtpVerificationActivity.this).saveSession(
                            accessToken, refreshToken != null ? refreshToken : "", expiresIn,
                            userId != null ? userId : "", email, name, "patient"
                    );

                    syncUserProfile(userId, accessToken, name);

                } catch (Exception e) {
                    progressBar.setVisibility(View.GONE);
                    btnVerifyOtp.setEnabled(true);
                    Log.e(TAG, "Failed to parse OTP response", e);
                    tilOtpCode.setError("Invalid session response: " + e.getMessage());
                }
            }

            @Override
            public void onError(Exception error) {
                progressBar.setVisibility(View.GONE);
                btnVerifyOtp.setEnabled(true);
                tilOtpCode.setError(error.getMessage() != null ? error.getMessage() : "Invalid or expired OTP");
            }
        });
    }

    private void syncUserProfile(String userId, String accessToken, String fallbackName) {
        if (userId == null || userId.isEmpty()) {
            progressBar.setVisibility(View.GONE);
            navigateToMain();
            return;
        }

        SupabaseClient.getInstance().get("profiles?id=eq." + userId + "&select=full_name,role", accessToken, new SupabaseClient.Callback() {
            @Override
            public void onSuccess(String response) {
                progressBar.setVisibility(View.GONE);
                try {
                    JSONArray arr = new JSONArray(response);
                    if (arr.length() > 0) {
                        JSONObject profile = arr.getJSONObject(0);
                        String role = SupabaseClient.optStringSafe(profile, "role");
                        String fetchedName = SupabaseClient.optStringSafe(profile, "full_name");
                        if (role != null) {
                            SessionManager.getInstance(OtpVerificationActivity.this).updateUserRole(role);
                        }
                        com.example.mediq.util.ToastUtils.show(OtpVerificationActivity.this, "Welcome, " + (fetchedName != null ? fetchedName : fallbackName) + "!");
                    }
                } catch (Exception ignored) {
                }
                navigateToMain();
            }

            @Override
            public void onError(Exception error) {
                progressBar.setVisibility(View.GONE);
                navigateToMain();
            }
        });
    }

    private void navigateToMain() {
        RoleRouter.routeAfterLogin(OtpVerificationActivity.this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (resendTimer != null) {
            resendTimer.cancel();
        }
    }
}
