package com.example.mediq.data.remote;

import com.example.mediq.BuildConfig;

/**
 * Supabase configuration constants loaded from BuildConfig (populated from local.properties).
 */
public final class SupabaseConfig {

    private SupabaseConfig() {
        // Prevent instantiation
    }

    public static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    public static final String SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;

    public static final String AUTH_URL = SUPABASE_URL + "/auth/v1";
    public static final String REST_URL = SUPABASE_URL + "/rest/v1";
    public static final String RPC_URL = REST_URL + "/rpc";
    public static final String STORAGE_URL = SUPABASE_URL + "/storage/v1";
    public static final String REALTIME_URL = SUPABASE_URL.replace("https://", "wss://") + "/realtime/v1/websocket";
}
