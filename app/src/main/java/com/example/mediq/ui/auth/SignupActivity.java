package com.example.mediq.ui.auth;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.mediq.MainActivity;
import com.example.mediq.R;
import com.example.mediq.data.remote.SessionManager;
import com.example.mediq.data.remote.SupabaseClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

public class SignupActivity extends AppCompatActivity {

    private static final String TAG = "SignupActivity";

    private ImageButton btnBack;
    private TextInputLayout tilFullName;
    private TextInputLayout tilMobileNumber;
    private TextInputLayout tilEmail;
    private TextInputLayout tilSignupPassword;
    private TextInputLayout tilConfirmPassword;

    private TextInputEditText etFullName;
    private android.widget.AutoCompleteTextView actvRole;
    private TextInputEditText etMobileNumber;
    private TextInputEditText etEmail;
    private TextInputEditText etSignupPassword;
    private TextInputEditText etConfirmPassword;

    private MaterialCheckBox cbTerms;
    private TextView tvTerms;
    private MaterialButton btnCreateAccount;
    private TextView tvLoginLink;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        initViews();
        setupTermsText();
        setupListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tilFullName = findViewById(R.id.tilFullName);
        tilMobileNumber = findViewById(R.id.tilMobileNumber);
        tilEmail = findViewById(R.id.tilEmail);
        tilSignupPassword = findViewById(R.id.tilSignupPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);

        etFullName = findViewById(R.id.etFullName);
        actvRole = findViewById(R.id.actvRole);
        actvRole.setAdapter(new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, new String[]{"Patient", "Doctor", "Receptionist"}));
        actvRole.setText("Patient", false);
        etMobileNumber = findViewById(R.id.etMobileNumber);
        etEmail = findViewById(R.id.etEmail);
        etSignupPassword = findViewById(R.id.etSignupPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);

        cbTerms = findViewById(R.id.cbTerms);
        tvTerms = findViewById(R.id.tvTerms);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);
        tvLoginLink = findViewById(R.id.tvLoginLink);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupTermsText() {
        String fullText = "I agree to the Terms & Conditions";
        SpannableString spannableString = new SpannableString(fullText);
        int startIndex = fullText.indexOf("Terms & Conditions");
        int endIndex = startIndex + "Terms & Conditions".length();

        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                showTermsDialog();
            }
        };

        spannableString.setSpan(clickableSpan, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(this, R.color.colorPrimary)),
                startIndex,
                endIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        tvTerms.setText(spannableString);
        tvTerms.setMovementMethod(LinkMovementMethod.getInstance());
        tvTerms.setHighlightColor(Color.TRANSPARENT);
    }

    private void showTermsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Terms & Conditions")
                .setMessage("By registering with MediQ, you agree to comply with hospital appointment booking rules, queue management policies, and patient privacy standards.")
                .setPositiveButton("Accept", (dialog, which) -> cbTerms.setChecked(true))
                .setNegativeButton("Close", null)
                .show();
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        tvLoginLink.setOnClickListener(v -> finish());
        btnCreateAccount.setOnClickListener(v -> attemptSignup());
    }

    private void attemptSignup() {
        tilFullName.setError(null);
        tilMobileNumber.setError(null);
        tilEmail.setError(null);
        tilSignupPassword.setError(null);
        tilConfirmPassword.setError(null);

        String fullName = etFullName.getText() != null ? etFullName.getText().toString().trim() : "";
        String mobile = etMobileNumber.getText() != null ? etMobileNumber.getText().toString().trim() : "";
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etSignupPassword.getText() != null ? etSignupPassword.getText().toString().trim() : "";
        String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString().trim() : "";

        boolean cancel = false;

        if (TextUtils.isEmpty(fullName)) {
            tilFullName.setError("Please enter your full name");
            cancel = true;
        }

        if (TextUtils.isEmpty(mobile)) {
            tilMobileNumber.setError("Please enter your mobile number");
            cancel = true;
        } else if (mobile.length() < 10) {
            tilMobileNumber.setError("Please enter a valid 10-digit mobile number");
            cancel = true;
        }

        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("Please enter your email address for verification");
            cancel = true;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Please enter a valid email address");
            cancel = true;
        }

        if (TextUtils.isEmpty(password)) {
            tilSignupPassword.setError("Please enter a password");
            cancel = true;
        } else if (password.length() < 6) {
            tilSignupPassword.setError("Password must be at least 6 characters");
            cancel = true;
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            tilConfirmPassword.setError("Please confirm your password");
            cancel = true;
        } else if (!password.equals(confirmPassword)) {
            tilConfirmPassword.setError("Passwords do not match");
            cancel = true;
        }

        if (!cbTerms.isChecked()) {
            com.example.mediq.util.ToastUtils.show(this, "Please agree to the Terms & Conditions to proceed");
            cancel = true;
        }

        if (cancel) {
            return;
        }

        setLoading(true);
        final String requestedRole = actvRole.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        SupabaseClient.getInstance().signUp(email, password, fullName, mobile,
                requestedRole.isEmpty() ? "patient" : requestedRole, new SupabaseClient.Callback() {
            @Override
            public void onSuccess(String response) {
                setLoading(false);
                try {
                    JSONObject json = new JSONObject(response);
                    // Check if an immediate session was returned (e.g. Confirm Email is disabled)
                    String accessToken = SupabaseClient.optStringSafe(json, "access_token");
                    if (accessToken != null && !accessToken.isEmpty()) {
                        String refreshToken = SupabaseClient.optStringSafe(json, "refresh_token");
                        long expiresIn = json.optLong("expires_in", 3600);
                        JSONObject user = json.optJSONObject("user");
                        String userId = user != null ? SupabaseClient.optStringSafe(user, "id") : "";

                        SessionManager.getInstance(SignupActivity.this).saveSession(
                                accessToken, refreshToken != null ? refreshToken : "", expiresIn,
                                userId != null ? userId : "", email, fullName, "patient"
                        );

                        com.example.mediq.util.ToastUtils.show(SignupActivity.this, "Account created successfully!");
                        com.example.mediq.ui.auth.RoleRouter.routeAfterLogin(SignupActivity.this);
                        return;
                    }
                } catch (Exception e) {
                    Log.d(TAG, "No immediate session returned, proceeding to OTP verification", e);
                }

                // Standard flow: email confirmation code sent
                com.example.mediq.util.ToastUtils.show(SignupActivity.this, "Verification email sent to " + email);
                Intent intent = new Intent(SignupActivity.this, OtpVerificationActivity.class);
                intent.putExtra(OtpVerificationActivity.EXTRA_EMAIL, email);
                intent.putExtra(OtpVerificationActivity.EXTRA_FULL_NAME, fullName);
                intent.putExtra(OtpVerificationActivity.EXTRA_PHONE, mobile);
                intent.putExtra(OtpVerificationActivity.EXTRA_MODE, OtpVerificationActivity.MODE_REGISTER);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(Exception error) {
                setLoading(false);
                tilEmail.setError("Signup failed: " + error.getMessage());
                com.example.mediq.util.ToastUtils.show(SignupActivity.this, error.getMessage());
            }
        });
    }

    private void setLoading(boolean loading) {
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        btnCreateAccount.setEnabled(!loading);
    }
}
