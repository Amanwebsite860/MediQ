package com.example.mediq.ui.patient;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.mediq.R;
import com.example.mediq.data.model.Doctor;
import com.example.mediq.data.model.Hospital;
import com.example.mediq.data.remote.SupabaseClient;
import com.example.mediq.data.repo.DoctorRepository;
import com.example.mediq.data.repo.FavoritesRepository;
import com.example.mediq.ui.adapters.CardBinder;
import com.google.android.material.tabs.TabLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Saved doctors and hospitals (user_favorites). */
public class FavoritesActivity extends AppCompatActivity {

    private LinearLayout container;
    private TextView tvEmpty;
    private ProgressBar progress;
    private final List<Doctor> doctors = new ArrayList<>();
    private final List<Hospital> hospitals = new ArrayList<>();
    private int tab = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);
        ((TextView) findViewById(R.id.tvScreenTitle)).setText(R.string.title_saved);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        container = findViewById(R.id.layoutFavItems);
        tvEmpty = findViewById(R.id.tvEmpty);
        progress = findViewById(R.id.progress);

        TabLayout tabs = findViewById(R.id.tabFav);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab t) { tab = t.getPosition(); render(); }
            @Override public void onTabUnselected(TabLayout.Tab t) { }
            @Override public void onTabReselected(TabLayout.Tab t) { }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        FavoritesRepository.get().load(this, this::fetch);
    }

    private void fetch() {
        List<String> docIds = ids(true);
        if (docIds.isEmpty()) {
            doctors.clear();
            loadHospitals();
        } else {
            DoctorRepository.load("&id=in.(" + android.text.TextUtils.join(",", docIds) + ")", new DoctorRepository.Result() {
                @Override public void onResult(List<Doctor> list) { doctors.clear(); doctors.addAll(list); loadHospitals(); }
                @Override public void onError(Exception e) { loadHospitals(); }
            });
        }
    }

    private List<String> ids(boolean doctor) {
        return new ArrayList<>(FavoritesRepository.get().idsOf(doctor));
    }

    private void loadHospitals() {
        List<String> ids = ids(false);
        if (ids.isEmpty()) {
            hospitals.clear();
            progress.setVisibility(View.GONE);
            render();
            return;
        }
        SupabaseClient.getInstance().get("hospitals?select=*&id=in.(" + android.text.TextUtils.join(",", ids) + ")",
                new SupabaseClient.Callback() {
                    @Override public void onSuccess(String json) {
                        hospitals.clear();
                        try {
                            JSONArray arr = new JSONArray(json);
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);
                                Hospital h = new Hospital(
                                        SupabaseClient.optStringSafe(o, "id"),
                                        SupabaseClient.optStringSafe(o, "name"),
                                        SupabaseClient.optStringSafe(o, "type"),
                                        SupabaseClient.optStringSafe(o, "address"),
                                        SupabaseClient.optStringSafe(o, "city"), "",
                                        SupabaseClient.optStringSafe(o, "phone"),
                                        SupabaseClient.optStringSafe(o, "opening_hours"),
                                        null, R.drawable.ic_hospital_building,
                                        o.optDouble("rating", 0), o.optInt("review_count", 0), true,
                                        o.optDouble("latitude", 0), o.optDouble("longitude", 0));
                                String cover = SupabaseClient.optStringSafe(o, "image_url");
                                if (cover == null || cover.trim().isEmpty()) {
                                    org.json.JSONArray imgs = o.optJSONArray("image_urls");
                                    if (imgs != null && imgs.length() > 0) {
                                        cover = imgs.optString(0, null);
                                    }
                                }
                                h.setImageUrl(cover);
                                hospitals.add(h);
                            }
                        } catch (Exception ignored) { }
                        progress.setVisibility(View.GONE);
                        render();
                    }
                    @Override public void onError(Exception e) { progress.setVisibility(View.GONE); render(); }
                });
    }

    private void render() {
        container.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        if (tab == 0) {
            for (Doctor d : doctors) {
                if (!FavoritesRepository.get().isDoctorFav(d.getId())) continue;
                View card = inf.inflate(R.layout.item_doctor, container, false);
                CardBinder.bindDoctor(card, d, true);
                card.setOnClickListener(v -> {
                    Intent i = new Intent(this, DoctorProfileActivity.class);
                    i.putExtra("extra_doctor", d);
                    startActivity(i);
                });
                container.addView(card);
            }
        } else {
            for (Hospital h : hospitals) {
                if (!FavoritesRepository.get().isHospitalFav(h.getId())) continue;
                View card = inf.inflate(R.layout.item_hospital, container, false);
                ((TextView) card.findViewById(R.id.tvHospitalName)).setText(h.getName());
                ((TextView) card.findViewById(R.id.tvHospitalType)).setText(h.getType());
                ((TextView) card.findViewById(R.id.tvHospitalLocation)).setText(h.getDistanceAndCity());
                ((TextView) card.findViewById(R.id.tvHospitalRating)).setText(h.getFormattedRating());
                ((TextView) card.findViewById(R.id.tvHospitalStatus)).setText(h.getOpenStatusText());
                ImageView iv = card.findViewById(R.id.ivHospitalImage);
                if (h.getImageUrl() != null && !h.getImageUrl().isEmpty()) {
                    Glide.with(this).load(h.getImageUrl()).placeholder(R.drawable.ic_hospital_building).into(iv);
                } else {
                    iv.setImageResource(R.drawable.ic_hospital_building);
                }
                CardBinder.bindHospitalHeart(card, h);
                card.setOnClickListener(v -> {
                    Intent i = new Intent(this, HospitalDetailsActivity.class);
                    i.putExtra("extra_hospital", h);
                    i.putExtra("hospital_id", h.getId());
                    startActivity(i);
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(dp(16), 0, dp(16), 0);
                container.addView(card, lp);
            }
        }
        tvEmpty.setText(tab == 0 ? "🤍\nNo saved doctors yet\nTap the heart on any doctor" : "🤍\nNo saved hospitals yet\nTap the heart on any hospital");
        tvEmpty.setVisibility(container.getChildCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
