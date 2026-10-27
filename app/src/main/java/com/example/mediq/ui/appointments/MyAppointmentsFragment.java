package com.example.mediq.ui.appointments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediq.R;
import com.example.mediq.data.model.Appointment;
import com.example.mediq.data.remote.SessionManager;
import com.example.mediq.data.remote.SupabaseClient;
import com.example.mediq.ui.adapters.AppointmentAdapter;
import com.example.mediq.ui.queue.LiveQueueActivity;
import com.google.android.material.tabs.TabLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * MyAppointmentsFragment displays upcoming and past appointments.
 * Connected to Supabase PostgREST and cancel_appointment RPC.
 */
public class MyAppointmentsFragment extends Fragment {

    private RecyclerView rvAppointments;
    private View layoutEmptyState;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipe;
    private android.widget.TextView tvEmptyTitle, tvEmptySub, tvEmptyEmoji;
    private AppointmentAdapter adapter;
    private final List<Appointment> upcomingList = new ArrayList<>();
    private final List<Appointment> pastList = new ArrayList<>();
    private int currentTabPosition = 0;

    public MyAppointmentsFragment() {
        // Required empty public constructor
    }

    public static MyAppointmentsFragment newInstance() {
        return new MyAppointmentsFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_appointments, container, false);

        rvAppointments = view.findViewById(R.id.rvAppointments);
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState);
        swipe = view.findViewById(R.id.swipeAppointments);
        tvEmptyTitle = view.findViewById(R.id.tvEmptyTitle);
        tvEmptySub = view.findViewById(R.id.tvEmptySub);
        tvEmptyEmoji = view.findViewById(R.id.tvEmptyEmoji);
        swipe.setOnRefreshListener(this::loadAppointmentsFromSupabase);
        view.findViewById(R.id.btnEmptyCta).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), com.example.mediq.ui.patient.DoctorListActivity.class)));
        TabLayout tabLayout = view.findViewById(R.id.tabLayoutAppointments);

        rvAppointments.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AppointmentAdapter(upcomingList, new AppointmentAdapter.OnAppointmentClickListener() {
            @Override
            public void onViewDetails(Appointment appointment) {
                Intent intent = new Intent(getContext(), LiveQueueActivity.class);
                intent.putExtra(LiveQueueActivity.EXTRA_TOKEN_NUMBER, appointment.getTokenNumber());
                intent.putExtra(LiveQueueActivity.EXTRA_DOCTOR_NAME, appointment.getDoctorName());
                intent.putExtra(LiveQueueActivity.EXTRA_HOSPITAL_NAME, appointment.getHospitalName());
                if (appointment.getQueueId() != null) {
                    intent.putExtra("extra_queue_id", appointment.getQueueId());
                }
                startActivity(intent);
            }

            @Override
            public void onCancel(Appointment appointment) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Cancel appointment?")
                        .setMessage("Your slot with " + appointment.getDoctorName() + " will be released.")
                        .setNegativeButton("Keep it", null)
                        .setPositiveButton("Yes, cancel", (d, w) -> cancelAppointment(appointment))
                        .show();
            }
        });
        rvAppointments.setAdapter(adapter);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTabPosition = tab.getPosition();
                updateDisplayedList();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        loadAppointmentsFromSupabase();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadAppointmentsFromSupabase();
    }

    private void updateDisplayedList() {
        List<Appointment> list = (currentTabPosition == 0) ? upcomingList : pastList;
        adapter.setAppointments(list);
        if (swipe != null) swipe.setRefreshing(false);
        swipe.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        layoutEmptyState.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        boolean up = currentTabPosition == 0;
        tvEmptyEmoji.setText(up ? "🗓️" : "🧾");
        tvEmptyTitle.setText(up ? "No upcoming appointments" : "No past visits yet");
        tvEmptySub.setText(up ? "Book a slot and skip the waiting room" : "Completed and cancelled visits show up here");
    }

    private void cancelAppointment(Appointment appt) {
        if (getContext() == null) return;
        com.example.mediq.util.ToastUtils.show(getActivity(), "Cancelling appointment...");

        try {
            JSONObject params = new JSONObject();
            params.put("p_appointment_id", appt.getId());
            params.put("p_reason", "Cancelled by patient");

            SupabaseClient.getInstance().rpc("cancel_appointment", params, new SupabaseClient.Callback() {
                @Override
                public void onSuccess(String response) {
                    if (getActivity() != null) {
                        com.example.mediq.util.ToastUtils.show(getActivity(), "Appointment cancelled successfully");
                    }
                    loadAppointmentsFromSupabase();
                }

                @Override
                public void onError(Exception error) {
                    if (getActivity() != null) {
                        com.example.mediq.util.ToastUtils.show(getActivity(), "Cancel failed: " + error.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            com.example.mediq.util.ToastUtils.show(getActivity(), "Error cancelling: " + e.getMessage());
        }
    }

    private void loadAppointmentsFromSupabase() {
        if (getContext() == null) return;

        SessionManager session = SessionManager.getInstance(requireContext());
        String patientId = session.getUserId();

        String endpoint = "appointments?select=id,status,appointment_date,appointment_time,doctor_id,hospital_id,doctors(name,specialty,avatar_url,profiles(gender)),hospitals(name),queue_entries(token_number,queue_id)&order=appointment_date.desc,appointment_time.desc";
        if (patientId != null && !patientId.isEmpty()) {
            endpoint += "&patient_id=eq." + patientId;
        }

        SupabaseClient.getInstance().get(endpoint, new SupabaseClient.Callback() {
            @Override
            public void onSuccess(String jsonResponse) {
                try {
                    upcomingList.clear();
                    pastList.clear();

                    JSONArray array = new JSONArray(jsonResponse);
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject obj = array.getJSONObject(i);
                        String id = SupabaseClient.optStringSafe(obj, "id");
                        String status = SupabaseClient.optStringSafe(obj, "status");
                        if (status == null) status = Appointment.STATUS_UPCOMING;
                        String date = SupabaseClient.optStringSafe(obj, "appointment_date");
                        if (date == null) date = "—";
                        String time = SupabaseClient.optStringSafe(obj, "appointment_time");
                        if (time == null) time = "—";

                        String docName = "Doctor";
                        String specialty = "Physician";
                        String avatarUrl = null;
                        JSONObject docObj = obj.optJSONObject("doctors");
                        if (docObj != null) {
                            String dN = SupabaseClient.optStringSafe(docObj, "name");
                            if (dN != null) docName = dN;
                            String sp = SupabaseClient.optStringSafe(docObj, "specialty");
                            if (sp != null) specialty = sp;
                            avatarUrl = SupabaseClient.optStringSafe(docObj, "avatar_url");
                        }

                        String hospName = "Hospital";
                        JSONObject hospObj = obj.optJSONObject("hospitals");
                        if (hospObj != null) {
                            String hn = SupabaseClient.optStringSafe(hospObj, "name");
                            if (hn != null) hospName = hn;
                        }

                        String token = "—";
                        String queueId = null;
                        JSONArray qArr = obj.optJSONArray("queue_entries");
                        if (qArr != null && qArr.length() > 0) {
                            JSONObject qObj = qArr.getJSONObject(0);
                            String t = SupabaseClient.optStringSafe(qObj, "token_number");
                            if (t != null) token = t;
                            queueId = SupabaseClient.optStringSafe(qObj, "queue_id");
                        }

                        String docGender = null;
                        if (docObj != null) {
                            JSONObject p = docObj.optJSONObject("profiles");
                            if (p != null) docGender = SupabaseClient.optStringSafe(p, "gender");
                        }
                        boolean isFemale = false;
                        if (docGender != null && !docGender.isEmpty()) {
                            isFemale = docGender.toLowerCase(java.util.Locale.ROOT).startsWith("f");
                        } else {
                            String dn = docName != null ? docName.toLowerCase(java.util.Locale.ROOT) : "";
                            isFemale = dn.contains("mrs") || dn.contains("ms.") || dn.contains("priya") || dn.contains("sunita")
                                    || dn.contains("ananya") || dn.contains("pooja") || dn.contains("neha")
                                    || specialty.toLowerCase(java.util.Locale.ROOT).contains("gyne");
                        }
                        int avatar = isFemale ? R.drawable.ic_doctor_female_avatar : R.drawable.ic_doctor_male_avatar;

                        Appointment appt = new Appointment(
                                id,
                                docName,
                                specialty,
                                avatar,
                                hospName,
                                date,
                                time,
                                token,
                                status
                        );
                        appt.setDoctorAvatarUrl(avatarUrl);
                        appt.setDoctorId(SupabaseClient.optStringSafe(obj, "doctor_id"));
                        appt.setHospitalId(SupabaseClient.optStringSafe(obj, "hospital_id"));
                        appt.setQueueId(queueId);

                        if ("completed".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status) || "no_show".equalsIgnoreCase(status)) {
                            pastList.add(appt);
                        } else {
                            upcomingList.add(appt);
                        }
                    }

                    java.util.Collections.reverse(upcomingList); // soonest first
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> updateDisplayedList());
                    }
                } catch (Exception e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> updateDisplayedList());
                    }
                }
            }

            @Override
            public void onError(Exception error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> updateDisplayedList());
                }
            }
        });
    }
}
