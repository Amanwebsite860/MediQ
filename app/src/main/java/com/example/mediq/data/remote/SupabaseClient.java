package com.example.mediq.data.remote;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.Route;

/**
 * Production-ready OkHttp-backed HTTP client for Supabase Auth, REST, and RPC operations.
 * - Automatically injects apikey and user Bearer JWT (when authenticated)
 * - Safe error stream reading (no null pointer exceptions)
 * - Main-thread callback dispatching for seamless UI updates
 * - OkHttp Authenticator for automatic 401 token refresh
 */
public class SupabaseClient {

    private static final String TAG = "SupabaseClient";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static SupabaseClient instance;
    private final OkHttpClient httpClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Context appContext;

    public interface Callback {
        void onSuccess(String response);
        void onError(Exception error);
    }

    private SupabaseClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .authenticator(new TokenAuthenticator())
                .build();
    }

    public static synchronized SupabaseClient getInstance() {
        if (instance == null) {
            instance = new SupabaseClient();
        }
        return instance;
    }

    public void init(Context context) {
        this.appContext = context.getApplicationContext();
    }

    private class TokenAuthenticator implements Authenticator {
        @Nullable
        @Override
        public Request authenticate(@Nullable Route route, @NonNull Response response) throws IOException {
            if (responseCount(response) >= 2) {
                return null; // Avoid refresh loop
            }
            if (appContext == null) return null;

            SessionManager session = SessionManager.getInstance(appContext);
            String refreshToken = session.getRefreshToken();
            if (refreshToken == null || refreshToken.isEmpty()) {
                return null;
            }

            try {
                String url = SupabaseConfig.AUTH_URL + "/token?grant_type=refresh_token";
                JSONObject body = new JSONObject();
                body.put("refresh_token", refreshToken);

                RequestBody requestBody = RequestBody.create(body.toString(), JSON);
                Request refreshReq = new Request.Builder()
                        .url(url)
                        .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                        .header("Content-Type", "application/json")
                        .post(requestBody)
                        .build();

                OkHttpClient directClient = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build();

                try (Response refreshRes = directClient.newCall(refreshReq).execute()) {
                    if (refreshRes.isSuccessful() && refreshRes.body() != null) {
                        String resStr = refreshRes.body().string();
                        JSONObject json = new JSONObject(resStr);
                        String newAccess = optStringSafe(json, "access_token");
                        String newRefresh = optStringSafe(json, "refresh_token");
                        long expiresIn = json.optLong("expires_in", 3600);

                        if (newAccess != null && !newAccess.isEmpty()) {
                            session.updateTokens(newAccess, newRefresh != null ? newRefresh : refreshToken, expiresIn);
                            return response.request().newBuilder()
                                    .header("Authorization", "Bearer " + newAccess)
                                    .build();
                        }
                    } else if (refreshRes.code() == 400) {
                        session.clearSession();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Authenticator token refresh failed", e);
            }
            return null;
        }

        private int responseCount(Response response) {
            int result = 1;
            while ((response = response.priorResponse()) != null) {
                result++;
            }
            return result;
        }
    }

    public static String optStringSafe(JSONObject obj, String key) {
        if (obj == null || obj.isNull(key)) return null;
        String val = obj.optString(key, null);
        if (val == null || "null".equalsIgnoreCase(val.trim())) return null;
        return val.trim();
    }

    public static String parseErrorMessage(String responseString, int statusCode) {
        if (responseString == null || responseString.trim().isEmpty()) {
            return "Request failed (HTTP " + statusCode + ")";
        }
        try {
            JSONObject json = new JSONObject(responseString);
            String msg = optStringSafe(json, "msg");
            if (msg != null && !msg.isEmpty()) return msg;

            String errorDesc = optStringSafe(json, "error_description");
            if (errorDesc != null && !errorDesc.isEmpty()) return errorDesc;

            String message = optStringSafe(json, "message");
            if (message != null && !message.isEmpty()) return message;

            String error = optStringSafe(json, "error");
            if (error != null && !error.isEmpty()) return error;
        } catch (Exception ignored) {
        }
        return "Request failed (HTTP " + statusCode + ")";
    }

    private Request.Builder newAuthenticatedRequest(String url) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                .header("Accept", "application/json");

        if (appContext != null) {
            String token = SessionManager.getInstance(appContext).getAccessToken();
            if (token != null && !token.isEmpty()) {
                builder.header("Authorization", "Bearer " + token);
            }
        }
        return builder;
    }

    private void dispatchSuccess(Callback callback, String body) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(body));
        }
    }

    private void dispatchError(Callback callback, Exception e) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(e));
        }
    }

    private void executeCall(Request request, Callback callback) {
        httpClient.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Request failed: " + call.request().url(), e);
                dispatchError(callback, e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    ResponseBody responseBody = response.body();
                    String responseString = responseBody != null ? responseBody.string() : "";

                    if (response.isSuccessful()) {
                        dispatchSuccess(callback, responseString);
                    } else {
                        String errMsg = parseErrorMessage(responseString, response.code());
                        Log.e(TAG, "HTTP " + response.code() + " error: " + responseString);
                        dispatchError(callback, new Exception(errMsg));
                    }
                } catch (Exception e) {
                    dispatchError(callback, e);
                }
            }
        });
    }

    // =========================================================================
    // AUTHENTICATION METHODS
    // =========================================================================

    /**
     * Password login via POST /auth/v1/token?grant_type=password
     */
    public void loginWithPassword(String email, String password, Callback callback) {
        try {
            String url = SupabaseConfig.AUTH_URL + "/token?grant_type=password";
            JSONObject body = new JSONObject();
            body.put("email", email.trim());
            body.put("password", password);

            RequestBody requestBody = RequestBody.create(body.toString(), JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build();

            executeCall(request, callback);
        } catch (Exception e) {
            dispatchError(callback, e);
        }
    }

    /**
     * Signup via POST /auth/v1/signup
     */
    public void signUp(String email, String password, String fullName, String phone, Callback callback) {
        signUp(email, password, fullName, phone, "patient", callback);
    }

    /** requested_role is only a request: the real role changes when an admin approves the staff application. */
    public void signUp(String email, String password, String fullName, String phone, String requestedRole, Callback callback) {
        try {
            String url = SupabaseConfig.AUTH_URL + "/signup";
            JSONObject body = new JSONObject();
            body.put("email", email.trim());
            body.put("password", password);

            JSONObject meta = new JSONObject();
            meta.put("full_name", fullName != null ? fullName.trim() : "");
            if (phone != null && !phone.isEmpty()) {
                meta.put("phone", phone.trim());
            }
            meta.put("requested_role", requestedRole == null ? "patient" : requestedRole);
            body.put("data", meta);

            RequestBody requestBody = RequestBody.create(body.toString(), JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build();

            executeCall(request, callback);
        } catch (Exception e) {
            dispatchError(callback, e);
        }
    }

    /**
     * Sends Magic Link / OTP via POST /auth/v1/otp with create_user=false for login
     */
    public void signInWithOtp(String email, Callback callback) {
        signInWithOtp(email, false, callback);
    }

    public void signInWithOtp(String email, boolean createUser, Callback callback) {
        try {
            String url = SupabaseConfig.AUTH_URL + "/otp";
            JSONObject body = new JSONObject();
            body.put("email", email.trim());
            body.put("create_user", createUser);

            RequestBody requestBody = RequestBody.create(body.toString(), JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build();

            executeCall(request, callback);
        } catch (Exception e) {
            dispatchError(callback, e);
        }
    }

    /**
     * Resend signup OTP via POST /auth/v1/resend with type=signup
     */
    public void resendSignupOtp(String email, Callback callback) {
        try {
            String url = SupabaseConfig.AUTH_URL + "/resend";
            JSONObject body = new JSONObject();
            body.put("email", email.trim());
            body.put("type", "signup");

            RequestBody requestBody = RequestBody.create(body.toString(), JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build();

            executeCall(request, callback);
        } catch (Exception e) {
            dispatchError(callback, e);
        }
    }

    /**
     * Verifies OTP code via POST /auth/v1/verify
     */
    public void verifyOtp(String email, String token, String type, Callback callback) {
        try {
            String url = SupabaseConfig.AUTH_URL + "/verify";
            JSONObject body = new JSONObject();
            body.put("email", email.trim());
            body.put("token", token.trim());
            body.put("type", type); // "signup", "email", "recovery", etc.

            RequestBody requestBody = RequestBody.create(body.toString(), JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build();

            executeCall(request, callback);
        } catch (Exception e) {
            dispatchError(callback, e);
        }
    }

    /**
     * Sends password recovery email via POST /auth/v1/recover
     */
    public void recoverPassword(String email, Callback callback) {
        try {
            String url = SupabaseConfig.AUTH_URL + "/recover";
            JSONObject body = new JSONObject();
            body.put("email", email.trim());

            RequestBody requestBody = RequestBody.create(body.toString(), JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build();

            executeCall(request, callback);
        } catch (Exception e) {
            dispatchError(callback, e);
        }
    }

    /**
     * Refreshes user session token via POST /auth/v1/token?grant_type=refresh_token
     */
    public void refreshToken(String refreshToken, Callback callback) {
        try {
            String url = SupabaseConfig.AUTH_URL + "/token?grant_type=refresh_token";
            JSONObject body = new JSONObject();
            body.put("refresh_token", refreshToken);

            RequestBody requestBody = RequestBody.create(body.toString(), JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build();

            executeCall(request, callback);
        } catch (Exception e) {
            dispatchError(callback, e);
        }
    }

    /**
     * Updates user's password via PUT /auth/v1/user
     */
    public void updateUserPassword(String newPassword, String accessToken, Callback callback) {
        try {
            String url = SupabaseConfig.AUTH_URL + "/user";
            JSONObject body = new JSONObject();
            body.put("password", newPassword.trim());

            RequestBody requestBody = RequestBody.create(body.toString(), JSON);
            Request.Builder builder = new Request.Builder()
                    .url(url)
                    .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .header("Content-Type", "application/json")
                    .put(requestBody);

            if (accessToken != null && !accessToken.isEmpty()) {
                builder.header("Authorization", "Bearer " + accessToken);
            } else if (appContext != null) {
                String token = SessionManager.getInstance(appContext).getAccessToken();
                if (token != null && !token.isEmpty()) {
                    builder.header("Authorization", "Bearer " + token);
                }
            }

            executeCall(builder.build(), callback);
        } catch (Exception e) {
            dispatchError(callback, e);
        }
    }

    // =========================================================================
    // REST & RPC METHODS (PostgREST)
    // =========================================================================

    /**
     * GET request to PostgREST endpoint with optional explicit accessToken.
     */
    public void get(String path, String accessToken, Callback cb) {
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        String fullUrl = cleanPath.startsWith("http") ? cleanPath : SupabaseConfig.REST_URL + "/" + cleanPath;

        Request.Builder builder = new Request.Builder()
                .url(fullUrl)
                .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                .header("Accept", "application/json");

        if (accessToken != null && !accessToken.isEmpty()) {
            builder.header("Authorization", "Bearer " + accessToken);
        } else if (appContext != null) {
            String token = SessionManager.getInstance(appContext).getAccessToken();
            if (token != null && !token.isEmpty()) {
                builder.header("Authorization", "Bearer " + token);
            }
        }

        executeCall(builder.get().build(), cb);
    }

    /**
     * GET request to PostgREST endpoint.
     * Example: get("hospitals?active=eq.true", callback)
     */
    public void get(String endpoint, Callback callback) {
        get(endpoint, null, callback);
    }

    /**
     * POST request to insert records.
     */
    public void post(String endpoint, JSONObject body, Callback callback) {
        String fullUrl = endpoint.startsWith("http") ? endpoint : SupabaseConfig.REST_URL + "/" + endpoint;
        String content = body != null ? body.toString() : "{}";
        RequestBody requestBody = RequestBody.create(content, JSON);

        Request request = newAuthenticatedRequest(fullUrl)
                .header("Prefer", "return=representation")
                .post(requestBody)
                .build();

        executeCall(request, callback);
    }

    /**
     * POST request to invoke RPC stored procedures.
     * Example: rpc("book_appointment", paramsJson, callback)
     */
    public void rpc(String functionName, JSONObject params, Callback callback) {
        String fullUrl = SupabaseConfig.RPC_URL + "/" + functionName;
        String content = params != null ? params.toString() : "{}";
        RequestBody requestBody = RequestBody.create(content, JSON);

        Request request = newAuthenticatedRequest(fullUrl)
                .header("Prefer", "return=representation")
                .post(requestBody)
                .build();

        executeCall(request, callback);
    }

    /**
     * PATCH request to update records.
     */
    public void patch(String endpoint, JSONObject body, Callback callback) {
        String fullUrl = endpoint.startsWith("http") ? endpoint : SupabaseConfig.REST_URL + "/" + endpoint;
        String content = body != null ? body.toString() : "{}";
        RequestBody requestBody = RequestBody.create(content, JSON);

        Request request = newAuthenticatedRequest(fullUrl)
                .header("Prefer", "return=representation")
                .patch(requestBody)
                .build();

        executeCall(request, callback);
    }

    /**
     * DELETE request.
     */
    public void delete(String endpoint, Callback callback) {
        String fullUrl = endpoint.startsWith("http") ? endpoint : SupabaseConfig.REST_URL + "/" + endpoint;
        Request request = newAuthenticatedRequest(fullUrl).delete().build();
        executeCall(request, callback);
    }

    /**
     * Uploads (or replaces) a file in a Storage bucket. The callback receives the file's public URL
     * (valid for public buckets such as "avatars").
     */
    public void uploadFile(String bucket, String path, byte[] data, String contentType, Callback callback) {
        String url = SupabaseConfig.STORAGE_URL + "/object/" + bucket + "/" + path;
        RequestBody body = RequestBody.create(data, MediaType.parse(contentType));
        Request request = newAuthenticatedRequest(url)
                .header("x-upsert", "true")
                .post(body)
                .build();
        final String publicUrl = SupabaseConfig.STORAGE_URL + "/object/public/" + bucket + "/" + path;
        executeCall(request, new Callback() {
            @Override public void onSuccess(String response) { if (callback != null) callback.onSuccess(publicUrl); }
            @Override public void onError(Exception error) { if (callback != null) callback.onError(error); }
        });
    }
}
