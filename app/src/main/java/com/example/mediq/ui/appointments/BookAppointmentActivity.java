package com.example.mediq.ui.appointments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediq.R;
import com.example.mediq.data.model.DateItem;
import com.example.mediq.data.model.Doctor;
import com.example.mediq.data.model.TimeSlotItem;
import com.example.mediq.data.remote.SessionManager;
import com.example.mediq.data.remote.SupabaseClient;
import com.example.mediq.ui.adapters.DateAdapter;
import com.example.mediq.ui.adapters.TimeSlotAdapter;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Screen where a patient selects appointment date and time slot.
 * Connected to live doctor_schedules and book_appointment RPC on Supabase.
 */
public class BookAppointmentActivity extends AppCompatActivity {

    private static final String TAG = "BookAppointmentActivity";

    public static final String EXTRA_DOCTOR_ID = "extra_doctor_id";
    public static final String EXTRA_DOCTOR_NAME = "extra_doctor_name";
    public static final String EXTRA_DOCTOR_SPECIALTY = "extra_doctor_specialty";
    public static final String EXTRA_HOSPITAL_NAME = "extra_hospital_name";
    public static final String EXTRA_HOSPITAL_ID = "extra_hospital_id";
    public static final String EXTRA_CONSULTATION_FEE = "extra_consultation_fee";

    private String doctorId = "";
    private String doctorName = "Doctor";
    private String doctorSpecialty = "General Physician";
    private String hospitalName = "Hospital";
    private String hospitalId = "";
    private int consultationFee = 300;

    private DateAdapter dateAdapter;
    private TimeSlotAdapter timeSlotAdapter;
    private MaterialButton btnConfirm;
    private TextView tvNoSlots;
    private RecyclerView rvTimeSlots;
    private boolean hasRetriedScheduleGeneration = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_appointment);

        extractIntentData();
        initViews();
        loadSchedulesForSelectedDate();
    }

    private void extractIntentData() {
        Intent intent = getIntent();
        if (intent != null) {
            if (intent.hasExtra("doctor")) {
                Doctor d = (Doctor) intent.getSerializableExtra("doctor");
                if (d != null) {
                    doctorId = d.getId();
                    doctorName = d.getName();
                    doctorSpecialty = d.getSpecialty();
                    hospitalName = d.getHospitalName();
                    hospitalId = d.getHospitalId();
                    consultationFee = d.getConsultationFee();
                }
            }
            if (intent.hasExtra(EXTRA_DOCTOR_NAME)) {
                doctorName = intent.getStringExtra(EXTRA_DOCTOR_NAME);
            } else if (intent.hasExtra("doctor_name")) {
                doctorName = intent.getStringExtra("doctor_name");
            }
            if (intent.hasExtra(EXTRA_DOCTOR_SPECIALTY)) {
                doctorSpecialty = intent.getStringExtra(EXTRA_DOCTOR_SPECIALTY);
            }
            if (intent.hasExtra(EXTRA_HOSPITAL_NAME)) {
                hospitalName = intent.getStringExtra(EXTRA_HOSPITAL_NAME);
            } else if (intent.hasExtra("hospital_name")) {
                hospitalName = intent.getStringExtra("hospital_name");
            }
            if (intent.hasExtra(EXTRA_DOCTOR_ID)) {
                doctorId = intent.getStringExtra(EXTRA_DOCTOR_ID);
            } else if (intent.hasExtra("doctor_id")) {
                doctorId = intent.getStringExtra("doctor_id");
            }
            if (intent.hasExtra(EXTRA_HOSPITAL_ID)) {
                hospitalId = intent.getStringExtra(EXTRA_HOSPITAL_ID);
            } else if (intent.hasExtra("hospital_id")) {
                hospitalId = intent.getStringExtra("hospital_id");
            }
            if (intent.hasExtra(EXTRA_CONSULTATION_FEE)) {
                consultationFee = intent.getIntExtra(EXTRA_CONSULTATION_FEE, consultationFee);
            }
        }
    }

    private void initViews() {
        ImageView btnBack = findViewById(R.id.btnBack);
        TextView tvDoctorSubtitle = findViewById(R.id.tvDoctorSubtitle);
        RecyclerView rvDates = findViewById(R.id.rvDates);
        rvTimeSlots = findViewById(R.id.rvTimeSlots);
        tvNoSlots = findViewById(R.id.tvNoSlots);
        btnConfirm = findViewById(R.id.btnConfirmAppointment);

        btnBack.setOnClickListener(v -> finish());
        tvDoctorSubtitle.setText(doctorName + " (" + doctorSpecialty + ")");

        // Setup 7-day date selector
        List<DateItem> dates = generateDates();
        dateAdapter = new DateAdapter(dates, (dateItem, position) -> loadSchedulesForSelectedDate());
        rvDates.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvDates.setAdapter(dateAdapter);

        // Setup Time Slots Grid
        timeSlotAdapter = new TimeSlotAdapter(new ArrayList<>(), (slotItem, position) -> {});
        rvTimeSlots.setLayoutManager(new GridLayoutManager(this, 2));
        rvTimeSlots.setAdapter(timeSlotAdapter);

        // Confirm appointment button
        btnConfirm.setOnClickListener(v -> attemptBooking());
    }

    private void loadSchedulesForSelectedDate() {
        DateItem selectedDate = dateAdapter.getSelectedDate();
        if (selectedDate == null) return;

        String dateIso = selectedDate.getIsoDate();

        // 1. Fetch doctor schedule for this date
        String schedQuery = "doctor_schedules?doctor_id=eq." + doctorId + "&schedule_date=eq." + dateIso + "&status=eq.available";
        SupabaseClient.getInstance().get(schedQuery, new SupabaseClient.Callback() {
            @Override
            public void onSuccess(String scheduleResponse) {
                // 2. Fetch existing booked appointments for this date
                String apptQuery = "appointments?doctor_id=eq." + doctorId + "&appointment_date=eq." + dateIso + "&status=neq.cancelled&select=appointment_time";
                SupabaseClient.getInstance().get(apptQuery, new SupabaseClient.Callback() {
                    @Override
                    public void onSuccess(String apptResponse) {
                        try {
                            Set<String> bookedTimes = new HashSet<>();
                            JSONArray apptArray = new JSONArray(apptResponse);
                            for (int i = 0; i < apptArray.length(); i++) {
                                String t = SupabaseClient.optStringSafe(apptArray.getJSONObject(i), "appointment_time");
                                if (t != null && !t.isEmpty()) {
                                    bookedTimes.add(t.substring(0, Math.min(5, t.length())));
                                }
                            }

                            JSONArray schedArray = new JSONArray(scheduleResponse);
                            if (schedArray.length() == 0 && !hasRetriedScheduleGeneration) {
                                hasRetriedScheduleGeneration = true;
                                JSONObject rpcParams = new JSONObject();
                                rpcParams.put("p_days_ahead", 30);
                                SupabaseClient.getInstance().rpc("generate_future_schedules", rpcParams, new SupabaseClient.Callback() {
                                    @Override
                                    public void onSuccess(String ignored) {
                                        loadSchedulesForSelectedDate();
                                    }

                                    @Override
                                    public void onError(Exception e) {
                                        runOnUiThread(() -> updateSlotViews(new ArrayList<>()));
                                    }
                                });
                                return;
                            }

                            List<TimeSlotItem> slots = new ArrayList<>();

                            String todayIso = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Calendar.getInstance().getTime());
                            boolean isToday = dateIso.equals(todayIso);
                            int currentMinuteOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 60 + Calendar.getInstance().get(Calendar.MINUTE);

                            for (int s = 0; s < schedArray.length(); s++) {
                                JSONObject sched = schedArray.getJSONObject(s);
                                String schedId = SupabaseClient.optStringSafe(sched, "id");
                                String startTime = sched.optString("start_time", "09:00:00");
                                String endTime = sched.optString("end_time", "13:00:00");
                                int duration = sched.optInt("slot_duration_minutes", 30);

                                generateSlotsFromRange(slots, schedId, startTime, endTime, duration, bookedTimes, isToday, currentMinuteOfDay);
                            }

                            final List<TimeSlotItem> finalSlots = slots;
                            runOnUiThread(() -> updateSlotViews(finalSlots));
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing slots", e);
                            runOnUiThread(() -> updateSlotViews(new ArrayList<>()));
                        }
                    }

                    @Override
                    public void onError(Exception error) {
                        Log.e(TAG, "Failed to load appointments: " + error.getMessage());
                        runOnUiThread(() -> updateSlotViews(new ArrayList<>()));
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                Log.e(TAG, "Failed to load schedules: " + error.getMessage());
                runOnUiThread(() -> updateSlotViews(new ArrayList<>()));
            }
        });
    }

    private void updateSlotViews(List<TimeSlotItem> slots) {
        if (slots == null || slots.isEmpty()) {
            rvTimeSlots.setVisibility(View.GONE);
            tvNoSlots.setVisibility(View.VISIBLE);
            timeSlotAdapter.updateSlots(new ArrayList<>());
            btnConfirm.setEnabled(false);
        } else {
            tvNoSlots.setVisibility(View.GONE);
            rvTimeSlots.setVisibility(View.VISIBLE);
            timeSlotAdapter.updateSlots(slots);
            btnConfirm.setEnabled(true);
        }
    }

    private void generateSlotsFromRange(List<TimeSlotItem> slots, String scheduleId,
                                        String start, String end, int durationMinutes,
                                        Set<String> booked, boolean isToday, int currentMinuteOfDay) {
        try {
            int startH = Integer.parseInt(start.substring(0, 2));
            int startM = Integer.parseInt(start.substring(3, 5));
            int endH = Integer.parseInt(end.substring(0, 2));
            int endM = Integer.parseInt(end.substring(3, 5));

            int cur = startH * 60 + startM;
            int finish = endH * 60 + endM;

            while (cur + durationMinutes <= finish) {
                // If appointment is today, filter out slots that have already passed
                if (isToday && cur <= currentMinuteOfDay) {
                    cur += durationMinutes;
                    continue;
                }

                int h = cur / 60;
                int m = cur % 60;
                String time24 = String.format(Locale.US, "%02d:%02d:00", h, m);
                String timeShort = String.format(Locale.US, "%02d:%02d", h, m);

                String amPm = h >= 12 ? "PM" : "AM";
                int displayH = h > 12 ? h - 12 : (h == 0 ? 12 : h);
                String displayTime = String.format(Locale.US, "%02d:%02d %s", displayH, m, amPm);

                boolean isAvail = !booked.contains(timeShort);
                boolean isFirstSlot = slots.isEmpty() && isAvail;
                slots.add(new TimeSlotItem(displayTime, time24, scheduleId, isAvail, isFirstSlot));

                cur += durationMinutes;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error calculating slot range", e);
        }
    }

    private void attemptBooking() {
        DateItem selectedDate = dateAdapter.getSelectedDate();
        TimeSlotItem selectedSlot = timeSlotAdapter.getSelectedTimeSlot();

        if (selectedDate == null) {
            com.example.mediq.util.ToastUtils.show(this, "Please select an appointment date");
            return;
        }
        if (selectedSlot == null || !selectedSlot.isAvailable()) {
            com.example.mediq.util.ToastUtils.show(this, "Please select an available time slot");
            return;
        }
        if (selectedSlot.getScheduleId() == null || selectedSlot.getScheduleId().isEmpty()) {
            com.example.mediq.util.ToastUtils.show(this, "Selected slot has no valid schedule.");
            return;
        }

        btnConfirm.setEnabled(false);
        com.example.mediq.util.ToastUtils.show(this, "Reserving slot & generating live queue token...");

        try {
            SessionManager session = SessionManager.getInstance(this);
            String patientId = session.getUserId();

            JSONObject params = new JSONObject();
            if (patientId != null && !patientId.isEmpty()) {
                params.put("p_patient_id", patientId);
            }
            params.put("p_doctor_id", doctorId);
            params.put("p_hospital_id", hospitalId);
            params.put("p_schedule_id", selectedSlot.getScheduleId());
            params.put("p_date", selectedDate.getIsoDate());
            params.put("p_time", selectedSlot.getTime24());

            SupabaseClient.getInstance().rpc("book_appointment", params, new SupabaseClient.Callback() {
                @Override
                public void onSuccess(String response) {
                    try {
                        JSONObject res = new JSONObject(response);
                        boolean success = res.optBoolean("success", false);
                        if (!success) {
                            btnConfirm.setEnabled(true);
                            String error = SupabaseClient.optStringSafe(res, "error");
                            if (error == null || error.isEmpty()) {
                                error = "Unable to book slot. Please try another slot.";
                            }
                            com.example.mediq.util.ToastUtils.show(BookAppointmentActivity.this, error);
                            return;
                        }

                        String tokenNumber = SupabaseClient.optStringSafe(res, "token_number");
                        if (tokenNumber == null || tokenNumber.isEmpty()) {
                            tokenNumber = "A-01";
                        }

                        btnConfirm.setEnabled(true);
                        Intent confirmIntent = new Intent(BookAppointmentActivity.this, AppointmentConfirmationActivity.class);
                        confirmIntent.putExtra(AppointmentConfirmationActivity.EXTRA_DOCTOR_NAME, doctorName);
                        confirmIntent.putExtra(AppointmentConfirmationActivity.EXTRA_DOCTOR_SPECIALTY, doctorSpecialty);
                        confirmIntent.putExtra(AppointmentConfirmationActivity.EXTRA_HOSPITAL_NAME, hospitalName);
                        confirmIntent.putExtra(AppointmentConfirmationActivity.EXTRA_APPOINTMENT_DATE, selectedDate.getFullDate());
                        confirmIntent.putExtra(AppointmentConfirmationActivity.EXTRA_APPOINTMENT_TIME, selectedSlot.getTime());
                        confirmIntent.putExtra(AppointmentConfirmationActivity.EXTRA_TOKEN_NUMBER, tokenNumber);
                        startActivity(confirmIntent);
                        finish();

                    } catch (Exception e) {
                        btnConfirm.setEnabled(true);
                        Log.e(TAG, "Failed to parse booking response: " + response, e);
                        com.example.mediq.util.ToastUtils.show(BookAppointmentActivity.this, "Booking response parse error: " + e.getMessage());
                    }
                }

                @Override
                public void onError(Exception error) {
                    btnConfirm.setEnabled(true);
                    com.example.mediq.util.ToastUtils.show(BookAppointmentActivity.this, "Booking error: " + error.getMessage());
                }
            });
        } catch (Exception e) {
            btnConfirm.setEnabled(true);
            com.example.mediq.util.ToastUtils.show(this, "Error preparing booking: " + e.getMessage());
        }
    }

    private List<DateItem> generateDates() {
        List<DateItem> dates = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();

        SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", Locale.ENGLISH);
        SimpleDateFormat numberFormat = new SimpleDateFormat("dd", Locale.ENGLISH);
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMM", Locale.ENGLISH);
        SimpleDateFormat fullDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.ENGLISH);
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

        for (int i = 0; i < 7; i++) {
            String day = dayFormat.format(calendar.getTime());
            String num = numberFormat.format(calendar.getTime());
            String month = monthFormat.format(calendar.getTime());
            String full = fullDateFormat.format(calendar.getTime());
            String iso = isoFormat.format(calendar.getTime());

            boolean isDefaultSelected = (i == 0);
            dates.add(new DateItem(day, num, month, full, iso, isDefaultSelected));

            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        return dates;
    }
}
