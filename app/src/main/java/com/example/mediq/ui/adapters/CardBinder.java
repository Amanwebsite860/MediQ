package com.example.mediq.ui.adapters;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.mediq.R;
import com.example.mediq.data.model.Doctor;
import com.example.mediq.data.model.Hospital;
import com.example.mediq.data.repo.FavoritesRepository;
import com.example.mediq.util.ToastUtils;

/** Shared binding for the doctor / hospital cards (Home, lists, favorites). */
public final class CardBinder {
    private CardBinder() { }

    public static void setHeart(ImageView iv, boolean fav) {
        Context c = iv.getContext();
        iv.setImageResource(fav ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
        iv.setColorFilter(ContextCompat.getColor(c, fav ? R.color.colorError : R.color.colorTextPrimary));
    }

    public static void bindDoctor(View card, Doctor d, boolean showHospital) {
        Context ctx = card.getContext();
        ((TextView) card.findViewById(R.id.tvDoctorName)).setText(d.getName());
        ((TextView) card.findViewById(R.id.tvDoctorSpecialty)).setText(d.getSpecialty());
        ((TextView) card.findViewById(R.id.tvDoctorRating)).setText(d.getRatingAndCountText());

        TextView tvExp = card.findViewById(R.id.tvDoctorExperience);
        if (tvExp != null) tvExp.setText(d.getExperienceYears() + " yrs exp");

        TextView tvFee = card.findViewById(R.id.tvDoctorFee);
        if (tvFee != null) tvFee.setText(d.getFormattedFee() + " consultation");

        TextView tvHosp = card.findViewById(R.id.tvDoctorHospital);
        if (tvHosp != null) {
            boolean show = showHospital && d.getHospitalName() != null && !d.getHospitalName().isEmpty();
            tvHosp.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) tvHosp.setText(d.getHospitalName());
        }

        TextView badge = card.findViewById(R.id.tvAvailableBadge);
        if (badge != null) {
            if (d.getNextSlotText() != null) {
                badge.setVisibility(View.VISIBLE);
                badge.setText(d.getNextSlotText());
            } else if (d.isAvailableToday()) {
                badge.setVisibility(View.VISIBLE);
                badge.setText(R.string.label_available_today);
            } else {
                badge.setVisibility(View.GONE);
            }
        }

        ImageView iv = card.findViewById(R.id.ivDoctorAvatar);
        int fallback = d.getAvatarResId() != 0 ? d.getAvatarResId() : R.drawable.ic_doctor_male_avatar;
        if (d.getAvatarUrl() != null && !d.getAvatarUrl().isEmpty()) {
            Glide.with(ctx).load(d.getAvatarUrl()).placeholder(fallback).error(fallback).into(iv);
        } else {
            iv.setImageResource(fallback);
        }

        ImageView heart = card.findViewById(R.id.ivFav);
        if (heart != null) {
            heart.setVisibility(View.VISIBLE);
            setHeart(heart, FavoritesRepository.get().isDoctorFav(d.getId()));
            heart.setOnClickListener(v -> FavoritesRepository.get().toggleDoctor(ctx, d.getId(), (now, err) -> {
                setHeart(heart, now);
                if (err != null) ToastUtils.show(ctx, "Couldn't update saved doctors");
            }));
        }
    }

    /** Open / closed badge using the hospital's opening hours (red when closed). */
    public static void bindHospitalStatus(View card, Hospital h) {
        TextView tv = card.findViewById(R.id.tvHospitalStatus);
        if (tv == null) return;
        Boolean open = h.isOpenNow();
        tv.setText(h.getOpenStatusText());
        boolean closed = open != null && !open;
        tv.setBackgroundResource(closed ? R.drawable.bg_badge_closed : R.drawable.bg_badge_success);
        tv.setTextColor(ContextCompat.getColor(card.getContext(), closed ? R.color.colorSos : R.color.colorSuccess));
    }

    public static void bindHospitalHeart(View card, Hospital h) {
        bindHospitalStatus(card, h);
        ImageView heart = card.findViewById(R.id.ivFav);
        if (heart == null) return;
        Context ctx = card.getContext();
        heart.setVisibility(View.VISIBLE);
        setHeart(heart, FavoritesRepository.get().isHospitalFav(h.getId()));
        heart.setOnClickListener(v -> FavoritesRepository.get().toggleHospital(ctx, h.getId(), (now, err) -> {
            setHeart(heart, now);
            if (err != null) ToastUtils.show(ctx, "Couldn't update saved hospitals");
        }));
    }
}
