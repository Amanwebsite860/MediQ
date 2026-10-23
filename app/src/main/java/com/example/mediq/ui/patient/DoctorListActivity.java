package com.example.mediq.ui.patient;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediq.R;
import com.example.mediq.data.model.Doctor;
import com.example.mediq.data.repo.DoctorRepository;
import com.example.mediq.data.repo.FavoritesRepository;
import com.example.mediq.ui.adapters.DoctorAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Doctors list — for one hospital, one specialty, or everyone — with sort + filter chips.
 */
public class DoctorListActivity extends AppCompatActivity implements DoctorAdapter.OnDoctorClickListener {

    public static final String EXTRA_HOSPITAL_NAME = "extra_hospital_name";
    public static final String EXTRA_HOSPITAL_ID = "extra_hospital_id";
    public static final String EXTRA_SPECIALTY = "extra_specialty";

    private static final String[] SORTS = {"Recommended", "Rating: high to low", "Fee: low to high", "Experience"};

    private RecyclerView rvDoctors;
    private DoctorAdapter doctorAdapter;
    private TextView tvTitle, tvSubtitle, tvEmptyState;
    private LinearLayout layoutChips;

    private String hospitalName = null;
    private String hospitalId = null;
    private String specialty = null;

    private final List<Doctor> all = new ArrayList<>();
    private int sortIndex = 0;
    private boolean fAvail, fTop, fBudget;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_list);

        Intent in = getIntent();
        if (in != null) {
            hospitalName = in.getStringExtra(EXTRA_HOSPITAL_NAME);
            hospitalId = in.getStringExtra(EXTRA_HOSPITAL_ID);
            specialty = in.getStringExtra(EXTRA_SPECIALTY);
        }

        ImageButton back = findViewById(R.id.btnBack);
        back.setOnClickListener(v -> finish());
        tvTitle = findViewById(R.id.tvTitle);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        layoutChips = findViewById(R.id.layoutFilterChips);
        if (specialty != null) tvTitle.setText(specialty);
        tvSubtitle.setText(hospitalName != null ? hospitalName : "All hospitals");

        rvDoctors = findViewById(R.id.rvDoctors);
        rvDoctors.setLayoutManager(new LinearLayoutManager(this));
        doctorAdapter = new DoctorAdapter(new ArrayList<>(), this);
        rvDoctors.setAdapter(doctorAdapter);

        buildChips();
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // refresh hearts after returning from a profile page
        FavoritesRepository.get().load(this, () -> doctorAdapter.notifyDataSetChanged());
    }

    private void load() {
        StringBuilder q = new StringBuilder();
        if (hospitalId != null && !hospitalId.isEmpty()) q.append("&hospital_id=eq.").append(hospitalId);
        if (specialty != null && !specialty.isEmpty()) q.append("&specialty=eq.").append(Uri.encode(specialty));
        DoctorRepository.load(q.toString(), new DoctorRepository.Result() {
            @Override public void onResult(List<Doctor> doctors) {
                all.clear();
                all.addAll(doctors);
                if (hospitalName == null && !doctors.isEmpty()) { /* subtitle stays generic */ }
                applyFilters();
            }
            @Override public void onError(Exception e) {
                all.clear();
                applyFilters();
            }
        });
    }

    private void buildChips() {
        layoutChips.removeAllViews();
        addChip("↕  " + SORTS[sortIndex], sortIndex != 0, () -> sortIndex = (sortIndex + 1) % SORTS.length);
        addChip("Available today", fAvail, () -> fAvail = !fAvail);
        addChip("Rating 4.5+", fTop, () -> fTop = !fTop);
        addChip("Under ₹500", fBudget, () -> fBudget = !fBudget);
    }

    private void addChip(String label, boolean selected, Runnable toggle) {
        TextView chip = (TextView) LayoutInflater.from(this).inflate(R.layout.item_chip, layoutChips, false);
        chip.setText(label);
        chip.setSelected(selected);
        chip.setTextColor(ContextCompat.getColor(this, selected ? R.color.colorPrimary : R.color.colorTextPrimary));
        chip.setOnClickListener(v -> { toggle.run(); buildChips(); applyFilters(); });
        layoutChips.addView(chip);
    }

    private void applyFilters() {
        List<Doctor> out = new ArrayList<>();
        for (Doctor d : all) {
            if (fAvail && !d.isAvailableToday()) continue;
            if (fTop && d.getRating() < 4.5) continue;
            if (fBudget && d.getConsultationFee() >= 500) continue;
            out.add(d);
        }
        switch (sortIndex) {
            case 1: Collections.sort(out, (a, b) -> Double.compare(b.getRating(), a.getRating())); break;
            case 2: Collections.sort(out, (a, b) -> Integer.compare(a.getConsultationFee(), b.getConsultationFee())); break;
            case 3: Collections.sort(out, (a, b) -> Integer.compare(b.getExperienceYears(), a.getExperienceYears())); break;
            default: break; // server order = rating desc
        }
        doctorAdapter.updateDoctors(out);
        tvEmptyState.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
        if (specialty != null || hospitalName == null) {
            tvSubtitle.setText(out.size() + (out.size() == 1 ? " doctor" : " doctors")
                    + (hospitalName != null ? " · " + hospitalName : ""));
        }
    }

    @Override
    public void onDoctorClick(Doctor doctor) {
        Intent intent = new Intent(this, DoctorProfileActivity.class);
        intent.putExtra(DoctorProfileActivity.EXTRA_DOCTOR, doctor);
        startActivity(intent);
    }
}
