-- ============================================================
-- MediQ — 08_staff_notifications.sql
-- Run ONCE in Supabase → SQL Editor (after 05, 06, 07). Safe to re-run.
--
-- Staff alerts (shown in the bell on the doctor / receptionist dashboards):
--   * new_booking        -> the doctor + the receptionists of that hospital
--   * booking_cancelled  -> the doctor + the receptionists of that hospital
--   * new_review         -> receptionists (hospital review) / the doctor (doctor review)
-- ============================================================

-- 1. Bookings & cancellations -------------------------------------------------
CREATE OR REPLACE FUNCTION notify_staff_booking_events()
RETURNS TRIGGER AS $$
DECLARE
    v_patient TEXT;
    v_doctor  TEXT;
    v_doc_profile UUID;
    v_when    TEXT;
    v_type    TEXT;
    v_title   TEXT;
    v_msg     TEXT;
BEGIN
    IF TG_OP = 'INSERT' THEN
        v_type := 'new_booking';
    ELSIF TG_OP = 'UPDATE' AND NEW.status = 'cancelled' AND OLD.status IS DISTINCT FROM 'cancelled' THEN
        v_type := 'booking_cancelled';
    ELSE
        RETURN NEW;
    END IF;

    SELECT full_name INTO v_patient FROM profiles WHERE id = NEW.patient_id;
    SELECT name, profile_id INTO v_doctor, v_doc_profile FROM doctors WHERE id = NEW.doctor_id;
    v_when := to_char(NEW.appointment_date, 'DD Mon') || ' at ' || to_char(NEW.appointment_time, 'HH12:MI AM');

    IF v_type = 'new_booking' THEN
        v_title := 'New booking 📅';
        v_msg   := COALESCE(v_patient, 'A patient') || ' booked ' || COALESCE(v_doctor, 'a doctor') || ' for ' || v_when || '.';
    ELSE
        v_title := 'Booking cancelled';
        v_msg   := COALESCE(v_patient, 'A patient') || ' cancelled the visit with ' || COALESCE(v_doctor, 'a doctor') || ' on ' || v_when || '.';
    END IF;

    -- the doctor
    IF v_doc_profile IS NOT NULL THEN
        INSERT INTO notifications (user_id, type, title, message, data)
        VALUES (v_doc_profile, v_type, v_title, v_msg, jsonb_build_object('appointment_id', NEW.id));
    END IF;

    -- every receptionist of that hospital (never the doctor twice)
    INSERT INTO notifications (user_id, type, title, message, data)
    SELECT p.id, v_type, v_title, v_msg, jsonb_build_object('appointment_id', NEW.id)
    FROM profiles p
    WHERE p.role = 'receptionist' AND p.hospital_id = NEW.hospital_id
      AND p.id IS DISTINCT FROM v_doc_profile;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

DROP TRIGGER IF EXISTS trg_staff_notify_booking_insert ON appointments;
CREATE TRIGGER trg_staff_notify_booking_insert
    AFTER INSERT ON appointments FOR EACH ROW EXECUTE FUNCTION notify_staff_booking_events();

DROP TRIGGER IF EXISTS trg_staff_notify_booking_update ON appointments;
CREATE TRIGGER trg_staff_notify_booking_update
    AFTER UPDATE OF status ON appointments FOR EACH ROW EXECUTE FUNCTION notify_staff_booking_events();

-- 2. Reviews ------------------------------------------------------------------
CREATE OR REPLACE FUNCTION notify_staff_hospital_review()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO notifications (user_id, type, title, message, data)
    SELECT p.id, 'new_review', 'New hospital review ⭐',
           COALESCE(NEW.patient_name, 'A patient') || ' rated your hospital ' || NEW.rating || '/5. Open the dashboard to reply.',
           jsonb_build_object('review_id', NEW.id, 'hospital_id', NEW.hospital_id)
    FROM profiles p
    WHERE p.role = 'receptionist' AND p.hospital_id = NEW.hospital_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

DROP TRIGGER IF EXISTS trg_staff_notify_hospital_review ON hospital_reviews;
CREATE TRIGGER trg_staff_notify_hospital_review
    AFTER INSERT ON hospital_reviews FOR EACH ROW EXECUTE FUNCTION notify_staff_hospital_review();

CREATE OR REPLACE FUNCTION notify_staff_doctor_review()
RETURNS TRIGGER AS $$
DECLARE v_profile UUID;
BEGIN
    SELECT profile_id INTO v_profile FROM doctors WHERE id = NEW.doctor_id;
    IF v_profile IS NOT NULL THEN
        INSERT INTO notifications (user_id, type, title, message, data)
        VALUES (v_profile, 'new_review', 'New patient review ⭐',
                COALESCE(NEW.patient_name, 'A patient') || ' rated you ' || NEW.rating || '/5.',
                jsonb_build_object('review_id', NEW.id, 'doctor_id', NEW.doctor_id));
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

DROP TRIGGER IF EXISTS trg_staff_notify_doctor_review ON doctor_reviews;
CREATE TRIGGER trg_staff_notify_doctor_review
    AFTER INSERT ON doctor_reviews FOR EACH ROW EXECUTE FUNCTION notify_staff_doctor_review();

-- Done ✅
