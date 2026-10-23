package com.example.mediq.ui.staff;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.mediq.R;
import com.example.mediq.data.remote.SessionManager;
import com.example.mediq.data.remote.SupabaseClient;
import com.example.mediq.util.ImageUtils;
import com.example.mediq.util.ToastUtils;
import com.example.mediq.util.UiUtils;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Hospital details + photos.
 *  MODE_REGISTER: a doctor/receptionist registers a NEW hospital -> goes to admin for approval.
 *  MODE_EDIT:     an approved receptionist edits their own hospital (saved immediately).
 */
public class HospitalFormActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_ROLE = "role";
    public static final String EXTRA_DETAILS = "details";
    public static final String EXTRA_HOSPITAL_ID = "hospital_id";
    public static final int MODE_REGISTER = 0, MODE_EDIT = 1;

    private static final int MAX_PHOTOS = 6;
    private static final String[] TYPES = {"Multi-Speciality", "Super-Speciality", "General", "Clinic", "Diagnostic Centre"};
    private static final String[] FACILITIES = {"Pharmacy", "Ambulance", "Parking", "Lab", "ICU", "Blood Bank",
            "Cafeteria", "Wheelchair Access", "X-Ray", "MRI / CT"};

    /** A photo is either an already-uploaded URL or a freshly picked local image. */
    private static class Photo { String url; Uri local; }

    private int mode;
    private String role = "receptionist";
    private String detailsJson = "{}";
    private String hospitalId;

    private final List<Photo> photos = new ArrayList<>();
    private Double lat, lon;

    private LinearLayout layoutPhotos;
    private ChipGroup chipGroup;
    private TextView tvLocation;
    private MaterialButton btnSubmit;
    private MaterialSwitch switchEmergency;
    private AutoCompleteTextView actvType;
    private TextInputEditText etName, etReg, etYear, etBeds, etDesc, etAddress, etCity, etState, etPhone,
            etEmail, etWebsite, etHours, etEmergencyPhone;

    private final ActivityResultLauncher<String> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                if (photos.size() >= MAX_PHOTOS) { ToastUtils.show(this, "You can add up to " + MAX_PHOTOS + " photos"); return; }
                Photo p = new Photo();
                p.local = uri;
                photos.add(p);
                renderPhotos();
            });

    private final ActivityResultLauncher<android.content.Intent> pickOnMap =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                android.content.Intent d = r.getData();
                if (r.getResultCode() == RESULT_OK && d != null) {
                    lat = d.getDoubleExtra("lat", 0);
                    lon = d.getDoubleExtra("lon", 0);
                    showLocation();
                }
            });

    private final ActivityResultLauncher<String[]> askLocation =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> fetchLocation());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hospital_form);

        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_REGISTER);
        if (getIntent().getStringExtra(EXTRA_ROLE) != null) role = getIntent().getStringExtra(EXTRA_ROLE);
        if (getIntent().getStringExtra(EXTRA_DETAILS) != null) detailsJson = getIntent().getStringExtra(EXTRA_DETAILS);
        hospitalId = getIntent().getStringExtra(EXTRA_HOSPITAL_ID);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.tvScreenTitle)).setText(mode == MODE_EDIT ? "Hospital profile" : "Register your hospital");

        layoutPhotos = findViewById(R.id.layoutPhotos);
        chipGroup = findViewById(R.id.chipGroupFacilities);
        tvLocation = findViewById(R.id.tvLocation);
        btnSubmit = findViewById(R.id.btnHSubmit);
        switchEmergency = findViewById(R.id.switchEmergency);
        actvType = findViewById(R.id.actvHType);
        etName = findViewById(R.id.etHName);
        etReg = findViewById(R.id.etHReg);
        etYear = findViewById(R.id.etHYear);
        etBeds = findViewById(R.id.etHBeds);
        etDesc = findViewById(R.id.etHDesc);
        etAddress = findViewById(R.id.etHAddress);
        etCity = findViewById(R.id.etHCity);
        etState = findViewById(R.id.etHState);
        etPhone = findViewById(R.id.etHPhone);
        etEmail = findViewById(R.id.etHEmail);
        etWebsite = findViewById(R.id.etHWebsite);
        etHours = findViewById(R.id.etHHours);
        etEmergencyPhone = findViewById(R.id.etHEmergencyPhone);

        actvType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, TYPES));
        for (String f : FACILITIES) {
            Chip c = new Chip(this);
            c.setText(f);
            c.setCheckable(true);
            chipGroup.addView(c);
        }
        btnSubmit.setText(mode == MODE_EDIT ? "Save changes" : "Submit for approval");
        findViewById(R.id.btnUseLocation).setOnClickListener(v -> {
            if (hasLocationPermission()) fetchLocation();
            else askLocation.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        });
        findViewById(R.id.btnPickOnMap).setOnClickListener(v -> {
            android.content.Intent i = new android.content.Intent(this, com.example.mediq.ui.patient.PickLocationActivity.class);
            if (lat != null && lon != null) { i.putExtra("lat", lat); i.putExtra("lon", lon); }
            pickOnMap.launch(i);
        });
        btnSubmit.setOnClickListener(v -> submit());

        renderPhotos();
        if (mode == MODE_EDIT && hospitalId != null) loadExisting();
    }

    // ------------------------------------------------------------------ photos
    private void renderPhotos() {
        layoutPhotos.removeAllViews();
        int size = UiUtils.dp(layoutPhotos, 96), gap = UiUtils.dp(layoutPhotos, 8);

        TextView add = new TextView(this);
        add.setText("＋\nAdd photo");
        add.setGravity(Gravity.CENTER);
        add.setTextSize(13);
        add.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));
        add.setBackgroundResource(R.drawable.bg_chip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMarginEnd(gap);
        add.setLayoutParams(lp);
        add.setOnClickListener(v -> pickPhoto.launch("image/*"));
        layoutPhotos.addView(add);

        for (int i = 0; i < photos.size(); i++) {
            final Photo p = photos.get(i);
            FrameLayout cell = new FrameLayout(this);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(size, size);
            cl.setMarginEnd(gap);
            cell.setLayoutParams(cl);

            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
            Glide.with(this).load(p.local != null ? p.local : Uri.parse(p.url)).into(iv);
            cell.addView(iv);

            TextView x = new TextView(this);
            x.setText("✕");
            x.setGravity(Gravity.CENTER);
            x.setTextColor(0xFFFFFFFF);
            x.setBackgroundColor(0x99000000);
            FrameLayout.LayoutParams xl = new FrameLayout.LayoutParams(UiUtils.dp(cell, 26), UiUtils.dp(cell, 26), Gravity.TOP | Gravity.END);
            x.setLayoutParams(xl);
            x.setOnClickListener(v -> { photos.remove(p); renderPhotos(); });
            cell.addView(x);

            if (i == 0) {
                TextView cover = new TextView(this);
                cover.setText("Cover");
                cover.setTextSize(10);
                cover.setTextColor(0xFFFFFFFF);
                cover.setBackgroundColor(0xCC1976D2);
                cover.setPadding(UiUtils.dp(cell, 6), 0, UiUtils.dp(cell, 6), 0);
                cover.setLayoutParams(new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START));
                cell.addView(cover);
            }
            layoutPhotos.addView(cell);
        }
    }

    // ------------------------------------------------------------------ location
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void fetchLocation() {
        if (!hasLocationPermission()) { ToastUtils.show(this, "Location permission is needed to pin the hospital"); return; }
        try {
            LocationServices.getFusedLocationProviderClient(this).getLastLocation().addOnSuccessListener(loc -> {
                if (loc == null) { ToastUtils.show(this, "Couldn't get location. Open Maps once and try again."); return; }
                lat = loc.getLatitude();
                lon = loc.getLongitude();
                showLocation();
            });
        } catch (SecurityException ignored) { }
    }

    private void showLocation() {
        tvLocation.setText(lat != null && lon != null
                ? String.format(java.util.Locale.US, "📍 Pinned at %.5f, %.5f", lat, lon) : "Map location not set");
    }

    // ------------------------------------------------------------------ edit mode
    private void loadExisting() {
        SupabaseClient.getInstance().get("hospitals?id=eq." + hospitalId + "&select=*", new SupabaseClient.Callback() {
            @Override public void onSuccess(String json) {
                try {
                    JSONArray arr = new JSONArray(json);
                    if (arr.length() == 0) return;
                    JSONObject h = arr.getJSONObject(0);
                    set(etName, h, "name"); set(etReg, h, "registration_number"); set(etYear, h, "established_year");
                    set(etBeds, h, "bed_count"); set(etDesc, h, "description"); set(etAddress, h, "address");
                    set(etCity, h, "city"); set(etState, h, "state"); set(etPhone, h, "phone"); set(etEmail, h, "email");
                    set(etWebsite, h, "website"); set(etHours, h, "opening_hours"); set(etEmergencyPhone, h, "emergency_phone");
                    String type = SupabaseClient.optStringSafe(h, "type");
                    if (type != null) actvType.setText(type, false);
                    switchEmergency.setChecked(h.optBoolean("emergency_24x7", false));
                    if (!h.isNull("latitude") && !h.isNull("longitude")) { lat = h.optDouble("latitude"); lon = h.optDouble("longitude"); showLocation(); }

                    JSONArray fac = h.optJSONArray("facilities");
                    if (fac != null) {
                        for (int i = 0; i < fac.length(); i++) {
                            String f = fac.optString(i);
                            boolean found = false;
                            for (int c = 0; c < chipGroup.getChildCount(); c++) {
                                Chip chip = (Chip) chipGroup.getChildAt(c);
                                if (chip.getText().toString().equalsIgnoreCase(f)) { chip.setChecked(true); found = true; }
                            }
                            if (!found && !f.isEmpty()) {
                                Chip extra = new Chip(HospitalFormActivity.this);
                                extra.setText(f); extra.setCheckable(true); extra.setChecked(true);
                                chipGroup.addView(extra);
                            }
                        }
                    }
                    JSONArray imgs = h.optJSONArray("image_urls");
                    photos.clear();
                    if (imgs != null && imgs.length() > 0) {
                        for (int i = 0; i < imgs.length(); i++) { Photo p = new Photo(); p.url = imgs.optString(i); photos.add(p); }
                    } else {
                        String cover = SupabaseClient.optStringSafe(h, "image_url");
                        if (cover != null) { Photo p = new Photo(); p.url = cover; photos.add(p); }
                    }
                    renderPhotos();
                } catch (Exception ignored) { }
            }
            @Override public void onError(Exception e) { ToastUtils.show(HospitalFormActivity.this, "Couldn't load hospital details"); }
        });
    }

    private void set(TextInputEditText et, JSONObject o, String key) {
        String v = SupabaseClient.optStringSafe(o, key);
        if (v != null) et.setText(v);
    }

    // ------------------------------------------------------------------ submit
    private String t(TextInputEditText et) { return et.getText() == null ? "" : et.getText().toString().trim(); }

    private void submit() {
        if (t(etName).isEmpty() || t(etAddress).isEmpty() || t(etCity).isEmpty() || t(etPhone).isEmpty()) {
            ToastUtils.show(this, "Name, address, city and phone are required");
            return;
        }
        if (mode == MODE_REGISTER && t(etReg).isEmpty()) {
            ToastUtils.show(this, "Please add the hospital's registration / license number");
            return;
        }
        if (mode == MODE_REGISTER && photos.isEmpty()) {
            ToastUtils.show(this, "Please add at least one hospital photo");
            return;
        }
        btnSubmit.setEnabled(false);
        btnSubmit.setText("Uploading…");
        uploadPhotos(0, new ArrayList<>());
    }

    /** Uploads new photos one by one (keeping order), then continues with the full URL list. */
    private void uploadPhotos(int index, List<String> urls) {
        if (index >= photos.size()) { save(urls); return; }
        Photo p = photos.get(index);
        if (p.local == null) { urls.add(p.url); uploadPhotos(index + 1, urls); return; }

        byte[] jpeg = ImageUtils.toJpeg(this, p.local, 1280, 82);
        String uid = SessionManager.getInstance(this).getUserId();
        if (jpeg == null || uid == null) { fail("Couldn't read one of the photos"); return; }
        String path = uid + "/hospital_" + System.currentTimeMillis() + "_" + index + ".jpg";
        SupabaseClient.getInstance().uploadFile("hospital-images", path, jpeg, "image/jpeg", new SupabaseClient.Callback() {
            @Override public void onSuccess(String publicUrl) { urls.add(publicUrl); uploadPhotos(index + 1, urls); }
            @Override public void onError(Exception e) { fail("Photo upload failed. Has sql/07 been run (hospital-images bucket)?"); }
        });
    }

    private JSONObject buildHospitalJson(List<String> urls) throws Exception {
        JSONObject h = new JSONObject();
        h.put("name", t(etName));
        h.put("type", actvType.getText().toString().trim());
        h.put("address", t(etAddress));
        h.put("city", t(etCity));
        h.put("state", t(etState));
        h.put("phone", t(etPhone));
        h.put("email", t(etEmail));
        h.put("website", t(etWebsite));
        h.put("registration_number", t(etReg));
        h.put("opening_hours", t(etHours));
        h.put("description", t(etDesc));
        h.put("established_year", t(etYear));
        h.put("bed_count", t(etBeds));
        h.put("emergency_24x7", switchEmergency.isChecked());
        h.put("emergency_phone", t(etEmergencyPhone));
        if (lat != null && lon != null) { h.put("latitude", String.valueOf(lat)); h.put("longitude", String.valueOf(lon)); }
        JSONArray fac = new JSONArray();
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            Chip c = (Chip) chipGroup.getChildAt(i);
            if (c.isChecked()) fac.put(c.getText().toString());
        }
        h.put("facilities", fac);
        JSONArray imgs = new JSONArray();
        for (String u : urls) imgs.put(u);
        h.put("image_urls", imgs);
        return h;
    }

    private void save(List<String> urls) {
        try {
            JSONObject h = buildHospitalJson(urls);
            if (mode == MODE_REGISTER) {
                JSONObject body = new JSONObject();
                body.put("p_role", role);
                body.put("p_hospital_id", JSONObject.NULL);
                body.put("p_new_hospital", h);
                body.put("p_details", new JSONObject(detailsJson));
                SupabaseClient.getInstance().rpc("submit_staff_application", body, new SupabaseClient.Callback() {
                    @Override public void onSuccess(String r) {
                        try {
                            JSONObject o = new JSONObject(r);
                            if (!o.optBoolean("success", false)) { fail(o.optString("error", "Couldn't submit")); return; }
                        } catch (Exception ignored) { }
                        Intent i = new Intent(HospitalFormActivity.this, PendingApprovalActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                        finish();
                    }
                    @Override public void onError(Exception e) { fail("Couldn't submit: " + e.getMessage()); }
                });
            } else {
                // Edit: send real column values (blank numbers become null)
                JSONObject patch = new JSONObject();
                for (String k : new String[]{"name", "type", "address", "city", "state", "phone", "email", "website",
                        "registration_number", "opening_hours", "description", "emergency_phone"}) {
                    String v = h.optString(k, "");
                    patch.put(k, v.isEmpty() ? JSONObject.NULL : v);
                }
                patch.put("established_year", t(etYear).isEmpty() ? JSONObject.NULL : Integer.parseInt(t(etYear)));
                patch.put("bed_count", t(etBeds).isEmpty() ? JSONObject.NULL : Integer.parseInt(t(etBeds)));
                patch.put("emergency_24x7", switchEmergency.isChecked());
                if (lat != null && lon != null) { patch.put("latitude", lat); patch.put("longitude", lon); }
                patch.put("facilities", h.getJSONArray("facilities"));
                patch.put("image_urls", h.getJSONArray("image_urls"));
                SupabaseClient.getInstance().patch("hospitals?id=eq." + hospitalId, patch, new SupabaseClient.Callback() {
                    @Override public void onSuccess(String r) {
                        ToastUtils.show(HospitalFormActivity.this, "Hospital details saved");
                        finish();
                    }
                    @Override public void onError(Exception e) { fail("Couldn't save: " + e.getMessage()); }
                });
            }
        } catch (Exception e) {
            fail("Please check the numbers you entered");
        }
    }

    private void fail(String msg) {
        ToastUtils.show(this, msg);
        btnSubmit.setEnabled(true);
        btnSubmit.setText(mode == MODE_EDIT ? "Save changes" : "Submit for approval");
    }
}
