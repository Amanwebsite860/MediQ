package com.example.mediq;

import android.app.Application;

import com.example.mediq.data.remote.SupabaseClient;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;

import java.io.File;

public class MediQApp extends Application {

    /**
     * Unique User-Agent required by OpenStreetMap Overpass and tile servers.
     * Must NOT be the package name (which OSM blocks).
     * Update the mailto address to your real contact email.
     */
    public static final String USER_AGENT = "MediQ/1.0 (mailto:support@mediq.app)";

    @Override
    public void onCreate() {
        super.onCreate();
        SupabaseClient.getInstance().init(this);

        // Configure osmdroid globally ONCE here so every screen is covered.
        // Doing this per-Activity (in MapViewActivity) is too late for some tile fetches.
        IConfigurationProvider cfg = Configuration.getInstance();
        cfg.setUserAgentValue(USER_AGENT);
        cfg.setOsmdroidBasePath(new File(getCacheDir(), "osmdroid"));
        cfg.setOsmdroidTileCache(new File(getCacheDir(), "osmdroid/tiles"));
    }
}
