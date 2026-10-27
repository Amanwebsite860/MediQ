package com.example.mediq.ui.patient;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediq.R;
import com.example.mediq.data.model.Doctor;
import com.example.mediq.data.model.Hospital;
import com.example.mediq.data.remote.SupabaseClient;
import com.example.mediq.data.repo.DoctorRepository;
import com.example.mediq.data.repo.FavoritesRepository;
import com.example.mediq.ui.adapters.SearchFeedAdapter;
import com.example.mediq.ui.adapters.SearchFeedItem;
import com.example.mediq.util.UiUtils;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Search for hospitals and doctors.
 * Filters live in a bottom sheet opened by the "Filters" button; only the filters that are switched on
 * are shown next to the button, as chips that can be tapped away.
 */
public class SearchActivity extends AppCompatActivity implements SearchFeedAdapter.OnItemClickListener {

    public static final String EXTRA_FILTER_TYPE = "extra_filter_type"; // "hospital" or "doctor"
    public static final String EXTRA_SORT = "extra_sort";               // "nearby" or "rating"
    public static final String EXTRA_QUERY = "extra_query";
    public static final String EXTRA_SPECIALTY = "extra_specialty";
    public static final String EXTRA_OPEN_FILTERS = "extra_open_filters";

    // ---- filter state ----
    private static final int TYPE_ALL = 0, TYPE_HOSPITALS = 1, TYPE_DOCTORS = 2;
    private static final int SORT_RECOMMENDED = 0, SORT_NEAREST = 1, SORT_RATING = 2, SORT_FEE = 3, SORT_EXPERIENCE = 4;

    private static class Filters {
        int type = TYPE_ALL;
        int sort = SORT_RECOMMENDED;
        final Set<String> specialties = new LinkedHashSet<>();
        double minRating = 0;
        int maxFee = 0;              // 0 = any
        boolean availToday = false;
        boolean emergency = false;

        void reset() {
            type = TYPE_ALL; sort = SORT_RECOMMENDED; specialties.clear();
            minRating = 0; maxFee = 0; availToday = false; emergency = false;
        }
    }

    private final Filters f = new Filters();

    private EditText etSearch;
    private ImageButton btnClearSearch;
    private TextView tvResultsCount, tvResetFilters, btnOpenFilters;
    private ProgressBar progressBar;
    private LinearLayout layoutEmpty, layoutActiveFilters;
    private RecyclerView rvSearchResults;

    private SearchFeedAdapter adapter;
    private final List<Hospital> allHospitals = new ArrayList<>();
    private final List<Doctor> allDoctors = new ArrayList<>();
    private final List<SearchFeedItem> displayList = new ArrayList<>();
    private final Map<String, Double> distanceKm = new HashMap<>();   // hospital id -> km (only when location is known)
    private final Set<String> emergencyIds = new HashSet<>();
    private Location myLocation;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        etSearch = findViewById(R.id.etSearch);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        tvResultsCount = findViewById(R.id.tvResultsCount);
        tvResetFilters = findViewById(R.id.tvResetFilters);
        btnOpenFilters = findViewById(R.id.btnOpenFilters);
        progressBar = findViewById(R.id.progressBar);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        layoutActiveFilters = findViewById(R.id.layoutActiveFilters);
        rvSearchResults = findViewById(R.id.rvSearchResults);

        rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SearchFeedAdapter(displayList, this);
        rvSearchResults.setAdapter(adapter);

        ((ImageButton) findViewById(R.id.btnBack)).setOnClickListener(v -> finish());
        btnClearSearch.setOnClickListener(v -> { etSearch.setText(""); btnClearSearch.setVisibility(View.GONE); });
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                applyFiltersAndSort();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        etSearch.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                    || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                applyFiltersAndSort();
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(tv.getWindowToken(), 0);
                return true;
            }
            return false;
        });
        btnOpenFilters.setOnClickListener(v -> showFilterSheet());
        tvResetFilters.setOnClickListener(v -> { etSearch.setText(""); f.reset(); applyFiltersAndSort(); });

        handleIntent(getIntent());
        loadLocationThenData();
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_OPEN_FILTERS, false)) {
            rvSearchResults.post(this::showFilterSheet);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        applyFiltersAndSort();
    }

    @Override
    protected void onResume() {
        super.onResume();
        FavoritesRepository.get().load(this, () -> adapter.notifyDataSetChanged());
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String q = intent.getStringExtra(EXTRA_QUERY);
        if (q != null) etSearch.setText(q);
        String type = intent.getStringExtra(EXTRA_FILTER_TYPE);
        if ("hospital".equalsIgnoreCase(type)) f.type = TYPE_HOSPITALS;
        else if ("doctor".equalsIgnoreCase(type)) f.type = TYPE_DOCTORS;
        String sort = intent.getStringExtra(EXTRA_SORT);
        if ("nearby".equalsIgnoreCase(sort)) f.sort = SORT_NEAREST;
        else if ("rating".equalsIgnoreCase(sort)) f.sort = SORT_RATING;
        String sp = intent.getStringExtra(EXTRA_SPECIALTY);
        if (sp != null && !sp.isEmpty()) f.specialties.add(sp);
    }

    // ------------------------------------------------------------------ data
    private void loadLocationThenData() {
        progressBar.setVisibility(View.VISIBLE);
        // Start loading data immediately so search works without waiting for GPS
        loadHospitals();

        boolean granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!granted) return;
        try {
            LocationServices.getFusedLocationProviderClient(this).getLastLocation()
                    .addOnSuccessListener(l -> {
                        if (l != null) {
                            myLocation = l;
                            recalculateDistances();
                            applyFiltersAndSort();
                        }
                    });
        } catch (SecurityException ignored) { }
    }

    private void recalculateDistances() {
        if (myLocation == null) return;
        for (Hospital h : allHospitals) {
            if (h.getLatitude() != 0 || h.getLongitude() != 0) {
                double km = UiUtils.distanceKm(myLocation.getLatitude(), myLocation.getLongitude(), h.getLatitude(), h.getLongitude());
                distanceKm.put(h.getId(), km);
                h.setDistance(String.format(Locale.US, "%.1f km", km));
            }
        }
    }

    private void loadHospitals() {
        SupabaseClient.getInstance().get("hospitals?select=*&active=eq.true&order=rating.desc",
                new SupabaseClient.Callback() {
                    @Override public void onSuccess(String json) {
                        try {
                            JSONArray arr = new JSONArray(json);
                            allHospitals.clear();
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);
                                double lat = o.optDouble("latitude", 0), lon = o.optDouble("longitude", 0);
                                String id = SupabaseClient.optStringSafe(o, "id");
                                String dist = "";
                                if (myLocation != null && (lat != 0 || lon != 0)) {
                                    double km = UiUtils.distanceKm(myLocation.getLatitude(), myLocation.getLongitude(), lat, lon);
                                    distanceKm.put(id, km);
                                    dist = String.format(Locale.US, "%.1f km", km);
                                }
                                if (o.optBoolean("emergency_24x7", false)) emergencyIds.add(id);
                                Hospital h = new Hospital(id,
                                        SupabaseClient.optStringSafe(o, "name"),
                                        SupabaseClient.optStringSafe(o, "type"),
                                        SupabaseClient.optStringSafe(o, "address"),
                                        SupabaseClient.optStringSafe(o, "city"),
                                        dist,
                                        SupabaseClient.optStringSafe(o, "phone"),
                                        SupabaseClient.optStringSafe(o, "opening_hours"),
                                        null, R.drawable.ic_hospital_building,
                                        o.optDouble("rating", 0), o.optInt("review_count", 0), true, lat, lon);
                                String cover = SupabaseClient.optStringSafe(o, "image_url");
                                if (cover == null || cover.trim().isEmpty()) {
                                    org.json.JSONArray imgs = o.optJSONArray("image_urls");
                                    if (imgs != null && imgs.length() > 0) {
                                        cover = imgs.optString(0, null);
                                    }
                                }
                                h.setImageUrl(cover);
                                allHospitals.add(h);
                            }
                        } catch (Exception ignored) { }
                        loadDoctors();
                    }
                    @Override public void onError(Exception e) { loadDoctors(); }
                });
    }

    private void loadDoctors() {
        DoctorRepository.load("", new DoctorRepository.Result() {
            @Override public void onResult(List<Doctor> doctors) {
                allDoctors.clear();
                allDoctors.addAll(doctors);
                progressBar.setVisibility(View.GONE);
                applyFiltersAndSort();
            }
            @Override public void onError(Exception e) {
                progressBar.setVisibility(View.GONE);
                applyFiltersAndSort();
            }
        });
    }

    // ------------------------------------------------------------------ filtering
    private boolean matchesQuery(String q, String... fields) {
        if (q == null || q.trim().isEmpty()) return true;
        String cleanQ = q.toLowerCase(Locale.ROOT).replaceAll("[.,\\-–—_#@!?:;]", " ").trim();
        if (cleanQ.isEmpty()) return true;

        StringBuilder all = new StringBuilder();
        for (String s : fields) {
            if (s != null) {
                all.append(s.toLowerCase(Locale.ROOT).replaceAll("[.,\\-–—_#@!?:;]", " ")).append(' ');
            }
        }
        String corpus = all.toString();
        for (String word : cleanQ.split("\\s+")) {
            if (word.isEmpty()) continue;
            if ((word.equals("dr") || word.equals("doctor")) && !corpus.contains("dr") && !corpus.contains("doctor")) {
                continue;
            }
            if (!corpus.contains(word)) return false;
        }
        return true;
    }

    private double hospitalDistance(String hospitalId) {
        Double d = hospitalId == null ? null : distanceKm.get(hospitalId);
        return d == null ? Double.MAX_VALUE : d;
    }

    private void applyFiltersAndSort() {
        String q = etSearch.getText() != null ? etSearch.getText().toString().trim() : "";

        // hospitals that have at least one doctor in the selected specialties
        Set<String> hospitalsWithSpecialty = new HashSet<>();
        if (!f.specialties.isEmpty()) {
            for (Doctor d : allDoctors) {
                if (specialtySelected(d)) hospitalsWithSpecialty.add(d.getHospitalId());
            }
        }
        boolean doctorOnlyFilter = f.maxFee > 0 || f.availToday;

        List<Hospital> hospitals = new ArrayList<>();
        if (f.type != TYPE_DOCTORS && !doctorOnlyFilter) {
            for (Hospital h : allHospitals) {
                if (!matchesQuery(q, h.getName(), h.getType(), h.getAddress(), h.getCity())) continue;
                if (h.getRating() < f.minRating) continue;
                if (f.emergency && !emergencyIds.contains(h.getId())) continue;
                if (!f.specialties.isEmpty() && !hospitalsWithSpecialty.contains(h.getId())) continue;
                hospitals.add(h);
            }
        }

        List<Doctor> doctors = new ArrayList<>();
        if (f.type != TYPE_HOSPITALS && !f.emergency) {
            for (Doctor d : allDoctors) {
                if (!matchesQuery(q, d.getName(), d.getSpecialty(), d.getHospitalName(), d.getQualification(), d.getBio())) continue;
                if (d.getRating() < f.minRating) continue;
                if (f.maxFee > 0 && d.getConsultationFee() > f.maxFee) continue;
                if (f.availToday && !d.isAvailableToday()) continue;
                if (!f.specialties.isEmpty() && !specialtySelected(d)) continue;
                doctors.add(d);
            }
        }

        // If query was typed and current tab had no results, show results from the other category
        if (!q.isEmpty() && f.type == TYPE_HOSPITALS && hospitals.isEmpty()) {
            for (Doctor d : allDoctors) {
                if (!matchesQuery(q, d.getName(), d.getSpecialty(), d.getHospitalName(), d.getQualification(), d.getBio())) continue;
                doctors.add(d);
            }
        } else if (!q.isEmpty() && f.type == TYPE_DOCTORS && doctors.isEmpty()) {
            for (Hospital h : allHospitals) {
                if (!matchesQuery(q, h.getName(), h.getType(), h.getAddress(), h.getCity())) continue;
                hospitals.add(h);
            }
        }

        switch (f.sort) {
            case SORT_NEAREST:
                Collections.sort(hospitals, (a, b) -> Double.compare(hospitalDistance(a.getId()), hospitalDistance(b.getId())));
                Collections.sort(doctors, (a, b) -> Double.compare(hospitalDistance(a.getHospitalId()), hospitalDistance(b.getHospitalId())));
                break;
            case SORT_RATING:
                Collections.sort(hospitals, (a, b) -> Double.compare(b.getRating(), a.getRating()));
                Collections.sort(doctors, (a, b) -> Double.compare(b.getRating(), a.getRating()));
                break;
            case SORT_FEE:
                Collections.sort(doctors, (a, b) -> Integer.compare(a.getConsultationFee(), b.getConsultationFee()));
                break;
            case SORT_EXPERIENCE:
                Collections.sort(doctors, (a, b) -> Integer.compare(b.getExperienceYears(), a.getExperienceYears()));
                break;
            default: break; // server order = rating
        }

        displayList.clear();
        int max = Math.max(hospitals.size(), doctors.size());
        if (f.type == TYPE_HOSPITALS && !hospitals.isEmpty()) {
            for (Hospital h : hospitals) displayList.add(new SearchFeedItem(h));
        } else if (f.type == TYPE_DOCTORS && !doctors.isEmpty()) {
            for (Doctor d : doctors) displayList.add(new SearchFeedItem(d));
        } else {
            for (int i = 0; i < max; i++) { // a few hospitals, then doctors, and repeat
                if (i < hospitals.size()) displayList.add(new SearchFeedItem(hospitals.get(i)));
                if (i < doctors.size()) displayList.add(new SearchFeedItem(doctors.get(i)));
            }
        }
        adapter.updateItems(displayList);

        int total = displayList.size();
        tvResultsCount.setText("Showing " + total + (total == 1 ? " match" : " matches"));
        layoutEmpty.setVisibility(total == 0 && progressBar.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE);
        tvResetFilters.setVisibility(f.type != TYPE_ALL || f.sort != SORT_RECOMMENDED || !f.specialties.isEmpty()
                || f.minRating > 0 || f.maxFee > 0 || f.availToday || f.emergency || !q.isEmpty() ? View.VISIBLE : View.GONE);
        renderActiveFilterChips();
    }

    private boolean specialtySelected(Doctor d) {
        String s = d.getSpecialty() == null ? "" : d.getSpecialty();
        for (String sel : f.specialties) if (sel.equalsIgnoreCase(s)) return true;
        return false;
    }

    // ------------------------------------------------------------------ active chips next to the button
    private static final String[] SORT_LABELS = {"", "Nearest", "Top rated", "Fee: low to high", "Most experienced"};

    private void renderActiveFilterChips() {
        layoutActiveFilters.removeAllViews();
        int count = 0;
        if (f.type == TYPE_HOSPITALS) { addActive("Hospitals", () -> f.type = TYPE_ALL); count++; }
        if (f.type == TYPE_DOCTORS) { addActive("Doctors", () -> f.type = TYPE_ALL); count++; }
        if (f.sort != SORT_RECOMMENDED) { addActive(SORT_LABELS[f.sort], () -> f.sort = SORT_RECOMMENDED); count++; }
        for (String sp : new ArrayList<>(f.specialties)) { addActive(sp, () -> f.specialties.remove(sp)); count++; }
        if (f.minRating > 0) { addActive(String.format(Locale.US, "%.1f ★+", f.minRating), () -> f.minRating = 0); count++; }
        if (f.maxFee > 0) { addActive("Up to ₹" + f.maxFee, () -> f.maxFee = 0); count++; }
        if (f.availToday) { addActive("Available today", () -> f.availToday = false); count++; }
        if (f.emergency) { addActive("24x7 emergency", () -> f.emergency = false); count++; }
        btnOpenFilters.setText(count == 0 ? "⚙  Filters" : "⚙  Filters · " + count);
    }

    private void addActive(String label, Runnable remove) {
        TextView chip = (TextView) LayoutInflater.from(this).inflate(R.layout.item_chip, layoutActiveFilters, false);
        chip.setText(label + "  ✕");
        chip.setSelected(true);
        chip.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));
        chip.setOnClickListener(v -> { remove.run(); applyFiltersAndSort(); });
        layoutActiveFilters.addView(chip);
    }

    // ------------------------------------------------------------------ filter sheet
    private void showFilterSheet() {
        final BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = getLayoutInflater().inflate(R.layout.sheet_search_filters, null);
        sheet.setContentView(v);

        final ChipGroup cgType = v.findViewById(R.id.cgType), cgSort = v.findViewById(R.id.cgSort),
                cgRating = v.findViewById(R.id.cgRating), cgFee = v.findViewById(R.id.cgFee),
                cgSpecialty = v.findViewById(R.id.cgSpecialty);
        final Chip chipAvail = v.findViewById(R.id.chipAvailToday), chipEmergency = v.findViewById(R.id.chipEmergency);

        // specialties come from the doctors that actually exist
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Doctor d : allDoctors) if (d.getSpecialty() != null && !d.getSpecialty().isEmpty()) names.add(d.getSpecialty());
        names.addAll(f.specialties);
        for (String n : names) {
            Chip c = (Chip) getLayoutInflater().inflate(R.layout.item_filter_chip, cgSpecialty, false);
            c.setText(n);
            c.setCheckable(true);
            cgSpecialty.addView(c);
        }

        applyStateToSheet(v, cgType, cgSort, cgRating, cgFee, cgSpecialty, chipAvail, chipEmergency);

        v.findViewById(R.id.tvSheetReset).setOnClickListener(x -> {
            f.reset();
            applyStateToSheet(v, cgType, cgSort, cgRating, cgFee, cgSpecialty, chipAvail, chipEmergency);
        });
        ((MaterialButton) v.findViewById(R.id.btnApplyFilters)).setOnClickListener(x -> {
            int t = cgType.getCheckedChipId();
            f.type = t == R.id.chipTypeHospitals ? TYPE_HOSPITALS : t == R.id.chipTypeDoctors ? TYPE_DOCTORS : TYPE_ALL;
            int s = cgSort.getCheckedChipId();
            f.sort = s == R.id.sortNearest ? SORT_NEAREST : s == R.id.sortRating ? SORT_RATING
                    : s == R.id.sortFee ? SORT_FEE : s == R.id.sortExperience ? SORT_EXPERIENCE : SORT_RECOMMENDED;
            int r = cgRating.getCheckedChipId();
            f.minRating = r == R.id.rating35 ? 3.5 : r == R.id.rating40 ? 4.0 : r == R.id.rating45 ? 4.5 : 0;
            int fee = cgFee.getCheckedChipId();
            f.maxFee = fee == R.id.fee300 ? 300 : fee == R.id.fee500 ? 500 : fee == R.id.fee1000 ? 1000 : 0;
            f.availToday = chipAvail.isChecked();
            f.emergency = chipEmergency.isChecked();
            f.specialties.clear();
            for (int i = 0; i < cgSpecialty.getChildCount(); i++) {
                Chip c = (Chip) cgSpecialty.getChildAt(i);
                if (c.isChecked()) f.specialties.add(c.getText().toString());
            }
            if (f.sort == SORT_NEAREST && distanceKm.isEmpty()) {
                com.example.mediq.util.ToastUtils.show(this, "Turn on location to sort by nearest");
            }
            sheet.dismiss();
            applyFiltersAndSort();
        });
        sheet.show();
    }

    private void applyStateToSheet(View v, ChipGroup cgType, ChipGroup cgSort, ChipGroup cgRating, ChipGroup cgFee,
                                   ChipGroup cgSpecialty, Chip chipAvail, Chip chipEmergency) {
        cgType.check(f.type == TYPE_HOSPITALS ? R.id.chipTypeHospitals : f.type == TYPE_DOCTORS ? R.id.chipTypeDoctors : R.id.chipTypeAll);
        cgSort.check(f.sort == SORT_NEAREST ? R.id.sortNearest : f.sort == SORT_RATING ? R.id.sortRating
                : f.sort == SORT_FEE ? R.id.sortFee : f.sort == SORT_EXPERIENCE ? R.id.sortExperience : R.id.sortRecommended);
        cgRating.check(f.minRating >= 4.5 ? R.id.rating45 : f.minRating >= 4.0 ? R.id.rating40 : f.minRating >= 3.5 ? R.id.rating35 : R.id.ratingAny);
        cgFee.check(f.maxFee == 300 ? R.id.fee300 : f.maxFee == 500 ? R.id.fee500 : f.maxFee == 1000 ? R.id.fee1000 : R.id.feeAny);
        chipAvail.setChecked(f.availToday);
        chipEmergency.setChecked(f.emergency);
        for (int i = 0; i < cgSpecialty.getChildCount(); i++) {
            Chip c = (Chip) cgSpecialty.getChildAt(i);
            c.setChecked(f.specialties.contains(c.getText().toString()));
        }
    }

    // ------------------------------------------------------------------ clicks
    @Override
    public void onHospitalClick(Hospital hospital) {
        Intent intent = new Intent(this, HospitalDetailsActivity.class);
        intent.putExtra("extra_hospital", hospital);
        intent.putExtra("hospital_id", hospital.getId());
        startActivity(intent);
    }

    @Override
    public void onDoctorClick(Doctor doctor) {
        Intent intent = new Intent(this, DoctorProfileActivity.class);
        intent.putExtra("extra_doctor", doctor);
        startActivity(intent);
    }
}
