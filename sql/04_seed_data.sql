-- ============================================================
-- MediQ — Sample Seed Data for Development & Testing
-- 04_seed_data.sql
-- ============================================================
-- NOTE: Run this AFTER 01_tables.sql.
-- Profile IDs here are placeholders; replace with actual auth.users UUIDs
-- when connecting to a real Supabase project.
-- ============================================================

-- ============================================================
-- HOSPITALS (matching the mockup screens)
-- ============================================================
INSERT INTO hospitals (id, name, type, address, city, state, latitude, longitude, phone, opening_hours, facilities, image_url, rating, review_count) VALUES

('a1000000-0000-0000-0000-000000000001', 'City Care Hospital', 'Multi-Speciality',
 'Bailey Road', 'Patna', 'Bihar',
 25.6093, 85.1376, '+91 98765 43210', '8:00 AM - 8:00 PM',
 ARRAY['Pharmacy', 'Ambulance', 'Parking', 'Lab'],
 NULL, 4.5, 320),

('a1000000-0000-0000-0000-000000000002', 'MediLife Hospital', 'Multi-Speciality',
 'Boring Road', 'Patna', 'Bihar',
 25.6100, 85.1450, '+91 98765 43211', '7:00 AM - 9:00 PM',
 ARRAY['Pharmacy', 'Ambulance', 'Parking', 'Lab', 'ICU'],
 NULL, 4.3, 210),

('a1000000-0000-0000-0000-000000000003', 'Sunrise Hospital', 'General Hospital',
 'Kankarbagh', 'Patna', 'Bihar',
 25.5980, 85.1280, '+91 98765 43212', '8:00 AM - 7:00 PM',
 ARRAY['Pharmacy', 'Parking', 'Lab'],
 NULL, 4.2, 180),

('a1000000-0000-0000-0000-000000000004', 'Global Hospital', 'Super-Speciality',
 'Patliputra Colony', 'Patna', 'Bihar',
 25.6200, 85.1500, '+91 98765 43213', '6:00 AM - 8:00 PM',
 ARRAY['Pharmacy', 'Ambulance', 'Parking', 'Lab', 'ICU', 'Blood Bank'],
 NULL, 4.6, 450);

-- ============================================================
-- DOCTORS (matching the mockup screens)
-- ============================================================
INSERT INTO doctors (id, hospital_id, name, specialty, bio, experience_years, consultation_fee, consultation_duration_min, rating, review_count) VALUES

-- City Care Hospital doctors
('d1000000-0000-0000-0000-000000000001',
 'a1000000-0000-0000-0000-000000000001',
 'Dr. Rajesh Kumar', 'General Physician',
 'Dr. Rajesh Kumar is a well experienced general physician with expertise in treating fever, cold, diabetes, hypertension and other common illnesses.',
 8, 300.00, 15, 4.8, 320),

('d1000000-0000-0000-0000-000000000002',
 'a1000000-0000-0000-0000-000000000001',
 'Dr. Priya Sharma', 'Dermatologist',
 'Dr. Priya Sharma is a renowned dermatologist specializing in skin disorders, cosmetic treatments and hair care.',
 6, 500.00, 15, 4.6, 210),

('d1000000-0000-0000-0000-000000000003',
 'a1000000-0000-0000-0000-000000000001',
 'Dr. Amit Singh', 'Orthopedic',
 'Dr. Amit Singh is a skilled orthopedic surgeon with extensive experience in joint replacements and sports injuries.',
 10, 400.00, 20, 4.7, 180),

('d1000000-0000-0000-0000-000000000004',
 'a1000000-0000-0000-0000-000000000001',
 'Dr. Neha Verma', 'Gynecologist',
 'Dr. Neha Verma is a compassionate gynecologist providing comprehensive women''s health care.',
 7, 350.00, 15, 4.5, 200),

-- MediLife Hospital doctors
('d1000000-0000-0000-0000-000000000005',
 'a1000000-0000-0000-0000-000000000002',
 'Dr. Vikram Patel', 'Cardiologist',
 'Dr. Vikram Patel is a leading cardiologist with expertise in interventional cardiology.',
 12, 600.00, 20, 4.9, 350),

('d1000000-0000-0000-0000-000000000006',
 'a1000000-0000-0000-0000-000000000002',
 'Dr. Sunita Gupta', 'Pediatrician',
 'Dr. Sunita Gupta is a pediatrician with 9 years of experience in child healthcare.',
 9, 350.00, 15, 4.4, 190),

-- Sunrise Hospital
('d1000000-0000-0000-0000-000000000007',
 'a1000000-0000-0000-0000-000000000003',
 'Dr. Arjun Mehta', 'ENT Specialist',
 'Dr. Arjun Mehta specializes in ear, nose and throat disorders with modern treatment approaches.',
 5, 300.00, 15, 4.3, 150),

-- Global Hospital
('d1000000-0000-0000-0000-000000000008',
 'a1000000-0000-0000-0000-000000000004',
 'Dr. Kavita Joshi', 'Neurologist',
 'Dr. Kavita Joshi is a neurologist specializing in migraine, epilepsy and neurodegenerative disorders.',
 11, 700.00, 20, 4.7, 280)
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- DOCTOR REVIEWS (Rahul Sharma & Ananya Patel)
-- ============================================================
INSERT INTO doctor_reviews (doctor_id, patient_name, rating, comment) VALUES
('d1000000-0000-0000-0000-000000000001', 'Rahul Sharma', 5.0, 'Very good doctor, listened patiently to all my concerns and gave effective treatment.'),
('d1000000-0000-0000-0000-000000000001', 'Ananya Patel', 4.5, 'Professional and knowledgeable. Clinic was well maintained and appointment was on time.')
ON CONFLICT DO NOTHING;

-- ============================================================
-- DOCTOR SCHEDULES (next 7 days for each doctor)
-- ============================================================
DO $$
DECLARE
    doc_id UUID;
    d DATE;
    v_start DATE;
BEGIN
    v_start := (NOW() AT TIME ZONE 'Asia/Kolkata')::DATE;
    FOR doc_id IN SELECT id FROM doctors LOOP
        FOR d IN SELECT generate_series(v_start, v_start + INTERVAL '6 days', '1 day')::DATE LOOP
            -- Morning block
            INSERT INTO doctor_schedules (doctor_id, schedule_date, start_time, end_time, slot_duration_minutes)
            VALUES (doc_id, d, '09:00', '13:00', 30)
            ON CONFLICT DO NOTHING;
            -- Afternoon block
            INSERT INTO doctor_schedules (doctor_id, schedule_date, start_time, end_time, slot_duration_minutes)
            VALUES (doc_id, d, '14:00', '18:00', 30)
            ON CONFLICT DO NOTHING;
        END LOOP;
    END LOOP;
END $$;
