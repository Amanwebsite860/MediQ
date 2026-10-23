package com.example.mediq.ui.patient;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediq.R;
import com.example.mediq.data.model.Hospital;
import com.example.mediq.data.remote.SupabaseClient;
import com.example.mediq.ui.adapters.HospitalAdapter;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Lists nearby hospitals matching mockup 05_Nearby_Hospitals.png.
 * Calculates dynamic distances from user GPS, sorts by proximity, and loads data from Supabase.
 */
public class NearbyHospitalsActivity extends AppCompatActivity {

    private static final int REQ_LOC = 201;

    private RecyclerView rvHospitals;
    private HospitalAdapter adapter;
    private final List<Hospital> hospitalList = new ArrayList<>();
    private Location userLocation;
    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nearby_hospitals);

        ImageButton btnBack = findViewById(R.id.btnBack);
        ImageButton btnFilter = findViewById(R.id.btnFilter);
        EditText etSearch = findViewById(R.id.etSearchHospital);
        rvHospitals = findViewById(R.id.rvHospitals);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        btnBack.setOnClickListener(v -> finish());
        btnFilter.setOnClickListener(v ->
                com.example.mediq.util.ToastUtils.show(this, "Filters: Distance, Rating, Facilities")
        );

        rvHospitals.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HospitalAdapter(hospitalList, hospital -> {
            Intent intent = new Intent(NearbyHospitalsActivity.this, HospitalDetailsActivity.class);
            intent.putExtra("extra_hospital", hospital);
            intent.putExtra("hospital_id", hospital.getId());
            startActivity(intent);
        });
        rvHospitals.setAdapter(adapter);

        requestLocationAndFetch();

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int count, int after) {
                filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void requestLocationAndFetch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, loc -> {
                if (loc != null) {
                    userLocation = loc;
                }
                fetchHospitalsFromSupabase();
            }).addOnFailureListener(e -> fetchHospitalsFromSupabase());
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOC);
            fetchHospitalsFromSupabase();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_LOC && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.getLastLocation().addOnSuccessListener(this, loc -> {
                    if (loc != null) {
                        userLocation = loc;
                        recalculateDistances();
                    }
                });
            }
        }
    }

    private void filter(String query) {
        if (query.isEmpty()) {
            adapter.filterList(hospitalList);
            return;
        }
        List<Hospital> filtered = new ArrayList<>();
        for (Hospital h : hospitalList) {
            if (h.getName().toLowerCase().contains(query.toLowerCase()) ||
                h.getAddress().toLowerCase().contains(query.toLowerCase()) ||
                h.getType().toLowerCase().contains(query.toLowerCase())) {
                filtered.add(h);
            }
        }
        adapter.filterList(filtered);
    }

    private void sortHospitalsByDistance(List<Hospital> list) {
        if (userLocation == null || list == null || list.isEmpty()) return;
        Collections.sort(list, (h1, h2) -> {
            float[] r1 = new float[1];
            float[] r2 = new float[1];
            Location.distanceBetween(userLocation.getLatitude(), userLocation.getLongitude(), h1.getLatitude(), h1.getLongitude(), r1);
            Location.distanceBetween(userLocation.getLatitude(), userLocation.getLongitude(), h2.getLatitude(), h2.getLongitude(), r2);
            return Float.compare(r1[0], r2[0]);
        });
    }

    private void fetchHospitalsFromSupabase() {
        SupabaseClient.getInstance().get("hospitals?select=*&active=eq.true&order=rating.desc", new SupabaseClient.Callback() {
            @Override
            public void onSuccess(String jsonResponse) {
                try {
                    JSONArray array = new JSONArray(jsonResponse);
                    List<Hospital> loaded = new ArrayList<>();

                    for (int i = 0; i < array.length(); i++) {
                        JSONObject obj = array.getJSONObject(i);
                        String id = SupabaseClient.optStringSafe(obj, "id");
                        String name = SupabaseClient.optStringSafe(obj, "name");
                        String type = SupabaseClient.optStringSafe(obj, "type");
                        String address = SupabaseClient.optStringSafe(obj, "address");
                        String city = SupabaseClient.optStringSafe(obj, "city");
                        if (city == null) city = "Patna";
                        String phone = SupabaseClient.optStringSafe(obj, "phone");
                        String openingHours = SupabaseClient.optStringSafe(obj, "opening_hours");
                        if (openingHours == null) openingHours = "8:00 AM - 8:00 PM";
                        double rating = obj.optDouble("rating", 4.5);
                        int reviewCount = obj.optInt("review_count", 0);
                        boolean active = obj.optBoolean("active", true);
                        double lat = obj.optDouble("latitude", 25.6093);
                        double lng = obj.optDouble("longitude", 85.1376);

                        JSONArray facArray = obj.optJSONArray("facilities");
                        String[] facilities = null;
                        if (facArray != null) {
                            facilities = new String[facArray.length()];
                            for (int f = 0; f < facArray.length(); f++) {
                                facilities[f] = facArray.getString(f);
                            }
                        }

                        String distanceStr = "—";
                        if (userLocation != null) {
                            float[] r = new float[1];
                            Location.distanceBetween(userLocation.getLatitude(), userLocation.getLongitude(), lat, lng, r);
                            distanceStr = String.format(Locale.US, "%.1f km", r[0] / 1000f);
                        }

                        loaded.add(new Hospital(
                                id,
                                name,
                                type != null ? type : "Hospital",
                                address != null ? address : "",
                                city + ", Bihar",
                                distanceStr,
                                phone != null ? phone : "",
                                openingHours,
                                facilities,
                                R.drawable.ic_hospital_building,
                                rating,
                                reviewCount,
                                active,
                                lat,
                                lng
                        ));
                    }

                    sortHospitalsByDistance(loaded);

                    runOnUiThread(() -> {
                        hospitalList.clear();
                        hospitalList.addAll(loaded);
                        adapter.notifyDataSetChanged();
                    });
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onError(Exception error) {}
        });
    }

    private void recalculateDistances() {
        if (userLocation == null) return;
        for (Hospital h : hospitalList) {
            float[] r = new float[1];
            Location.distanceBetween(userLocation.getLatitude(), userLocation.getLongitude(),
                    h.getLatitude(), h.getLongitude(), r);
            h.setDistance(String.format(Locale.US, "%.1f km", r[0] / 1000f));
        }
        sortHospitalsByDistance(hospitalList);
        adapter.notifyDataSetChanged();
    }
}
