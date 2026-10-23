package com.example.mediq.ui.auth;

import android.app.Activity;
import android.content.Intent;

import com.example.mediq.MainActivity;
import com.example.mediq.data.remote.SessionManager;
import com.example.mediq.data.remote.SupabaseClient;
import com.example.mediq.ui.staff.DoctorHomeActivity;
import com.example.mediq.ui.staff.PendingApprovalActivity;
import com.example.mediq.ui.staff.ReceptionistHomeActivity;
import com.example.mediq.ui.staff.StaffOnboardingActivity;
import com.example.mediq.util.ToastUtils;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * One login screen for everyone. After sign-in this decides where the user lands:
 *  patient                                    -> patient app (MainActivity)
 *  receptionist / doctor (approved by admin)  -> their dashboard
 *  signed up as doctor/receptionist, no application yet -> onboarding (choose / register hospital)
 *  application pending or rejected            -> "waiting for admin approval"
 */
public final class RoleRouter {
    private RoleRouter() { }

    public static void routeAfterLogin(Activity a) {
        SessionManager session = SessionManager.getInstance(a);
        String uid = session.getUserId();
        if (uid == null || uid.isEmpty()) { launch(a, MainActivity.class); return; }

        SupabaseClient.getInstance().get("profiles?id=eq." + uid + "&select=role,requested_role",
                new SupabaseClient.Callback() {
                    @Override public void onSuccess(String json) {
                        try {
                            JSONArray arr = new JSONArray(json);
                            if (arr.length() == 0) { launch(a, MainActivity.class); return; }
                            JSONObject p = arr.getJSONObject(0);
                            String role = orDefault(SupabaseClient.optStringSafe(p, "role"), "patient");
                            String requested = orDefault(SupabaseClient.optStringSafe(p, "requested_role"), "patient");
                            session.updateUserRole(role);

                            if (role.equals("receptionist")) { launch(a, ReceptionistHomeActivity.class); return; }
                            if (role.equals("doctor")) { launch(a, DoctorHomeActivity.class); return; }
                            if (role.equals("admin")) {
                                ToastUtils.show(a, "Admin accounts are managed from the web admin panel");
                                launch(a, MainActivity.class);
                                return;
                            }
                            if (requested.equals("doctor") || requested.equals("receptionist")) {
                                checkApplication(a, uid);
                                return;
                            }
                        } catch (Exception ignored) { }
                        launch(a, MainActivity.class);
                    }

                    @Override public void onError(Exception e) { launchFromCachedRole(a); }
                });
    }

    private static void checkApplication(Activity a, String uid) {
        SupabaseClient.getInstance().get("staff_applications?user_id=eq." + uid + "&select=status",
                new SupabaseClient.Callback() {
                    @Override public void onSuccess(String json) {
                        try {
                            JSONArray arr = new JSONArray(json);
                            if (arr.length() == 0) launch(a, StaffOnboardingActivity.class);
                            else launch(a, PendingApprovalActivity.class);
                        } catch (Exception e) { launch(a, StaffOnboardingActivity.class); }
                    }
                    @Override public void onError(Exception e) { launch(a, StaffOnboardingActivity.class); }
                });
    }

    /** Offline / error fallback so a staff member isn't dumped into the patient app. */
    public static void launchFromCachedRole(Activity a) {
        String role = SessionManager.getInstance(a).getUserRole();
        if ("receptionist".equals(role)) launch(a, ReceptionistHomeActivity.class);
        else if ("doctor".equals(role)) launch(a, DoctorHomeActivity.class);
        else launch(a, MainActivity.class);
    }

    private static String orDefault(String v, String d) { return v == null || v.isEmpty() ? d : v; }

    private static void launch(Activity a, Class<?> target) {
        if (a.isFinishing()) return;
        Intent i = new Intent(a, target);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        a.startActivity(i);
        a.finish();
    }
}
