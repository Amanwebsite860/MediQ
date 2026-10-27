-- ============================================================
-- MediQ — 07_staff_hospitals_admin.sql
-- Run ONCE in Supabase → SQL Editor (after 05 and 06). Safe to re-run.
--
-- Adds:
--   1. requested_role on profiles (chosen at sign-up; NEVER grants access by itself)
--   2. Richer hospitals table (gallery photos, details, approval status)
--   3. hospital_reviews (patients review hospitals, receptionist can reply)
--   4. staff_applications (doctor / receptionist onboarding + admin approval)
--   5. Admin RPCs: approve / reject application, set hospital status
--   6. Storage bucket "hospital-images"
--   7. Tighter queue permissions (staff can only touch THEIR hospital)
--   8. "Queue moved" notifications for waiting patients
--
-- To create your first admin, run (replace the email):
--   UPDATE profiles SET role = 'admin' WHERE email = 'you@example.com';
-- ============================================================

-- ------------------------------------------------------------
-- 0. HELPERS
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.my_hospital_id()
RETURNS UUID AS $$
    SELECT hospital_id FROM profiles WHERE id = auth.uid();
$$ LANGUAGE sql SECURITY DEFINER STABLE SET search_path = public, pg_temp;

-- ------------------------------------------------------------
-- 1. PROFILES: requested role (informational only)
-- ------------------------------------------------------------
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS requested_role TEXT;
ALTER TABLE profiles DROP CONSTRAINT IF EXISTS profiles_requested_role_check;
ALTER TABLE profiles ADD CONSTRAINT profiles_requested_role_check
    CHECK (requested_role IS NULL OR requested_role IN ('patient', 'doctor', 'receptionist'));

-- Sign-up trigger: everyone starts as 'patient'. The role picked in the app is stored in
-- requested_role only; the real role changes ONLY when an admin approves the application.
CREATE OR REPLACE FUNCTION handle_new_user()
RETURNS TRIGGER AS $$
DECLARE
    v_req TEXT := NEW.raw_user_meta_data ->> 'requested_role';
BEGIN
    IF v_req IS NULL OR v_req NOT IN ('patient', 'doctor', 'receptionist') THEN
        v_req := 'patient';
    END IF;

    INSERT INTO public.profiles (id, full_name, phone, email, role, requested_role)
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data ->> 'full_name', split_part(COALESCE(NEW.email, 'User'), '@', 1)),
        NEW.raw_user_meta_data ->> 'phone',
        NEW.email,
        'patient',
        v_req
    )
    ON CONFLICT (id) DO UPDATE
    SET full_name = COALESCE(EXCLUDED.full_name, profiles.full_name),
        phone     = COALESCE(EXCLUDED.phone, profiles.phone),
        email     = COALESCE(EXCLUDED.email, profiles.email),
        updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

-- ------------------------------------------------------------
-- 2. HOSPITALS: gallery, details, approval status
-- ------------------------------------------------------------
ALTER TABLE hospitals ADD COLUMN IF NOT EXISTS description         TEXT;
ALTER TABLE hospitals ADD COLUMN IF NOT EXISTS image_urls          TEXT[] NOT NULL DEFAULT '{}';
ALTER TABLE hospitals ADD COLUMN IF NOT EXISTS email               TEXT;
ALTER TABLE hospitals ADD COLUMN IF NOT EXISTS website             TEXT;
ALTER TABLE hospitals ADD COLUMN IF NOT EXISTS registration_number TEXT;
ALTER TABLE hospitals ADD COLUMN IF NOT EXISTS established_year    INTEGER;
ALTER TABLE hospitals ADD COLUMN IF NOT EXISTS bed_count           INTEGER;
ALTER TABLE hospitals ADD COLUMN IF NOT EXISTS emergency_24x7      BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE hospitals ADD COLUMN IF NOT EXISTS emergency_phone     TEXT;
ALTER TABLE hospitals ADD COLUMN IF NOT EXISTS status              TEXT NOT NULL DEFAULT 'approved';
ALTER TABLE hospitals ADD COLUMN IF NOT EXISTS rejection_reason    TEXT;
ALTER TABLE hospitals ADD COLUMN IF NOT EXISTS submitted_by        UUID REFERENCES profiles(id) ON DELETE SET NULL;

ALTER TABLE hospitals DROP CONSTRAINT IF EXISTS hospitals_status_check;
ALTER TABLE hospitals ADD CONSTRAINT hospitals_status_check CHECK (status IN ('pending', 'approved', 'rejected'));

CREATE INDEX IF NOT EXISTS idx_hospitals_status ON hospitals (status);

-- Keep the single cover image (used by Home cards) in sync with the first gallery photo
CREATE OR REPLACE FUNCTION sync_hospital_cover_image()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.image_urls IS NOT NULL AND array_length(NEW.image_urls, 1) >= 1 THEN
        NEW.image_url := NEW.image_urls[1];
    END IF;
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_hospital_cover ON hospitals;
CREATE TRIGGER trg_sync_hospital_cover BEFORE INSERT OR UPDATE ON hospitals
    FOR EACH ROW EXECUTE FUNCTION sync_hospital_cover_image();

-- Receptionists may edit their own hospital's details, but never its approval/visibility/rating
CREATE OR REPLACE FUNCTION protect_hospital_admin_fields()
RETURNS TRIGGER AS $$
BEGIN
    IF current_user IN ('authenticated', 'anon') AND public.get_my_role() IS DISTINCT FROM 'admin' THEN
        IF NEW.status IS DISTINCT FROM OLD.status
           OR NEW.active IS DISTINCT FROM OLD.active
           OR NEW.rating IS DISTINCT FROM OLD.rating
           OR NEW.review_count IS DISTINCT FROM OLD.review_count
           OR NEW.submitted_by IS DISTINCT FROM OLD.submitted_by THEN
            RAISE EXCEPTION 'Only an admin can change approval status, visibility or ratings';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

DROP TRIGGER IF EXISTS trg_protect_hospital_fields ON hospitals;
CREATE TRIGGER trg_protect_hospital_fields BEFORE UPDATE ON hospitals
    FOR EACH ROW EXECUTE FUNCTION protect_hospital_admin_fields();

-- Policies (patients still only see active hospitals via hospitals_select_all)
DROP POLICY IF EXISTS hospitals_select_mine ON hospitals;
CREATE POLICY hospitals_select_mine ON hospitals FOR SELECT
    USING (submitted_by = auth.uid() OR id = public.my_hospital_id());

DROP POLICY IF EXISTS hospitals_update_receptionist ON hospitals;
CREATE POLICY hospitals_update_receptionist ON hospitals FOR UPDATE
    USING (public.get_my_role() = 'receptionist' AND id = public.my_hospital_id())
    WITH CHECK (public.get_my_role() = 'receptionist' AND id = public.my_hospital_id());

-- ------------------------------------------------------------
-- 3. HOSPITAL REVIEWS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hospital_reviews (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id  UUID        NOT NULL REFERENCES hospitals(id) ON DELETE CASCADE,
    patient_id   UUID        NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    patient_name TEXT,
    rating       INTEGER     NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment      TEXT,
    reply        TEXT,
    replied_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (hospital_id, patient_id)
);
CREATE INDEX IF NOT EXISTS idx_hospital_reviews_hospital ON hospital_reviews (hospital_id, created_at DESC);

ALTER TABLE hospital_reviews ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS hospital_reviews_select_all ON hospital_reviews;
CREATE POLICY hospital_reviews_select_all ON hospital_reviews FOR SELECT USING (TRUE);

-- Only patients who have a COMPLETED visit at that hospital can review it
DROP POLICY IF EXISTS hospital_reviews_insert_patient ON hospital_reviews;
CREATE POLICY hospital_reviews_insert_patient ON hospital_reviews FOR INSERT
    WITH CHECK (
        patient_id = auth.uid()
        AND EXISTS (SELECT 1 FROM appointments a
                    WHERE a.hospital_id = hospital_reviews.hospital_id
                      AND a.patient_id = auth.uid()
                      AND a.status = 'completed')
    );

DROP POLICY IF EXISTS hospital_reviews_update ON hospital_reviews;
CREATE POLICY hospital_reviews_update ON hospital_reviews FOR UPDATE
    USING (patient_id = auth.uid()
           OR (public.get_my_role() = 'receptionist' AND hospital_id = public.my_hospital_id())
           OR public.get_my_role() = 'admin');

DROP POLICY IF EXISTS hospital_reviews_delete ON hospital_reviews;
CREATE POLICY hospital_reviews_delete ON hospital_reviews FOR DELETE
    USING (patient_id = auth.uid() OR public.get_my_role() = 'admin');

-- Patients can't write replies; receptionists can ONLY write the reply
CREATE OR REPLACE FUNCTION guard_hospital_review_update()
RETURNS TRIGGER AS $$
DECLARE v_role TEXT := public.get_my_role();
BEGIN
    IF current_user IN ('authenticated', 'anon') THEN
        IF v_role = 'receptionist' THEN
            NEW.rating := OLD.rating; NEW.comment := OLD.comment;
            NEW.patient_id := OLD.patient_id; NEW.patient_name := OLD.patient_name;
            NEW.hospital_id := OLD.hospital_id;
            IF NEW.reply IS DISTINCT FROM OLD.reply THEN NEW.replied_at := NOW(); END IF;
        ELSIF v_role IS DISTINCT FROM 'admin' THEN
            NEW.reply := OLD.reply; NEW.replied_at := OLD.replied_at;
            NEW.patient_id := OLD.patient_id; NEW.hospital_id := OLD.hospital_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

DROP TRIGGER IF EXISTS trg_guard_hospital_review ON hospital_reviews;
CREATE TRIGGER trg_guard_hospital_review BEFORE UPDATE ON hospital_reviews
    FOR EACH ROW EXECUTE FUNCTION guard_hospital_review_update();

-- Hospital rating = average of its reviews (kept as-is until the first review arrives)
CREATE OR REPLACE FUNCTION refresh_hospital_rating()
RETURNS TRIGGER AS $$
DECLARE v_h UUID := COALESCE(NEW.hospital_id, OLD.hospital_id); v_cnt INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM hospital_reviews WHERE hospital_id = v_h;
    IF v_cnt > 0 THEN
        UPDATE hospitals
        SET rating = (SELECT ROUND(AVG(rating)::numeric, 1) FROM hospital_reviews WHERE hospital_id = v_h),
            review_count = v_cnt
        WHERE id = v_h;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

DROP TRIGGER IF EXISTS trg_refresh_hospital_rating ON hospital_reviews;
CREATE TRIGGER trg_refresh_hospital_rating AFTER INSERT OR UPDATE OF rating OR DELETE ON hospital_reviews
    FOR EACH ROW EXECUTE FUNCTION refresh_hospital_rating();

-- ------------------------------------------------------------
-- 4. STAFF APPLICATIONS  (doctor / receptionist onboarding)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS staff_applications (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL UNIQUE REFERENCES profiles(id) ON DELETE CASCADE,
    requested_role    TEXT        NOT NULL CHECK (requested_role IN ('doctor', 'receptionist')),
    hospital_id       UUID        REFERENCES hospitals(id) ON DELETE SET NULL,
    is_new_hospital   BOOLEAN     NOT NULL DEFAULT FALSE,
    status            TEXT        NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    rejection_reason  TEXT,
    license_number    TEXT,
    specialty         TEXT,
    experience_years  INTEGER,
    consultation_fee  NUMERIC(10,2),
    qualification     TEXT,
    bio               TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at       TIMESTAMPTZ,
    reviewed_by       UUID        REFERENCES profiles(id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_staff_apps_status ON staff_applications (status, created_at DESC);

ALTER TABLE staff_applications ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS staff_apps_select_own ON staff_applications;
CREATE POLICY staff_apps_select_own ON staff_applications FOR SELECT USING (user_id = auth.uid());

DROP POLICY IF EXISTS staff_apps_admin ON staff_applications;
CREATE POLICY staff_apps_admin ON staff_applications FOR ALL USING (public.get_my_role() = 'admin');

-- Submit (or re-submit after rejection). All writes happen here so users can't self-approve.
CREATE OR REPLACE FUNCTION submit_staff_application(
    p_role         TEXT,
    p_hospital_id  UUID,
    p_new_hospital JSONB,
    p_details      JSONB
) RETURNS JSONB AS $$
DECLARE
    v_uid     UUID := auth.uid();
    v_app     staff_applications%ROWTYPE;
    v_hid     UUID;
    v_is_new  BOOLEAN := (p_new_hospital IS NOT NULL AND p_new_hospital <> 'null'::jsonb);
BEGIN
    IF v_uid IS NULL THEN RETURN jsonb_build_object('success', FALSE, 'error', 'Not signed in'); END IF;
    IF p_role NOT IN ('doctor', 'receptionist') THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Invalid role');
    END IF;

    SELECT * INTO v_app FROM staff_applications WHERE user_id = v_uid;
    IF FOUND AND v_app.status IN ('pending', 'approved') THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'You already have an application (' || v_app.status || ')');
    END IF;

    IF v_is_new THEN
        -- re-use the user's earlier rejected hospital instead of creating duplicates
        SELECT id INTO v_hid FROM hospitals WHERE submitted_by = v_uid AND status IN ('pending', 'rejected') LIMIT 1;
        IF v_hid IS NULL THEN
            INSERT INTO hospitals (name, address, active, status, submitted_by)
            VALUES (COALESCE(p_new_hospital ->> 'name', 'Unnamed hospital'), COALESCE(p_new_hospital ->> 'address', ''),
                    FALSE, 'pending', v_uid)
            RETURNING id INTO v_hid;
        END IF;
        UPDATE hospitals SET
            name = COALESCE(p_new_hospital ->> 'name', name),
            type = p_new_hospital ->> 'type',
            address = COALESCE(p_new_hospital ->> 'address', address),
            city = p_new_hospital ->> 'city',
            state = p_new_hospital ->> 'state',
            latitude = NULLIF(p_new_hospital ->> 'latitude', '')::DOUBLE PRECISION,
            longitude = NULLIF(p_new_hospital ->> 'longitude', '')::DOUBLE PRECISION,
            phone = p_new_hospital ->> 'phone',
            email = p_new_hospital ->> 'email',
            website = p_new_hospital ->> 'website',
            registration_number = p_new_hospital ->> 'registration_number',
            opening_hours = p_new_hospital ->> 'opening_hours',
            description = p_new_hospital ->> 'description',
            established_year = NULLIF(p_new_hospital ->> 'established_year', '')::INTEGER,
            bed_count = NULLIF(p_new_hospital ->> 'bed_count', '')::INTEGER,
            emergency_24x7 = COALESCE((p_new_hospital ->> 'emergency_24x7')::BOOLEAN, FALSE),
            emergency_phone = p_new_hospital ->> 'emergency_phone',
            facilities = ARRAY(SELECT jsonb_array_elements_text(COALESCE(p_new_hospital -> 'facilities', '[]'::jsonb))),
            image_urls = ARRAY(SELECT jsonb_array_elements_text(COALESCE(p_new_hospital -> 'image_urls', '[]'::jsonb))),
            status = 'pending', rejection_reason = NULL, active = FALSE
        WHERE id = v_hid;
    ELSE
        SELECT id INTO v_hid FROM hospitals WHERE id = p_hospital_id AND status = 'approved' AND active = TRUE;
        IF v_hid IS NULL THEN
            RETURN jsonb_build_object('success', FALSE, 'error', 'Please choose a registered hospital');
        END IF;
    END IF;

    INSERT INTO staff_applications (user_id, requested_role, hospital_id, is_new_hospital, status,
                                    license_number, specialty, experience_years, consultation_fee, qualification, bio)
    VALUES (v_uid, p_role, v_hid, v_is_new, 'pending',
            p_details ->> 'license_number', p_details ->> 'specialty',
            NULLIF(p_details ->> 'experience_years', '')::INTEGER,
            NULLIF(p_details ->> 'consultation_fee', '')::NUMERIC,
            p_details ->> 'qualification', p_details ->> 'bio')
    ON CONFLICT (user_id) DO UPDATE SET
        requested_role = EXCLUDED.requested_role, hospital_id = EXCLUDED.hospital_id,
        is_new_hospital = EXCLUDED.is_new_hospital, status = 'pending', rejection_reason = NULL,
        license_number = EXCLUDED.license_number, specialty = EXCLUDED.specialty,
        experience_years = EXCLUDED.experience_years, consultation_fee = EXCLUDED.consultation_fee,
        qualification = EXCLUDED.qualification, bio = EXCLUDED.bio, updated_at = NOW(),
        reviewed_at = NULL, reviewed_by = NULL;

    UPDATE profiles SET requested_role = p_role WHERE id = v_uid;

    RETURN jsonb_build_object('success', TRUE, 'hospital_id', v_hid);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

REVOKE EXECUTE ON FUNCTION submit_staff_application(TEXT, UUID, JSONB, JSONB) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION submit_staff_application(TEXT, UUID, JSONB, JSONB) TO authenticated;

-- ------------------------------------------------------------
-- 5. ADMIN RPCs
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION approve_staff_application(p_application_id UUID)
RETURNS JSONB AS $$
DECLARE
    v_app staff_applications%ROWTYPE;
    v_name TEXT;
BEGIN
    IF public.get_my_role() IS DISTINCT FROM 'admin' THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Admins only');
    END IF;
    SELECT * INTO v_app FROM staff_applications WHERE id = p_application_id;
    IF NOT FOUND THEN RETURN jsonb_build_object('success', FALSE, 'error', 'Application not found'); END IF;

    IF v_app.is_new_hospital AND v_app.hospital_id IS NOT NULL THEN
        UPDATE hospitals SET status = 'approved', active = TRUE, rejection_reason = NULL WHERE id = v_app.hospital_id;
    END IF;

    UPDATE profiles SET role = v_app.requested_role, hospital_id = v_app.hospital_id WHERE id = v_app.user_id;
    SELECT full_name INTO v_name FROM profiles WHERE id = v_app.user_id;

    IF v_app.requested_role = 'doctor' THEN
        IF EXISTS (SELECT 1 FROM doctors WHERE profile_id = v_app.user_id) THEN
            UPDATE doctors SET hospital_id = v_app.hospital_id, active = TRUE WHERE profile_id = v_app.user_id;
        ELSE
            INSERT INTO doctors (profile_id, hospital_id, name, specialty, bio, experience_years, consultation_fee, active)
            VALUES (v_app.user_id, v_app.hospital_id, COALESCE(v_name, 'Doctor'),
                    COALESCE(v_app.specialty, 'General Physician'), v_app.bio,
                    v_app.experience_years, v_app.consultation_fee, TRUE);
        END IF;

        -- Auto-generate consultation schedule shifts for the next 30 days
        PERFORM generate_future_schedules(30);
    END IF;

    UPDATE staff_applications SET status = 'approved', reviewed_at = NOW(), reviewed_by = auth.uid(),
           rejection_reason = NULL, updated_at = NOW() WHERE id = p_application_id;

    INSERT INTO notifications (user_id, type, title, message, data)
    VALUES (v_app.user_id, 'staff_approved', 'You''re approved! 🎉',
            'Your ' || v_app.requested_role || ' account is active. Sign in again to open your dashboard.',
            jsonb_build_object('application_id', v_app.id));

    RETURN jsonb_build_object('success', TRUE);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION reject_staff_application(p_application_id UUID, p_reason TEXT)
RETURNS JSONB AS $$
DECLARE v_app staff_applications%ROWTYPE;
BEGIN
    IF public.get_my_role() IS DISTINCT FROM 'admin' THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Admins only');
    END IF;
    SELECT * INTO v_app FROM staff_applications WHERE id = p_application_id;
    IF NOT FOUND THEN RETURN jsonb_build_object('success', FALSE, 'error', 'Application not found'); END IF;

    UPDATE staff_applications SET status = 'rejected', rejection_reason = p_reason,
           reviewed_at = NOW(), reviewed_by = auth.uid(), updated_at = NOW() WHERE id = p_application_id;
    IF v_app.is_new_hospital AND v_app.hospital_id IS NOT NULL THEN
        UPDATE hospitals SET status = 'rejected', active = FALSE, rejection_reason = p_reason WHERE id = v_app.hospital_id;
    END IF;

    INSERT INTO notifications (user_id, type, title, message, data)
    VALUES (v_app.user_id, 'staff_rejected', 'Application needs changes',
            COALESCE(NULLIF(p_reason, ''), 'Please review your details and submit again.'),
            jsonb_build_object('application_id', v_app.id));

    RETURN jsonb_build_object('success', TRUE);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION admin_set_hospital_status(p_hospital_id UUID, p_status TEXT)
RETURNS JSONB AS $$
BEGIN
    IF public.get_my_role() IS DISTINCT FROM 'admin' THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Admins only');
    END IF;
    IF p_status NOT IN ('pending', 'approved', 'rejected') THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Invalid status');
    END IF;
    UPDATE hospitals SET status = p_status, active = (p_status = 'approved') WHERE id = p_hospital_id;
    RETURN jsonb_build_object('success', TRUE);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

REVOKE EXECUTE ON FUNCTION approve_staff_application(UUID) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION reject_staff_application(UUID, TEXT) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION admin_set_hospital_status(UUID, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION approve_staff_application(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION reject_staff_application(UUID, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION admin_set_hospital_status(UUID, TEXT) TO authenticated;

-- ------------------------------------------------------------
-- 6. STORAGE: hospital-images (public read; uploads only into your own folder)
-- ------------------------------------------------------------
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('hospital-images', 'hospital-images', TRUE, 3145728, ARRAY['image/jpeg', 'image/png', 'image/webp'])
ON CONFLICT (id) DO UPDATE
    SET public = TRUE, file_size_limit = 3145728,
        allowed_mime_types = ARRAY['image/jpeg', 'image/png', 'image/webp'];

DROP POLICY IF EXISTS "hospital_images_read"   ON storage.objects;
DROP POLICY IF EXISTS "hospital_images_insert" ON storage.objects;
DROP POLICY IF EXISTS "hospital_images_update" ON storage.objects;
DROP POLICY IF EXISTS "hospital_images_delete" ON storage.objects;

CREATE POLICY "hospital_images_read" ON storage.objects FOR SELECT USING (bucket_id = 'hospital-images');
CREATE POLICY "hospital_images_insert" ON storage.objects FOR INSERT TO authenticated
    WITH CHECK (bucket_id = 'hospital-images' AND (storage.foldername(name))[1] = auth.uid()::text);
CREATE POLICY "hospital_images_update" ON storage.objects FOR UPDATE TO authenticated
    USING (bucket_id = 'hospital-images' AND (storage.foldername(name))[1] = auth.uid()::text);
CREATE POLICY "hospital_images_delete" ON storage.objects FOR DELETE TO authenticated
    USING (bucket_id = 'hospital-images' AND (storage.foldername(name))[1] = auth.uid()::text);

-- ------------------------------------------------------------
-- 7. TIGHTER QUEUE PERMISSIONS (staff only touch their own hospital / their own queue)
-- ------------------------------------------------------------
DROP POLICY IF EXISTS queues_staff ON queues;
DROP POLICY IF EXISTS queues_admin ON queues;
DROP POLICY IF EXISTS queues_receptionist ON queues;
DROP POLICY IF EXISTS queues_doctor ON queues;
CREATE POLICY queues_admin ON queues FOR ALL USING (public.get_my_role() = 'admin');
CREATE POLICY queues_receptionist ON queues FOR ALL
    USING (public.get_my_role() = 'receptionist' AND hospital_id = public.my_hospital_id());
CREATE POLICY queues_doctor ON queues FOR ALL
    USING (public.get_my_role() = 'doctor'
           AND doctor_id IN (SELECT id FROM doctors WHERE profile_id = auth.uid()));

DROP POLICY IF EXISTS queue_entries_staff ON queue_entries;
DROP POLICY IF EXISTS queue_entries_admin ON queue_entries;
DROP POLICY IF EXISTS queue_entries_receptionist ON queue_entries;
DROP POLICY IF EXISTS queue_entries_doctor ON queue_entries;
CREATE POLICY queue_entries_admin ON queue_entries FOR ALL USING (public.get_my_role() = 'admin');
CREATE POLICY queue_entries_receptionist ON queue_entries FOR ALL
    USING (public.get_my_role() = 'receptionist'
           AND queue_id IN (SELECT id FROM queues WHERE hospital_id = public.my_hospital_id()));
CREATE POLICY queue_entries_doctor ON queue_entries FOR ALL
    USING (public.get_my_role() = 'doctor'
           AND queue_id IN (SELECT q.id FROM queues q JOIN doctors d ON d.id = q.doctor_id WHERE d.profile_id = auth.uid()));

-- advance_queue: same as before + a hospital check for non-admins
CREATE OR REPLACE FUNCTION advance_queue(p_queue_id UUID)
RETURNS JSONB AS $$
DECLARE
    v_next RECORD;
    v_role TEXT := (SELECT role FROM profiles WHERE id = auth.uid());
    v_allowed BOOLEAN;
BEGIN
    IF v_role NOT IN ('admin', 'receptionist', 'doctor') THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Forbidden: Staff permissions required');
    END IF;

    IF v_role = 'admin' THEN v_allowed := TRUE;
    ELSIF v_role = 'receptionist' THEN
        v_allowed := EXISTS (SELECT 1 FROM queues WHERE id = p_queue_id AND hospital_id = public.my_hospital_id());
    ELSE
        v_allowed := EXISTS (SELECT 1 FROM queues q JOIN doctors d ON d.id = q.doctor_id
                             WHERE q.id = p_queue_id AND d.profile_id = auth.uid());
    END IF;
    IF NOT v_allowed THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Forbidden: not your queue');
    END IF;

    PERFORM id FROM queues WHERE id = p_queue_id FOR UPDATE;

    UPDATE queue_entries SET status = 'completed', completed_at = NOW()
    WHERE queue_id = p_queue_id AND status = 'in_progress';

    SELECT id, token_number, patient_id, appointment_id INTO v_next
    FROM queue_entries
    WHERE queue_id = p_queue_id AND status IN ('waiting', 'checked_in')
    ORDER BY token_sequence ASC LIMIT 1;

    IF v_next IS NULL THEN
        UPDATE queues SET current_token = NULL WHERE id = p_queue_id;
        RETURN jsonb_build_object('success', TRUE, 'message', 'Queue is empty');
    END IF;

    UPDATE queue_entries SET status = 'in_progress', started_at = NOW() WHERE id = v_next.id;
    UPDATE queues SET current_token = v_next.token_number WHERE id = p_queue_id;

    RETURN jsonb_build_object('success', TRUE, 'current_token', v_next.token_number, 'patient_id', v_next.patient_id);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

REVOKE EXECUTE ON FUNCTION advance_queue(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION advance_queue(UUID) TO authenticated;

-- ------------------------------------------------------------
-- 8. "QUEUE MOVED" NOTIFICATIONS
--    Whenever a token goes in-progress, every patient still waiting in that queue gets
--    "Now serving A-05 · your token A-08 · 2 ahead". One row per patient per queue:
--    the older unread one is replaced so the inbox doesn't fill with stale updates.
--    (The "you're next" / "your turn" alerts come from 05_feature_upgrade.sql.)
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION notify_queue_moved()
RETURNS TRIGGER AS $$
DECLARE r RECORD; v_ahead INTEGER;
BEGIN
    IF NEW.status = 'in_progress' AND OLD.status IS DISTINCT FROM 'in_progress' THEN
        FOR r IN
            SELECT patient_id, token_number, token_sequence
            FROM queue_entries
            WHERE queue_id = NEW.queue_id
              AND id <> NEW.id
              AND status IN ('waiting', 'checked_in', 'booked')
        LOOP
            SELECT COUNT(*) INTO v_ahead FROM queue_entries
            WHERE queue_id = NEW.queue_id AND token_sequence < r.token_sequence
              AND status IN ('waiting', 'checked_in', 'booked');

            IF v_ahead >= 1 THEN   -- the very next patient gets the "you're next" alert instead
                DELETE FROM notifications
                WHERE user_id = r.patient_id AND type = 'queue_update' AND read_at IS NULL
                  AND (data ->> 'queue_id') = NEW.queue_id::TEXT;

                INSERT INTO notifications (user_id, type, title, message, data)
                VALUES (r.patient_id, 'queue_update', 'Queue moved ➡️',
                        'Now serving ' || NEW.token_number || '. Your token ' || r.token_number || ' — '
                        || v_ahead || (CASE WHEN v_ahead = 1 THEN ' patient' ELSE ' patients' END) || ' ahead of you.',
                        jsonb_build_object('queue_id', NEW.queue_id, 'now_serving', NEW.token_number,
                                           'token', r.token_number, 'ahead', v_ahead));
            END IF;
        END LOOP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

DROP TRIGGER IF EXISTS trg_notify_queue_moved ON queue_entries;
CREATE TRIGGER trg_notify_queue_moved AFTER UPDATE OF status ON queue_entries
    FOR EACH ROW EXECUTE FUNCTION notify_queue_moved();

-- Done ✅
