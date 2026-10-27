package com.example.mediq.ui.patient;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.mediq.R;
import com.example.mediq.data.model.Hospital;
import com.example.mediq.data.remote.SessionManager;
import com.example.mediq.data.remote.SupabaseClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Hospital Details screen with live data from Supabase and user_favorites sync.
 */
public class HospitalDetailsActivity extends AppCompatActivity {

    private static final String TAG = "HospitalDetailsActivity";

    private Hospital hospital;
    private boolean isFavorite = false;

    private ImageView ivHospitalBanner;
    private TextView tvName;
    private TextView tvType;
    private TextView tvRating;
    private TextView tvStatus;
    private TextView tvAddress;
    private TextView tvPhone;
    private TextView tvFacilities;
    private ImageButton btnFavorite;
    private TabLayout tabLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hospital_details);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnFavorite = findViewById(R.id.btnFavorite);
        ivHospitalBanner = findViewById(R.id.ivHospitalBanner);
        tvName = findViewById(R.id.tvHospitalDetailsName);
        tvType = findViewById(R.id.tvHospitalDetailsType);
        tvRating = findViewById(R.id.tvHospitalDetailsRating);
        tvStatus = findViewById(R.id.tvHospitalDetailsStatus);
        tvAddress = findViewById(R.id.tvHospitalAddress);
        tvPhone = findViewById(R.id.tvHospitalPhone);
        tvFacilities = findViewById(R.id.tvHospitalFacilities);
        tabLayout = findViewById(R.id.tabLayoutHospital);
        MaterialButton btnBookAppointment = findViewById(R.id.btnBookAppointment);

        if (getIntent().hasExtra("extra_hospital")) {
            hospital = (Hospital) getIntent().getSerializableExtra("extra_hospital");
            populateViews();
            checkFavoriteStatus();
            fetchLatestHospitalData();
        } else if (getIntent().hasExtra("hospital_id")) {
            fetchHospitalById(getIntent().getStringExtra("hospital_id"));
        } else {
            com.example.mediq.util.ToastUtils.show(this, "Hospital details not available");
            finish();
            return;
        }

        btnBack.setOnClickListener(v -> finish());

        btnFavorite.setOnClickListener(v -> toggleFavorite());

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 1) { // "Doctors" tab
                    openDoctorList();
                } else if (tab.getPosition() == 2) { // "Services"
                    showServicesSheet();
                } else if (tab.getPosition() == 3) { // "Reviews"
                    showReviewsSheet();
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                if (tab.getPosition() == 1) {
                    openDoctorList();
                } else if (tab.getPosition() == 2) {
                    showServicesSheet();
                } else if (tab.getPosition() == 3) {
                    showReviewsSheet();
                }
            }
        });

        btnBookAppointment.setOnClickListener(v -> openDoctorList());
        setupQuickActions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Coming back from the Doctors list: move the blue indicator back to Overview
        if (tabLayout != null && tabLayout.getSelectedTabPosition() != 0) {
            TabLayout.Tab overview = tabLayout.getTabAt(0);
            if (overview != null) overview.select();
        }
    }

    private void setupQuickActions() {
        findViewById(R.id.btnActionCall).setOnClickListener(v -> {
            String phone = hospital != null ? hospital.getPhone() : null;
            if (phone == null || phone.trim().isEmpty()) {
                com.example.mediq.util.ToastUtils.show(this, "No phone number listed");
                return;
            }
            startActivity(new Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + phone.replaceAll("[^0-9+]", ""))));
        });
        findViewById(R.id.btnActionDirections).setOnClickListener(v -> {
            if (hospital == null) return;
            double lat = hospital.getLatitude(), lon = hospital.getLongitude();
            String label = android.net.Uri.encode(hospital.getName() == null ? "Hospital" : hospital.getName());
            android.net.Uri geo = (lat != 0 || lon != 0)
                    ? android.net.Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon + "(" + label + ")")
                    : android.net.Uri.parse("geo:0,0?q=" + label);
            try { startActivity(new Intent(Intent.ACTION_VIEW, geo)); }
            catch (android.content.ActivityNotFoundException e) {
                com.example.mediq.util.ToastUtils.show(this, "No maps app available");
            }
        });
        findViewById(R.id.btnActionShare).setOnClickListener(v -> {
            if (hospital == null) return;
            String text = hospital.getName() + (hospital.getAddress() != null ? "\n" + hospital.getAddress() : "")
                    + (hospital.getPhone() != null ? "\nPhone: " + hospital.getPhone() : "");
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(send, "Share hospital"));
        });
    }

    private void checkFavoriteStatus() {
        if (hospital == null || hospital.getId() == null) return;
        String myId = SessionManager.getInstance(this).getUserId();
        if (myId == null || myId.isEmpty()) return;

        SupabaseClient.getInstance().get("user_favorites?user_id=eq." + myId + "&hospital_id=eq." + hospital.getId() + "&select=id", new SupabaseClient.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONArray arr = new JSONArray(response);
                    isFavorite = arr.length() > 0;
                    updateFavoriteIcon();
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onError(Exception error) {}
        });
    }

    private void toggleFavorite() {
        if (hospital == null || hospital.getId() == null) return;
        String myId = SessionManager.getInstance(this).getUserId();
        if (myId == null || myId.isEmpty()) {
            com.example.mediq.util.ToastUtils.show(this, "Sign in to save hospitals");
            return;
        }

        if (isFavorite) {
            SupabaseClient.getInstance().delete("user_favorites?user_id=eq." + myId + "&hospital_id=eq." + hospital.getId(), new SupabaseClient.Callback() {
                @Override
                public void onSuccess(String response) {
                    isFavorite = false;
                    updateFavoriteIcon();
                    com.example.mediq.util.ToastUtils.show(HospitalDetailsActivity.this, "Removed from saved hospitals");
                }

                @Override
                public void onError(Exception error) {
                    com.example.mediq.util.ToastUtils.show(HospitalDetailsActivity.this, "Failed to update favorite: " + error.getMessage());
                }
            });
        } else {
            try {
                JSONObject body = new JSONObject();
                body.put("user_id", myId);
                body.put("hospital_id", hospital.getId());

                SupabaseClient.getInstance().post("user_favorites", body, new SupabaseClient.Callback() {
                    @Override
                    public void onSuccess(String response) {
                        isFavorite = true;
                        updateFavoriteIcon();
                        com.example.mediq.util.ToastUtils.show(HospitalDetailsActivity.this, "Saved to favorites");
                    }

                    @Override
                    public void onError(Exception error) {
                        com.example.mediq.util.ToastUtils.show(HospitalDetailsActivity.this, "Failed to save: " + error.getMessage());
                    }
                });
            } catch (Exception ignored) {}
        }
    }

    private void updateFavoriteIcon() {
        if (btnFavorite == null) return;
        if (isFavorite) {
            btnFavorite.setImageResource(R.drawable.ic_heart_filled);
            btnFavorite.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.colorError));
        } else {
            btnFavorite.setImageResource(R.drawable.ic_heart_outline);
            btnFavorite.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.colorTextPrimary));
        }
    }

    private void fetchHospitalById(String hospitalId) {
        SupabaseClient.getInstance().get("hospitals?id=eq." + hospitalId + "&select=*", new SupabaseClient.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONArray arr = new JSONArray(response);
                    if (arr.length() > 0) {
                        JSONObject obj = arr.getJSONObject(0);
                        JSONArray facArr = obj.optJSONArray("facilities");
                        String[] facilities = null;
                        if (facArr != null) {
                            facilities = new String[facArr.length()];
                            for (int i = 0; i < facArr.length(); i++) {
                                facilities[i] = facArr.getString(i);
                            }
                        }

                        hospital = new Hospital(
                                obj.getString("id"),
                                obj.getString("name"),
                                obj.optString("type", "Hospital"),
                                obj.getString("address"),
                                obj.optString("city", "Patna"),
                                "",
                                obj.optString("phone", ""),
                                obj.optString("opening_hours", "8:00 AM - 8:00 PM"),
                                facilities,
                                R.drawable.ic_hospital_illustration,
                                obj.optDouble("rating", 4.5),
                                obj.optInt("review_count", 0),
                                obj.optBoolean("active", true),
                                obj.optDouble("latitude", 25.6093),
                                obj.optDouble("longitude", 85.1376)
                        );
                        String cover = SupabaseClient.optStringSafe(obj, "image_url");
                        if (cover == null || cover.trim().isEmpty()) {
                            JSONArray imgs = obj.optJSONArray("image_urls");
                            if (imgs != null && imgs.length() > 0) {
                                cover = imgs.optString(0, null);
                            }
                        }
                        hospital.setImageUrl(cover);
                        populateViews();
                        checkFavoriteStatus();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse hospital", e);
                }
            }

            @Override
            public void onError(Exception error) {}
        });
    }

    private void fetchLatestHospitalData() {
        if (hospital == null || hospital.getId() == null) return;
        SupabaseClient.getInstance().get("hospitals?id=eq." + hospital.getId() + "&select=image_url,image_urls", new SupabaseClient.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONArray arr = new JSONArray(response);
                    if (arr.length() > 0) {
                        JSONObject obj = arr.getJSONObject(0);
                        String cover = SupabaseClient.optStringSafe(obj, "image_url");
                        if (cover == null || cover.trim().isEmpty()) {
                            JSONArray imgs = obj.optJSONArray("image_urls");
                            if (imgs != null && imgs.length() > 0) {
                                cover = imgs.optString(0, null);
                            }
                        }
                        if (cover != null && !cover.trim().isEmpty()) {
                            hospital.setImageUrl(cover);
                            if (ivHospitalBanner != null && !isFinishing() && !isDestroyed()) {
                                Glide.with(HospitalDetailsActivity.this)
                                        .load(cover)
                                        .placeholder(R.drawable.ic_hospital_illustration)
                                        .error(R.drawable.ic_hospital_illustration)
                                        .centerCrop()
                                        .into(ivHospitalBanner);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            @Override
            public void onError(Exception error) {}
        });
    }

    private void populateViews() {
        if (hospital == null) return;
        tvName.setText(hospital.getName());
        tvType.setText(hospital.getType());
        tvRating.setText(hospital.getFormattedRating() + " reviews");
        Boolean open = hospital.isOpenNow();
        tvStatus.setText(hospital.getOpenStatusText());
        boolean closed = open != null && !open;
        tvStatus.setBackgroundResource(closed ? R.drawable.bg_badge_closed : R.drawable.bg_badge_success);
        tvStatus.setTextColor(ContextCompat.getColor(this, closed ? R.color.colorSos : R.color.colorSuccess));
        tvAddress.setText(hospital.getAddress());
        tvPhone.setText(hospital.getPhone());

        if (hospital.getFacilities() != null) {
            tvFacilities.setText(android.text.TextUtils.join(", ", hospital.getFacilities()));
        }

        if (ivHospitalBanner != null) {
            String imgUrl = hospital.getImageUrl();
            if (imgUrl != null && !imgUrl.trim().isEmpty()) {
                Glide.with(this)
                        .load(imgUrl)
                        .placeholder(R.drawable.ic_hospital_illustration)
                        .error(R.drawable.ic_hospital_illustration)
                        .centerCrop()
                        .into(ivHospitalBanner);
            } else {
                ivHospitalBanner.setImageResource(R.drawable.ic_hospital_illustration);
            }
        }
    }

    private void openDoctorList() {
        if (hospital == null) return;
        Intent intent = new Intent(this, DoctorListActivity.class);
        intent.putExtra(DoctorListActivity.EXTRA_HOSPITAL_NAME, hospital.getName());
        intent.putExtra(DoctorListActivity.EXTRA_HOSPITAL_ID, hospital.getId());
        startActivity(intent);
    }

    // ------------------------------------------------------------------ Services & Reviews sheets
    private com.google.android.material.bottomsheet.BottomSheetDialog newSheet(String title, android.widget.LinearLayout body) {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = com.example.mediq.util.UiUtils.dp(root, 20);
        root.setPadding(pad, pad, pad, pad);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(18);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.colorTextPrimary));
        root.addView(t);

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(body);
        root.addView(sv);
        sheet.setContentView(root);
        // go back to Overview when the sheet closes so the tab strip doesn't look stuck
        sheet.setOnDismissListener(d -> {
            TabLayout.Tab overview = tabLayout.getTabAt(0);
            if (overview != null) overview.select();
        });
        return sheet;
    }

    private TextView sheetText(String text, int sizeSp, int colorRes, boolean bold, int topMarginDp) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sizeSp);
        tv.setTextColor(androidx.core.content.ContextCompat.getColor(this, colorRes));
        if (bold) tv.setTypeface(null, android.graphics.Typeface.BOLD);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = com.example.mediq.util.UiUtils.dp(tv, topMarginDp);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void showServicesSheet() {
        if (hospital == null) return;
        android.widget.LinearLayout body = new android.widget.LinearLayout(this);
        body.setOrientation(android.widget.LinearLayout.VERTICAL);

        String[] f = hospital.getFacilities();
        if (f == null || f.length == 0) {
            body.addView(sheetText("This hospital hasn't listed its services yet.", 14, R.color.colorTextSecondary, false, 12));
        } else {
            for (String item : f) {
                if (item == null || item.trim().isEmpty()) continue;
                body.addView(sheetText("✔  " + item.trim(), 15, R.color.colorTextPrimary, false, 12));
            }
        }
        String hours = hospital.getOpeningHours();
        if (hours != null && !hours.isEmpty()) {
            body.addView(sheetText("Opening hours", 13, R.color.colorTextSecondary, true, 20));
            body.addView(sheetText(hours, 15, R.color.colorTextPrimary, false, 4));
        }
        final android.widget.LinearLayout extra = new android.widget.LinearLayout(this);
        extra.setOrientation(android.widget.LinearLayout.VERTICAL);
        body.addView(extra, 0);
        newSheet("Services & facilities", body).show();

        // About text + photo gallery entered by the hospital's receptionist
        SupabaseClient.getInstance().get("hospitals?id=eq." + hospital.getId() + "&select=description,image_urls",
                new SupabaseClient.Callback() {
                    @Override public void onSuccess(String json) {
                        try {
                            JSONArray arr = new JSONArray(json);
                            if (arr.length() == 0) return;
                            JSONObject h = arr.getJSONObject(0);
                            String desc = SupabaseClient.optStringSafe(h, "description");
                            if (desc != null && !desc.isEmpty()) extra.addView(sheetText(desc, 14, R.color.colorTextSecondary, false, 12));
                            JSONArray imgs = h.optJSONArray("image_urls");
                            if (imgs != null && imgs.length() > 0) {
                                extra.addView(sheetText("Photos", 13, R.color.colorTextSecondary, true, 16));
                                android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(HospitalDetailsActivity.this);
                                hs.setHorizontalScrollBarEnabled(false);
                                android.widget.LinearLayout row = new android.widget.LinearLayout(HospitalDetailsActivity.this);
                                row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                                for (int i = 0; i < imgs.length(); i++) {
                                    android.widget.ImageView iv = new android.widget.ImageView(HospitalDetailsActivity.this);
                                    android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                                            com.example.mediq.util.UiUtils.dp(row, 220), com.example.mediq.util.UiUtils.dp(row, 140));
                                    lp.setMarginEnd(com.example.mediq.util.UiUtils.dp(row, 8));
                                    lp.topMargin = com.example.mediq.util.UiUtils.dp(row, 8);
                                    iv.setLayoutParams(lp);
                                    iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                                    com.bumptech.glide.Glide.with(HospitalDetailsActivity.this).load(imgs.getString(i)).into(iv);
                                    row.addView(iv);
                                }
                                hs.addView(row);
                                extra.addView(hs);
                            }
                        } catch (Exception ignored) { }
                    }
                    @Override public void onError(Exception e) { }
                });
    }

    private void showReviewsSheet() {
        if (hospital == null || hospital.getId() == null) return;
        final android.widget.LinearLayout body = new android.widget.LinearLayout(this);
        body.setOrientation(android.widget.LinearLayout.VERTICAL);

        TextView write = sheetText("✍  Write a review", 14, R.color.colorPrimary, true, 12);
        write.setOnClickListener(v -> showWriteHospitalReviewDialog());
        body.addView(write);

        final android.widget.LinearLayout list = new android.widget.LinearLayout(this);
        list.setOrientation(android.widget.LinearLayout.VERTICAL);
        list.addView(sheetText("Loading reviews…", 14, R.color.colorTextSecondary, false, 12));
        body.addView(list);

        final com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                newSheet("Patient reviews · " + hospital.getFormattedRating(), body);
        sheet.show();

        String q = "hospital_reviews?hospital_id=eq." + hospital.getId()
                + "&select=patient_name,rating,comment,reply&order=created_at.desc&limit=20";
        SupabaseClient.getInstance().get(q, new SupabaseClient.Callback() {
            @Override public void onSuccess(String json) {
                list.removeAllViews();
                try {
                    JSONArray arr = new JSONArray(json);
                    if (arr.length() == 0) {
                        list.addView(sheetText("No reviews yet. Be the first after your visit!", 14, R.color.colorTextSecondary, false, 12));
                        return;
                    }
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.addView(sheetText(String.format(java.util.Locale.ENGLISH, "★ %.1f  ·  %s",
                                o.optDouble("rating", 0), o.optString("patient_name", "Patient")), 14, R.color.colorTextPrimary, true, 16));
                        String c = SupabaseClient.optStringSafe(o, "comment");
                        if (c != null && !c.isEmpty()) list.addView(sheetText(c, 13, R.color.colorTextSecondary, false, 2));
                        String r = SupabaseClient.optStringSafe(o, "reply");
                        if (r != null && !r.isEmpty()) list.addView(sheetText("Hospital replied: " + r, 13, R.color.colorPrimary, false, 6));
                    }
                } catch (Exception e) {
                    list.addView(sheetText("Couldn't load reviews.", 14, R.color.colorTextSecondary, false, 12));
                }
            }
            @Override public void onError(Exception e) {
                list.removeAllViews();
                list.addView(sheetText("Couldn't load reviews. (Has sql/07 been run?)", 14, R.color.colorTextSecondary, false, 12));
            }
        });
    }

    private void showWriteHospitalReviewDialog() {
        com.example.mediq.data.remote.SessionManager sm = com.example.mediq.data.remote.SessionManager.getInstance(this);
        final String uid = sm.getUserId();
        if (uid == null || uid.isEmpty()) {
            com.example.mediq.util.ToastUtils.show(this, "Sign in to write a review");
            return;
        }
        View v = getLayoutInflater().inflate(R.layout.dialog_write_review, null);
        final android.widget.RatingBar bar = v.findViewById(R.id.ratingInput);
        final android.widget.EditText et = v.findViewById(R.id.etReviewComment);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Rate " + hospital.getName())
                .setView(v)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Submit", (d, w) -> {
                    try {
                        JSONObject body = new JSONObject();
                        body.put("hospital_id", hospital.getId());
                        body.put("patient_id", uid);
                        body.put("patient_name", sm.getUserName() == null || sm.getUserName().isEmpty() ? "Patient" : sm.getUserName());
                        body.put("rating", Math.max(1, Math.round(bar.getRating())));
                        body.put("comment", et.getText().toString().trim());
                        SupabaseClient.getInstance().post("hospital_reviews", body, new SupabaseClient.Callback() {
                            @Override public void onSuccess(String r) {
                                com.example.mediq.util.ToastUtils.show(HospitalDetailsActivity.this, "Thanks for your review!");
                            }
                            @Override public void onError(Exception e) {
                                String m = e.getMessage() == null ? "" : e.getMessage();
                                com.example.mediq.util.ToastUtils.show(HospitalDetailsActivity.this,
                                        m.contains("23505") || m.toLowerCase().contains("duplicate")
                                                ? "You've already reviewed this hospital"
                                                : "You can review a hospital after a completed visit there");
                            }
                        });
                    } catch (Exception ignored) { }
                })
                .show();
    }
}
