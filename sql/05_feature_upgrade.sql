-- ============================================================
-- MediQ — 05_feature_upgrade.sql
-- Run this ONCE in the Supabase SQL Editor (after 00_full_setup.sql
-- or 01–04). Safe to re-run: every statement is idempotent.
--
-- Adds:
--   1. Specialties table        -> category circles on Home
--   2. Promo banners table      -> editable Home offers/banners
--   3. Emergency flag/hospital  -> SOS screen
--   4. Doctor extra fields      -> qualification, languages
--   5. Auto notifications       -> booking, cancel, queue "you're next / your turn", review prompt
--   6. Auto doctor rating       -> recalculated when a review is added
--   7. doctor_next_slot view    -> "Next: Today 4:30 PM" on doctor cards
--   8. Notification helpers     -> mark all read, delete policy
-- ============================================================

-- ------------------------------------------------------------
-- 1. EXTRA COLUMNS
-- ------------------------------------------------------------
ALTER TABLE hospitals ADD COLUMN IF NOT EXISTS emergency_24x7 BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE hospitals ADD COLUMN IF NOT EXISTS emergency_phone TEXT;

ALTER TABLE doctors ADD COLUMN IF NOT EXISTS qualification TEXT;
ALTER TABLE doctors ADD COLUMN IF NOT EXISTS languages TEXT[];

CREATE INDEX IF NOT EXISTS idx_doctors_specialty ON doctors (LOWER(specialty)) WHERE active = TRUE;
CREATE INDEX IF NOT EXISTS idx_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;

-- ------------------------------------------------------------
-- 2. SPECIALTIES  (public read, admin managed from dashboard)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS specialties (
    id          UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT    NOT NULL UNIQUE,      -- must match doctors.specialty text
    emoji       TEXT    NOT NULL DEFAULT '🩺',
    color_hex   TEXT    NOT NULL DEFAULT '#EAF2FF',
    sort_order  INTEGER NOT NULL DEFAULT 100,
    active      BOOLEAN NOT NULL DEFAULT TRUE
);

ALTER TABLE specialties ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS specialties_select_all ON specialties;
CREATE POLICY specialties_select_all ON specialties FOR SELECT USING (TRUE);
GRANT SELECT ON specialties TO anon, authenticated;

INSERT INTO specialties (name, emoji, color_hex, sort_order) VALUES
 ('General Physician', '🩺', '#E3F2FD', 1),
 ('Dermatologist',     '🧴', '#FCE4EC', 2),
 ('Cardiologist',      '❤️', '#FFEBEE', 3),
 ('Pediatrician',      '🧒', '#FFF8E1', 4),
 ('Orthopedic',        '🦴', '#EDE7F6', 5),
 ('Gynecologist',      '🤰', '#F3E5F5', 6),
 ('ENT Specialist',    '👂', '#E0F2F1', 7),
 ('Neurologist',       '🧠', '#E8F5E9', 8)
ON CONFLICT (name) DO NOTHING;

-- ------------------------------------------------------------
-- 3. PROMO BANNERS  (edit rows in Table Editor to change Home banners)
--    action: 'doctors' | 'hospitals' | 'map' | 'emergency' | 'book' | 'favorites'
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS promo_banners (
    id          UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    title       TEXT    NOT NULL,
    subtitle    TEXT,
    cta_text    TEXT    NOT NULL DEFAULT 'Explore',
    emoji       TEXT    NOT NULL DEFAULT '🏥',
    color_start TEXT    NOT NULL DEFAULT '#1976D2',
    color_end   TEXT    NOT NULL DEFAULT '#42A5F5',
    action      TEXT    NOT NULL DEFAULT 'doctors',
    sort_order  INTEGER NOT NULL DEFAULT 100,
    active      BOOLEAN NOT NULL DEFAULT TRUE
);

ALTER TABLE promo_banners ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS promo_banners_select_all ON promo_banners;
CREATE POLICY promo_banners_select_all ON promo_banners FOR SELECT USING (active = TRUE);
GRANT SELECT ON promo_banners TO anon, authenticated;

INSERT INTO promo_banners (title, subtitle, cta_text, emoji, color_start, color_end, action, sort_order)
SELECT * FROM (VALUES
 ('Skip the waiting room',  'Book a slot & track your token live from home', 'Book now',   '⏱️', '#1565C0', '#42A5F5', 'book',      1),
 ('Doctors near you',       'Top rated specialists, ready today',            'Find doctors','👩‍⚕️', '#00897B', '#4DB6AC', 'doctors',   2),
 ('Emergency? We''re fast', 'Call an ambulance & find 24x7 hospitals',       'Open SOS',    '🚑', '#C62828', '#EF5350', 'emergency', 3)
) AS v(title, subtitle, cta_text, emoji, color_start, color_end, action, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM promo_banners);

-- ------------------------------------------------------------
-- 4. SEED: emergency flags + doctor details for the sample data
--    (harmless no-ops if those rows don't exist)
-- ------------------------------------------------------------
UPDATE hospitals SET emergency_24x7 = TRUE, emergency_phone = '+91 98765 43213'
 WHERE name IN ('Global Hospital', 'MediLife Hospital') AND emergency_24x7 = FALSE;

UPDATE doctors SET qualification = 'MBBS, MD (Medicine)'      WHERE qualification IS NULL AND specialty = 'General Physician';
UPDATE doctors SET qualification = 'MBBS, MD (Dermatology)'   WHERE qualification IS NULL AND specialty = 'Dermatologist';
UPDATE doctors SET qualification = 'MBBS, MS (Orthopedics)'   WHERE qualification IS NULL AND specialty = 'Orthopedic';
UPDATE doctors SET qualification = 'MBBS, DGO, MS (OBG)'      WHERE qualification IS NULL AND specialty = 'Gynecologist';
UPDATE doctors SET qualification = 'MBBS, DM (Cardiology)'    WHERE qualification IS NULL AND specialty = 'Cardiologist';
UPDATE doctors SET qualification = 'MBBS, MD (Pediatrics)'    WHERE qualification IS NULL AND specialty = 'Pediatrician';
UPDATE doctors SET qualification = 'MBBS, MS (ENT)'           WHERE qualification IS NULL AND specialty = 'ENT Specialist';
UPDATE doctors SET qualification = 'MBBS, DM (Neurology)'     WHERE qualification IS NULL AND specialty = 'Neurologist';
UPDATE doctors SET languages = ARRAY['English', 'Hindi']      WHERE languages IS NULL;

-- ------------------------------------------------------------
-- 5. AUTOMATIC NOTIFICATIONS
-- ------------------------------------------------------------
-- 5a. Appointment booked / cancelled / completed
CREATE OR REPLACE FUNCTION notify_appointment_events()
RETURNS TRIGGER AS $$
DECLARE
    v_doctor   TEXT;
    v_hospital TEXT;
    v_when     TEXT;
BEGIN
    SELECT d.name, h.name INTO v_doctor, v_hospital
    FROM doctors d JOIN hospitals h ON h.id = NEW.hospital_id
    WHERE d.id = NEW.doctor_id;

    v_when := to_char(NEW.appointment_date, 'DD Mon') || ' at ' || to_char(NEW.appointment_time, 'HH12:MI AM');

    IF TG_OP = 'INSERT' THEN
        INSERT INTO notifications (user_id, type, title, message, data)
        VALUES (NEW.patient_id, 'appointment_booked', 'Appointment booked ✅',
                'Your visit with ' || v_doctor || ' at ' || v_hospital || ' is set for ' || v_when || '.',
                jsonb_build_object('appointment_id', NEW.id));

    ELSIF TG_OP = 'UPDATE' AND NEW.status IS DISTINCT FROM OLD.status THEN
        IF NEW.status = 'cancelled' THEN
            INSERT INTO notifications (user_id, type, title, message, data)
            VALUES (NEW.patient_id, 'appointment_cancelled', 'Appointment cancelled',
                    'Your visit with ' || v_doctor || ' on ' || v_when || ' was cancelled.',
                    jsonb_build_object('appointment_id', NEW.id));
        ELSIF NEW.status = 'completed' THEN
            INSERT INTO notifications (user_id, type, title, message, data)
            VALUES (NEW.patient_id, 'review_prompt', 'How was your visit? ⭐',
                    'Rate your experience with ' || v_doctor || ' and help other patients.',
                    jsonb_build_object('appointment_id', NEW.id, 'doctor_id', NEW.doctor_id));
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

DROP TRIGGER IF EXISTS trg_notify_appointment_insert ON appointments;
CREATE TRIGGER trg_notify_appointment_insert
    AFTER INSERT ON appointments FOR EACH ROW EXECUTE FUNCTION notify_appointment_events();

DROP TRIGGER IF EXISTS trg_notify_appointment_update ON appointments;
CREATE TRIGGER trg_notify_appointment_update
    AFTER UPDATE OF status ON appointments FOR EACH ROW EXECUTE FUNCTION notify_appointment_events();

-- 5b. Queue: "your turn" + "you're next"
CREATE OR REPLACE FUNCTION notify_queue_progress()
RETURNS TRIGGER AS $$
DECLARE
    v_next_patient UUID;
    v_next_token   TEXT;
BEGIN
    IF NEW.status = 'in_progress' AND OLD.status IS DISTINCT FROM 'in_progress' THEN
        INSERT INTO notifications (user_id, type, title, message, data)
        VALUES (NEW.patient_id, 'your_turn', 'It''s your turn! 🎉',
                'Token ' || NEW.token_number || ' — please head to the doctor''s room now.',
                jsonb_build_object('queue_id', NEW.queue_id, 'token', NEW.token_number));

        SELECT patient_id, token_number INTO v_next_patient, v_next_token
        FROM queue_entries
        WHERE queue_id = NEW.queue_id
          AND token_sequence > NEW.token_sequence
          AND status IN ('waiting', 'checked_in', 'booked')
        ORDER BY token_sequence
        LIMIT 1;

        IF v_next_patient IS NOT NULL THEN
            INSERT INTO notifications (user_id, type, title, message, data)
            VALUES (v_next_patient, 'turn_approaching', 'You''re next in line ⏳',
                    'Token ' || v_next_token || ' is up next. Please be ready outside the room.',
                    jsonb_build_object('queue_id', NEW.queue_id, 'token', v_next_token));
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

DROP TRIGGER IF EXISTS trg_notify_queue_progress ON queue_entries;
CREATE TRIGGER trg_notify_queue_progress
    AFTER UPDATE OF status ON queue_entries FOR EACH ROW EXECUTE FUNCTION notify_queue_progress();

-- 5c. Notification helpers
DROP POLICY IF EXISTS notifications_delete_own ON notifications;
CREATE POLICY notifications_delete_own ON notifications FOR DELETE USING (user_id = auth.uid());

CREATE OR REPLACE FUNCTION mark_all_notifications_read()
RETURNS INTEGER AS $$
DECLARE v_count INTEGER;
BEGIN
    UPDATE notifications SET read_at = NOW()
    WHERE user_id = auth.uid() AND read_at IS NULL;
    GET DIAGNOSTICS v_count = ROW_COUNT;
    RETURN v_count;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;
GRANT EXECUTE ON FUNCTION mark_all_notifications_read() TO authenticated;

-- ------------------------------------------------------------
-- 6. AUTO-UPDATE DOCTOR RATING WHEN REVIEWS CHANGE
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION refresh_doctor_rating()
RETURNS TRIGGER AS $$
DECLARE v_doctor UUID;
BEGIN
    v_doctor := COALESCE(NEW.doctor_id, OLD.doctor_id);
    UPDATE doctors d
    SET rating       = COALESCE((SELECT ROUND(AVG(rating)::numeric, 1) FROM doctor_reviews WHERE doctor_id = v_doctor), 0),
        review_count = (SELECT COUNT(*) FROM doctor_reviews WHERE doctor_id = v_doctor)
    WHERE d.id = v_doctor;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

DROP TRIGGER IF EXISTS trg_refresh_doctor_rating ON doctor_reviews;
CREATE TRIGGER trg_refresh_doctor_rating
    AFTER INSERT OR UPDATE OR DELETE ON doctor_reviews
    FOR EACH ROW EXECUTE FUNCTION refresh_doctor_rating();

-- ------------------------------------------------------------
-- 7. NEXT AVAILABLE SLOT PER DOCTOR  (for "Next: Today 4:30 PM")
-- ------------------------------------------------------------
DROP VIEW IF EXISTS doctor_next_slot;
CREATE VIEW doctor_next_slot WITH (security_invoker = false) AS
SELECT DISTINCT ON (ds.doctor_id)
       ds.doctor_id,
       ds.schedule_date,
       GREATEST(ds.start_time,
                CASE WHEN ds.schedule_date = (NOW() AT TIME ZONE 'Asia/Kolkata')::DATE
                     THEN (NOW() AT TIME ZONE 'Asia/Kolkata')::TIME ELSE ds.start_time END) AS slot_time,
       (ds.schedule_date = (NOW() AT TIME ZONE 'Asia/Kolkata')::DATE) AS is_today
FROM doctor_schedules ds
WHERE ds.status = 'available'
  AND (ds.schedule_date > (NOW() AT TIME ZONE 'Asia/Kolkata')::DATE
       OR (ds.schedule_date = (NOW() AT TIME ZONE 'Asia/Kolkata')::DATE
           AND ds.end_time > (NOW() AT TIME ZONE 'Asia/Kolkata')::TIME))
ORDER BY ds.doctor_id, ds.schedule_date, ds.start_time;

GRANT SELECT ON doctor_next_slot TO anon, authenticated;

-- ------------------------------------------------------------
-- 8. REALTIME for notifications (live badge updates)
-- ------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables
                   WHERE pubname = 'supabase_realtime' AND tablename = 'notifications') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE notifications;
    END IF;
END $$;

-- ------------------------------------------------------------
-- 9. PROFILE HEALTH DETAILS (so blood group / gender / emergency contact
--    survive reinstalls and follow the user to a new phone)
-- ------------------------------------------------------------
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS blood_group        TEXT;
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS gender             TEXT;
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS emergency_contact  TEXT;

-- Done ✅
