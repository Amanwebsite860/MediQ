package com.example.mediq.ui.patient;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.mediq.R;
import com.example.mediq.data.model.Doctor;
import com.example.mediq.data.remote.SessionManager;
import com.example.mediq.data.remote.SupabaseClient;
import com.example.mediq.ui.appointments.BookAppointmentActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Activity displaying the detailed profile of a Doctor.
 * Dynamically loads reviews and connects to user_favorites table.
 */
public class DoctorProfileActivity extends AppCompatActivity {

    private static final String TAG = "DoctorProfileActivity";
    public static final String EXTRA_DOCTOR = "extra_doctor";

    private ImageButton btnBack;
    private ImageButton btnFavorite;
    private ImageView ivDoctorProfileAvatar;
    private TextView tvDoctorProfileName;
    private TextView tvDoctorProfileSpecialty;
    private TextView tvDoctorProfileRating;
    private TextView tvDoctorProfileExperience;
    private TabLayout tabLayout;

    private LinearLayout layoutTabAbout;
    private LinearLayout layoutTabAvailability;
    private LinearLayout layoutTabReviews;
    private LinearLayout containerReviews;
    private TextView tvNoReviews;

    private TextView tvDoctorBio;
    private TextView tvConsultationFee;
    private TextView tvConsultationDuration;
    private TextView tvHospitalLocation;
    private MaterialButton btnBookAppointment;
    private LinearLayout containerAvailability;
    private TextView tvNoAvailability, tvReviewSummary;

    private Doctor currentDoctor;
    private boolean isFavorite = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_profile);

        loadDoctorData();
        initViews();
        bindDoctorData();
        setupListeners();
        checkFavoriteStatus();
        fetchReviews();
        fetchAvailability();
        View wr = findViewById(R.id.btnWriteReview);
        if (wr != null) wr.setOnClickListener(v -> showWriteReviewDialog());
    }

    private void loadDoctorData() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_DOCTOR)) {
            try {
                currentDoctor = (Doctor) intent.getSerializableExtra(EXTRA_DOCTOR);
            } catch (Exception ignored) {
            }
        }

        if (currentDoctor == null) {
            com.example.mediq.util.ToastUtils.show(this, "Doctor information not found");
            finish();
        }
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnFavorite = findViewById(R.id.btnFavorite);
        ivDoctorProfileAvatar = findViewById(R.id.ivDoctorProfileAvatar);
        tvDoctorProfileName = findViewById(R.id.tvDoctorProfileName);
        tvDoctorProfileSpecialty = findViewById(R.id.tvDoctorProfileSpecialty);
        tvDoctorProfileRating = findViewById(R.id.tvDoctorProfileRating);
        tvDoctorProfileExperience = findViewById(R.id.tvDoctorProfileExperience);
        tabLayout = findViewById(R.id.tabLayout);

        layoutTabAbout = findViewById(R.id.layoutTabAbout);
        layoutTabAvailability = findViewById(R.id.layoutTabAvailability);
        layoutTabReviews = findViewById(R.id.layoutTabReviews);
        containerReviews = findViewById(R.id.containerReviews);
        tvNoReviews = findViewById(R.id.tvNoReviews);

        tvDoctorBio = findViewById(R.id.tvDoctorBio);
        tvConsultationFee = findViewById(R.id.tvConsultationFee);
        tvConsultationDuration = findViewById(R.id.tvConsultationDuration);
        tvHospitalLocation = findViewById(R.id.tvHospitalLocation);
        btnBookAppointment = findViewById(R.id.btnBookAppointment);
        containerAvailability = findViewById(R.id.containerAvailability);
        tvNoAvailability = findViewById(R.id.tvNoAvailability);
        tvReviewSummary = findViewById(R.id.tvReviewSummary);
    }

    private void bindDoctorData() {
        if (currentDoctor == null) return;

        tvDoctorProfileName.setText(currentDoctor.getName());
        String q = currentDoctor.getQualification();
        tvDoctorProfileSpecialty.setText(q != null && !q.isEmpty()
                ? currentDoctor.getSpecialty() + " \u00B7 " + q : currentDoctor.getSpecialty());
        tvDoctorProfileRating.setText(currentDoctor.getRatingAndReviewsText());
        tvDoctorProfileExperience.setText(currentDoctor.getFormattedExperience());

        if (currentDoctor.getAvatarUrl() != null && !currentDoctor.getAvatarUrl().isEmpty()) {
            Glide.with(this)
                    .load(currentDoctor.getAvatarUrl())
                    .placeholder(currentDoctor.getAvatarResId() != 0 ? currentDoctor.getAvatarResId() : R.drawable.ic_doctor_male_avatar)
                    .into(ivDoctorProfileAvatar);
        } else if (currentDoctor.getAvatarResId() != 0) {
            ivDoctorProfileAvatar.setImageResource(currentDoctor.getAvatarResId());
        } else {
            ivDoctorProfileAvatar.setImageResource(R.drawable.ic_doctor_male_avatar);
        }

        tvDoctorBio.setText(currentDoctor.getBio());
        tvConsultationFee.setText(currentDoctor.getFormattedFee());
        tvConsultationDuration.setText(currentDoctor.getFormattedDuration());

        if (tvHospitalLocation != null && currentDoctor.getHospitalName() != null) {
            tvHospitalLocation.setText(currentDoctor.getHospitalName());
        }
    }

    private void setupListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (btnFavorite != null) {
            btnFavorite.setOnClickListener(v -> toggleFavorite());
        }

        if (tabLayout != null) {
            tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    switchTab(tab.getPosition());
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) {}

                @Override
                public void onTabReselected(TabLayout.Tab tab) {}
            });
        }

        if (btnBookAppointment != null) {
            btnBookAppointment.setOnClickListener(v -> navigateToBookAppointment());
        }
    }

    private void checkFavoriteStatus() {
        if (currentDoctor == null || currentDoctor.getId() == null) return;
        isFavorite = com.example.mediq.data.repo.FavoritesRepository.get().isDoctorFav(currentDoctor.getId());
        updateFavoriteIcon();
        com.example.mediq.data.repo.FavoritesRepository.get().load(this, () -> {
            isFavorite = com.example.mediq.data.repo.FavoritesRepository.get().isDoctorFav(currentDoctor.getId());
            updateFavoriteIcon();
        });
    }

    private void toggleFavorite() {
        if (currentDoctor == null || currentDoctor.getId() == null) return;
        com.example.mediq.data.repo.FavoritesRepository.get().toggleDoctor(this, currentDoctor.getId(), (now, err) -> {
            isFavorite = now;
            updateFavoriteIcon();
            com.example.mediq.util.ToastUtils.show(this, err != null ? "Couldn't update saved doctors"
                    : (now ? "Added to saved doctors" : "Removed from saved doctors"));
        });
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

    private void switchTab(int position) {
        if (layoutTabAbout != null) {
            layoutTabAbout.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        }
        if (layoutTabAvailability != null) {
            layoutTabAvailability.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        }
        if (layoutTabReviews != null) {
            layoutTabReviews.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
        }
    }

    private void fetchReviews() {
        if (currentDoctor == null || currentDoctor.getId() == null) return;

        SupabaseClient.getInstance().get("doctor_reviews?doctor_id=eq." + currentDoctor.getId() + "&select=*&order=created_at.desc", new SupabaseClient.Callback() {
            @Override
            public void onSuccess(String jsonResponse) {
                try {
                    JSONArray arr = new JSONArray(jsonResponse);
                    if (tvReviewSummary != null) {
                        double sum = 0;
                        for (int i = 0; i < arr.length(); i++) sum += arr.getJSONObject(i).optDouble("rating", 0);
                        tvReviewSummary.setText(arr.length() == 0 ? "Be the first to review"
                                : String.format(java.util.Locale.ENGLISH, "★ %.1f \u00B7 %d review%s",
                                sum / arr.length(), arr.length(), arr.length() == 1 ? "" : "s"));
                    }
                    if (containerReviews != null) {
                        containerReviews.removeAllViews();
                        if (arr.length() == 0) {
                            if (tvNoReviews != null) tvNoReviews.setVisibility(View.VISIBLE);
                        } else {
                            if (tvNoReviews != null) tvNoReviews.setVisibility(View.GONE);
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject rev = arr.getJSONObject(i);
                                String patientName = SupabaseClient.optStringSafe(rev, "patient_name");
                                if (patientName == null || patientName.isEmpty()) patientName = "Verified Patient";
                                double rating = rev.optDouble("rating", 5.0);
                                String comment = SupabaseClient.optStringSafe(rev, "comment");
                                if (comment == null) comment = "";

                                View card = createReviewCard(patientName, rating, comment);
                                containerReviews.addView(card);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing reviews", e);
                }
            }

            @Override
            public void onError(Exception error) {
                if (tvNoReviews != null) tvNoReviews.setVisibility(View.VISIBLE);
            }
        });
    }

    private View createReviewCard(String patientName, double rating, String comment) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(32, 28, 32, 28);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 20;
        card.setLayoutParams(lp);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView tvName = new TextView(this);
        tvName.setText(patientName);
        tvName.setTextSize(15);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.colorTextPrimary));
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tvName.setLayoutParams(nameLp);

        TextView tvRating = new TextView(this);
        tvRating.setText(String.format(java.util.Locale.ENGLISH, "★ %.1f", rating));
        tvRating.setTextSize(14);
        tvRating.setTypeface(null, android.graphics.Typeface.BOLD);
        tvRating.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.colorStar));

        topRow.addView(tvName);
        topRow.addView(tvRating);

        TextView tvComment = new TextView(this);
        tvComment.setText(comment);
        tvComment.setTextSize(13);
        tvComment.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.colorTextSecondary));
        LinearLayout.LayoutParams commentLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        commentLp.topMargin = 12;
        tvComment.setLayoutParams(commentLp);

        card.addView(topRow);
        card.addView(tvComment);
        return card;
    }

    private void navigateToBookAppointment() {
        Intent intent = new Intent(DoctorProfileActivity.this, BookAppointmentActivity.class);
        intent.putExtra(BookAppointmentActivity.EXTRA_DOCTOR_ID, currentDoctor.getId());
        intent.putExtra(BookAppointmentActivity.EXTRA_DOCTOR_NAME, currentDoctor.getName());
        intent.putExtra(BookAppointmentActivity.EXTRA_DOCTOR_SPECIALTY, currentDoctor.getSpecialty());
        intent.putExtra(BookAppointmentActivity.EXTRA_HOSPITAL_NAME, currentDoctor.getHospitalName());
        intent.putExtra(BookAppointmentActivity.EXTRA_HOSPITAL_ID, currentDoctor.getHospitalId());
        intent.putExtra(BookAppointmentActivity.EXTRA_CONSULTATION_FEE, currentDoctor.getConsultationFee());

        // Also pass generic keys for compatibility
        intent.putExtra("doctor", currentDoctor);
        intent.putExtra("doctor_id", currentDoctor.getId());
        intent.putExtra("doctor_name", currentDoctor.getName());
        intent.putExtra("hospital_id", currentDoctor.getHospitalId());
        startActivity(intent);
    }

    // ------------------------------------------------------------------ availability
    private void fetchAvailability() {
        if (currentDoctor == null || currentDoctor.getId() == null || containerAvailability == null) return;
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        String q = "doctor_schedules?doctor_id=eq." + currentDoctor.getId() + "&status=eq.available&schedule_date=gte." + today
                + "&select=schedule_date,start_time,end_time&order=schedule_date,start_time&limit=40";
        SupabaseClient.getInstance().get(q, new SupabaseClient.Callback() {
            @Override public void onSuccess(String json) {
                try {
                    JSONArray arr = new JSONArray(json);
                    containerAvailability.removeAllViews();
                    java.util.LinkedHashMap<String, String> byDate = new java.util.LinkedHashMap<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        String d = o.optString("schedule_date");
                        String range = com.example.mediq.util.UiUtils.formatTime(o.optString("start_time"))
                                + " - " + com.example.mediq.util.UiUtils.formatTime(o.optString("end_time"));
                        byDate.put(d, byDate.containsKey(d) ? byDate.get(d) + ",  " + range : range);
                    }
                    int shown = 0;
                    for (java.util.Map.Entry<String, String> e : byDate.entrySet()) {
                        if (shown++ >= 7) break;
                        containerAvailability.addView(availabilityRow(
                                com.example.mediq.util.UiUtils.friendlyDate(e.getKey()), e.getValue()));
                    }
                    tvNoAvailability.setVisibility(byDate.isEmpty() ? View.VISIBLE : View.GONE);
                } catch (Exception e) {
                    tvNoAvailability.setVisibility(View.VISIBLE);
                }
            }
            @Override public void onError(Exception e) { tvNoAvailability.setVisibility(View.VISIBLE); }
        });
    }

    private View availabilityRow(String day, String hours) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = com.example.mediq.util.UiUtils.dp(row, 10);
        row.setLayoutParams(lp);

        TextView d = new TextView(this);
        d.setText(day);
        d.setTextSize(14);
        d.setTypeface(null, android.graphics.Typeface.BOLD);
        d.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.colorTextPrimary));
        d.setLayoutParams(new LinearLayout.LayoutParams(com.example.mediq.util.UiUtils.dp(row, 110),
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView h = new TextView(this);
        h.setText(hours);
        h.setTextSize(14);
        h.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.colorTextSecondary));
        h.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(d);
        row.addView(h);
        return row;
    }

    // ------------------------------------------------------------------ write a review
    private void showWriteReviewDialog() {
        SessionManager sm = SessionManager.getInstance(this);
        String uid = sm.getUserId();
        if (uid == null || uid.isEmpty()) {
            com.example.mediq.util.ToastUtils.show(this, "Sign in to write a review");
            return;
        }
        View v = getLayoutInflater().inflate(R.layout.dialog_write_review, null);
        android.widget.RatingBar bar = v.findViewById(R.id.ratingInput);
        android.widget.EditText et = v.findViewById(R.id.etReviewComment);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Rate " + currentDoctor.getName())
                .setView(v)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Submit", (d, w) -> submitReview(uid, sm.getUserName(), bar.getRating(), et.getText().toString().trim()))
                .show();
    }

    private void submitReview(String uid, String name, float rating, String comment) {
        if (rating < 1f) rating = 1f;
        try {
            JSONObject body = new JSONObject();
            body.put("doctor_id", currentDoctor.getId());
            body.put("patient_id", uid);
            body.put("patient_name", (name == null || name.isEmpty()) ? "Patient" : name);
            body.put("rating", rating);
            body.put("comment", comment.isEmpty() ? "Great experience." : comment);
            SupabaseClient.getInstance().post("doctor_reviews", body, new SupabaseClient.Callback() {
                @Override public void onSuccess(String r) {
                    com.example.mediq.util.ToastUtils.show(DoctorProfileActivity.this, "Thanks for your review!");
                    fetchReviews();
                }
                @Override public void onError(Exception e) {
                    String m = e.getMessage() == null ? "" : e.getMessage();
                    com.example.mediq.util.ToastUtils.show(DoctorProfileActivity.this,
                            m.contains("23505") || m.toLowerCase().contains("duplicate")
                                    ? "You've already reviewed this doctor"
                                    : "You can review a doctor after a completed visit");
                }
            });
        } catch (Exception ignored) { }
    }
}
