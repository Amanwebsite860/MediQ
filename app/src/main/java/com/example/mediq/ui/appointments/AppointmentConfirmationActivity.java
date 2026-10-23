package com.example.mediq.ui.appointments;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mediq.MainActivity;
import com.example.mediq.R;
import com.google.android.material.button.MaterialButton;

/**
 * Screen displayed immediately after booking confirmation.
 * Shows token number, booking summary, and navigation options.
 */
public class AppointmentConfirmationActivity extends AppCompatActivity {

    public static final String EXTRA_DOCTOR_NAME = "extra_doctor_name";
    public static final String EXTRA_DOCTOR_SPECIALTY = "extra_doctor_specialty";
    public static final String EXTRA_HOSPITAL_NAME = "extra_hospital_name";
    public static final String EXTRA_APPOINTMENT_DATE = "extra_appointment_date";
    public static final String EXTRA_APPOINTMENT_TIME = "extra_appointment_time";
    public static final String EXTRA_TOKEN_NUMBER = "extra_token_number";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appointment_confirmation);

        TextView tvTokenNumber = findViewById(R.id.tvTokenNumber);
        TextView tvDoctorName = findViewById(R.id.tvDoctorName);
        TextView tvDoctorSpecialty = findViewById(R.id.tvDoctorSpecialty);
        TextView tvAppointmentDate = findViewById(R.id.tvAppointmentDate);
        TextView tvAppointmentTime = findViewById(R.id.tvAppointmentTime);
        TextView tvHospitalName = findViewById(R.id.tvHospitalName);
        ImageView ivDoctorAvatar = findViewById(R.id.ivDoctorAvatar);

        MaterialButton btnViewInAppointments = findViewById(R.id.btnViewInAppointments);
        MaterialButton btnGoToHome = findViewById(R.id.btnGoToHome);

        // Read extras with fallbacks matching the mockup
        String doctorName = getIntent().getStringExtra(EXTRA_DOCTOR_NAME);
        if (doctorName == null) doctorName = "";

        String specialty = getIntent().getStringExtra(EXTRA_DOCTOR_SPECIALTY);
        if (specialty == null) specialty = "";

        String hospital = getIntent().getStringExtra(EXTRA_HOSPITAL_NAME);
        if (hospital == null) hospital = "";

        String date = getIntent().getStringExtra(EXTRA_APPOINTMENT_DATE);
        if (date == null) date = "";

        String time = getIntent().getStringExtra(EXTRA_APPOINTMENT_TIME);
        if (time == null) time = "";

        String token = getIntent().getStringExtra(EXTRA_TOKEN_NUMBER);
        if (token == null) token = "";

        tvDoctorName.setText(doctorName);
        tvDoctorSpecialty.setText(specialty);
        tvHospitalName.setText(hospital);
        tvAppointmentDate.setText(date);
        tvAppointmentTime.setText(time);
        tvTokenNumber.setText(token);

        // Add to calendar (system calendar editor; user confirms)
        final String fDoctor = doctorName, fHospital = hospital, fDate = date, fTime = time;
        findViewById(R.id.btnAddToCalendar).setOnClickListener(v -> {
            try {
                java.util.Date start = new java.text.SimpleDateFormat("EEE, dd MMM yyyy hh:mm a", java.util.Locale.ENGLISH)
                        .parse(fDate + " " + fTime.trim());
                Intent cal = new Intent(Intent.ACTION_INSERT)
                        .setData(android.provider.CalendarContract.Events.CONTENT_URI)
                        .putExtra(android.provider.CalendarContract.Events.TITLE, "Appointment with " + fDoctor)
                        .putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, fHospital)
                        .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.getTime())
                        .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, start.getTime() + 30 * 60 * 1000L);
                startActivity(cal);
            } catch (Exception e) {
                com.example.mediq.util.ToastUtils.show(this, "Couldn't add to calendar");
            }
        });

        // Buttons
        btnViewInAppointments.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("navigate_tab", R.id.nav_appointments);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        btnGoToHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }
}
