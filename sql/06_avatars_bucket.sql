-- ============================================================
-- MediQ — 06_avatars_bucket.sql
-- Creates the public "avatars" Storage bucket for profile photos.
-- Run ONCE in Supabase → SQL Editor. Safe to re-run.
--
-- Rules:
--   * Anyone can VIEW avatars (public bucket, needed so the image URL loads in the app)
--   * A signed-in user can only upload / replace / delete files inside their OWN
--     folder:  avatars/<their-user-id>/...
--   * Max 2 MB per file, images only (jpeg / png / webp)
-- ============================================================

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('avatars', 'avatars', TRUE, 2097152, ARRAY['image/jpeg', 'image/png', 'image/webp'])
ON CONFLICT (id) DO UPDATE
    SET public             = TRUE,
        file_size_limit    = 2097152,
        allowed_mime_types = ARRAY['image/jpeg', 'image/png', 'image/webp'];

DROP POLICY IF EXISTS "avatars_public_read"   ON storage.objects;
DROP POLICY IF EXISTS "avatars_insert_own"    ON storage.objects;
DROP POLICY IF EXISTS "avatars_update_own"    ON storage.objects;
DROP POLICY IF EXISTS "avatars_delete_own"    ON storage.objects;

CREATE POLICY "avatars_public_read" ON storage.objects
    FOR SELECT USING (bucket_id = 'avatars');

CREATE POLICY "avatars_insert_own" ON storage.objects
    FOR INSERT TO authenticated
    WITH CHECK (bucket_id = 'avatars' AND (storage.foldername(name))[1] = auth.uid()::text);

CREATE POLICY "avatars_update_own" ON storage.objects
    FOR UPDATE TO authenticated
    USING      (bucket_id = 'avatars' AND (storage.foldername(name))[1] = auth.uid()::text)
    WITH CHECK (bucket_id = 'avatars' AND (storage.foldername(name))[1] = auth.uid()::text);

CREATE POLICY "avatars_delete_own" ON storage.objects
    FOR DELETE TO authenticated
    USING (bucket_id = 'avatars' AND (storage.foldername(name))[1] = auth.uid()::text);

-- Done ✅
