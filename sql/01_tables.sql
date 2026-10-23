-- ============================================================
-- MediQ Database Schema — Supabase PostgreSQL
-- 01_tables.sql — Core table definitions
-- ============================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- 1. PROFILES
-- Extends Supabase auth.users with app-specific data.
-- ============================================================
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

COMMENT ON TABLE profiles IS 'Application user profiles linked 1-to-1 with auth.users';

-- ============================================================
-- 2. HOSPITALS
-- ============================================================
CREATE TABLE IF NOT EXISTS hospitals (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT            NOT NULL,
    type            TEXT,                             -- Multi-Speciality, General, Super-Speciality
    address         TEXT            NOT NULL,
    city            TEXT,
    state           TEXT,
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    phone           TEXT,
    opening_hours   TEXT,                             -- e.g. "8:00 AM - 8:00 PM"
    facilities      TEXT[],                           -- {Pharmacy, Ambulance, Parking, Lab}
    image_url       TEXT,
    rating          NUMERIC(2,1)    NOT NULL DEFAULT 0,
    review_count    INTEGER         NOT NULL DEFAULT 0,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE hospitals IS 'Registered hospitals / clinics shown to patients';

-- ============================================================
-- 3. DOCTORS
-- ============================================================
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

COMMENT ON TABLE doctors IS 'Doctor profiles associated with a hospital';

-- ============================================================
-- 4. DOCTOR SCHEDULES
-- One row per doctor per day-and-time-block.
-- ============================================================
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

COMMENT ON TABLE doctor_schedules IS 'Time blocks a doctor is available for appointments';

-- ============================================================
-- 5. APPOINTMENTS
-- ============================================================
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

COMMENT ON TABLE appointments IS 'Patient appointment bookings';

-- Prevent double-booking the same doctor at the same date/time
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_doctor_slot
    ON appointments (doctor_id, appointment_date, appointment_time)
    WHERE status NOT IN ('cancelled');

-- ============================================================
-- 6. QUEUES
-- One queue per doctor per day.
-- ============================================================
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

COMMENT ON TABLE queues IS 'Daily queue per doctor/service';

-- ============================================================
-- 7. QUEUE ENTRIES
-- Individual tokens in the queue.
-- ============================================================
CREATE TABLE IF NOT EXISTS queue_entries (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_id        UUID        NOT NULL REFERENCES queues(id) ON DELETE CASCADE,
    appointment_id  UUID        REFERENCES appointments(id),
    patient_id      UUID        NOT NULL REFERENCES profiles(id),
    token_number    TEXT        NOT NULL,                      -- e.g. "A-12"
    token_sequence  INTEGER     NOT NULL,                      -- numeric ordering
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

COMMENT ON TABLE queue_entries IS 'Individual patient entries/tokens inside a queue';

-- ============================================================
-- 8. NOTIFICATIONS
-- ============================================================
CREATE TABLE IF NOT EXISTS notifications (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    type        TEXT        NOT NULL,                          -- appointment_confirmed, turn_approaching, etc.
    title       TEXT        NOT NULL,
    message     TEXT        NOT NULL,
    data        JSONB,                                         -- extra structured payload
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE notifications IS 'In-app notifications for users';

-- ============================================================
-- 9. AUDIT LOGS
-- ============================================================
CREATE TABLE IF NOT EXISTS audit_logs (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id    UUID        REFERENCES profiles(id),
    action      TEXT        NOT NULL,                          -- e.g. appointment_booked, queue_updated
    entity_type TEXT        NOT NULL,                          -- e.g. appointment, queue_entry
    entity_id   UUID,
    metadata    JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE audit_logs IS 'Immutable log of sensitive actions for traceability';

-- ============================================================
-- 10. OTP VERIFICATIONS
-- For Gmail SMTP OTP delivery during registration and login.
-- ============================================================
CREATE TABLE IF NOT EXISTS otp_verifications (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT        NOT NULL,
    otp_code        TEXT        NOT NULL,
    purpose         TEXT        NOT NULL CHECK (purpose IN ('register', 'login', 'reset_password')),
    expires_at      TIMESTAMPTZ NOT NULL,
    verified        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE otp_verifications IS 'Stores temporary OTP codes sent via Gmail SMTP for email verification and login';

-- ============================================================
-- 11. DOCTOR REVIEWS
-- ============================================================
CREATE TABLE IF NOT EXISTS doctor_reviews (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id       UUID            NOT NULL REFERENCES doctors(id) ON DELETE CASCADE,
    patient_id      UUID            REFERENCES profiles(id) ON DELETE SET NULL,
    patient_name    TEXT            NOT NULL,
    rating          NUMERIC(2,1)    NOT NULL CHECK (rating >= 1.0 AND rating <= 5.0),
    comment         TEXT            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_doctor_patient_review UNIQUE (doctor_id, patient_id)
);

COMMENT ON TABLE doctor_reviews IS 'Patient reviews and feedback for doctors';

-- ============================================================
-- 12. USER FAVORITES
-- ============================================================
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

COMMENT ON TABLE user_favorites IS 'Saved favorite doctors and hospitals by patients';

-- ============================================================
-- INDEXES for common queries
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_doctors_hospital      ON doctors(hospital_id)       WHERE active = TRUE;
CREATE INDEX IF NOT EXISTS idx_schedules_doctor_date ON doctor_schedules(doctor_id, schedule_date);
CREATE INDEX IF NOT EXISTS idx_appointments_patient  ON appointments(patient_id, appointment_date);
CREATE INDEX IF NOT EXISTS idx_appointments_doctor   ON appointments(doctor_id, appointment_date);
CREATE INDEX IF NOT EXISTS idx_queue_entries_queue   ON queue_entries(queue_id, token_sequence);
CREATE INDEX IF NOT EXISTS idx_notifications_user    ON notifications(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_hospitals_location    ON hospitals(latitude, longitude) WHERE active = TRUE;
CREATE INDEX IF NOT EXISTS idx_otp_email_purpose     ON otp_verifications(email, purpose, expires_at DESC);
CREATE INDEX IF NOT EXISTS idx_profiles_email        ON profiles(LOWER(email));

-- ============================================================
-- AUTO-UPDATE updated_at TRIGGER
-- ============================================================
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_profiles_updated_at ON profiles;
CREATE TRIGGER trg_profiles_updated_at
    BEFORE UPDATE ON profiles FOR EACH ROW EXECUTE FUNCTION update_updated_at();

DROP TRIGGER IF EXISTS trg_hospitals_updated_at ON hospitals;
CREATE TRIGGER trg_hospitals_updated_at
    BEFORE UPDATE ON hospitals FOR EACH ROW EXECUTE FUNCTION update_updated_at();

DROP TRIGGER IF EXISTS trg_doctors_updated_at ON doctors;
CREATE TRIGGER trg_doctors_updated_at
    BEFORE UPDATE ON doctors FOR EACH ROW EXECUTE FUNCTION update_updated_at();

DROP TRIGGER IF EXISTS trg_appointments_updated_at ON appointments;
CREATE TRIGGER trg_appointments_updated_at
    BEFORE UPDATE ON appointments FOR EACH ROW EXECUTE FUNCTION update_updated_at();
