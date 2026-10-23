-- ============================================================
-- MediQ — Complete Teardown / Reset Script (Clean Slate)
-- 99_teardown_clean_slate.sql
--
-- Run this in your Supabase SQL Editor if you want to completely
-- wipe all MediQ tables, policies, functions, triggers, views,
-- and enums to start 100% fresh.
-- ============================================================

-- 1. Drop auth triggers
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;

-- 2. Drop views
DROP VIEW IF EXISTS live_queue_status CASCADE;

-- 3. Drop all tables (CASCADE automatically drops all RLS policies, foreign keys, and indexes)
DROP TABLE IF EXISTS doctor_reviews CASCADE;
DROP TABLE IF EXISTS user_favorites CASCADE;
DROP TABLE IF EXISTS queue_entries CASCADE;
DROP TABLE IF EXISTS queues CASCADE;
DROP TABLE IF EXISTS appointments CASCADE;
DROP TABLE IF EXISTS doctor_schedules CASCADE;
DROP TABLE IF EXISTS doctors CASCADE;
DROP TABLE IF EXISTS hospitals CASCADE;
DROP TABLE IF EXISTS audit_logs CASCADE;
DROP TABLE IF EXISTS notifications CASCADE;
DROP TABLE IF EXISTS otp_verifications CASCADE;
DROP TABLE IF EXISTS profiles CASCADE;

-- 4. Drop all custom functions
DROP FUNCTION IF EXISTS book_appointment(UUID, UUID, UUID, UUID, DATE, TIME);
DROP FUNCTION IF EXISTS cancel_appointment(UUID, TEXT);
DROP FUNCTION IF EXISTS cancel_appointment(UUID, UUID, TEXT);
DROP FUNCTION IF EXISTS register_walkin(UUID, UUID, TEXT, TEXT, TEXT, TIME);
DROP FUNCTION IF EXISTS get_estimated_wait(UUID, INT);
DROP FUNCTION IF EXISTS record_otp(TEXT, TEXT, TEXT, INTEGER);
DROP FUNCTION IF EXISTS verify_otp(TEXT, TEXT, TEXT);
DROP FUNCTION IF EXISTS get_my_role();
DROP FUNCTION IF EXISTS handle_new_user();
DROP FUNCTION IF EXISTS update_doctor_rating_stats();
DROP FUNCTION IF EXISTS update_updated_at();

-- 5. Drop custom ENUM types
DROP TYPE IF EXISTS user_role CASCADE;
DROP TYPE IF EXISTS appointment_status CASCADE;
DROP TYPE IF EXISTS queue_status CASCADE;
DROP TYPE IF EXISTS schedule_status CASCADE;
