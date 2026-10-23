package com.example.mediq.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediq.R;
import com.example.mediq.util.UiUtils;

import java.util.List;

/** Promo banner carousel. Items come from the promo_banners table. */
public class HomeBannerAdapter extends RecyclerView.Adapter<HomeBannerAdapter.VH> {

    public static class BannerItem {
        public final String title, subtitle, cta, emoji, colorStart, colorEnd, action;
        public BannerItem(String title, String subtitle, String cta, String emoji,
                          String colorStart, String colorEnd, String action) {
            this.title = title; this.subtitle = subtitle; this.cta = cta; this.emoji = emoji;
            this.colorStart = colorStart; this.colorEnd = colorEnd; this.action = action;
        }
    }

    public interface OnBannerClick { void onClick(BannerItem item); }

    private final List<BannerItem> items;
    private final OnBannerClick listener;

    public HomeBannerAdapter(List<BannerItem> items, OnBannerClick listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_banner, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        BannerItem b = items.get(position);
        h.title.setText(b.title);
        h.subtitle.setText(b.subtitle);
        h.cta.setText(b.cta);
        h.emoji.setText(b.emoji);
        h.card.setBackground(UiUtils.gradient(b.colorStart, b.colorEnd, UiUtils.dp(h.card, 18)));
        h.card.setOnClickListener(v -> { if (listener != null) listener.onClick(b); });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final View card; final TextView title, subtitle, cta, emoji;
        VH(@NonNull View v) {
            super(v);
            card = v.findViewById(R.id.bannerCard);
            title = v.findViewById(R.id.tvBannerTitle);
            subtitle = v.findViewById(R.id.tvBannerSubtitle);
            cta = v.findViewById(R.id.tvBannerCta);
            emoji = v.findViewById(R.id.tvBannerEmoji);
        }
    }
}
