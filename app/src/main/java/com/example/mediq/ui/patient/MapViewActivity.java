package com.example.mediq.ui.patient;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.example.mediq.R;
import com.example.mediq.data.model.Hospital;
import com.example.mediq.data.remote.OverpassClient;
import com.example.mediq.data.remote.SupabaseClient;
import com.example.mediq.util.MapIcons;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Live MapView using osmdroid and OpenStreetMap Overpass API.
 * Free, open-source, requires no API key or billing, and displays both
 * MediQ bookable hospitals and real surrounding clinics/pharmacies.
 */
public class MapViewActivity extends AppCompatActivity {

    private static final int REQ_LOC = 101;
    private static final double FALLBACK_LAT = 25.6093;
    private static final double FALLBACK_LON = 85.1376;

    private MapView map;
    private MyLocationNewOverlay myLocation;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FloatingActionButton fabMyLocation;
    private ImageButton btnBack;
    private ImageButton btnFilter;
    private ChipGroup chipGroupMap;

    private final List<Marker> mediqMarkers = new ArrayList<>();
    private final List<Marker> hospitalOsmMarkers = new ArrayList<>();
    private final List<Marker> pharmacyOsmMarkers = new ArrayList<>();

    private final Map<Marker, Integer> kinds = new HashMap<>();
    private final Map<Marker, Hospital> hospitalOf = new HashMap<>();   // MediQ markers only
    private final Map<Marker, String[]> placeInfo = new HashMap<>();     // marker -> {name, type}
    private android.widget.EditText etMapSearch;
    private Marker selected;

    private boolean locationFixed = false;
    private boolean initialLoadDone = false;
    private double currentLat = FALLBACK_LAT;
    private double currentLon = FALLBACK_LON;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // osmdroid is now configured globally in MediQApp.onCreate(); no per-Activity setup needed.
        setContentView(R.layout.activity_map_view);

        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(14.0);
        map.getController().setCenter(new GeoPoint(FALLBACK_LAT, FALLBACK_LON));

        btnBack = findViewById(R.id.btnBack);
        btnFilter = findViewById(R.id.btnFilter);
        fabMyLocation = findViewById(R.id.fabMyLocation);
        chipGroupMap = findViewById(R.id.chipGroupMap);

        btnBack.setOnClickListener(v -> finish());
        // Re-run the search around wherever the map is currently centred
        btnFilter.setOnClickListener(v -> {
            org.osmdroid.api.IGeoPoint c = map.getMapCenter();
            com.example.mediq.util.ToastUtils.show(this, "Searching this area...");
            loadAround(c.getLatitude(), c.getLongitude());
        });

        fabMyLocation.setOnClickListener(v -> {
            if (myLocation != null && myLocation.getMyLocation() != null) {
                map.getController().animateTo(myLocation.getMyLocation());
            } else {
                com.example.mediq.util.ToastUtils.show(this, "Locating your position...");
            }
        });

        setupChipFilters();

        etMapSearch = findViewById(R.id.etMapSearch);
        etMapSearch.setOnEditorActionListener((tv, actionId, ev) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                runMapSearch(tv.getText().toString().trim());
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(tv.getWindowToken(), 0);
                return true;
            }
            return false;
        });
        findViewById(R.id.btnMapList).setOnClickListener(v -> showListSheet());

        if (hasLocationPermission()) {
            enableLocation();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQ_LOC);
        }
    }

    /** Makes the tapped marker bigger and returns the previous one to normal size. */
    private void select(Marker m) {
        if (selected != null && kinds.containsKey(selected)) {
            selected.setIcon(MapIcons.get(this, kinds.get(selected), false));
        }
        selected = m;
        Integer k = kinds.get(m);
        if (k != null) m.setIcon(MapIcons.get(this, k, true));
        map.invalidate();
    }

    private void setupChipFilters() {
        if (chipGroupMap == null) return;
        chipGroupMap.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            applyMarkerFilter(id);
        });
    }

    private void applyMarkerFilter(int chipId) {
        // Clear all place markers from map overlay (preserve myLocation overlay)
        List<Overlay> toRemove = new ArrayList<>();
        for (Overlay o : map.getOverlays()) {
            if (o instanceof Marker) {
                toRemove.add(o);
            }
        }
        map.getOverlays().removeAll(toRemove);

        if (chipId == R.id.chipEmergency) {
            for (Marker m : mediqMarkers) {
                Integer k = kinds.get(m);
                if (k != null && k == MapIcons.EMERGENCY) map.getOverlays().add(m);
            }
        } else if (chipId == R.id.chipPharmacies) {
            map.getOverlays().addAll(pharmacyOsmMarkers);
        } else if (chipId == R.id.chipHospitals) {
            map.getOverlays().addAll(mediqMarkers);
            map.getOverlays().addAll(hospitalOsmMarkers);
        } else {
            // All
            map.getOverlays().addAll(mediqMarkers);
            map.getOverlays().addAll(hospitalOsmMarkers);
            map.getOverlays().addAll(pharmacyOsmMarkers);
        }
        map.invalidate();
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_LOC && hasLocationPermission()) {
            enableLocation();
        } else {
            com.example.mediq.util.ToastUtils.show(this, "Location off - showing hospitals around Patna");
            loadAround(FALLBACK_LAT, FALLBACK_LON);
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void enableLocation() {
        myLocation = new MyLocationNewOverlay(new GpsMyLocationProvider(this), map);
        myLocation.enableMyLocation();
        map.getOverlays().add(myLocation);

        // Fallback timer: if GPS fix doesn't arrive in 3.5s, load fallback location markers
        mainHandler.postDelayed(() -> {
            if (!locationFixed && !isFinishing() && !initialLoadDone) {
                loadAround(FALLBACK_LAT, FALLBACK_LON);
            }
        }, 3500);

        myLocation.runOnFirstFix(() -> runOnUiThread(() -> {
            GeoPoint p = myLocation.getMyLocation();
            if (p != null) {
                locationFixed = true;
                currentLat = p.getLatitude();
                currentLon = p.getLongitude();
                map.getController().animateTo(p);
                loadAround(currentLat, currentLon);
            }
        }));
    }

    private void loadAround(double lat, double lon) {
        initialLoadDone = true;
        currentLat = lat;
        currentLon = lon;

        // 1. MediQ partner hospitals (Bookable with queues)
        fetchPartnerHospitals(true);

        // 2. Real nearby clinics and pharmacies from OpenStreetMap Overpass
        io.execute(() -> {
            try {
                List<OverpassClient.Place> places = OverpassClient.fetch(lat, lon, 4000);
                runOnUiThread(() -> { if (!isFinishing()) addOsmMarkers(places); });
            } catch (Exception ignored) {
            }
        });
    }

    /** extended = also ask for emergency_24x7 / image_url; falls back if those columns aren't there yet. */
    private void fetchPartnerHospitals(boolean extended) {
        String cols = "id,name,type,address,city,latitude,longitude,phone,rating,review_count,opening_hours"
                + (extended ? ",emergency_24x7,image_url" : "");
        SupabaseClient.getInstance().get("hospitals?select=" + cols + "&active=eq.true", null,
                new SupabaseClient.Callback() {
                    @Override
                    public void onSuccess(String json) {
                        runOnUiThread(() -> { if (!isFinishing()) addMediqMarkers(json); });
                    }

                    @Override
                    public void onError(Exception e) {
                        if (extended) fetchPartnerHospitals(false);
                    }
                });
    }

    private void addMediqMarkers(String json) {
        try {
            JSONArray arr = new JSONArray(json);
            forget(mediqMarkers);
            mediqMarkers.clear();
            hospitalOf.clear();
            selected = null;
            Hospital nearest = null;
            GeoPoint nearestPos = null;
            float nearestDist = Float.MAX_VALUE;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject h = arr.getJSONObject(i);
                if (h.isNull("latitude") || h.isNull("longitude")) continue; // can't pin it without coordinates
                double lat = h.optDouble("latitude", 0);
                double lon = h.optDouble("longitude", 0);
                if (lat == 0 && lon == 0) continue;

                Marker m = new Marker(map);
                GeoPoint pos = new GeoPoint(lat, lon);
                m.setPosition(pos);
                m.setTitle(SupabaseClient.optStringSafe(h, "name"));
                m.setSnippet("Book appointments on MediQ");
                final boolean emergency = h.optBoolean("emergency_24x7", false);
                final int kind = emergency ? MapIcons.EMERGENCY : MapIcons.PARTNER;
                m.setIcon(MapIcons.get(this, kind, false));
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                kinds.put(m, kind);

                final Hospital hospObj = new Hospital(
                        SupabaseClient.optStringSafe(h, "id"),
                        SupabaseClient.optStringSafe(h, "name"),
                        SupabaseClient.optStringSafe(h, "type"),
                        SupabaseClient.optStringSafe(h, "address"),
                        SupabaseClient.optStringSafe(h, "city"),
                        "",
                        SupabaseClient.optStringSafe(h, "phone"),
                        SupabaseClient.optStringSafe(h, "opening_hours"),
                        null,
                        R.drawable.ic_hospital_building,
                        h.optDouble("rating", 4.5),
                        h.optInt("review_count", 0),
                        true,
                        lat,
                        lon
                );

                hospObj.setImageUrl(SupabaseClient.optStringSafe(h, "image_url"));

                m.setOnMarkerClickListener((mk, mv) -> {
                    select(mk);
                    showCard(hospObj.getName(), hospObj.getType(), mk.getPosition(), true, hospObj);
                    return true;
                });

                mediqMarkers.add(m);
                hospitalOf.put(m, hospObj);
                placeInfo.put(m, new String[]{hospObj.getName(), hospObj.getType()});

                float[] dist = new float[1];
                Location.distanceBetween(currentLat, currentLon, lat, lon, dist);
                if (dist[0] < nearestDist) {
                    nearestDist = dist[0];
                    nearest = hospObj;
                    nearestPos = pos;
                }
            }
            if (nearest != null) {
                showCard(nearest.getName(), nearest.getType(), nearestPos, true, nearest);
            }

            int checkedId = chipGroupMap != null && !chipGroupMap.getCheckedChipIds().isEmpty()
                    ? chipGroupMap.getCheckedChipIds().get(0) : R.id.chipAll;
            applyMarkerFilter(checkedId);

        } catch (Exception ignored) {
        }
    }

    private void addOsmMarkers(List<OverpassClient.Place> places) {
        forget(hospitalOsmMarkers);
        forget(pharmacyOsmMarkers);
        hospitalOsmMarkers.clear();
        pharmacyOsmMarkers.clear();

        for (OverpassClient.Place p : places) {
            boolean pharmacy = "pharmacy".equalsIgnoreCase(p.type);
            Marker m = new Marker(map);
            m.setPosition(new GeoPoint(p.lat, p.lon));
            m.setTitle(p.name);
            m.setSnippet(pharmacy ? "Pharmacy (OpenStreetMap)" : "Healthcare (OpenStreetMap)");
            final int kind = pharmacy ? MapIcons.PHARMACY : MapIcons.CLINIC;
            m.setIcon(MapIcons.get(this, kind, false));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            kinds.put(m, kind);
            placeInfo.put(m, new String[]{p.name, pharmacy ? "Pharmacy" : "Clinic / Hospital"});

            m.setOnMarkerClickListener((mk, mv) -> {
                select(mk);
                showCard(p.name, pharmacy ? "Pharmacy" : "Clinic / Hospital",
                        mk.getPosition(), false, null);
                return true;
            });

            if (pharmacy) {
                pharmacyOsmMarkers.add(m);
            } else {
                hospitalOsmMarkers.add(m);
            }
        }

        int checkedId = chipGroupMap != null && !chipGroupMap.getCheckedChipIds().isEmpty()
                ? chipGroupMap.getCheckedChipIds().get(0) : R.id.chipAll;
        applyMarkerFilter(checkedId);
    }

    /** Drops the bookkeeping for markers that are about to be replaced (each list is loaded separately). */
    private void forget(List<Marker> markers) {
        for (Marker m : markers) { kinds.remove(m); placeInfo.remove(m); hospitalOf.remove(m); }
    }

    private GeoPoint myPosition() {
        return (myLocation != null && myLocation.getMyLocation() != null)
                ? myLocation.getMyLocation() : new GeoPoint(currentLat, currentLon);
    }

    private float distanceKmTo(GeoPoint p) {
        GeoPoint me = myPosition();
        float[] r = new float[1];
        Location.distanceBetween(me.getLatitude(), me.getLongitude(), p.getLatitude(), p.getLongitude(), r);
        return r[0] / 1000f;
    }

    /** Centres the map on a marker, enlarges it and opens its card. */
    private void focusMarker(Marker m) {
        if (!map.getOverlays().contains(m)) {      // hidden by the current chip filter -> show everything
            chipGroupMap.check(R.id.chipAll);
            applyMarkerFilter(R.id.chipAll);
        }
        map.getController().setZoom(16.0);
        map.getController().animateTo(m.getPosition());
        select(m);
        String[] info = placeInfo.get(m);
        Hospital h = hospitalOf.get(m);
        showCard(info != null ? info[0] : "", info != null ? info[1] : "", m.getPosition(), h != null, h);
    }

    /** Search box: first a hospital / pharmacy by name, otherwise an area name. */
    private void runMapSearch(String q) {
        if (q.isEmpty()) return;
        String needle = q.toLowerCase(Locale.ROOT);
        Marker best = null;
        for (Map.Entry<Marker, String[]> e : placeInfo.entrySet()) {
            String name = e.getValue()[0] == null ? "" : e.getValue()[0].toLowerCase(Locale.ROOT);
            if (!name.contains(needle)) continue;
            // MediQ hospitals win over OpenStreetMap places
            if (best == null || (hospitalOf.containsKey(e.getKey()) && !hospitalOf.containsKey(best))) best = e.getKey();
        }
        if (best != null) { focusMarker(best); return; }

        io.execute(() -> {
            try {
                List<android.location.Address> res = new android.location.Geocoder(this, Locale.getDefault()).getFromLocationName(q, 1);
                if (res != null && !res.isEmpty()) {
                    final double la = res.get(0).getLatitude(), lo = res.get(0).getLongitude();
                    runOnUiThread(() -> {
                        if (isFinishing()) return;
                        map.getController().setZoom(14.0);
                        map.getController().animateTo(new GeoPoint(la, lo));
                        loadAround(la, lo);
                    });
                    return;
                }
            } catch (Exception ignored) { }
            runOnUiThread(() -> com.example.mediq.util.ToastUtils.show(this, "Nothing found for \"" + q + "\""));
        });
    }

    /** Same places as the map, as a list sorted by distance. */
    private void showListSheet() {
        List<Marker> all = new ArrayList<>(mediqMarkers);
        all.addAll(hospitalOsmMarkers);
        all.addAll(pharmacyOsmMarkers);
        if (all.isEmpty()) {
            com.example.mediq.util.ToastUtils.show(this, "Still loading places...");
            return;
        }
        java.util.Collections.sort(all, (a, b) -> Float.compare(distanceKmTo(a.getPosition()), distanceKmTo(b.getPosition())));

        final com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = com.example.mediq.util.UiUtils.dp(root, 20);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Nearby places");
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(this, R.color.colorTextPrimary));
        root.addView(title);

        android.widget.LinearLayout list = new android.widget.LinearLayout(this);
        list.setOrientation(android.widget.LinearLayout.VERTICAL);
        int shown = 0;
        for (Marker m : all) {
            if (shown++ >= 40) break;
            String[] info = placeInfo.get(m);
            if (info == null) continue;
            Integer kind = kinds.get(m);
            String icon = kind == null ? "" : (kind == MapIcons.EMERGENCY ? "\uD83D\uDEA8 " : kind == MapIcons.PARTNER ? "\uD83C\uDFE5 "
                    : kind == MapIcons.PHARMACY ? "\uD83D\uDC8A " : "\u2795 ");
            Hospital h = hospitalOf.get(m);
            String sub = info[1] + " \u2022 " + String.format(Locale.US, "%.1f km", distanceKmTo(m.getPosition()))
                    + (h != null ? " \u2022 " + h.getOpenStatusText() : "");

            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.VERTICAL);
            row.setPadding(0, com.example.mediq.util.UiUtils.dp(row, 12), 0, com.example.mediq.util.UiUtils.dp(row, 12));
            TextView n = new TextView(this);
            n.setText(icon + info[0]);
            n.setTextSize(15);
            n.setTypeface(null, android.graphics.Typeface.BOLD);
            n.setTextColor(ContextCompat.getColor(this, R.color.colorTextPrimary));
            TextView d = new TextView(this);
            d.setText(sub);
            d.setTextSize(12);
            d.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary));
            row.addView(n);
            row.addView(d);
            row.setOnClickListener(v -> { sheet.dismiss(); focusMarker(m); });
            list.addView(row);
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(list);
        root.addView(sv);
        sheet.setContentView(root);
        sheet.show();
    }

    private void openDirections(String name, GeoPoint pos) {
        Uri geo = Uri.parse("geo:" + pos.getLatitude() + "," + pos.getLongitude()
                + "?q=" + pos.getLatitude() + "," + pos.getLongitude() + "(" + Uri.encode(name) + ")");
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, geo));
        } catch (android.content.ActivityNotFoundException e) {
            com.example.mediq.util.ToastUtils.show(this, "No maps app available");
        }
    }

    private Drawable tinted(int drawableRes, int colorRes) {
        Drawable d = ContextCompat.getDrawable(this, drawableRes);
        if (d != null) {
            Drawable wrapped = DrawableCompat.wrap(d.mutate());
            DrawableCompat.setTint(wrapped, ContextCompat.getColor(this, colorRes));
            return wrapped;
        }
        return ContextCompat.getDrawable(this, R.drawable.ic_location_pin);
    }

    private void showCard(String name, String type, GeoPoint pos, boolean onMediq, Hospital hospitalObj) {
        TextView tvName = findViewById(R.id.tvSelectedHospitalName);
        TextView tvType = findViewById(R.id.tvSelectedHospitalType);
        TextView tvLocation = findViewById(R.id.tvSelectedHospitalLocation);
        MaterialButton btn = findViewById(R.id.btnViewDetails);

        tvName.setText(name);
        tvType.setText(hospitalObj != null ? type + " \u2022 " + hospitalObj.getOpenStatusText() : type);

        // Rating + photo for MediQ hospitals; plain details for OpenStreetMap places
        TextView tvRating = findViewById(R.id.tvSelectedHospitalRating);
        View ratingRow = (View) tvRating.getParent();
        android.widget.ImageView ivPhoto = findViewById(R.id.ivSelectedHospital);
        View actions = findViewById(R.id.layoutCardActions);
        if (hospitalObj != null) {
            ratingRow.setVisibility(View.VISIBLE);
            tvRating.setText(hospitalObj.getFormattedRating());
            String img = hospitalObj.getImageUrl();
            if (img != null && !img.isEmpty()) {
                com.bumptech.glide.Glide.with(this).load(img).placeholder(R.drawable.ic_hospital_building).into(ivPhoto);
            } else {
                ivPhoto.setImageResource(R.drawable.ic_hospital_building);
            }
            actions.setVisibility(View.VISIBLE);
            findViewById(R.id.btnCardCall).setOnClickListener(v -> {
                String ph = hospitalObj.getPhone();
                if (ph == null || ph.trim().isEmpty()) {
                    com.example.mediq.util.ToastUtils.show(this, "No phone number listed");
                } else {
                    startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + ph.replaceAll("[^0-9+]", ""))));
                }
            });
            findViewById(R.id.btnCardDirections).setOnClickListener(v -> openDirections(name, pos));
        } else {
            ratingRow.setVisibility(View.GONE);
            ivPhoto.setImageResource(R.drawable.ic_hospital);
            actions.setVisibility(View.GONE);
        }

        // Calculate dynamic distance from user
        GeoPoint me = (myLocation != null && myLocation.getMyLocation() != null)
                ? myLocation.getMyLocation() : new GeoPoint(currentLat, currentLon);

        float[] r = new float[1];
        Location.distanceBetween(me.getLatitude(), me.getLongitude(),
                pos.getLatitude(), pos.getLongitude(), r);
        float km = r[0] / 1000f;
        tvLocation.setText(String.format(Locale.US, "%.1f km away", km));
        if (hospitalObj != null) {
            hospitalObj.setDistance(String.format(Locale.US, "%.1f km", km));
        }

        if (onMediq) {
            btn.setText("View Details & Book");
            btn.setOnClickListener(v -> {
                Intent i = new Intent(this, HospitalDetailsActivity.class);
                if (hospitalObj != null) {
                    i.putExtra("extra_hospital", hospitalObj);
                    i.putExtra("hospital_id", hospitalObj.getId());
                }
                startActivity(i);
            });
        } else {
            btn.setText("Get Directions");
            btn.setOnClickListener(v -> openDirections(name, pos));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (map != null) map.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        if (map != null) map.onDetach();
        io.shutdown();
    }
}
