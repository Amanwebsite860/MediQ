package com.example.mediq.ui.staff;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mediq.R;
import com.example.mediq.data.remote.SessionManager;
import com.example.mediq.data.remote.SupabaseClient;
import com.example.mediq.ui.auth.LoginActivity;
import com.example.mediq.util.ToastUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Shown on the first login of a doctor / receptionist.
 * Step 1: professional details + pick a registered hospital (or choose "register my hospital").
 * Registered hospital -> submit now. New hospital -> HospitalFormActivity collects the rest.
 * Either way the admin must approve before the dashboard opens.
 */
public class StaffOnboardingActivity extends AppCompatActivity {

    public static final String[] SPECIALTIES = {
            "General Physician", "Dermatologist", "Cardiologist", "Pediatrician", "Orthopedic",
            "Gynecologist", "ENT Specialist", "Neurologist", "Dentist", "Psychiatrist", "Ophthalmologist", "Other"};

    private String role = "receptionist";
    private AutoCompleteTextView actvHospital, actvSpecialty;
    private TextInputEditText etExperience, etFee, etQualification, etLicense;
    private View layoutDoctorFields;
    private MaterialButton btnContinue, btnRegister;

    private final List<String> hospitalIds = new ArrayList<>();
    private final List<String> hospitalLabels = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff_onboarding);

        ((TextView) findViewById(R.id.tvScreenTitle)).setText("Complete your profile");
        findViewById(R.id.btnBack).setVisibility(View.INVISIBLE);

        actvHospital = findViewById(R.id.actvHospital);
        actvSpecialty = findViewById(R.id.actvSpecialty);
        etExperience = findViewById(R.id.etExperience);
        etFee = findViewById(R.id.etFee);
        etQualification = findViewById(R.id.etQualification);
        etLicense = findViewById(R.id.etLicense);
        layoutDoctorFields = findViewById(R.id.layoutDoctorFields);
        btnContinue = findViewById(R.id.btnObContinue);
        btnRegister = findViewById(R.id.btnObRegisterHospital);

        actvSpecialty.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, SPECIALTIES));

        String name = SessionManager.getInstance(this).getUserName();
        ((TextView) findViewById(R.id.tvObHello)).setText("Welcome" + (name != null && !name.isEmpty() ? ", " + name.split(" ")[0] : ""));

        btnContinue.setOnClickListener(v -> submitExistingHospital());
        btnRegister.setOnClickListener(v -> openHospitalForm());
        findViewById(R.id.btnObLogout).setOnClickListener(v -> logout());

        loadRole();
        loadHospitals();
    }

    private void loadRole() {
        String uid = SessionManager.getInstance(this).getUserId();
        SupabaseClient.getInstance().get("profiles?id=eq." + uid + "&select=requested_role", new SupabaseClient.Callback() {
            @Override public void onSuccess(String json) {
                try {
                    JSONArray arr = new JSONArray(json);
                    if (arr.length() > 0) {
                        String r = SupabaseClient.optStringSafe(arr.getJSONObject(0), "requested_role");
                        if ("doctor".equals(r) || "receptionist".equals(r)) role = r;
                    }
                } catch (Exception ignored) { }
                layoutDoctorFields.setVisibility("doctor".equals(role) ? View.VISIBLE : View.GONE);
                ((TextView) findViewById(R.id.tvObSub)).setText("doctor".equals(role)
                        ? "Add your details and the hospital you practise at. An admin will verify you before your account goes live."
                        : "Tell us which hospital you manage. An admin will verify you before your account goes live.");
            }
            @Override public void onError(Exception e) { }
        });
    }

    private void loadHospitals() {
        SupabaseClient.getInstance().get("hospitals?select=id,name,city&active=eq.true&status=eq.approved&order=name",
                new SupabaseClient.Callback() {
                    @Override public void onSuccess(String json) {
                        try {
                            JSONArray arr = new JSONArray(json);
                            hospitalIds.clear();
                            hospitalLabels.clear();
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);
                                hospitalIds.add(o.optString("id"));
                                String city = SupabaseClient.optStringSafe(o, "city");
                                hospitalLabels.add(o.optString("name") + (city != null ? " — " + city : ""));
                            }
                            actvHospital.setAdapter(new ArrayAdapter<>(StaffOnboardingActivity.this,
                                    android.R.layout.simple_list_item_1, hospitalLabels));
                        } catch (Exception ignored) { }
                    }
                    @Override public void onError(Exception e) { }
                });
    }

    private String text(TextInputEditText et) { return et.getText() == null ? "" : et.getText().toString().trim(); }

    /** Professional details as JSON, or null (with a toast) if something required is missing. */
    private JSONObject collectDetails() {
        try {
            JSONObject d = new JSONObject();
            d.put("license_number", text(etLicense));
            if ("doctor".equals(role)) {
                String specialty = actvSpecialty.getText().toString().trim();
                if (specialty.isEmpty()) { ToastUtils.show(this, "Please choose your specialty"); return null; }
                if (text(etExperience).isEmpty() || text(etFee).isEmpty()) {
                    ToastUtils.show(this, "Please add your experience and consultation fee");
                    return null;
                }
                d.put("specialty", specialty);
                d.put("experience_years", text(etExperience));
                d.put("consultation_fee", text(etFee));
                d.put("qualification", text(etQualification));
            }
            if (text(etLicense).isEmpty()) { ToastUtils.show(this, "Please add your registration / staff ID number"); return null; }
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    private void submitExistingHospital() {
        String label = actvHospital.getText().toString().trim();
        int idx = hospitalLabels.indexOf(label);
        if (idx < 0) { ToastUtils.show(this, "Please select your hospital from the list"); return; }
        JSONObject details = collectDetails();
        if (details == null) return;

        setBusy(true);
        try {
            JSONObject body = new JSONObject();
            body.put("p_role", role);
            body.put("p_hospital_id", hospitalIds.get(idx));
            body.put("p_new_hospital", JSONObject.NULL);
            body.put("p_details", details);
            SupabaseClient.getInstance().rpc("submit_staff_application", body, new SupabaseClient.Callback() {
                @Override public void onSuccess(String r) {
                    setBusy(false);
                    try {
                        JSONObject o = new JSONObject(r);
                        if (!o.optBoolean("success", false)) {
                            ToastUtils.show(StaffOnboardingActivity.this, o.optString("error", "Couldn't submit"));
                            return;
                        }
                    } catch (Exception ignored) { }
                    goPending();
                }
                @Override public void onError(Exception e) {
                    setBusy(false);
                    ToastUtils.show(StaffOnboardingActivity.this, "Couldn't submit. Has sql/07 been run? (" + e.getMessage() + ")");
                }
            });
        } catch (Exception e) {
            setBusy(false);
        }
    }

    private void openHospitalForm() {
        JSONObject details = collectDetails();
        if (details == null) return;
        Intent i = new Intent(this, HospitalFormActivity.class);
        i.putExtra(HospitalFormActivity.EXTRA_MODE, HospitalFormActivity.MODE_REGISTER);
        i.putExtra(HospitalFormActivity.EXTRA_ROLE, role);
        i.putExtra(HospitalFormActivity.EXTRA_DETAILS, details.toString());
        startActivity(i);
    }

    private void goPending() {
        Intent i = new Intent(this, PendingApprovalActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void setBusy(boolean busy) {
        btnContinue.setEnabled(!busy);
        btnRegister.setEnabled(!busy);
    }

    private void logout() {
        SessionManager.getInstance(this).clearSession();
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
