package com.example.mediq.data.remote;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Manages user authentication tokens and profile session using EncryptedSharedPreferences.
 */
public class SessionManager {

    private static final String TAG = "SessionManager";
    private static final String PREF_NAME = "mediq_secure_session";

    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_EXPIRES_AT = "expires_at";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_ROLE = "user_role";
    private static final String KEY_USER_PHONE = "user_phone";
    private static final String KEY_USER_AVATAR = "user_avatar";
    private static final String KEY_USER_BLOOD_GROUP = "user_blood_group";
    private static final String KEY_USER_GENDER = "user_gender";
    private static final String KEY_USER_EMERGENCY = "user_emergency";

    private static SessionManager instance;
    private final SharedPreferences prefs;

    private SessionManager(Context context) {
        SharedPreferences tempPrefs;
        try {
            MasterKey masterKey = new MasterKey.Builder(context.getApplicationContext())
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            tempPrefs = EncryptedSharedPreferences.create(
                    context.getApplicationContext(),
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences, using standard fallback", e);
            tempPrefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }
        this.prefs = tempPrefs;
    }

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context);
        }
        return instance;
    }

    public void saveSession(String accessToken, String refreshToken, long expiresInSeconds,
                            String userId, String email, String name, String role) {
        long expiresAtMillis = System.currentTimeMillis() + (expiresInSeconds * 1000L);
        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putLong(KEY_EXPIRES_AT, expiresAtMillis)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_USER_EMAIL, email)
                .putString(KEY_USER_NAME, name)
                .putString(KEY_USER_ROLE, role != null ? role : "patient")
                .apply();
    }

    public void updateTokens(String accessToken, String refreshToken, long expiresInSeconds) {
        long expiresAtMillis = System.currentTimeMillis() + (expiresInSeconds * 1000L);
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putLong(KEY_EXPIRES_AT, expiresAtMillis);
        if (refreshToken != null && !refreshToken.isEmpty()) {
            editor.putString(KEY_REFRESH_TOKEN, refreshToken);
        }
        editor.apply();
    }

    public void updateUserRole(String role) {
        if (role != null && !role.isEmpty()) {
            prefs.edit().putString(KEY_USER_ROLE, role).apply();
        }
    }

    public boolean isLoggedIn() {
        String token = getAccessToken();
        if (token == null || token.isEmpty()) {
            return false;
        }
        // Check if token has not expired
        long expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0);
        return expiresAt > System.currentTimeMillis();
    }

    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH_TOKEN, null);
    }

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    public String getUserEmail() {
        return prefs.getString(KEY_USER_EMAIL, "user@example.com");
    }

    public String getUserName() {
        return prefs.getString(KEY_USER_NAME, "Patient");
    }

    public String getUserRole() {
        return prefs.getString(KEY_USER_ROLE, "patient");
    }

    public String getUserPhone() {
        return prefs.getString(KEY_USER_PHONE, "");
    }

    public void setUserPhone(String phone) {
        prefs.edit().putString(KEY_USER_PHONE, phone).apply();
    }

    public String getUserAvatarUrl() {
        return prefs.getString(KEY_USER_AVATAR, null);
    }

    public void setUserAvatarUrl(String url) {
        prefs.edit().putString(KEY_USER_AVATAR, url).apply();
    }

    public String getUserBloodGroup() {
        return prefs.getString(KEY_USER_BLOOD_GROUP, "Not set");
    }

    public void setUserBloodGroup(String bg) {
        prefs.edit().putString(KEY_USER_BLOOD_GROUP, bg).apply();
    }

    public String getUserGender() {
        return prefs.getString(KEY_USER_GENDER, "Not set");
    }

    public void setUserGender(String gender) {
        prefs.edit().putString(KEY_USER_GENDER, gender).apply();
    }

    public String getUserEmergencyContact() {
        return prefs.getString(KEY_USER_EMERGENCY, "Not set");
    }

    public void setUserEmergencyContact(String contact) {
        prefs.edit().putString(KEY_USER_EMERGENCY, contact).apply();
    }

    public void updateProfileDetails(String name, String phone, String bloodGroup, String gender, String emergency) {
        SharedPreferences.Editor editor = prefs.edit();
        if (name != null) editor.putString(KEY_USER_NAME, name);
        if (phone != null) editor.putString(KEY_USER_PHONE, phone);
        if (bloodGroup != null) editor.putString(KEY_USER_BLOOD_GROUP, bloodGroup);
        if (gender != null) editor.putString(KEY_USER_GENDER, gender);
        if (emergency != null) editor.putString(KEY_USER_EMERGENCY, emergency);
        editor.apply();
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }
}
