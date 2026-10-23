# Upgrading your current MediQ to v2

1. BACKUP: copy your current project folder (or `git commit`).
2. DATABASE (Supabase → SQL Editor), run in this order, each once (all are safe to re-run):
   05_feature_upgrade.sql -> 06_avatars_bucket.sql -> 07_staff_hospitals_admin.sql -> 08_staff_notifications.sql
3. FIRST ADMIN: create a user in Supabase → Authentication → Users (auto-confirm), then run
   UPDATE profiles SET role = 'admin' WHERE email = 'you@example.com';
4. APP: unzip MediQ_v2.zip into a NEW folder. Copy your old `local.properties` into it (it holds your keys and
   SDK path; the zip does not include it). If you changed code after sending me the project, compare those files.
5. Android Studio: open the new folder -> File > Sync Project with Gradle Files -> Build > Clean Project ->
   Run. No Gradle changes were needed (all libraries were already in your project).
6. ADMIN PANEL: open admin/index.html in a browser, paste Project URL + anon key, sign in as the admin.
