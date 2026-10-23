package com.example.mediq.ui.queue;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediq.R;
import com.example.mediq.data.model.QueueEntry;
import com.example.mediq.data.remote.SupabaseClient;
import com.example.mediq.ui.adapters.QueueEntryAdapter;
import com.example.mediq.ui.patient.MapViewActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen displaying the real-time queue status, current token, estimated wait time,
 * and the list of preceding and subsequent tokens for a hospital appointment.
 * Automatically polls every 10 seconds for live updates.
 */
public class LiveQueueActivity extends AppCompatActivity {

    private static final String TAG = "LiveQueueActivity";

    public static final String EXTRA_HOSPITAL_NAME = "extra_hospital_name";
    public static final String EXTRA_DOCTOR_NAME = "extra_doctor_name";
    public static final String EXTRA_TOKEN_NUMBER = "extra_token_number";
    public static final String EXTRA_QUEUE_ID = "extra_queue_id";

    private ImageButton btnBack;
    private TextView tvHospitalName;
    private TextView tvDoctorName;
    private TextView tvDoctorSpecialty;
    private TextView tvConsultationStatusBadge;
    private TextView tvYourTokenValue;
    private TextView tvPatientsAheadValue;
    private TextView tvEstimatedWaitValue;
    private RecyclerView recyclerViewQueue;
    private FloatingActionButton fabLocation;

    private QueueEntryAdapter queueAdapter;
    private String userToken = null;
    private String lastBadge = "";
    private String queueId = null;

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            loadQueueData();
            pollHandler.postDelayed(this, 10000);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_queue);

        initViews();
        setupListeners();
        setupRecyclerView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        pollHandler.removeCallbacks(pollRunnable);
        pollHandler.post(pollRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pollHandler.removeCallbacks(pollRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pollHandler.removeCallbacks(pollRunnable);
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        tvHospitalName = findViewById(R.id.tvHospitalName);
        tvDoctorName = findViewById(R.id.tvDoctorName);
        tvDoctorSpecialty = findViewById(R.id.tvDoctorSpecialty);
        tvConsultationStatusBadge = findViewById(R.id.tvConsultationStatusBadge);
        tvYourTokenValue = findViewById(R.id.tvYourTokenValue);
        tvPatientsAheadValue = findViewById(R.id.tvPatientsAheadValue);
        tvEstimatedWaitValue = findViewById(R.id.tvEstimatedWaitValue);
        recyclerViewQueue = findViewById(R.id.recyclerViewQueue);
        fabLocation = findViewById(R.id.fabLocation);

        if (getIntent() != null) {
            String hospital = getIntent().getStringExtra(EXTRA_HOSPITAL_NAME);
            if (hospital != null && !hospital.isEmpty()) {
                tvHospitalName.setText(hospital);
            }
            String doctor = getIntent().getStringExtra(EXTRA_DOCTOR_NAME);
            if (doctor != null && !doctor.isEmpty()) {
                tvDoctorName.setText(doctor);
            }
            String token = getIntent().getStringExtra(EXTRA_TOKEN_NUMBER);
            if (token != null && !token.isEmpty()) {
                userToken = token;
                tvYourTokenValue.setText(token);
            }
            if (getIntent().hasExtra(EXTRA_QUEUE_ID)) {
                queueId = getIntent().getStringExtra(EXTRA_QUEUE_ID);
            } else if (getIntent().hasExtra("extra_queue_id")) {
                queueId = getIntent().getStringExtra("extra_queue_id");
            }
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        // Directions to the hospital for this queue (falls back to the map screen)
        fabLocation.setOnClickListener(v -> {
            String name = tvHospitalName.getText() == null ? "" : tvHospitalName.getText().toString().trim();
            try {
                if (!name.isEmpty()) {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode(name))));
                } else {
                    startActivity(new Intent(this, MapViewActivity.class));
                }
            } catch (android.content.ActivityNotFoundException e) {
                startActivity(new Intent(this, MapViewActivity.class));
            }
        });
    }

    private void setupRecyclerView() {
        queueAdapter = new QueueEntryAdapter();
        recyclerViewQueue.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewQueue.setNestedScrollingEnabled(false);
        recyclerViewQueue.setAdapter(queueAdapter);
    }

    private void loadQueueData() {
        if (queueId == null || queueId.isEmpty()) {
            tvConsultationStatusBadge.setText("No Active Queue");
            tvPatientsAheadValue.setText("—");
            tvEstimatedWaitValue.setText("—");
            queueAdapter.setItems(new ArrayList<>());
            return;
        }

        String endpoint = "live_queue_status?queue_id=eq." + queueId + "&order=token_sequence.asc";

        SupabaseClient.getInstance().get(endpoint, new SupabaseClient.Callback() {
            @Override
            public void onSuccess(String jsonResponse) {
                try {
                    JSONArray array = new JSONArray(jsonResponse);
                    List<QueueEntry> queueEntries = new ArrayList<>();
                    int userSequence = -1;
                    String activeToken = null;
                    int waitingAhead = 0;
                    boolean foundUser = false;
                    boolean isUserInConsultation = false;

                    for (int i = 0; i < array.length(); i++) {
                        JSONObject obj = array.getJSONObject(i);
                        String id = SupabaseClient.optStringSafe(obj, "id");
                        String token = SupabaseClient.optStringSafe(obj, "token_number");
                        if (token == null) token = "A-" + (i + 1);
                        int seq = obj.optInt("token_sequence", i + 1);
                        String rawStatus = SupabaseClient.optStringSafe(obj, "status");
                        if (rawStatus == null) rawStatus = "waiting";

                        boolean isCurrent = userToken != null && userToken.equalsIgnoreCase(token);
                        if (isCurrent) {
                            userSequence = seq;
                            foundUser = true;
                            if ("in_progress".equalsIgnoreCase(rawStatus)) {
                                isUserInConsultation = true;
                            }
                        }

                        if ("in_progress".equalsIgnoreCase(rawStatus)) {
                            activeToken = token;
                        }

                        // Map raw DB status to display status
                        String displayStatus;
                        if ("completed".equalsIgnoreCase(rawStatus)) {
                            displayStatus = QueueEntry.STATUS_COMPLETED;
                        } else if ("in_progress".equalsIgnoreCase(rawStatus)) {
                            displayStatus = QueueEntry.STATUS_IN_CONSULTATION;
                        } else {
                            displayStatus = QueueEntry.STATUS_WAITING;
                            if (!foundUser && ("waiting".equalsIgnoreCase(rawStatus) || "checked_in".equalsIgnoreCase(rawStatus))) {
                                waitingAhead++;
                            }
                        }

                        queueEntries.add(new QueueEntry(id, token, seq, displayStatus, isCurrent));
                    }

                    // Check if current user is next
                    if (!isUserInConsultation && waitingAhead == 0 && foundUser) {
                        for (int i = 0; i < queueEntries.size(); i++) {
                            QueueEntry qe = queueEntries.get(i);
                            if (qe.isCurrentUser() && QueueEntry.STATUS_WAITING.equals(qe.getStatus())) {
                                queueEntries.set(i, new QueueEntry(qe.getId(), qe.getTokenNumber(), qe.getTokenSequence(), QueueEntry.STATUS_YOUR_TURN, true));
                                break;
                            }
                        }
                    }

                    final List<QueueEntry> finalList = queueEntries;
                    final int ahead = waitingAhead;
                    final boolean userInConsultation = isUserInConsultation;
                    final String inProgToken = activeToken;
                    final int targetSeq = userSequence;

                    runOnUiThread(() -> {
                        queueAdapter.setItems(finalList);

                        if (userInConsultation) {
                            tvConsultationStatusBadge.setText("In Consultation");
                            tvPatientsAheadValue.setText("0");
                            tvEstimatedWaitValue.setText("Now");
                        } else if (ahead == 0 && targetSeq > 0) {
                            tvConsultationStatusBadge.setText("Your Turn Next");
                            tvPatientsAheadValue.setText("0");
                            tvEstimatedWaitValue.setText("< 5 minutes");
                        } else {
                            if (inProgToken != null) {
                                tvConsultationStatusBadge.setText("Now Serving: " + inProgToken);
                            } else {
                                tvConsultationStatusBadge.setText("Queue Active");
                            }
                            tvPatientsAheadValue.setText(String.valueOf(ahead));
                            tvEstimatedWaitValue.setText("~" + (ahead * 10) + " minutes");
                        }

                        String badge = tvConsultationStatusBadge.getText().toString();
                        if (!badge.equals(lastBadge)) {
                            if (userInConsultation && !"In Consultation".equals(lastBadge)) {
                                com.google.android.material.snackbar.Snackbar.make(recyclerViewQueue,
                                        "It's your turn — please go in now 🎉", com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
                            } else if (!userInConsultation && ahead == 0 && targetSeq > 0 && !"Your Turn Next".equals(lastBadge)) {
                                com.google.android.material.snackbar.Snackbar.make(recyclerViewQueue,
                                        "You're next — please be ready ⏳", com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
                            }
                            lastBadge = badge;
                        }

                        // Server-side estimate refines the numbers, but never overrides "in consultation"
                        if (targetSeq > 0 && !userInConsultation) {
                            fetchEstimatedWaitRpc(queueId, targetSeq);
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "Error parsing live queue status", e);
                }
            }

            @Override
            public void onError(Exception error) {
                Log.w(TAG, "Failed to load live queue status: " + error.getMessage());
            }
        });
    }

    private void fetchEstimatedWaitRpc(String qId, int tokenSeq) {
        try {
            JSONObject params = new JSONObject();
            params.put("p_queue_id", qId);
            params.put("p_token_seq", tokenSeq);

            SupabaseClient.getInstance().rpc("get_estimated_wait", params, new SupabaseClient.Callback() {
                @Override
                public void onSuccess(String response) {
                    try {
                        JSONObject res = new JSONObject(response);
                        int ahead = res.optInt("patients_ahead", 0);
                        int waitMins = res.optInt("estimated_wait_minutes", ahead * 10);

                        tvPatientsAheadValue.setText(String.valueOf(ahead));
                        tvEstimatedWaitValue.setText("~" + waitMins + " minutes");
                        if (ahead == 0) {
                            tvConsultationStatusBadge.setText("Your Turn Next");
                        }
                    } catch (Exception ignored) {
                    }
                }

                @Override
                public void onError(Exception error) {}
            });
        } catch (Exception ignored) {
        }
    }
}
