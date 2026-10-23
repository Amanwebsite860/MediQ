package com.example.mediq.ui.patient;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.mediq.MainActivity;
import com.example.mediq.R;
import com.example.mediq.data.model.Appointment;
import com.example.mediq.data.remote.SessionManager;
import com.example.mediq.data.remote.SupabaseClient;
import com.example.mediq.ui.appointments.BookAppointmentActivity;
import com.example.mediq.ui.auth.LoginActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ProfileFragment extends Fragment {

    private ShapeableImageView ivProfileAvatar;
    private View btnChangeAvatar;
    private TextView tvProfileName;
    private TextView tvProfileEmail;
    private TextView tvProfileRole;

    private TextView tvDetailName;
    private TextView tvDetailPhone;
    private TextView tvDetailBloodGroup;
    private TextView tvDetailGender;
    private TextView tvDetailEmergency;
    private MaterialButton btnEditProfile;

    private LinearLayout layoutAppointmentsContainer;
    private View cardNoAppointments;
    private TextView btnViewAllAppointments;
    private MaterialButton btnBookFirstAppointment;
    private MaterialButton btnLogout;

    private ActivityResultLauncher<String> avatarPickerLauncher;

    public ProfileFragment() {
        // Required empty public constructor
    }

    public static ProfileFragment newInstance() {
        return new ProfileFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Image picker for avatar
        avatarPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && getContext() != null) {
                        byte[] jpeg = prepareAvatarBytes(uri);
                        if (jpeg == null) {
                            com.example.mediq.util.ToastUtils.show(getActivity(), "Couldn't use that picture");
                            return;
                        }
                        // Show it immediately from a private local copy, then upload in the background
                        String saved = saveAvatarLocally(jpeg);
                        if (saved != null) {
                            SessionManager.getInstance(requireContext()).setUserAvatarUrl(saved);
                            loadAvatarImage(saved);
                        }
                        uploadAvatar(jpeg);
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        populateProfileViews();
        fetchProfileFromBackend();
        fetchRecentAppointments();
        setupActions();
    }

    private void initViews(View view) {
        ivProfileAvatar = view.findViewById(R.id.ivProfileAvatar);
        btnChangeAvatar = view.findViewById(R.id.btnChangeAvatar);
        tvProfileName = view.findViewById(R.id.tvProfileName);
        tvProfileEmail = view.findViewById(R.id.tvProfileEmail);
        tvProfileRole = view.findViewById(R.id.tvProfileRole);

        tvDetailName = view.findViewById(R.id.tvDetailName);
        tvDetailPhone = view.findViewById(R.id.tvDetailPhone);
        tvDetailBloodGroup = view.findViewById(R.id.tvDetailBloodGroup);
        tvDetailGender = view.findViewById(R.id.tvDetailGender);
        tvDetailEmergency = view.findViewById(R.id.tvDetailEmergency);
        btnEditProfile = view.findViewById(R.id.btnEditProfile);

        layoutAppointmentsContainer = view.findViewById(R.id.layoutAppointmentsContainer);
        cardNoAppointments = view.findViewById(R.id.cardNoAppointments);
        btnViewAllAppointments = view.findViewById(R.id.btnViewAllAppointments);
        btnBookFirstAppointment = view.findViewById(R.id.btnBookFirstAppointment);
        btnLogout = view.findViewById(R.id.btnLogout);
    }

    private void populateProfileViews() {
        if (getContext() == null) return;
        SessionManager session = SessionManager.getInstance(requireContext());

        String name = session.getUserName();
        String email = session.getUserEmail();
        String role = session.getUserRole();
        String phone = session.getUserPhone();
        String blood = session.getUserBloodGroup();
        String gender = session.getUserGender();
        String emergency = session.getUserEmergencyContact();
        String avatar = session.getUserAvatarUrl();

        tvProfileName.setText(name != null && !name.isEmpty() ? name : "Patient");
        tvProfileEmail.setText(email);
        tvProfileRole.setText("Role: " + (role != null ? role.toUpperCase() : "PATIENT"));

        tvDetailName.setText(name != null && !name.isEmpty() ? name : "—");
        tvDetailPhone.setText(phone != null && !phone.isEmpty() ? phone : "Not provided");
        tvDetailBloodGroup.setText(blood != null && !blood.isEmpty() ? blood : "Not set");
        tvDetailGender.setText(gender != null && !gender.isEmpty() ? gender : "Not set");
        tvDetailEmergency.setText(emergency != null && !emergency.isEmpty() ? emergency : "Not set");

        loadAvatarImage(avatar);
    }

    /** Decodes the picked image, scales it to at most 512px and returns JPEG bytes (well under the 2 MB bucket limit). */
    private byte[] prepareAvatarBytes(Uri src) {
        try {
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (java.io.InputStream in = requireContext().getContentResolver().openInputStream(src)) {
                android.graphics.BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2;

            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inSampleSize = sample;
            android.graphics.Bitmap bmp;
            try (java.io.InputStream in = requireContext().getContentResolver().openInputStream(src)) {
                bmp = android.graphics.BitmapFactory.decodeStream(in, null, opts);
            }
            if (bmp == null) return null;

            int max = 512;
            float scale = Math.min(1f, (float) max / Math.max(bmp.getWidth(), bmp.getHeight()));
            if (scale < 1f) {
                bmp = android.graphics.Bitmap.createScaledBitmap(bmp,
                        Math.round(bmp.getWidth() * scale), Math.round(bmp.getHeight() * scale), true);
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** Keeps a private copy so the photo shows instantly and survives a failed upload. Returns a file:// URI string. */
    private String saveAvatarLocally(byte[] jpeg) {
        try {
            java.io.File out = new java.io.File(requireContext().getFilesDir(), "avatar_" + System.currentTimeMillis() + ".jpg");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                fos.write(jpeg);
            }
            java.io.File[] old = requireContext().getFilesDir().listFiles(
                    (d, name) -> name.startsWith("avatar_") && !name.equals(out.getName()));
            if (old != null) for (java.io.File f : old) //noinspection ResultOfMethodCallIgnored
                f.delete();
            return Uri.fromFile(out).toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Uploads to the "avatars" bucket (avatars/<user-id>/avatar.jpg) and stores the URL on the profile. */
    private void uploadAvatar(byte[] jpeg) {
        if (getContext() == null) return;
        final SessionManager sm = SessionManager.getInstance(requireContext());
        final String uid = sm.getUserId();
        if (uid == null || uid.isEmpty()) {
            com.example.mediq.util.ToastUtils.show(getActivity(), "Profile picture updated on this phone");
            return;
        }
        SupabaseClient.getInstance().uploadFile("avatars", uid + "/avatar.jpg", jpeg, "image/jpeg",
                new SupabaseClient.Callback() {
                    @Override
                    public void onSuccess(String publicUrl) {
                        // cache-bust so Glide fetches the new picture instead of the old one at the same URL
                        final String versioned = publicUrl + "?v=" + System.currentTimeMillis();
                        try {
                            JSONObject body = new JSONObject();
                            body.put("avatar_url", versioned);
                            SupabaseClient.getInstance().patch("profiles?id=eq." + uid, body, new SupabaseClient.Callback() {
                                @Override public void onSuccess(String r) { }
                                @Override public void onError(Exception e) { }
                            });
                        } catch (Exception ignored) { }
                        if (getContext() != null) {
                            sm.setUserAvatarUrl(versioned);
                            com.example.mediq.util.ToastUtils.show(getActivity(), "Profile picture updated");
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        if (getContext() != null) {
                            com.example.mediq.util.ToastUtils.show(getActivity(),
                                    "Saved on this phone only - couldn't upload (is the avatars bucket created?)");
                        }
                    }
                });
    }

    private void loadAvatarImage(String urlOrUri) {
        if (urlOrUri != null && !urlOrUri.isEmpty() && getContext() != null) {
            Glide.with(this)
                    .load(urlOrUri.startsWith("content://") || urlOrUri.startsWith("file://") ? Uri.parse(urlOrUri) : urlOrUri)
                    .placeholder(R.drawable.ic_user_avatar)
                    .circleCrop()
                    .into(ivProfileAvatar);
        } else {
            ivProfileAvatar.setImageResource(R.drawable.ic_user_avatar);
        }
    }

    private void fetchProfileFromBackend() {
        if (getContext() == null) return;
        String uid = SessionManager.getInstance(requireContext()).getUserId();
        if (uid == null || uid.isEmpty()) return;

        SupabaseClient.getInstance().get("profiles?id=eq." + uid + "&select=*", new SupabaseClient.Callback() {
            @Override
            public void onSuccess(String json) {
                try {
                    JSONArray arr = new JSONArray(json);
                    if (arr.length() > 0) {
                        JSONObject p = arr.getJSONObject(0);
                        String fullName = SupabaseClient.optStringSafe(p, "full_name");
                        String phone = SupabaseClient.optStringSafe(p, "phone");
                        String role = SupabaseClient.optStringSafe(p, "role");
                        String avatar = SupabaseClient.optStringSafe(p, "avatar_url");
                        String blood = SupabaseClient.optStringSafe(p, "blood_group");
                        String gender = SupabaseClient.optStringSafe(p, "gender");
                        String emergency = SupabaseClient.optStringSafe(p, "emergency_contact");

                        if (getActivity() != null) {
                            requireActivity().runOnUiThread(() -> {
                                SessionManager sm = SessionManager.getInstance(requireContext());
                                if (fullName != null) sm.updateProfileDetails(fullName, phone, blood, gender, emergency);
                                // server photo wins, unless a freshly picked local file hasn't uploaded yet
                                String localAvatar = sm.getUserAvatarUrl();
                                boolean pendingLocal = localAvatar != null && localAvatar.startsWith("file://");
                                if (avatar != null && !pendingLocal) sm.setUserAvatarUrl(avatar);
                                if (role != null) sm.updateUserRole(role);
                                populateProfileViews();
                            });
                        }
                    }
                } catch (Exception ignored) {}
            }

            @Override
            public void onError(Exception e) {}
        });
    }

    private void setupActions() {
        // Change Avatar
        if (btnChangeAvatar != null) {
            btnChangeAvatar.setOnClickListener(v -> avatarPickerLauncher.launch("image/*"));
        }

        // Edit Profile Modal
        if (btnEditProfile != null) {
            btnEditProfile.setOnClickListener(v -> showEditProfileDialog());
        }

        // View All appointments
        View.OnClickListener goToAppointments = v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateToTab(R.id.nav_appointments);
            }
        };
        if (btnViewAllAppointments != null) btnViewAllAppointments.setOnClickListener(goToAppointments);

        // Book first appointment
        if (btnBookFirstAppointment != null) {
            btnBookFirstAppointment.setOnClickListener(v -> {
                Intent i = new Intent(requireContext(), BookAppointmentActivity.class);
                startActivity(i);
            });
        }

        // Shortcuts
        View rowSaved = requireView().findViewById(R.id.rowSaved);
        if (rowSaved != null) rowSaved.setOnClickListener(v -> startActivity(new Intent(requireContext(), FavoritesActivity.class)));
        View rowNotif = requireView().findViewById(R.id.rowNotifications);
        if (rowNotif != null) rowNotif.setOnClickListener(v -> startActivity(new Intent(requireContext(), NotificationsActivity.class)));
        View rowSos = requireView().findViewById(R.id.rowEmergency);
        if (rowSos != null) rowSos.setOnClickListener(v -> startActivity(new Intent(requireContext(), EmergencyActivity.class)));

        // Logout
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> handleLogout());
        }
    }

    private void showEditProfileDialog() {
        if (getContext() == null) return;
        SessionManager session = SessionManager.getInstance(requireContext());

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_profile, null);
        TextInputEditText etName = dialogView.findViewById(R.id.etEditName);
        TextInputEditText etPhone = dialogView.findViewById(R.id.etEditPhone);
        TextInputEditText etBlood = dialogView.findViewById(R.id.etEditBloodGroup);
        TextInputEditText etGender = dialogView.findViewById(R.id.etEditGender);
        TextInputEditText etEmergency = dialogView.findViewById(R.id.etEditEmergency);

        etName.setText(session.getUserName());
        etPhone.setText(session.getUserPhone());
        if (!"Not set".equalsIgnoreCase(session.getUserBloodGroup())) etBlood.setText(session.getUserBloodGroup());
        if (!"Not set".equalsIgnoreCase(session.getUserGender())) etGender.setText(session.getUserGender());
        if (!"Not set".equalsIgnoreCase(session.getUserEmergencyContact())) etEmergency.setText(session.getUserEmergencyContact());

        new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Save Changes", (dialog, which) -> {
                    String newName = etName.getText() != null ? etName.getText().toString().trim() : "";
                    String newPhone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
                    String newBlood = etBlood.getText() != null ? etBlood.getText().toString().trim() : "";
                    String newGender = etGender.getText() != null ? etGender.getText().toString().trim() : "";
                    String newEmergency = etEmergency.getText() != null ? etEmergency.getText().toString().trim() : "";

                    String bloodNorm = newBlood.toUpperCase(java.util.Locale.ROOT).replace(" ", "");
                    if (!bloodNorm.isEmpty() && !bloodNorm.matches("^(A|B|AB|O)[+-]$")) {
                        com.example.mediq.util.ToastUtils.show(getActivity(), "Blood group should look like A+, O-, AB+");
                        return;
                    }
                    newBlood = bloodNorm;

                    session.updateProfileDetails(
                            newName.isEmpty() ? session.getUserName() : newName,
                            newPhone,
                            newBlood.isEmpty() ? "Not set" : newBlood,
                            newGender.isEmpty() ? "Not set" : newGender,
                            newEmergency.isEmpty() ? "Not set" : newEmergency
                    );

                    populateProfileViews();
                    syncProfileToBackend(newName, newPhone, newBlood, newGender, newEmergency);
                    com.example.mediq.util.ToastUtils.show(getActivity(), "Profile updated successfully");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void syncProfileToBackend(String name, String phone, String blood, String gender, String emergency) {
        if (getContext() == null) return;
        String uid = SessionManager.getInstance(requireContext()).getUserId();
        if (uid == null || uid.isEmpty()) return;

        try {
            JSONObject body = new JSONObject();
            if (name != null && !name.isEmpty()) body.put("full_name", name);
            if (phone != null) body.put("phone", phone);

            SupabaseClient.getInstance().patch("profiles?id=eq." + uid, body, new SupabaseClient.Callback() {
                @Override
                public void onSuccess(String response) {}

                @Override
                public void onError(Exception e) {}
            });

            // Health details go in a separate request so a missing column (SQL not run yet)
            // can never block the name / phone update above.
            JSONObject extra = new JSONObject();
            extra.put("blood_group", blood == null || blood.isEmpty() ? JSONObject.NULL : blood);
            extra.put("gender", gender == null || gender.isEmpty() ? JSONObject.NULL : gender);
            extra.put("emergency_contact", emergency == null || emergency.isEmpty() ? JSONObject.NULL : emergency);
            SupabaseClient.getInstance().patch("profiles?id=eq." + uid, extra, new SupabaseClient.Callback() {
                @Override
                public void onSuccess(String response) {}

                @Override
                public void onError(Exception e) {}
            });
        } catch (Exception ignored) {}
    }

    private void fetchRecentAppointments() {
        if (getContext() == null) return;
        String uid = SessionManager.getInstance(requireContext()).getUserId();
        if (uid == null || uid.isEmpty()) {
            showEmptyAppointments();
            return;
        }

        SupabaseClient.getInstance().get(
                "appointments?patient_id=eq." + uid + "&select=id,status,appointment_date,appointment_time,doctors(name,specialty),hospitals(name),queue_entries(token_number)&order=appointment_date.desc,appointment_time.desc&limit=3",
                new SupabaseClient.Callback() {
                    @Override
                    public void onSuccess(String json) {
                        try {
                            JSONArray arr = new JSONArray(json);
                            List<Appointment> list = new ArrayList<>();
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject obj = arr.getJSONObject(i);
                                String id = obj.optString("id");
                                String date = obj.optString("appointment_date");
                                String time = obj.optString("appointment_time");
                                String status = obj.optString("status", "CONFIRMED");
                                String tokenNum = "—";
                                JSONArray qe = obj.optJSONArray("queue_entries");
                                if (qe != null && qe.length() > 0) {
                                    String t = SupabaseClient.optStringSafe(qe.getJSONObject(0), "token_number");
                                    if (t != null) tokenNum = t;
                                }

                                String docName = "Dr. Specialist";
                                String docSpec = "General Medicine";
                                JSONObject docObj = obj.optJSONObject("doctors");
                                if (docObj != null) {
                                    docName = docObj.optString("name", docName);
                                    docSpec = docObj.optString("specialty", docSpec);
                                }

                                String hospName = "Hospital";
                                JSONObject hospObj = obj.optJSONObject("hospitals");
                                if (hospObj != null) {
                                    hospName = hospObj.optString("name", hospName);
                                }

                                list.add(new Appointment(id, docName, docSpec, R.drawable.ic_doctor_male_avatar,
                                        hospName, date, time, tokenNum, status));
                            }

                            if (getActivity() != null) {
                                requireActivity().runOnUiThread(() -> {
                                    if (list.isEmpty()) {
                                        showEmptyAppointments();
                                    } else {
                                        populateAppointmentCards(list);
                                    }
                                });
                            }
                        } catch (Exception e) {
                            if (getActivity() != null) {
                                requireActivity().runOnUiThread(() -> showEmptyAppointments());
                            }
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        if (getActivity() != null) {
                            requireActivity().runOnUiThread(() -> showEmptyAppointments());
                        }
                    }
                }
        );
    }

    private void showEmptyAppointments() {
        if (layoutAppointmentsContainer != null) layoutAppointmentsContainer.removeAllViews();
        if (cardNoAppointments != null) cardNoAppointments.setVisibility(View.VISIBLE);
    }

    private void populateAppointmentCards(List<Appointment> appointments) {
        if (layoutAppointmentsContainer == null || getContext() == null) return;
        layoutAppointmentsContainer.removeAllViews();
        if (cardNoAppointments != null) cardNoAppointments.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Appointment a : appointments) {
            View card = inflater.inflate(R.layout.item_appointment, layoutAppointmentsContainer, false);
            TextView tvDoctorName = card.findViewById(R.id.tvDoctorName);
            TextView tvDoctorSpecialty = card.findViewById(R.id.tvDoctorSpecialty);
            TextView tvHospitalName = card.findViewById(R.id.tvHospitalName);
            TextView tvDate = card.findViewById(R.id.tvAppointmentDate);
            TextView tvTime = card.findViewById(R.id.tvAppointmentTime);
            TextView tvToken = card.findViewById(R.id.tvTokenNumber);

            tvDoctorName.setText(a.getDoctorName());
            tvDoctorSpecialty.setText(a.getDoctorSpecialty());
            tvHospitalName.setText(a.getHospitalName());
            tvDate.setText(com.example.mediq.util.UiUtils.friendlyDate(a.getAppointmentDate()));
            String t = a.getAppointmentTime();
            tvTime.setText(t != null && t.matches("\\d{2}:\\d{2}.*") ? com.example.mediq.util.UiUtils.formatTime(t) : t);
            tvToken.setText(a.getTokenNumber());

            // These preview cards are read-only: actions live in the Appointments tab
            View actions = card.findViewById(R.id.layoutActions);
            if (actions != null) actions.setVisibility(View.GONE);
            TextView chip = card.findViewById(R.id.tvStatusChip);
            if (chip != null) {
                String st = a.getStatus() == null ? "" : a.getStatus().toLowerCase(java.util.Locale.ROOT);
                chip.setText(st.equals("cancelled") ? R.string.status_cancelled
                        : st.equals("completed") ? R.string.status_completed
                        : st.equals("no_show") ? R.string.status_no_show : R.string.status_upcoming);
            }

            layoutAppointmentsContainer.addView(card);
        }
    }

    private void handleLogout() {
        if (getContext() == null) return;
        com.example.mediq.data.repo.FavoritesRepository.get().clear();
        com.example.mediq.service.QueueWatcherService.stop(requireContext());
        SessionManager.getInstance(requireContext()).clearSession();
        com.example.mediq.util.ToastUtils.show(requireActivity(), "Logged out successfully");

        Intent intent = new Intent(requireActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}
