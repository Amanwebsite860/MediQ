package com.example.mediq;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.mediq.data.remote.SessionManager;
import com.example.mediq.data.remote.SupabaseClient;
import com.example.mediq.ui.appointments.MyAppointmentsFragment;
import com.example.mediq.ui.queue.LiveQueueActivity;
import com.example.mediq.util.UiUtils;
import com.example.mediq.ui.patient.HomeFragment;
import com.example.mediq.ui.patient.ProfileFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;
    private final androidx.activity.result.ActivityResultLauncher<String> askNotifPermission =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                    granted -> { if (granted) com.example.mediq.service.QueueWatcherService.start(this); });
    private boolean askedNotifPermission = false;
    private View stripActive;
    private TextView tvStripTitle, tvStripSub, btnStripAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Apply system window insets so content is not obscured by system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        stripActive = findViewById(R.id.stripActive);
        tvStripTitle = findViewById(R.id.tvStripTitle);
        tvStripSub = findViewById(R.id.tvStripSub);
        btnStripAction = findViewById(R.id.btnStripAction);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            Fragment selectedFragment = null;

            if (itemId == R.id.nav_map) {
                // The map is a full-screen page: open it, but keep the current tab highlighted
                startActivity(new Intent(this, com.example.mediq.ui.patient.MapViewActivity.class));
                return false;
            }
            if (itemId == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else if (itemId == R.id.nav_appointments) {
                selectedFragment = new MyAppointmentsFragment();
            } else if (itemId == R.id.nav_profile) {
                selectedFragment = new ProfileFragment();
            }

            if (selectedFragment != null) {
                replaceFragment(selectedFragment);
                return true;
            }
            return false;
        });

        // Handle intent extra if launched to open a specific tab
        handleIncomingIntent(getIntent(), savedInstanceState);
    }


    @Override
    protected void onResume() {
        super.onResume();
        refreshActiveAppointmentStrip();
    }

    /** Phone alerts while the app is closed, only on a day the patient has a visit. */
    private void startQueueWatcher() {
        if (com.example.mediq.service.QueueWatcherService.canNotify(this)) {
            com.example.mediq.service.QueueWatcherService.start(this);
        } else if (!askedNotifPermission && android.os.Build.VERSION.SDK_INT >= 33) {
            askedNotifPermission = true;
            askNotifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    /** Shows the patient's next booked appointment above the bottom nav, with a one-tap "Track". */
    private void refreshActiveAppointmentStrip() {
        String uid = SessionManager.getInstance(this).getUserId();
        if (uid == null || uid.isEmpty()) { stripActive.setVisibility(View.GONE); return; }
        final String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String q = "appointments?patient_id=eq." + uid
                + "&status=in.(pending,confirmed)&appointment_date=gte." + today
                + "&select=id,appointment_date,appointment_time,doctors(name),hospitals(name),queue_entries(token_number,queue_id,status)"
                + "&order=appointment_date.asc,appointment_time.asc&limit=1";
        SupabaseClient.getInstance().get(q, new SupabaseClient.Callback() {
            @Override public void onSuccess(String json) {
                try {
                    JSONArray arr = new JSONArray(json);
                    if (arr.length() == 0) { stripActive.setVisibility(View.GONE); return; }
                    JSONObject a = arr.getJSONObject(0);
                    String date = a.optString("appointment_date");
                    String time = UiUtils.formatTime(a.optString("appointment_time"));
                    JSONObject doc = a.optJSONObject("doctors");
                    JSONObject hosp = a.optJSONObject("hospitals");
                    String docName = doc != null ? doc.optString("name") : "your doctor";
                    String hospName = hosp != null ? hosp.optString("name") : "";

                    String token = null, queueId = null;
                    JSONArray qe = a.optJSONArray("queue_entries");
                    if (qe != null && qe.length() > 0) {
                        JSONObject e = qe.getJSONObject(0);
                        token = SupabaseClient.optStringSafe(e, "token_number");
                        queueId = SupabaseClient.optStringSafe(e, "queue_id");
                    }

                    boolean isToday = today.equals(date);
                    tvStripTitle.setText(docName);
                    final String fToken = token, fQueue = queueId, fHosp = hospName, fDoc = docName;
                    if (isToday) {
                        startQueueWatcher();
                        tvStripSub.setText("Today \u00B7 " + time + (token != null ? " \u00B7 Token " + token : ""));
                        btnStripAction.setText("Track");
                        btnStripAction.setOnClickListener(v -> {
                            Intent i = new Intent(MainActivity.this, LiveQueueActivity.class);
                            i.putExtra(LiveQueueActivity.EXTRA_TOKEN_NUMBER, fToken);
                            i.putExtra(LiveQueueActivity.EXTRA_DOCTOR_NAME, fDoc);
                            i.putExtra(LiveQueueActivity.EXTRA_HOSPITAL_NAME, fHosp);
                            if (fQueue != null) i.putExtra("extra_queue_id", fQueue);
                            startActivity(i);
                        });
                    } else {
                        tvStripSub.setText("Upcoming \u00B7 " + UiUtils.formatShortDate(date) + ", " + time);
                        btnStripAction.setText("View");
                        btnStripAction.setOnClickListener(v -> navigateToTab(R.id.nav_appointments));
                    }
                    stripActive.setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    stripActive.setVisibility(View.GONE);
                }
            }
            @Override public void onError(Exception e) { stripActive.setVisibility(View.GONE); }
        });
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent, null);
    }

    private void handleIncomingIntent(android.content.Intent intent, Bundle savedInstanceState) {
        if (intent != null && intent.hasExtra("navigate_tab")) {
            int tabId = intent.getIntExtra("navigate_tab", R.id.nav_home);
            bottomNavigationView.setSelectedItemId(tabId);
        } else if (savedInstanceState == null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        }
    }

    /**
     * Programmatically switch the active tab in BottomNavigationView.
     *
     * @param menuItemId Resource ID of the menu item (e.g., R.id.nav_appointments)
     */
    public void navigateToTab(int menuItemId) {
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(menuItemId);
        }
    }

    private void replaceFragment(@NonNull Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}