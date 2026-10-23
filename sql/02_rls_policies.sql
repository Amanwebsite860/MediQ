-- ============================================================
-- MediQ — Row Level Security (RLS) Policies (Production Hardened)
-- 02_rls_policies.sql
-- ============================================================

CREATE OR REPLACE FUNCTION public.get_my_role()
RETURNS TEXT AS $$
    SELECT role FROM profiles WHERE id = auth.uid();
$$ LANGUAGE sql SECURITY DEFINER STABLE SET search_path = public, pg_temp;

-- ============================================================
-- 1. PROFILES
-- ============================================================
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS profiles_select_own ON profiles;
CREATE POLICY profiles_select_own ON profiles
    FOR SELECT USING (id = auth.uid());

-- Lock role and hospital_id columns against user tampering
DROP POLICY IF EXISTS profiles_update_own ON profiles;
CREATE POLICY profiles_update_own ON profiles
    FOR UPDATE USING (id = auth.uid())
    WITH CHECK (
        id = auth.uid()
        AND role = (SELECT role FROM profiles WHERE id = auth.uid())
        AND hospital_id IS NOT DISTINCT FROM (SELECT hospital_id FROM profiles WHERE id = auth.uid())
    );

DROP POLICY IF EXISTS profiles_insert_own ON profiles;
CREATE POLICY profiles_insert_own ON profiles
    FOR INSERT WITH CHECK (id = auth.uid() AND role = 'patient');

DROP POLICY IF EXISTS profiles_select_admin ON profiles;
CREATE POLICY profiles_select_admin ON profiles
    FOR SELECT USING (public.get_my_role() = 'admin');

-- ============================================================
-- 2. HOSPITALS
-- ============================================================
ALTER TABLE hospitals ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS hospitals_select_all ON hospitals;
CREATE POLICY hospitals_select_all ON hospitals
    FOR SELECT USING (active = TRUE);

DROP POLICY IF EXISTS hospitals_admin_all ON hospitals;
CREATE POLICY hospitals_admin_all ON hospitals
    FOR ALL USING (public.get_my_role() = 'admin');

-- ============================================================
-- 3. DOCTORS
-- ============================================================
ALTER TABLE doctors ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS doctors_select_all ON doctors;
CREATE POLICY doctors_select_all ON doctors
    FOR SELECT USING (active = TRUE);

DROP POLICY IF EXISTS doctors_admin_all ON doctors;
CREATE POLICY doctors_admin_all ON doctors
    FOR ALL USING (public.get_my_role() = 'admin');

-- ============================================================
-- 4. DOCTOR SCHEDULES
-- ============================================================
ALTER TABLE doctor_schedules ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS schedules_select_all ON doctor_schedules;
CREATE POLICY schedules_select_all ON doctor_schedules
    FOR SELECT USING (status = 'available');

DROP POLICY IF EXISTS schedules_admin ON doctor_schedules;
CREATE POLICY schedules_admin ON doctor_schedules
    FOR ALL USING (public.get_my_role() IN ('admin', 'doctor'));

-- ============================================================
-- 5. APPOINTMENTS
-- Booking and cancelling must go through stored procedures (RPC).
-- Direct insert/update by patients is dropped to prevent bypass.
-- ============================================================
ALTER TABLE appointments ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS appointments_select_patient ON appointments;
CREATE POLICY appointments_select_patient ON appointments
    FOR SELECT USING (patient_id = auth.uid());

DROP POLICY IF EXISTS appointments_insert_patient ON appointments;
DROP POLICY IF EXISTS appointments_update_patient ON appointments;

DROP POLICY IF EXISTS appointments_select_doctor ON appointments;
CREATE POLICY appointments_select_doctor ON appointments
    FOR SELECT USING (
        doctor_id IN (SELECT id FROM doctors WHERE profile_id = auth.uid())
    );

DROP POLICY IF EXISTS appointments_update_doctor ON appointments;
CREATE POLICY appointments_update_doctor ON appointments
    FOR UPDATE USING (
        doctor_id IN (SELECT id FROM doctors WHERE profile_id = auth.uid())
    );

DROP POLICY IF EXISTS appointments_receptionist ON appointments;
CREATE POLICY appointments_receptionist ON appointments
    FOR ALL USING (
        public.get_my_role() = 'receptionist'
        AND hospital_id IN (
            SELECT hospital_id FROM profiles WHERE id = auth.uid()
        )
    );

DROP POLICY IF EXISTS appointments_admin ON appointments;
CREATE POLICY appointments_admin ON appointments
    FOR ALL USING (public.get_my_role() = 'admin');

-- ============================================================
-- 6. QUEUES
-- ============================================================
ALTER TABLE queues ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS queues_select_all ON queues;
CREATE POLICY queues_select_all ON queues
    FOR SELECT USING (TRUE);

DROP POLICY IF EXISTS queues_staff ON queues;
CREATE POLICY queues_staff ON queues
    FOR ALL USING (public.get_my_role() IN ('admin', 'receptionist', 'doctor'));

-- ============================================================
-- 7. QUEUE ENTRIES & LIVE VIEW
-- ============================================================
ALTER TABLE queue_entries ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS queue_entries_select_patient ON queue_entries;
CREATE POLICY queue_entries_select_patient ON queue_entries
    FOR SELECT USING (patient_id = auth.uid());

DROP POLICY IF EXISTS queue_entries_select_all ON queue_entries;

DROP POLICY IF EXISTS queue_entries_staff ON queue_entries;
CREATE POLICY queue_entries_staff ON queue_entries
    FOR ALL USING (public.get_my_role() IN ('admin', 'receptionist', 'doctor'));

DROP VIEW IF EXISTS live_queue_status;
CREATE OR REPLACE VIEW live_queue_status WITH (security_invoker = false) AS
SELECT id, queue_id, token_number, token_sequence, status, checked_in_at, started_at, completed_at, created_at
FROM queue_entries;

REVOKE ALL ON live_queue_status FROM anon;
GRANT SELECT ON live_queue_status TO authenticated;

-- ============================================================
-- 8. NOTIFICATIONS
-- ============================================================
ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS notifications_select_own ON notifications;
CREATE POLICY notifications_select_own ON notifications
    FOR SELECT USING (user_id = auth.uid());

DROP POLICY IF EXISTS notifications_update_own ON notifications;
CREATE POLICY notifications_update_own ON notifications
    FOR UPDATE USING (user_id = auth.uid());

-- ============================================================
-- 9. AUDIT LOGS
-- ============================================================
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS audit_logs_select_admin ON audit_logs;
CREATE POLICY audit_logs_select_admin ON audit_logs
    FOR SELECT USING (public.get_my_role() = 'admin');

-- ============================================================
-- 10. OTP VERIFICATIONS
-- ============================================================
ALTER TABLE otp_verifications ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS otp_insert_all ON otp_verifications;
DROP POLICY IF EXISTS otp_select_all ON otp_verifications;
DROP POLICY IF EXISTS otp_update_all ON otp_verifications;

-- ============================================================
-- 11. DOCTOR REVIEWS
-- ============================================================
ALTER TABLE doctor_reviews ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS doctor_reviews_select_all ON doctor_reviews;
CREATE POLICY doctor_reviews_select_all ON doctor_reviews
    FOR SELECT USING (TRUE);

DROP POLICY IF EXISTS doctor_reviews_insert_patient ON doctor_reviews;
CREATE POLICY doctor_reviews_insert_patient ON doctor_reviews
    FOR INSERT WITH CHECK (
        patient_id = auth.uid()
        AND EXISTS (
            SELECT 1 FROM appointments
            WHERE doctor_id = doctor_reviews.doctor_id
              AND patient_id = auth.uid()
              AND status = 'completed'
        )
    );

DROP POLICY IF EXISTS doctor_reviews_admin ON doctor_reviews;
CREATE POLICY doctor_reviews_admin ON doctor_reviews
    FOR ALL USING (public.get_my_role() = 'admin');

-- ============================================================
-- 12. USER FAVORITES
-- ============================================================
ALTER TABLE user_favorites ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS favorites_select_own ON user_favorites;
CREATE POLICY favorites_select_own ON user_favorites
    FOR SELECT USING (user_id = auth.uid());

DROP POLICY IF EXISTS favorites_insert_own ON user_favorites;
CREATE POLICY favorites_insert_own ON user_favorites
    FOR INSERT WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS favorites_delete_own ON user_favorites;
CREATE POLICY favorites_delete_own ON user_favorites
    FOR DELETE USING (user_id = auth.uid());
