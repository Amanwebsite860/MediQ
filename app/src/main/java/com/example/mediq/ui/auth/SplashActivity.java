package com.example.mediq.ui.auth;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mediq.MainActivity;
import com.example.mediq.R;
import com.example.mediq.data.remote.SessionManager;
import com.example.mediq.data.remote.SupabaseClient;

import org.json.JSONObject;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    private static final int SPLASH_TIMEOUT_MS = 2500;
    private final Handler splashHandler = new Handler(Looper.getMainLooper());
    private boolean navigated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Fallback navigation timeout in case network hangs
        splashHandler.postDelayed(() -> {
            if (!navigated && !isFinishing()) {
                proceedBasedOnSession();
            }
        }, SPLASH_TIMEOUT_MS);

        checkAndRefreshToken();
    }

    private void checkAndRefreshToken() {
        SessionManager session = SessionManager.getInstance(this);
        String refreshToken = session.getRefreshToken();

        if (refreshToken != null && !refreshToken.isEmpty()) {
            SupabaseClient.getInstance().refreshToken(refreshToken, new SupabaseClient.Callback() {
                @Override
                public void onSuccess(String response) {
                    try {
                        JSONObject json = new JSONObject(response);
                        String accessToken = SupabaseClient.optStringSafe(json, "access_token");
                        String newRefreshToken = SupabaseClient.optStringSafe(json, "refresh_token");
                        long expiresIn = json.optLong("expires_in", 3600);

                        if (accessToken != null && !accessToken.isEmpty()) {
                            session.updateTokens(accessToken, newRefreshToken != null ? newRefreshToken : refreshToken, expiresIn);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to parse refreshed token", e);
                    }
                    navigateToNext(true);
                }

                @Override
                public void onError(Exception error) {
                    Log.w(TAG, "Token refresh failed: " + error.getMessage());
                    // If refresh failed because token is revoked/invalid, clear session
                    if (error.getMessage() != null && error.getMessage().contains("400")) {
                        session.clearSession();
                        navigateToNext(false);
                    } else {
                        // Offline or network error: check if local token is still technically valid
                        navigateToNext(session.isLoggedIn());
                    }
                }
            });
        } else {
            // Not logged in
            splashHandler.postDelayed(() -> navigateToNext(false), 1000);
        }
    }

    private synchronized void navigateToNext(boolean loggedIn) {
        if (navigated || isFinishing()) return;
        navigated = true;
        splashHandler.removeCallbacksAndMessages(null);

        if (loggedIn) {
            RoleRouter.routeAfterLogin(this);
        } else {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }

    private void proceedBasedOnSession() {
        boolean loggedIn = SessionManager.getInstance(this).isLoggedIn();
        navigateToNext(loggedIn);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        splashHandler.removeCallbacksAndMessages(null);
    }
}
