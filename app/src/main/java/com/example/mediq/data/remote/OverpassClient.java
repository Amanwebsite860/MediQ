package com.example.mediq.data.remote;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fetches real nearby hospitals, clinics, and pharmacies from OpenStreetMap Overpass API.
 * Free, open, and requires no API key or billing.
 * Implements 5-minute memory caching.
 */
public class OverpassClient {

    private static List<Place> cachedPlaces = null;
    private static long cacheTimestamp = 0;
    private static double cachedLat = 0;
    private static double cachedLon = 0;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L; // 5 minutes

    public static class Place {
        public String name;
        public String type; // "hospital", "clinic", "pharmacy"
        public String address;
        public String phone;
        public double lat;
        public double lon;
    }

    public static synchronized List<Place> fetch(double lat, double lon, int radiusM) throws Exception {
        // Check 5-minute cache
        if (cachedPlaces != null
                && (System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS)
                && Math.abs(cachedLat - lat) < 0.01
                && Math.abs(cachedLon - lon) < 0.01) {
            return new ArrayList<>(cachedPlaces);
        }

        String around = "(around:" + radiusM + "," +
                String.format(Locale.US, "%.5f,%.5f", lat, lon) + ")";
        String q = "[out:json][timeout:25];(" +
                "nwr[\"amenity\"=\"hospital\"]" + around + ";" +
                "nwr[\"amenity\"=\"clinic\"]" + around + ";" +
                "nwr[\"amenity\"=\"pharmacy\"]" + around + ";" +
                ");out center tags;";

        HttpURLConnection c = (HttpURLConnection)
                new URL("https://overpass-api.de/api/interpreter").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(10000);
        c.setReadTimeout(30000);
        c.setDoOutput(true);
        // Use the shared User-Agent defined in MediQApp so it is consistent across all HTTP calls.
        c.setRequestProperty("User-Agent", com.example.mediq.MediQApp.USER_AGENT);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        try (OutputStream os = c.getOutputStream()) {
            os.write(("data=" + URLEncoder.encode(q, "UTF-8")).getBytes(StandardCharsets.UTF_8));
        }

        if (c.getResponseCode() != 200) {
            throw new Exception("Overpass HTTP " + c.getResponseCode());
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
        }

        List<Place> out = new ArrayList<>();
        JSONArray els = new JSONObject(sb.toString()).getJSONArray("elements");
        for (int i = 0; i < els.length(); i++) {
            JSONObject e = els.getJSONObject(i);
            JSONObject tags = e.optJSONObject("tags");
            if (tags == null) continue;

            Place p = new Place();
            if (e.has("lat")) {
                p.lat = e.getDouble("lat");
                p.lon = e.getDouble("lon");
            } else if (e.has("center")) {
                p.lat = e.getJSONObject("center").getDouble("lat");
                p.lon = e.getJSONObject("center").getDouble("lon");
            } else {
                continue;
            }

            p.name = tags.optString("name", "");
            if (p.name.isEmpty()) continue; // skip unnamed entries

            p.type = tags.optString("amenity");
            p.phone = tags.optString("phone", tags.optString("contact:phone", ""));
            p.address = tags.optString("addr:street", tags.optString("addr:city", ""));
            out.add(p);
        }

        // Cache results
        cachedPlaces = new ArrayList<>(out);
        cacheTimestamp = System.currentTimeMillis();
        cachedLat = lat;
        cachedLon = lon;

        return out;
    }
}
