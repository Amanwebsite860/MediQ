package com.example.mediq.ui.patient;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mediq.R;
import com.example.mediq.data.remote.SessionManager;
import com.example.mediq.data.remote.SupabaseClient;
import com.example.mediq.util.ToastUtils;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Emergency screen. Calls only open the dialer (ACTION_DIAL) — the user always confirms the call.
 * Lists hospitals flagged emergency_24x7 (see sql/05_feature_upgrade.sql).
 */
public class EmergencyActivity extends AppCompatActivity {

    private LinearLayout layoutHospitals;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency);
        ((TextView) findViewById(R.id.tvScreenTitle)).setText(R.string.title_emergency);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        layoutHospitals = findViewById(R.id.layoutEmergencyHospitals);
        tvEmpty = findViewById(R.id.tvEmpty);

        findViewById(R.id.btnCallAmbulance).setOnClickListener(v -> dial("108"));
        findViewById(R.id.btnCallPolice).setOnClickListener(v -> dial("112"));
        findViewById(R.id.btnCallContact).setOnClickListener(v -> {
            String c = SessionManager.getInstance(this).getUserEmergencyContact();
            if (c == null || c.trim().isEmpty() || "Not set".equalsIgnoreCase(c.trim())) {
                ToastUtils.show(this, "Add an emergency contact in Profile first");
            } else {
                dial(c.replaceAll("[^0-9+]", ""));
            }
        });
        loadHospitals();
    }

    private void dial(String number) {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)));
        } catch (ActivityNotFoundException e) {
            ToastUtils.show(this, "No dialer available");
        }
    }

    private void loadHospitals() {
        SupabaseClient.getInstance().get(
                "hospitals?select=name,address,city,phone,emergency_phone,latitude,longitude&active=eq.true&emergency_24x7=eq.true&order=rating.desc",
                new SupabaseClient.Callback() {
                    @Override public void onSuccess(String json) {
                        try {
                            JSONArray arr = new JSONArray(json);
                            LayoutInflater inf = LayoutInflater.from(EmergencyActivity.this);
                            layoutHospitals.removeAllViews();
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);
                                View row = inf.inflate(R.layout.item_emergency_hospital, layoutHospitals, false);
                                ((TextView) row.findViewById(R.id.tvName)).setText(o.optString("name"));
                                String addr = SupabaseClient.optStringSafe(o, "address");
                                String city = SupabaseClient.optStringSafe(o, "city");
                                ((TextView) row.findViewById(R.id.tvMeta)).setText(
                                        (addr != null ? addr : "") + (city != null ? ", " + city : ""));
                                String phone = SupabaseClient.optStringSafe(o, "emergency_phone");
                                if (phone == null) phone = SupabaseClient.optStringSafe(o, "phone");
                                final String callNo = phone;
                                row.findViewById(R.id.btnCall).setOnClickListener(v -> {
                                    if (callNo == null) ToastUtils.show(EmergencyActivity.this, "No phone number listed");
                                    else dial(callNo.replaceAll("[^0-9+]", ""));
                                });
                                double lat = o.optDouble("latitude", 0), lon = o.optDouble("longitude", 0);
                                final String label = Uri.encode(o.optString("name"));
                                row.findViewById(R.id.btnDirections).setOnClickListener(v -> {
                                    Uri geo = (lat != 0 || lon != 0)
                                            ? Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon + "(" + label + ")")
                                            : Uri.parse("geo:0,0?q=" + label);
                                    try { startActivity(new Intent(Intent.ACTION_VIEW, geo)); }
                                    catch (ActivityNotFoundException e) { ToastUtils.show(EmergencyActivity.this, "No maps app available"); }
                                });
                                layoutHospitals.addView(row);
                            }
                            tvEmpty.setVisibility(arr.length() == 0 ? View.VISIBLE : View.GONE);
                        } catch (Exception e) { tvEmpty.setVisibility(View.VISIBLE); }
                    }
                    @Override public void onError(Exception e) {
                        // Most likely sql/05_feature_upgrade.sql hasn't been run yet (missing emergency_24x7 column)
                        tvEmpty.setVisibility(View.VISIBLE);
                    }
                });
    }
}
