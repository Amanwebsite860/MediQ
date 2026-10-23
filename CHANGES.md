# MediQ v2 — Swiggy-style UI + new features

## Step 1 — Database (do this first)
Supabase Dashboard → SQL Editor → paste `sql/05_feature_upgrade.sql` → Run. Safe to re-run.
The app still opens without it, but specialties, banners, SOS hospitals, "next slot" and notifications
need it.

## Step 2 — App
Copy the project over yours (your `local.properties` is untouched), Sync Gradle, Run.

## What changed
**UI (Swiggy-style)**
- Home: location-style top bar, floating search, "Who do you want to see?" specialty circles,
  swipeable promo banners, SOS card, filter chips, big image hospital cards, richer doctor cards
  (green rating pill, fee, experience, next free slot, heart), pull-to-refresh.
- Real distance on hospital cards when location permission is already granted (no fake km values).
- Doctor list: sort + filter chips (rating, fee, experience, available today).
- Active-appointment strip above the bottom bar with one-tap "Track" (like Swiggy order tracking).

**New features**
- Notifications inbox with unread badge, mark-all-read (auto-created on booking, cancellation,
  "you're next", "your turn", and review prompt after a completed visit).
- Saved doctors & hospitals (heart on every card) + Saved screen.
- Emergency screen: dial 108 / 112 / your emergency contact, 24x7 hospitals with Call + Directions.
- Doctor ratings now update automatically when a review is added.
- Banners and specialties are editable from the Supabase Table Editor (`promo_banners`, `specialties`).

**Bug fixed**
- Favorites used column `patient_id`, but the table column is `user_id`, so saving always failed.
  Fixed in DoctorProfileActivity and HospitalDetailsActivity.
- Home showed "City Care Hospital" for every doctor; doctors now load their real hospital name.

## Round 2 — other screens
- **Notifications** (bell on Home): grouped Today / Earlier, unread dot + bold title, tap opens Appointments,
  swipe to delete with Undo, pull to refresh, Mark all read.
- **My Appointments**: new card (status strip with friendly date "Today/Tomorrow", formatted time, status chip,
  token pill), soonest-first order, pull to refresh, confirm dialog before cancelling, helpful empty states
  with a "Find a doctor" button, missed visits count as Past.
- **Hospital details**: Call / Directions / Share quick actions.
- **Booking confirmation**: "Add to calendar" button.

## Round 3
**Doctor profile**: Availability tab now shows real upcoming slots from `doctor_schedules` (the old fixed
"Mon-Fri 9-5 / Room 204" text was made up); reviews summary + "Write a review" (rating + comment; the database
only allows it after a completed visit, one review per doctor); qualification shown under the specialty; heart
now stays in sync with the Saved screen.
**Live queue**: no longer assumes token "A-01" when none is passed; the server estimate no longer overwrites
"In consultation"; "You're next" / "It's your turn" alerts; the location button opens directions to the hospital.
**Hospital details**: Services tab lists facilities + opening hours; Reviews tab shows recent reviews for doctors
at that hospital; fake default phone number removed.
**Map**: hospitals without coordinates are skipped (they used to pile up at a default spot); nearest hospital is
selected first; works with approximate location; no duplicate initial load; the top button now "searches this area".
**Profile**: photo is copied into app storage (it used to vanish after restarting the app); blood group / gender /
emergency contact now save to your account (new columns, in the SQL file, section 9) and are validated;
recent-appointment cards show the real token, readable dates, and no dead Track/Cancel buttons.

Re-run `sql/05_feature_upgrade.sql` (safe to repeat) to add the new profile columns.

## Round 4 — avatars bucket
Run `sql/06_avatars_bucket.sql` once in the Supabase SQL Editor. It creates the public `avatars` bucket
(2 MB, jpeg/png/webp) with policies so each user can only write to `avatars/<their-user-id>/`.
The Profile photo is now resized to 512px, uploaded there, and its URL saved to `profiles.avatar_url`,
so it follows the user to any phone.

## Round 5 — staff roles, hospital onboarding, admin panel
Run `sql/07_staff_hospitals_admin.sql` (after 05 and 06). Make yourself the first admin:
`UPDATE profiles SET role = 'admin' WHERE email = 'you@example.com';`
- Sign-up has a Patient / Doctor / Receptionist dropdown. The choice is only a *request*; the real role changes
  only when an admin approves.
- Login is unchanged for users: after sign-in the app routes by role (patient app / receptionist home /
  doctor home / waiting-for-approval / first-time hospital onboarding).
- Admin panel: open `admin/index.html` in a browser (or host it anywhere), paste Supabase URL + anon key once,
  sign in with the admin account.

## Round 6 — staff alerts + phone notifications
- `sql/08_staff_notifications.sql` (run after 07): doctors/receptionists get a notification for new bookings,
  cancellations and new reviews. A 🔔 with unread count is on both staff dashboards.
- Phone notifications while the app is closed: `QueueWatcherService` runs on a day the patient has an
  appointment, checks every 15 s and shows "You're next", "It's your turn", "Queue moved"... as normal phone
  notifications. It asks for notification permission once, stops itself when the visit is over or on logout.

## Round 7 — Map
- New **Map** button in the bottom bar (between Appointments and Profile).
- Map icons redrawn in code: blue pin = MediQ hospital, red pin = MediQ hospital open 24x7, amber dot = clinic,
  green "P" dot = pharmacy; the tapped marker grows. New "24x7 Emergency" filter chip.
- Bottom card now shows the hospital photo, rating, Call and Directions buttons, distance.

## Round 8 — search filters, hospital tab, map upgrades
- Search: a **Filters** button opens a sheet (Show, Sort, Specialty, Minimum rating, Fee, Available today,
  24x7 emergency). Only active filters show as removable chips. The filter icon on Home opens it directly.
  Real distances (no more made-up km), real hospital names on doctors, multi-word search.
- Hospital page: the blue tab indicator returns to Overview after coming back from Doctors.
- Map: search box (hospital / pharmacy by name, or an area), List view sorted by distance, Open / Closed shown
  from the hospital's opening hours (also on hospital cards across the app).
- Hospital form: "Pick on map" - drag the map under a pin to set the exact hospital location.
