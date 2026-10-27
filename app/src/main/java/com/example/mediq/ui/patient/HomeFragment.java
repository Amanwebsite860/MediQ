package com.example.mediq.ui.patient;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.mediq.MainActivity;
import com.example.mediq.R;
import com.example.mediq.data.model.Doctor;
import com.example.mediq.data.model.Hospital;
import com.example.mediq.data.remote.SessionManager;
import com.example.mediq.data.remote.SupabaseClient;
import com.example.mediq.data.repo.DoctorRepository;
import com.example.mediq.data.repo.FavoritesRepository;
import com.example.mediq.ui.adapters.CardBinder;
import com.example.mediq.ui.adapters.HomeBannerAdapter;
import com.example.mediq.ui.appointments.BookAppointmentActivity;
import com.example.mediq.util.UiUtils;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Swiggy-style home:
 *  top bar (greeting, saved, notifications) → search → specialty circles → promo banners
 *  → SOS card → filter chips → hospital cards → doctor cards.
 */
public class HomeFragment extends Fragment {

    private static final long SLIDE_DELAY_MS = 4000L;

    private SwipeRefreshLayout swipe;
    private ViewPager2 vpBanner;
    private LinearLayout layoutSpecialties, layoutChips, layoutHospitals, layoutDoctors;
    private TextView tvEmpty, tvBadge;

    private final Handler sliderHandler = new Handler(Looper.getMainLooper());
    private Runnable sliderRunnable;
    private int bannerCount = 0;

    private final List<Doctor> allDoctors = new ArrayList<>();
    private final List<Hospital> hospitals = new ArrayList<>();
    private Location lastLocation;

    // Filter chip state
    private boolean fAvailToday, fTopRated, fBudget, fSenior;

    public static HomeFragment newInstance() { return new HomeFragment(); }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        swipe = view.findViewById(R.id.swipeHome);
        vpBanner = view.findViewById(R.id.vpHomeBanner);
        layoutSpecialties = view.findViewById(R.id.layoutSpecialties);
        layoutChips = view.findViewById(R.id.layoutHomeChips);
        layoutHospitals = view.findViewById(R.id.layoutHospitalsContainer);
        layoutDoctors = view.findViewById(R.id.layoutDoctorsContainer);
        tvEmpty = view.findViewById(R.id.tvHomeEmpty);
        tvBadge = view.findViewById(R.id.notification_badge);

        initHeader(view);
        initSearchAndShortcuts(view);
        buildChips();
        swipe.setOnRefreshListener(this::loadAll);
        loadAll();
    }

    // ------------------------------------------------------------------ header
    private void initHeader(View view) {
        TextView title = view.findViewById(R.id.tv_greeting_title);
        String name = SessionManager.getInstance(requireContext()).getUserName();
        if (name != null && !name.isEmpty() && !"Patient".equalsIgnoreCase(name)) {
            title.setText("Hi, " + name.split(" ")[0]);
        }
        view.findViewById(R.id.btn_location).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NearbyHospitalsActivity.class)));
        view.findViewById(R.id.btn_notification).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NotificationsActivity.class)));
        view.findViewById(R.id.btn_favorites).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), FavoritesActivity.class)));
    }

    private void initSearchAndShortcuts(View view) {
        View.OnClickListener openSearch = v -> startActivity(new Intent(requireContext(), SearchActivity.class));
        view.findViewById(R.id.card_search).setOnClickListener(openSearch);
        View inner = view.findViewById(R.id.card_search_inner);
        if (inner != null) inner.setOnClickListener(openSearch);
        View placeholder = view.findViewById(R.id.tv_search_placeholder);
        if (placeholder != null) placeholder.setOnClickListener(openSearch);
        View icon = view.findViewById(R.id.iv_search_icon);
        if (icon != null) icon.setOnClickListener(openSearch);

        view.findViewById(R.id.btn_filter).setOnClickListener(v -> startActivity(
                new Intent(requireContext(), SearchActivity.class).putExtra(SearchActivity.EXTRA_OPEN_FILTERS, true)));
        view.findViewById(R.id.cardSos).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), EmergencyActivity.class)));
        view.findViewById(R.id.btn_view_all_hospitals).setOnClickListener(v -> openSearch("hospital", "nearby"));
        view.findViewById(R.id.btn_view_all_doctors).setOnClickListener(v -> openSearch("doctor", null));
    }

    private void openSearch(String type, String sort) {
        Intent i = new Intent(requireContext(), SearchActivity.class);
        if (type != null) i.putExtra(SearchActivity.EXTRA_FILTER_TYPE, type);
        if (sort != null) i.putExtra(SearchActivity.EXTRA_SORT, sort);
        startActivity(i);
    }

    // ------------------------------------------------------------------ loading
    private void loadAll() {
        loadLocationThenHospitals();
        loadSpecialties();
        loadBanners();
        loadDoctors();
        FavoritesRepository.get().load(requireContext(), this::rebindFavorites);
        loadUnreadCount();
    }

    private void finishRefresh() {
        if (swipe != null) swipe.setRefreshing(false);
    }

    private void loadLocationThenHospitals() {
        boolean granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!granted) { loadHospitals(); return; }
        try {
            LocationServices.getFusedLocationProviderClient(requireContext()).getLastLocation()
                    .addOnSuccessListener(loc -> { lastLocation = loc; loadHospitals(); })
                    .addOnFailureListener(e -> loadHospitals());
        } catch (SecurityException e) {
            loadHospitals();
        }
    }

    private void loadHospitals() {
        SupabaseClient.getInstance().get("hospitals?select=*&active=eq.true&order=rating.desc&limit=20",
                new SupabaseClient.Callback() {
                    @Override public void onSuccess(String json) {
                        if (!isAdded()) return;
                        try {
                            JSONArray arr = new JSONArray(json);
                            hospitals.clear();
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);
                                double lat = o.optDouble("latitude", 0), lon = o.optDouble("longitude", 0);
                                String dist = "";
                                if (lastLocation != null && lat != 0 && lon != 0) {
                                    dist = String.format(Locale.US, "%.1f km",
                                            UiUtils.distanceKm(lastLocation.getLatitude(), lastLocation.getLongitude(), lat, lon));
                                }
                                Hospital h = new Hospital(
                                        SupabaseClient.optStringSafe(o, "id"),
                                        SupabaseClient.optStringSafe(o, "name"),
                                        SupabaseClient.optStringSafe(o, "type"),
                                        SupabaseClient.optStringSafe(o, "address"),
                                        SupabaseClient.optStringSafe(o, "city"),
                                        dist,
                                        SupabaseClient.optStringSafe(o, "phone"),
                                        SupabaseClient.optStringSafe(o, "opening_hours"),
                                        null, R.drawable.ic_hospital_building,
                                        o.optDouble("rating", 0), o.optInt("review_count", 0), true, lat, lon);
                                String cover = SupabaseClient.optStringSafe(o, "image_url");
                                if (cover == null || cover.trim().isEmpty()) {
                                    org.json.JSONArray imgs = o.optJSONArray("image_urls");
                                    if (imgs != null && imgs.length() > 0) {
                                        cover = imgs.optString(0, null);
                                    }
                                }
                                h.setImageUrl(cover);
                                hospitals.add(h);
                            }
                            if (lastLocation != null) {
                                final Location L = lastLocation;
                                hospitals.sort((a, b) -> Double.compare(
                                        UiUtils.distanceKm(L.getLatitude(), L.getLongitude(), a.getLatitude(), a.getLongitude()),
                                        UiUtils.distanceKm(L.getLatitude(), L.getLongitude(), b.getLatitude(), b.getLongitude())));
                            }
                            renderHospitals();
                        } catch (Exception ignored) { }
                        finishRefresh();
                    }
                    @Override public void onError(Exception e) { finishRefresh(); }
                });
    }

    private void loadDoctors() {
        DoctorRepository.load("&limit=40", new DoctorRepository.Result() {
            @Override public void onResult(List<Doctor> doctors) {
                if (!isAdded()) return;
                allDoctors.clear();
                allDoctors.addAll(doctors);
                renderDoctors();
                finishRefresh();
            }
            @Override public void onError(Exception e) { finishRefresh(); }
        });
    }

    private void loadSpecialties() {
        SupabaseClient.getInstance().get("specialties?select=name,emoji,color_hex&active=eq.true&order=sort_order",
                new SupabaseClient.Callback() {
                    @Override public void onSuccess(String json) {
                        if (!isAdded()) return;
                        try {
                            JSONArray arr = new JSONArray(json);
                            List<String[]> rows = new ArrayList<>();
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);
                                rows.add(new String[]{o.optString("name"), o.optString("emoji", "🩺"),
                                        o.optString("color_hex", "#EAF2FF")});
                            }
                            renderSpecialties(rows.isEmpty() ? defaultSpecialties() : rows);
                        } catch (Exception e) { renderSpecialties(defaultSpecialties()); }
                    }
                    @Override public void onError(Exception e) { if (isAdded()) renderSpecialties(defaultSpecialties()); }
                });
    }

    private List<String[]> defaultSpecialties() {
        List<String[]> r = new ArrayList<>();
        r.add(new String[]{"General Physician", "🩺", "#E3F2FD"});
        r.add(new String[]{"Dermatologist", "🧴", "#FCE4EC"});
        r.add(new String[]{"Cardiologist", "❤️", "#FFEBEE"});
        r.add(new String[]{"Pediatrician", "🧒", "#FFF8E1"});
        r.add(new String[]{"Orthopedic", "🦴", "#EDE7F6"});
        r.add(new String[]{"Gynecologist", "🤰", "#F3E5F5"});
        return r;
    }

    private void loadBanners() {
        SupabaseClient.getInstance().get("promo_banners?select=*&order=sort_order",
                new SupabaseClient.Callback() {
                    @Override public void onSuccess(String json) {
                        if (!isAdded()) return;
                        List<HomeBannerAdapter.BannerItem> items = new ArrayList<>();
                        try {
                            JSONArray arr = new JSONArray(json);
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);
                                items.add(new HomeBannerAdapter.BannerItem(
                                        o.optString("title"), o.optString("subtitle"), o.optString("cta_text", "Explore"),
                                        o.optString("emoji", "🏥"), o.optString("color_start", "#1976D2"),
                                        o.optString("color_end", "#42A5F5"), o.optString("action", "doctors")));
                            }
                        } catch (Exception ignored) { }
                        showBanners(items.isEmpty() ? defaultBanners() : items);
                    }
                    @Override public void onError(Exception e) { if (isAdded()) showBanners(defaultBanners()); }
                });
    }

    private List<HomeBannerAdapter.BannerItem> defaultBanners() {
        List<HomeBannerAdapter.BannerItem> l = new ArrayList<>();
        l.add(new HomeBannerAdapter.BannerItem("Skip the waiting room", "Book a slot & track your token live",
                "Book now", "⏱️", "#1565C0", "#42A5F5", "book"));
        l.add(new HomeBannerAdapter.BannerItem("Doctors near you", "Top rated specialists, ready today",
                "Find doctors", "👩‍⚕️", "#00897B", "#4DB6AC", "doctors"));
        return l;
    }

    private void loadUnreadCount() {
        String uid = SessionManager.getInstance(requireContext()).getUserId();
        if (uid == null || uid.isEmpty()) return;
        SupabaseClient.getInstance().get("notifications?user_id=eq." + uid + "&read_at=is.null&select=id",
                new SupabaseClient.Callback() {
                    @Override public void onSuccess(String json) {
                        if (!isAdded() || tvBadge == null) return;
                        try {
                            int n = new JSONArray(json).length();
                            tvBadge.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
                            tvBadge.setText(n > 9 ? "9+" : String.valueOf(n));
                        } catch (Exception ignored) { }
                    }
                    @Override public void onError(Exception e) { }
                });
    }

    // ------------------------------------------------------------------ rendering
    private void renderSpecialties(List<String[]> rows) {
        if (layoutSpecialties == null || getContext() == null) return;
        layoutSpecialties.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(requireContext());
        for (String[] r : rows) {
            View v = inf.inflate(R.layout.item_specialty, layoutSpecialties, false);
            TextView emoji = v.findViewById(R.id.tvSpecialtyEmoji);
            emoji.setText(r[1]);
            emoji.setBackground(UiUtils.oval(r[2]));
            ((TextView) v.findViewById(R.id.tvSpecialtyName)).setText(r[0]);
            v.setOnClickListener(x -> {
                Intent i = new Intent(requireContext(), DoctorListActivity.class);
                i.putExtra(DoctorListActivity.EXTRA_SPECIALTY, r[0]);
                startActivity(i);
            });
            layoutSpecialties.addView(v);
        }
    }

    private void showBanners(List<HomeBannerAdapter.BannerItem> items) {
        if (vpBanner == null) return;
        bannerCount = items.size();
        vpBanner.setVisibility(View.VISIBLE);
        vpBanner.setAdapter(new HomeBannerAdapter(items, this::onBannerClick));
        sliderRunnable = () -> {
            if (isAdded() && vpBanner != null && bannerCount > 1) {
                vpBanner.setCurrentItem((vpBanner.getCurrentItem() + 1) % bannerCount, true);
                sliderHandler.postDelayed(sliderRunnable, SLIDE_DELAY_MS);
            }
        };
        sliderHandler.removeCallbacks(sliderRunnable);
        sliderHandler.postDelayed(sliderRunnable, SLIDE_DELAY_MS);
    }

    private void onBannerClick(HomeBannerAdapter.BannerItem b) {
        String a = b.action == null ? "" : b.action;
        switch (a) {
            case "book": startActivity(new Intent(requireContext(), BookAppointmentActivity.class)); break;
            case "hospitals": openSearch("hospital", "nearby"); break;
            case "map": startActivity(new Intent(requireContext(), MapViewActivity.class)); break;
            case "emergency": startActivity(new Intent(requireContext(), EmergencyActivity.class)); break;
            case "favorites": startActivity(new Intent(requireContext(), FavoritesActivity.class)); break;
            default: openSearch("doctor", null);
        }
    }

    private void buildChips() {
        layoutChips.removeAllViews();
        addChip("Available today", fAvailToday, () -> fAvailToday = !fAvailToday);
        addChip("Rating 4.5+", fTopRated, () -> fTopRated = !fTopRated);
        addChip("Under ₹500", fBudget, () -> fBudget = !fBudget);
        addChip("10+ yrs exp", fSenior, () -> fSenior = !fSenior);
    }

    private void addChip(String label, boolean selected, Runnable toggle) {
        TextView chip = (TextView) LayoutInflater.from(requireContext()).inflate(R.layout.item_chip, layoutChips, false);
        chip.setText(label);
        chip.setSelected(selected);
        chip.setTextColor(ContextCompat.getColor(requireContext(), selected ? R.color.colorPrimary : R.color.colorTextPrimary));
        chip.setOnClickListener(v -> { toggle.run(); buildChips(); renderDoctors(); });
        layoutChips.addView(chip);
    }

    private void renderHospitals() {
        if (layoutHospitals == null || getContext() == null) return;
        layoutHospitals.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(requireContext());
        int n = Math.min(3, hospitals.size());
        for (int i = 0; i < n; i++) {
            Hospital h = hospitals.get(i);
            View card = inf.inflate(R.layout.item_hospital, layoutHospitals, false);
            ((TextView) card.findViewById(R.id.tvHospitalName)).setText(h.getName());
            ((TextView) card.findViewById(R.id.tvHospitalType)).setText(h.getType());
            ((TextView) card.findViewById(R.id.tvHospitalLocation)).setText(h.getDistanceAndCity());
            ((TextView) card.findViewById(R.id.tvHospitalRating)).setText(h.getFormattedRating());
            ((TextView) card.findViewById(R.id.tvHospitalStatus)).setText(h.getOpenStatusText());
            ImageView iv = card.findViewById(R.id.ivHospitalImage);
            if (h.getImageUrl() != null && !h.getImageUrl().isEmpty()) {
                Glide.with(this).load(h.getImageUrl()).placeholder(R.drawable.ic_hospital_building).into(iv);
            } else {
                iv.setImageResource(R.drawable.ic_hospital_building);
            }
            CardBinder.bindHospitalHeart(card, h);
            card.setOnClickListener(v -> {
                Intent it = new Intent(requireContext(), HospitalDetailsActivity.class);
                it.putExtra("extra_hospital", h);
                it.putExtra("hospital_id", h.getId());
                startActivity(it);
            });
            layoutHospitals.addView(card);
        }
    }

    private boolean passes(Doctor d) {
        if (fAvailToday && !d.isAvailableToday()) return false;
        if (fTopRated && d.getRating() < 4.5) return false;
        if (fBudget && d.getConsultationFee() >= 500) return false;
        if (fSenior && d.getExperienceYears() < 10) return false;
        return true;
    }

    private void renderDoctors() {
        if (layoutDoctors == null || getContext() == null) return;
        layoutDoctors.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(requireContext());
        int shown = 0;
        for (Doctor d : allDoctors) {
            if (!passes(d)) continue;
            if (shown++ >= 6) break;
            View card = inf.inflate(R.layout.item_doctor, layoutDoctors, false);
            CardBinder.bindDoctor(card, d, true);
            card.setOnClickListener(v -> {
                Intent i = new Intent(requireContext(), DoctorProfileActivity.class);
                i.putExtra("extra_doctor", d);
                startActivity(i);
            });
            layoutDoctors.addView(card);
        }
        tvEmpty.setVisibility(shown == 0 && !allDoctors.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void rebindFavorites() {
        if (!isAdded()) return;
        renderHospitals();
        renderDoctors();
    }

    // ------------------------------------------------------------------ lifecycle
    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null && isAdded()) {
            loadUnreadCount();
            FavoritesRepository.get().load(requireContext(), this::rebindFavorites);
        }
        if (sliderRunnable != null && bannerCount > 1) {
            sliderHandler.removeCallbacks(sliderRunnable);
            sliderHandler.postDelayed(sliderRunnable, SLIDE_DELAY_MS);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (sliderRunnable != null) sliderHandler.removeCallbacks(sliderRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (sliderRunnable != null) sliderHandler.removeCallbacks(sliderRunnable);
    }
}
