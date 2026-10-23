-- ============================================================
-- MediQ — Database Functions & Stored Procedures (Production Hardened)
-- 03_functions.sql
-- ============================================================

-- Cleanup old signatures
DROP FUNCTION IF EXISTS cancel_appointment(UUID, UUID, TEXT);
DROP FUNCTION IF EXISTS record_otp(TEXT, TEXT, TEXT, INTEGER);
DROP FUNCTION IF EXISTS verify_otp(TEXT, TEXT, TEXT);

-- ------------------------------------------------------------------------------
-- 1. AUTO-CREATE / SYNC PROFILE ON SIGNUP
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profiles (id, full_name, phone, email, role)
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data ->> 'full_name', split_part(COALESCE(NEW.email, 'User'), '@', 1)),
        NEW.raw_user_meta_data ->> 'phone',
        NEW.email,
        'patient'
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

-- ------------------------------------------------------------------------------
-- 2. BOOK APPOINTMENT (With rigorous validations & row lock)
-- ------------------------------------------------------------------------------
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

-- ------------------------------------------------------------------------------
-- 3. WALK-IN REGISTRATION (Staff Only)
-- ------------------------------------------------------------------------------
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

-- ------------------------------------------------------------------------------
-- 4. ADVANCE QUEUE (Staff only)
-- ------------------------------------------------------------------------------
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

-- ------------------------------------------------------------------------------
-- 5. CANCEL APPOINTMENT
-- ------------------------------------------------------------------------------
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

-- ------------------------------------------------------------------------------
-- 6. ESTIMATED WAIT
-- ------------------------------------------------------------------------------
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

-- ------------------------------------------------------------------------------
-- 7. FUTURE SCHEDULE GENERATOR
-- ------------------------------------------------------------------------------
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
