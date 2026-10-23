package com.example.mediq.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mediq.MainActivity;
import com.example.mediq.R;
import com.example.mediq.data.remote.SessionManager;
import com.example.mediq.data.remote.SupabaseClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private TextInputLayout tilEmailPhone;
    private TextInputLayout tilPassword;
    private TextInputEditText etEmailPhone;
    private TextInputEditText etPassword;
    private MaterialCheckBox cbRememberMe;
    private TextView tvForgotPassword;
    private MaterialButton btnLogin;
    private MaterialButton btnLoginOtp;
    private TextView tvCreateAccountLink;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initViews();
        setupListeners();
    }

    private void initViews() {
        tilEmailPhone = findViewById(R.id.tilEmailPhone);
        tilPassword = findViewById(R.id.tilPassword);
        etEmailPhone = findViewById(R.id.etEmailPhone);
        etPassword = findViewById(R.id.etPassword);
        cbRememberMe = findViewById(R.id.cbRememberMe);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnLoginOtp = findViewById(R.id.btnLoginOtp);
        tvCreateAccountLink = findViewById(R.id.tvCreateAccountLink);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> attemptLogin());

        btnLoginOtp.setOnClickListener(v -> attemptOtpLogin());

        tvForgotPassword.setOnClickListener(v -> attemptForgotPassword());

        tvCreateAccountLink.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
            startActivity(intent);
        });
    }

    private void attemptForgotPassword() {
        tilEmailPhone.setError(null);
        String email = etEmailPhone.getText() != null ? etEmailPhone.getText().toString().trim() : "";

        if (!TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            setLoading(true);
            SupabaseClient.getInstance().recoverPassword(email, new SupabaseClient.Callback() {
                @Override
                public void onSuccess(String response) {
                    setLoading(false);
                    com.example.mediq.util.ToastUtils.show(LoginActivity.this, "8-digit OTP sent to " + email);
                    Intent intent = new Intent(LoginActivity.this, ResetPasswordActivity.class);
                    intent.putExtra(ResetPasswordActivity.EXTRA_EMAIL, email);
                    intent.putExtra(ResetPasswordActivity.EXTRA_AUTO_SENT, true);
                    startActivity(intent);
                }

                @Override
                public void onError(Exception error) {
                    setLoading(false);
                    com.example.mediq.util.ToastUtils.show(LoginActivity.this, "Failed to send reset code: " + error.getMessage());
                    Intent intent = new Intent(LoginActivity.this, ResetPasswordActivity.class);
                    intent.putExtra(ResetPasswordActivity.EXTRA_EMAIL, email);
                    startActivity(intent);
                }
            });
        } else {
            Intent intent = new Intent(LoginActivity.this, ResetPasswordActivity.class);
            if (!TextUtils.isEmpty(email)) {
                intent.putExtra(ResetPasswordActivity.EXTRA_EMAIL, email);
            }
            startActivity(intent);
        }
    }

    private void attemptOtpLogin() {
        tilEmailPhone.setError(null);
        String email = etEmailPhone.getText() != null ? etEmailPhone.getText().toString().trim() : "";

        if (TextUtils.isEmpty(email)) {
            tilEmailPhone.setError("Enter your email address to receive login OTP");
            etEmailPhone.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmailPhone.setError("Please enter a valid email address");
            etEmailPhone.requestFocus();
            return;
        }

        setLoading(true);
        // create_user: false so we don't accidentally create ghost accounts
        SupabaseClient.getInstance().signInWithOtp(email, false, new SupabaseClient.Callback() {
            @Override
            public void onSuccess(String response) {
                setLoading(false);
                com.example.mediq.util.ToastUtils.show(LoginActivity.this, "Login code sent to " + email);
                Intent intent = new Intent(LoginActivity.this, OtpVerificationActivity.class);
                intent.putExtra(OtpVerificationActivity.EXTRA_EMAIL, email);
                intent.putExtra(OtpVerificationActivity.EXTRA_MODE, OtpVerificationActivity.MODE_LOGIN);
                startActivity(intent);
            }

            @Override
            public void onError(Exception error) {
                setLoading(false);
                tilEmailPhone.setError("Failed to send OTP: " + error.getMessage());
            }
        });
    }

    private void attemptLogin() {
        tilEmailPhone.setError(null);
        tilPassword.setError(null);

        String email = etEmailPhone.getText() != null ? etEmailPhone.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        boolean cancel = false;

        if (TextUtils.isEmpty(email)) {
            tilEmailPhone.setError("Please enter your email");
            cancel = true;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmailPhone.setError("Please enter a valid email address");
            cancel = true;
        }

        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("Please enter your password");
            cancel = true;
        } else if (password.length() < 6) {
            tilPassword.setError("Password must be at least 6 characters");
            cancel = true;
        }

        if (cancel) {
            return;
        }

        setLoading(true);
        SupabaseClient.getInstance().loginWithPassword(email, password, new SupabaseClient.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject json = new JSONObject(response);
                    String accessToken = SupabaseClient.optStringSafe(json, "access_token");
                    if (accessToken == null || accessToken.isEmpty()) {
                        setLoading(false);
                        tilPassword.setError("Authentication succeeded but no access token received.");
                        return;
                    }

                    String refreshToken = SupabaseClient.optStringSafe(json, "refresh_token");
                    long expiresIn = json.optLong("expires_in", 3600);

                    JSONObject user = json.optJSONObject("user");
                    String userId = user != null ? SupabaseClient.optStringSafe(user, "id") : "";
                    String userEmail = user != null ? SupabaseClient.optStringSafe(user, "email") : email;
                    if (userEmail == null) userEmail = email;

                    String initialName = "Patient";
                    if (user != null && user.has("user_metadata")) {
                        JSONObject meta = user.getJSONObject("user_metadata");
                        String metaName = SupabaseClient.optStringSafe(meta, "full_name");
                        if (metaName != null && !metaName.isEmpty()) initialName = metaName;
                    }

                    // Save session immediately with patient role
                    SessionManager.getInstance(LoginActivity.this).saveSession(
                            accessToken, refreshToken != null ? refreshToken : "", expiresIn, userId, userEmail, initialName, "patient"
                    );

                    // Fetch authoritative role and profile details from profiles table
                    syncUserProfile(userId, accessToken, initialName);

                } catch (Exception e) {
                    setLoading(false);
                    Log.e(TAG, "Failed to parse login response", e);
                    tilPassword.setError("Failed to parse login session: " + e.getMessage());
                }
            }

            @Override
            public void onError(Exception error) {
                setLoading(false);
                tilPassword.setError(error.getMessage() != null ? error.getMessage() : "Invalid email or password");
                com.example.mediq.util.ToastUtils.show(LoginActivity.this, error.getMessage());
            }
        });
    }

    private void syncUserProfile(String userId, String accessToken, String fallbackName) {
        if (userId == null || userId.isEmpty()) {
            setLoading(false);
            navigateToMain();
            return;
        }

        SupabaseClient.getInstance().get("profiles?id=eq." + userId + "&select=full_name,role", accessToken, new SupabaseClient.Callback() {
            @Override
            public void onSuccess(String response) {
                setLoading(false);
                try {
                    JSONArray arr = new JSONArray(response);
                    if (arr.length() > 0) {
                        JSONObject profile = arr.getJSONObject(0);
                        String role = SupabaseClient.optStringSafe(profile, "role");
                        String fullName = SupabaseClient.optStringSafe(profile, "full_name");

                        if (role != null) {
                            SessionManager.getInstance(LoginActivity.this).updateUserRole(role);
                        }
                        com.example.mediq.util.ToastUtils.show(LoginActivity.this, "Welcome back, " + (fullName != null ? fullName : fallbackName) + "!");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Could not parse profile response: " + e.getMessage());
                }
                navigateToMain();
            }

            @Override
            public void onError(Exception error) {
                setLoading(false);
                Log.w(TAG, "Profile sync non-fatal error: " + error.getMessage());
                navigateToMain();
            }
        });
    }

    private void setLoading(boolean loading) {
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        btnLogin.setEnabled(!loading);
        btnLoginOtp.setEnabled(!loading);
    }

    private void navigateToMain() {
        RoleRouter.routeAfterLogin(LoginActivity.this);
    }
}
