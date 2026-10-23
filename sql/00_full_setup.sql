-- ==============================================================================
-- MEDIQ FULL SUPABASE SETUP SCRIPT (Version 3.0 - Production Hardened)
-- One-click deployment for Supabase SQL Editor
-- Features:
--  - Timezone-accurate IST (Asia/Kolkata) date/time calculations
--  - Full validation on book_appointment (schedules, slot step, past time, hospital match)
--  - Booking bypass prevention (patient insert/update policies dropped)
--  - Cleanup of obsolete insecure functions
--  - Column-locked profile updates (prevents changing role or hospital_id)
--  - Authenticated-only sanitized live queue view
--  - Anti-abuse doctor reviews with verified completed appointment requirement & auto-rating trigger
--  - Concurrency-safe queue token numbering with row locks
--  - One source of truth including register_walkin
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 0. CLEANUP OBSOLETE INSECURE FUNCTIONS
-- ------------------------------------------------------------------------------
DROP FUNCTION IF EXISTS cancel_appointment(UUID, UUID, TEXT);
DROP FUNCTION IF EXISTS record_otp(TEXT, TEXT, TEXT, INTEGER);
DROP FUNCTION IF EXISTS verify_otp(TEXT, TEXT, TEXT);

-- ------------------------------------------------------------------------------
-- 1. EXTENSIONS
-- ------------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ------------------------------------------------------------------------------
-- 2. TABLES
-- ------------------------------------------------------------------------------

-- Hospitals
CREATE TABLE IF NOT EXISTS hospitals (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT            NOT NULL,
    type            TEXT,
    address         TEXT            NOT NULL,
    city            TEXT,
    state           TEXT,
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    phone           TEXT,
    opening_hours   TEXT,
    facilities      TEXT[],
    image_url       TEXT,
    rating          NUMERIC(2,1)    NOT NULL DEFAULT 0,
    review_count    INTEGER         NOT NULL DEFAULT 0,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Profiles: 1-to-1 extension of Supabase auth.users
CREATE TABLE IF NOT EXISTS profiles (
    id            UUID        PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name     TEXT        NOT NULL,
    phone         TEXT,
    email         TEXT,
    avatar_url    TEXT,
    role          TEXT        NOT NULL DEFAULT 'patient'
                              CHECK (role IN ('patient', 'doctor', 'receptionist', 'admin')),
    hospital_id   UUID        REFERENCES hospitals(id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Doctors
CREATE TABLE IF NOT EXISTS doctors (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id                  UUID            REFERENCES profiles(id) ON DELETE SET NULL,
    hospital_id                 UUID            NOT NULL REFERENCES hospitals(id) ON DELETE CASCADE,
    name                        TEXT            NOT NULL,
    specialty                   TEXT            NOT NULL,
    bio                         TEXT,
    experience_years            INTEGER,
    consultation_fee            NUMERIC(10,2),
    consultation_duration_min   INTEGER         NOT NULL DEFAULT 15,
    avatar_url                  TEXT,
    rating                      NUMERIC(2,1)    NOT NULL DEFAULT 0,
    review_count                INTEGER         NOT NULL DEFAULT 0,
    active                      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Doctor Schedules
CREATE TABLE IF NOT EXISTS doctor_schedules (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id               UUID        NOT NULL REFERENCES doctors(id) ON DELETE CASCADE,
    schedule_date           DATE        NOT NULL,
    start_time              TIME        NOT NULL,
    end_time                TIME        NOT NULL,
    slot_duration_minutes   INTEGER     NOT NULL DEFAULT 30,
    status                  TEXT        NOT NULL DEFAULT 'available'
                                        CHECK (status IN ('available', 'blocked', 'inactive')),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (doctor_id, schedule_date, start_time)
);

-- Appointments
CREATE TABLE IF NOT EXISTS appointments (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id          UUID        NOT NULL REFERENCES profiles(id),
    doctor_id           UUID        NOT NULL REFERENCES doctors(id),
    hospital_id         UUID        NOT NULL REFERENCES hospitals(id),
    schedule_id         UUID        REFERENCES doctor_schedules(id),
    appointment_date    DATE        NOT NULL,
    appointment_time    TIME        NOT NULL,
    status              TEXT        NOT NULL DEFAULT 'pending'
                                    CHECK (status IN ('pending', 'confirmed', 'cancelled', 'completed', 'no_show')),
    cancellation_reason TEXT,
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_doctor_slot
    ON appointments (doctor_id, appointment_date, appointment_time)
    WHERE status NOT IN ('cancelled');

-- Queues (uses Asia/Kolkata timezone default)
CREATE TABLE IF NOT EXISTS queues (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id     UUID        NOT NULL REFERENCES hospitals(id),
    doctor_id       UUID        NOT NULL REFERENCES doctors(id),
    queue_date      DATE        NOT NULL DEFAULT ((NOW() AT TIME ZONE 'Asia/Kolkata')::DATE),
    status          TEXT        NOT NULL DEFAULT 'active'
                                CHECK (status IN ('active', 'paused', 'closed')),
    current_token   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (doctor_id, queue_date)
);

-- Queue Entries
CREATE TABLE IF NOT EXISTS queue_entries (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_id        UUID        NOT NULL REFERENCES queues(id) ON DELETE CASCADE,
    appointment_id  UUID        REFERENCES appointments(id),
    patient_id      UUID        NOT NULL REFERENCES profiles(id),
    token_number    TEXT        NOT NULL,
    token_sequence  INTEGER     NOT NULL,
    status          TEXT        NOT NULL DEFAULT 'waiting'
                                CHECK (status IN (
                                    'booked', 'checked_in', 'waiting',
                                    'in_progress', 'completed', 'skipped', 'cancelled'
                                )),
    checked_in_at   TIMESTAMPTZ,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (queue_id, token_number)
);

-- Notifications
CREATE TABLE IF NOT EXISTS notifications (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    type        TEXT        NOT NULL,
    title       TEXT        NOT NULL,
    message     TEXT        NOT NULL,
    data        JSONB,
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Audit Logs
CREATE TABLE IF NOT EXISTS audit_logs (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id    UUID        REFERENCES profiles(id),
    action      TEXT        NOT NULL,
    entity_type TEXT        NOT NULL,
    entity_id   UUID,
    metadata    JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- OTP Verifications (Strictly private)
CREATE TABLE IF NOT EXISTS otp_verifications (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT        NOT NULL,
    otp_code        TEXT        NOT NULL,
    purpose         TEXT        NOT NULL CHECK (purpose IN ('register', 'login', 'reset_password')),
    expires_at      TIMESTAMPTZ NOT NULL,
    verified        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Doctor Reviews (Anti-abuse: unique per doctor/patient)
CREATE TABLE IF NOT EXISTS doctor_reviews (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id       UUID            NOT NULL REFERENCES doctors(id) ON DELETE CASCADE,
    patient_id      UUID            NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    patient_name    TEXT            NOT NULL,
    rating          NUMERIC(2,1)    NOT NULL CHECK (rating >= 1.0 AND rating <= 5.0),
    comment         TEXT            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_doctor_patient_review UNIQUE (doctor_id, patient_id)
);

-- User Favorites
CREATE TABLE IF NOT EXISTS user_favorites (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    doctor_id       UUID            REFERENCES doctors(id) ON DELETE CASCADE,
    hospital_id     UUID            REFERENCES hospitals(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_favorite_target CHECK (doctor_id IS NOT NULL OR hospital_id IS NOT NULL),
    CONSTRAINT unique_user_doctor UNIQUE (user_id, doctor_id),
    CONSTRAINT unique_user_hospital UNIQUE (user_id, hospital_id)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_doctors_hospital      ON doctors(hospital_id)       WHERE active = TRUE;
CREATE INDEX IF NOT EXISTS idx_schedules_doctor_date ON doctor_schedules(doctor_id, schedule_date);
CREATE INDEX IF NOT EXISTS idx_appointments_patient  ON appointments(patient_id, appointment_date);
CREATE INDEX IF NOT EXISTS idx_appointments_doctor   ON appointments(doctor_id, appointment_date);
CREATE INDEX IF NOT EXISTS idx_queue_entries_queue   ON queue_entries(queue_id, token_sequence);
CREATE INDEX IF NOT EXISTS idx_notifications_user    ON notifications(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_hospitals_location    ON hospitals(latitude, longitude) WHERE active = TRUE;
CREATE INDEX IF NOT EXISTS idx_otp_email_purpose     ON otp_verifications(email, purpose, expires_at DESC);
CREATE INDEX IF NOT EXISTS idx_profiles_email        ON profiles(LOWER(email));
CREATE INDEX IF NOT EXISTS idx_reviews_doctor        ON doctor_reviews(doctor_id);
CREATE INDEX IF NOT EXISTS idx_favorites_user        ON user_favorites(user_id);

-- ------------------------------------------------------------------------------
-- 3. TRIGGERS & FUNCTIONS
-- ------------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_profiles_updated_at ON profiles;
CREATE TRIGGER trg_profiles_updated_at BEFORE UPDATE ON profiles FOR EACH ROW EXECUTE FUNCTION update_updated_at();

DROP TRIGGER IF EXISTS trg_hospitals_updated_at ON hospitals;
CREATE TRIGGER trg_hospitals_updated_at BEFORE UPDATE ON hospitals FOR EACH ROW EXECUTE FUNCTION update_updated_at();

DROP TRIGGER IF EXISTS trg_doctors_updated_at ON doctors;
CREATE TRIGGER trg_doctors_updated_at BEFORE UPDATE ON doctors FOR EACH ROW EXECUTE FUNCTION update_updated_at();

DROP TRIGGER IF EXISTS trg_appointments_updated_at ON appointments;
CREATE TRIGGER trg_appointments_updated_at BEFORE UPDATE ON appointments FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- Auto-update doctor average rating and review count
CREATE OR REPLACE FUNCTION update_doctor_rating_stats()
RETURNS TRIGGER AS $$
DECLARE
    v_doctor_id UUID;
    v_avg NUMERIC(2,1);
    v_count INT;
BEGIN
    v_doctor_id := COALESCE(NEW.doctor_id, OLD.doctor_id);
    SELECT COALESCE(ROUND(AVG(rating), 1), 0), COUNT(*)
    INTO v_avg, v_count
    FROM doctor_reviews
    WHERE doctor_id = v_doctor_id;

    UPDATE doctors
    SET rating = v_avg, review_count = v_count, updated_at = NOW()
    WHERE id = v_doctor_id;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

DROP TRIGGER IF EXISTS trg_doctor_reviews_stats ON doctor_reviews;
CREATE TRIGGER trg_doctor_reviews_stats
    AFTER INSERT OR UPDATE OR DELETE ON doctor_reviews
    FOR EACH ROW EXECUTE FUNCTION update_doctor_rating_stats();

-- Auto-create/sync profile on signup
CREATE OR REPLACE FUNCTION handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profiles (id, full_name, phone, email, role)
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data ->> 'full_name', split_part(COALESCE(NEW.email, 'User'), '@', 1)),
        NEW.raw_user_meta_data ->> 'phone',
        NEW.email,
        'patient' -- Force patient role
    )
    ON CONFLICT (id) DO UPDATE
    SET full_name = COALESCE(EXCLUDED.full_name, profiles.full_name),
        phone = COALESCE(EXCLUDED.phone, profiles.phone),
        email = COALESCE(EXCLUDED.email, profiles.email),
        updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION handle_new_user();

-- Atomic Appointment Booking with Full Validations
CREATE OR REPLACE FUNCTION book_appointment(
    p_patient_id    UUID,
    p_doctor_id     UUID,
    p_hospital_id   UUID,
    p_schedule_id   UUID,
    p_date          DATE,
    p_time          TIME
)
RETURNS JSONB AS $$
DECLARE
    v_caller_id         UUID;
    v_actual_patient_id UUID;
    v_appointment_id    UUID;
    v_queue_id          UUID;
    v_token_seq         INTEGER;
    v_token_number      TEXT;
    v_queue_entry_id    UUID;
    v_sched             RECORD;
    v_doc_hospital_id   UUID;
    v_now_ist_date      DATE;
    v_now_ist_time      TIME;
BEGIN
    v_caller_id := auth.uid();
    IF v_caller_id IS NULL THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Unauthorized: Must be logged in');
    END IF;

    IF p_patient_id IS NOT NULL AND p_patient_id <> v_caller_id THEN
        IF (SELECT role FROM profiles WHERE id = v_caller_id) NOT IN ('admin', 'receptionist') THEN
            RETURN jsonb_build_object('success', FALSE, 'error', 'Forbidden: Cannot book on behalf of another patient');
        END IF;
        v_actual_patient_id := p_patient_id;
    ELSE
        v_actual_patient_id := v_caller_id;
    END IF;

    -- Validate schedule existence and matching
    SELECT * INTO v_sched
    FROM doctor_schedules
    WHERE id = p_schedule_id
      AND doctor_id = p_doctor_id
      AND status = 'available';

    IF v_sched IS NULL THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Schedule slot not available');
    END IF;

    IF v_sched.schedule_date <> p_date THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Appointment date does not match schedule date');
    END IF;

    -- Validate time bounds and slot duration alignment
    IF p_time < v_sched.start_time OR p_time >= v_sched.end_time THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Appointment time outside schedule hours');
    END IF;

    IF (EXTRACT(EPOCH FROM (p_time - v_sched.start_time))::INTEGER % (v_sched.slot_duration_minutes * 60)) <> 0 THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Appointment time does not align with slot intervals');
    END IF;

    -- Validate appointment is not in the past (Asia/Kolkata time)
    v_now_ist_date := (NOW() AT TIME ZONE 'Asia/Kolkata')::DATE;
    v_now_ist_time := (NOW() AT TIME ZONE 'Asia/Kolkata')::TIME;

    IF p_date < v_now_ist_date OR (p_date = v_now_ist_date AND p_time < v_now_ist_time) THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Cannot book appointment in the past');
    END IF;

    -- Validate doctor belongs to the specified hospital
    SELECT hospital_id INTO v_doc_hospital_id FROM doctors WHERE id = p_doctor_id;
    IF v_doc_hospital_id IS NULL OR v_doc_hospital_id <> p_hospital_id THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Doctor does not practice at the selected hospital');
    END IF;

    -- Check for slot conflict
    IF EXISTS (
        SELECT 1 FROM appointments
        WHERE doctor_id = p_doctor_id
          AND appointment_date = p_date
          AND appointment_time = p_time
          AND status NOT IN ('cancelled')
    ) THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Time slot already booked');
    END IF;

    -- Create appointment
    INSERT INTO appointments (patient_id, doctor_id, hospital_id, schedule_id, appointment_date, appointment_time, status)
    VALUES (v_actual_patient_id, p_doctor_id, p_hospital_id, p_schedule_id, p_date, p_time, 'confirmed')
    RETURNING id INTO v_appointment_id;

    -- Get or create today's queue
    INSERT INTO queues (hospital_id, doctor_id, queue_date)
    VALUES (p_hospital_id, p_doctor_id, p_date)
    ON CONFLICT (doctor_id, queue_date) DO UPDATE SET status = queues.status
    RETURNING id INTO v_queue_id;

    -- Lock queue row for atomic sequence generation
    PERFORM id FROM queues WHERE id = v_queue_id FOR UPDATE;

    SELECT COALESCE(MAX(token_sequence), 0) + 1
    INTO v_token_seq
    FROM queue_entries
    WHERE queue_id = v_queue_id;

    v_token_number := 'A-' || LPAD(v_token_seq::TEXT, 2, '0');

    INSERT INTO queue_entries (queue_id, appointment_id, patient_id, token_number, token_sequence, status)
    VALUES (v_queue_id, v_appointment_id, v_actual_patient_id, v_token_number, v_token_seq, 'booked')
    RETURNING id INTO v_queue_entry_id;

    INSERT INTO audit_logs (actor_id, action, entity_type, entity_id, metadata)
    VALUES (
        v_actual_patient_id,
        'appointment_booked',
        'appointment',
        v_appointment_id,
        jsonb_build_object(
            'doctor_id', p_doctor_id,
            'hospital_id', p_hospital_id,
            'date', p_date,
            'time', p_time,
            'token', v_token_number
        )
    );

    RETURN jsonb_build_object(
        'success', TRUE,
        'appointment_id', v_appointment_id,
        'queue_entry_id', v_queue_entry_id,
        'token_number', v_token_number,
        'token_sequence', v_token_seq
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

-- Walk-in Registration (Staff Only)
CREATE OR REPLACE FUNCTION register_walkin(
    p_patient_id    UUID,
    p_doctor_id     UUID,
    p_hospital_id   UUID
)
RETURNS JSONB AS $$
DECLARE
    v_queue_id       UUID;
    v_token_seq      INTEGER;
    v_token_number   TEXT;
    v_queue_entry_id UUID;
    v_today_ist      DATE;
BEGIN
    IF (SELECT role FROM profiles WHERE id = auth.uid()) NOT IN ('admin', 'receptionist') THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Forbidden: Staff permissions required');
    END IF;

    v_today_ist := (NOW() AT TIME ZONE 'Asia/Kolkata')::DATE;

    INSERT INTO queues (hospital_id, doctor_id, queue_date)
    VALUES (p_hospital_id, p_doctor_id, v_today_ist)
    ON CONFLICT (doctor_id, queue_date) DO UPDATE SET status = queues.status
    RETURNING id INTO v_queue_id;

    PERFORM id FROM queues WHERE id = v_queue_id FOR UPDATE;

    SELECT COALESCE(MAX(token_sequence), 0) + 1
    INTO v_token_seq
    FROM queue_entries
    WHERE queue_id = v_queue_id;

    v_token_number := 'W-' || LPAD(v_token_seq::TEXT, 2, '0');

    INSERT INTO queue_entries (queue_id, patient_id, token_number, token_sequence, status)
    VALUES (v_queue_id, p_patient_id, v_token_number, v_token_seq, 'checked_in')
    RETURNING id INTO v_queue_entry_id;

    RETURN jsonb_build_object(
        'success', TRUE,
        'queue_entry_id', v_queue_entry_id,
        'token_number', v_token_number,
        'token_sequence', v_token_seq
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

REVOKE EXECUTE ON FUNCTION register_walkin(UUID, UUID, UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION register_walkin(UUID, UUID, UUID) TO authenticated;

-- Advance Queue (Staff only)
CREATE OR REPLACE FUNCTION advance_queue(
    p_queue_id UUID
)
RETURNS JSONB AS $$
DECLARE
    v_next RECORD;
BEGIN
    IF (SELECT role FROM profiles WHERE id = auth.uid()) NOT IN ('admin', 'receptionist', 'doctor') THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Forbidden: Staff permissions required');
    END IF;

    PERFORM id FROM queues WHERE id = p_queue_id FOR UPDATE;

    UPDATE queue_entries
    SET status = 'completed', completed_at = NOW()
    WHERE queue_id = p_queue_id AND status = 'in_progress';

    SELECT id, token_number, patient_id
    INTO v_next
    FROM queue_entries
    WHERE queue_id = p_queue_id
      AND status IN ('waiting', 'checked_in')
    ORDER BY token_sequence ASC
    LIMIT 1;

    IF v_next IS NULL THEN
        UPDATE queues SET current_token = NULL WHERE id = p_queue_id;
        RETURN jsonb_build_object('success', TRUE, 'message', 'Queue is empty');
    END IF;

    UPDATE queue_entries
    SET status = 'in_progress', started_at = NOW()
    WHERE id = v_next.id;

    UPDATE queues SET current_token = v_next.token_number WHERE id = p_queue_id;

    RETURN jsonb_build_object(
        'success', TRUE,
        'current_token', v_next.token_number,
        'patient_id', v_next.patient_id
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

REVOKE EXECUTE ON FUNCTION advance_queue(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION advance_queue(UUID) TO authenticated;

-- Cancel Appointment (Auth verified)
CREATE OR REPLACE FUNCTION cancel_appointment(
    p_appointment_id UUID,
    p_reason         TEXT DEFAULT NULL
)
RETURNS JSONB AS $$
DECLARE
    v_caller_id UUID;
    v_appt      RECORD;
BEGIN
    v_caller_id := auth.uid();
    IF v_caller_id IS NULL THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Unauthorized');
    END IF;

    SELECT * INTO v_appt FROM appointments WHERE id = p_appointment_id;

    IF v_appt IS NULL THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Appointment not found');
    END IF;

    IF v_appt.patient_id <> v_caller_id AND (SELECT role FROM profiles WHERE id = v_caller_id) NOT IN ('admin', 'receptionist') THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Forbidden: Not authorized to cancel this appointment');
    END IF;

    IF v_appt.status IN ('cancelled', 'completed') THEN
        RETURN jsonb_build_object('success', FALSE, 'error', 'Cannot cancel an appointment that is already ' || v_appt.status);
    END IF;

    UPDATE appointments
    SET status = 'cancelled', cancellation_reason = p_reason, updated_at = NOW()
    WHERE id = p_appointment_id;

    UPDATE queue_entries
    SET status = 'cancelled'
    WHERE appointment_id = p_appointment_id
      AND status NOT IN ('completed', 'cancelled');

    INSERT INTO audit_logs (actor_id, action, entity_type, entity_id, metadata)
    VALUES (v_caller_id, 'appointment_cancelled', 'appointment', p_appointment_id,
            jsonb_build_object('reason', p_reason));

    RETURN jsonb_build_object('success', TRUE);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

-- Calculate Estimated Wait
CREATE OR REPLACE FUNCTION get_estimated_wait(
    p_queue_id      UUID,
    p_token_seq     INTEGER
)
RETURNS JSONB AS $$
DECLARE
    v_ahead         INTEGER;
    v_avg_duration  INTERVAL;
    v_est_minutes   INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO v_ahead
    FROM queue_entries
    WHERE queue_id = p_queue_id
      AND token_sequence < p_token_seq
      AND status IN ('waiting', 'checked_in', 'in_progress');

    SELECT AVG(completed_at - started_at)
    INTO v_avg_duration
    FROM queue_entries
    WHERE queue_id = p_queue_id
      AND status = 'completed'
      AND started_at IS NOT NULL
      AND completed_at IS NOT NULL;

    IF v_avg_duration IS NULL THEN
        v_est_minutes := v_ahead * 10;
    ELSE
        v_est_minutes := v_ahead * EXTRACT(EPOCH FROM v_avg_duration)::INTEGER / 60;
    END IF;

    RETURN jsonb_build_object(
        'patients_ahead', v_ahead,
        'estimated_wait_minutes', v_est_minutes
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

GRANT EXECUTE ON FUNCTION get_estimated_wait(UUID, INTEGER) TO authenticated;

-- Dynamic Schedule Generator (Timezone aware)
CREATE OR REPLACE FUNCTION generate_future_schedules(p_days_ahead INT DEFAULT 7)
RETURNS INTEGER AS $$
DECLARE
    doc RECORD;
    d DATE;
    v_start_date DATE;
    v_count INTEGER := 0;
BEGIN
    v_start_date := (NOW() AT TIME ZONE 'Asia/Kolkata')::DATE;

    FOR doc IN SELECT id FROM doctors WHERE active = TRUE LOOP
        FOR d IN SELECT generate_series(v_start_date, v_start_date + (p_days_ahead || ' days')::INTERVAL, '1 day')::DATE LOOP
            INSERT INTO doctor_schedules (doctor_id, schedule_date, start_time, end_time, slot_duration_minutes, status)
            VALUES (doc.id, d, '09:00', '13:00', 30, 'available')
            ON CONFLICT (doctor_id, schedule_date, start_time) DO NOTHING;

            INSERT INTO doctor_schedules (doctor_id, schedule_date, start_time, end_time, slot_duration_minutes, status)
            VALUES (doc.id, d, '14:00', '18:00', 30, 'available')
            ON CONFLICT (doctor_id, schedule_date, start_time) DO NOTHING;

            v_count := v_count + 2;
        END LOOP;
    END LOOP;
    RETURN v_count;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

-- ------------------------------------------------------------------------------
-- 4. ROW LEVEL SECURITY (RLS)
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.get_my_role()
RETURNS TEXT AS $$
    SELECT role FROM profiles WHERE id = auth.uid();
$$ LANGUAGE sql SECURITY DEFINER STABLE SET search_path = public, pg_temp;

ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS profiles_select_own ON profiles;
CREATE POLICY profiles_select_own ON profiles FOR SELECT USING (id = auth.uid());

-- Lock role and hospital_id columns against unauthorized user modifications
DROP POLICY IF EXISTS profiles_update_own ON profiles;
CREATE POLICY profiles_update_own ON profiles FOR UPDATE USING (id = auth.uid())
    WITH CHECK (
        id = auth.uid()
        AND role = (SELECT role FROM profiles WHERE id = auth.uid())
        AND hospital_id IS NOT DISTINCT FROM (SELECT hospital_id FROM profiles WHERE id = auth.uid())
    );

DROP POLICY IF EXISTS profiles_insert_own ON profiles;
CREATE POLICY profiles_insert_own ON profiles FOR INSERT WITH CHECK (
    id = auth.uid() AND role = 'patient'
);

DROP POLICY IF EXISTS profiles_select_admin ON profiles;
CREATE POLICY profiles_select_admin ON profiles FOR SELECT USING (public.get_my_role() = 'admin');

ALTER TABLE hospitals ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS hospitals_select_all ON hospitals;
CREATE POLICY hospitals_select_all ON hospitals FOR SELECT USING (active = TRUE);
DROP POLICY IF EXISTS hospitals_admin_all ON hospitals;
CREATE POLICY hospitals_admin_all ON hospitals FOR ALL USING (public.get_my_role() = 'admin');

ALTER TABLE doctors ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS doctors_select_all ON doctors;
CREATE POLICY doctors_select_all ON doctors FOR SELECT USING (active = TRUE);
DROP POLICY IF EXISTS doctors_admin_all ON doctors;
CREATE POLICY doctors_admin_all ON doctors FOR ALL USING (public.get_my_role() = 'admin');

ALTER TABLE doctor_schedules ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS schedules_select_all ON doctor_schedules;
CREATE POLICY schedules_select_all ON doctor_schedules FOR SELECT USING (status = 'available');
DROP POLICY IF EXISTS schedules_admin ON doctor_schedules;
CREATE POLICY schedules_admin ON doctor_schedules FOR ALL USING (public.get_my_role() IN ('admin', 'doctor'));

-- Appointments: DROP insert & update for patients so booking bypass is closed
ALTER TABLE appointments ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS appointments_select_patient ON appointments;
CREATE POLICY appointments_select_patient ON appointments FOR SELECT USING (patient_id = auth.uid());

DROP POLICY IF EXISTS appointments_insert_patient ON appointments;
DROP POLICY IF EXISTS appointments_update_patient ON appointments;

DROP POLICY IF EXISTS appointments_select_doctor ON appointments;
CREATE POLICY appointments_select_doctor ON appointments FOR SELECT USING (
    doctor_id IN (SELECT id FROM doctors WHERE profile_id = auth.uid())
);
DROP POLICY IF EXISTS appointments_update_doctor ON appointments;
CREATE POLICY appointments_update_doctor ON appointments FOR UPDATE USING (
    doctor_id IN (SELECT id FROM doctors WHERE profile_id = auth.uid())
);
DROP POLICY IF EXISTS appointments_receptionist ON appointments;
CREATE POLICY appointments_receptionist ON appointments FOR ALL USING (
    public.get_my_role() = 'receptionist' AND hospital_id IN (
        SELECT hospital_id FROM profiles WHERE id = auth.uid()
    )
);
DROP POLICY IF EXISTS appointments_admin ON appointments;
CREATE POLICY appointments_admin ON appointments FOR ALL USING (public.get_my_role() = 'admin');

ALTER TABLE queues ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS queues_select_all ON queues;
CREATE POLICY queues_select_all ON queues FOR SELECT USING (TRUE);
DROP POLICY IF EXISTS queues_staff ON queues;
CREATE POLICY queues_staff ON queues FOR ALL USING (public.get_my_role() IN ('admin', 'receptionist', 'doctor'));

ALTER TABLE queue_entries ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS queue_entries_select_patient ON queue_entries;
CREATE POLICY queue_entries_select_patient ON queue_entries FOR SELECT USING (patient_id = auth.uid());
DROP POLICY IF EXISTS queue_entries_select_all ON queue_entries;
DROP POLICY IF EXISTS queue_entries_staff ON queue_entries;
CREATE POLICY queue_entries_staff ON queue_entries FOR ALL USING (public.get_my_role() IN ('admin', 'receptionist', 'doctor'));

-- Sanitized live queue view (omits patient_id and appointment_id; authenticated only)
DROP VIEW IF EXISTS live_queue_status;
CREATE OR REPLACE VIEW live_queue_status WITH (security_invoker = false) AS
SELECT id, queue_id, token_number, token_sequence, status, checked_in_at, started_at, completed_at, created_at
FROM queue_entries;

REVOKE ALL ON live_queue_status FROM anon;
GRANT SELECT ON live_queue_status TO authenticated;

ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS notifications_select_own ON notifications;
CREATE POLICY notifications_select_own ON notifications FOR SELECT USING (user_id = auth.uid());
DROP POLICY IF EXISTS notifications_update_own ON notifications;
CREATE POLICY notifications_update_own ON notifications FOR UPDATE USING (user_id = auth.uid());

ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS audit_logs_select_admin ON audit_logs;
CREATE POLICY audit_logs_select_admin ON audit_logs FOR SELECT USING (public.get_my_role() = 'admin');

ALTER TABLE otp_verifications ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS otp_insert_all ON otp_verifications;
DROP POLICY IF EXISTS otp_select_all ON otp_verifications;
DROP POLICY IF EXISTS otp_update_all ON otp_verifications;

-- Doctor Reviews: Requires a verified completed appointment to review
ALTER TABLE doctor_reviews ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS doctor_reviews_select_all ON doctor_reviews;
CREATE POLICY doctor_reviews_select_all ON doctor_reviews FOR SELECT USING (TRUE);

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
CREATE POLICY doctor_reviews_admin ON doctor_reviews FOR ALL USING (public.get_my_role() = 'admin');

ALTER TABLE user_favorites ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS favorites_select_own ON user_favorites;
CREATE POLICY favorites_select_own ON user_favorites FOR SELECT USING (user_id = auth.uid());
DROP POLICY IF EXISTS favorites_insert_own ON user_favorites;
CREATE POLICY favorites_insert_own ON user_favorites FOR INSERT WITH CHECK (user_id = auth.uid());
DROP POLICY IF EXISTS favorites_delete_own ON user_favorites;
CREATE POLICY favorites_delete_own ON user_favorites FOR DELETE USING (user_id = auth.uid());

-- ------------------------------------------------------------------------------
-- 5. REALTIME REPLICATION
-- ------------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND tablename = 'queues') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE queues;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND tablename = 'queue_entries') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE queue_entries;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND tablename = 'appointments') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE appointments;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND tablename = 'notifications') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE notifications;
    END IF;
END $$;

-- ------------------------------------------------------------------------------
-- 6. SEED DATA
-- ------------------------------------------------------------------------------
INSERT INTO hospitals (id, name, type, address, city, state, latitude, longitude, phone, opening_hours, facilities, image_url, rating, review_count) VALUES
('a1000000-0000-0000-0000-000000000001', 'City Care Hospital', 'Multi-Speciality', 'Bailey Road', 'Patna', 'Bihar', 25.6093, 85.1376, '+91 98765 43210', '8:00 AM - 8:00 PM', ARRAY['Pharmacy', 'Ambulance', 'Parking', 'Lab'], NULL, 4.5, 320),
('a1000000-0000-0000-0000-000000000002', 'MediLife Hospital', 'Multi-Speciality', 'Boring Road', 'Patna', 'Bihar', 25.6100, 85.1450, '+91 98765 43211', '7:00 AM - 9:00 PM', ARRAY['Pharmacy', 'Ambulance', 'Parking', 'Lab', 'ICU'], NULL, 4.3, 210),
('a1000000-0000-0000-0000-000000000003', 'Sunrise Hospital', 'General Hospital', 'Kankarbagh', 'Patna', 'Bihar', 25.5980, 85.1280, '+91 98765 43212', '8:00 AM - 7:00 PM', ARRAY['Pharmacy', 'Parking', 'Lab'], NULL, 4.2, 180),
('a1000000-0000-0000-0000-000000000004', 'Global Hospital', 'Super-Speciality', 'Patliputra Colony', 'Patna', 'Bihar', 25.6200, 85.1500, '+91 98765 43213', '6:00 AM - 8:00 PM', ARRAY['Pharmacy', 'Ambulance', 'Parking', 'Lab', 'ICU', 'Blood Bank'], NULL, 4.6, 450)
ON CONFLICT (id) DO NOTHING;

INSERT INTO doctors (id, hospital_id, name, specialty, bio, experience_years, consultation_fee, consultation_duration_min, rating, review_count) VALUES
('d1000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001', 'Dr. Rajesh Kumar', 'General Physician', 'Experienced general physician with expertise in fever, diabetes, and hypertension.', 8, 300.00, 15, 4.8, 320),
('d1000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000001', 'Dr. Priya Sharma', 'Dermatologist', 'Renowned dermatologist specializing in skin disorders and cosmetic treatments.', 6, 500.00, 15, 4.6, 210),
('d1000000-0000-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000001', 'Dr. Amit Singh', 'Orthopedic', 'Skilled orthopedic surgeon with experience in joint replacements.', 10, 400.00, 20, 4.7, 180),
('d1000000-0000-0000-0000-000000000004', 'a1000000-0000-0000-0000-000000000001', 'Dr. Neha Verma', 'Gynecologist', 'Compassionate gynecologist providing women health care.', 7, 350.00, 15, 4.5, 200),
('d1000000-0000-0000-0000-000000000005', 'a1000000-0000-0000-0000-000000000002', 'Dr. Vikram Patel', 'Cardiologist', 'Leading cardiologist with expertise in interventional cardiology.', 12, 600.00, 20, 4.9, 350),
('d1000000-0000-0000-0000-000000000006', 'a1000000-0000-0000-0000-000000000002', 'Dr. Sunita Gupta', 'Pediatrician', 'Pediatrician with 9 years of experience in child healthcare.', 9, 350.00, 15, 4.4, 190),
('d1000000-0000-0000-0000-000000000007', 'a1000000-0000-0000-0000-000000000003', 'Dr. Arjun Mehta', 'ENT Specialist', 'Ear, nose and throat disorders with modern treatment approaches.', 5, 300.00, 15, 4.3, 150),
('d1000000-0000-0000-0000-000000000008', 'a1000000-0000-0000-0000-000000000004', 'Dr. Kavita Joshi', 'Neurologist', 'Neurologist specializing in migraine and epilepsy treatment.', 11, 700.00, 20, 4.7, 280)
ON CONFLICT (id) DO NOTHING;

-- Populate schedules for next 7 days in Asia/Kolkata timezone
SELECT generate_future_schedules(7);
