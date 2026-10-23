package com.example.mediq.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mediq.R;
import com.example.mediq.data.model.Doctor;
import com.example.mediq.data.model.Hospital;

import java.util.ArrayList;
import java.util.List;

public class SearchFeedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnItemClickListener {
        void onHospitalClick(Hospital hospital);
        void onDoctorClick(Doctor doctor);
    }

    private final List<SearchFeedItem> items = new ArrayList<>();
    private final OnItemClickListener listener;

    public SearchFeedAdapter(List<SearchFeedItem> items, OnItemClickListener listener) {
        if (items != null) {
            this.items.addAll(items);
        }
        this.listener = listener;
    }

    public void updateItems(List<SearchFeedItem> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == SearchFeedItem.TYPE_HOSPITAL) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hospital, parent, false);
            return new HospitalViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_doctor, parent, false);
            return new DoctorViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        SearchFeedItem item = items.get(position);
        if (holder instanceof HospitalViewHolder && item.getHospital() != null) {
            ((HospitalViewHolder) holder).bind(item.getHospital(), listener);
        } else if (holder instanceof DoctorViewHolder && item.getDoctor() != null) {
            ((DoctorViewHolder) holder).bind(item.getDoctor(), listener);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HospitalViewHolder extends RecyclerView.ViewHolder {
        final TextView tvHospitalName;
        final TextView tvHospitalType;
        final TextView tvHospitalLocation;
        final TextView tvHospitalRating;
        final TextView tvHospitalStatus;
        final ImageView ivHospitalImage;

        HospitalViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHospitalName = itemView.findViewById(R.id.tvHospitalName);
            tvHospitalType = itemView.findViewById(R.id.tvHospitalType);
            tvHospitalLocation = itemView.findViewById(R.id.tvHospitalLocation);
            tvHospitalRating = itemView.findViewById(R.id.tvHospitalRating);
            tvHospitalStatus = itemView.findViewById(R.id.tvHospitalStatus);
            ivHospitalImage = itemView.findViewById(R.id.ivHospitalImage);
        }

        void bind(Hospital hospital, OnItemClickListener listener) {
            tvHospitalName.setText(hospital.getName());
            tvHospitalType.setText(hospital.getType());
            tvHospitalLocation.setText(hospital.getDistanceAndCity());
            tvHospitalRating.setText(hospital.getFormattedRating());
            tvHospitalStatus.setText(hospital.getOpenStatusText());

            if (hospital.getImageUrl() != null && !hospital.getImageUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(hospital.getImageUrl())
                        .placeholder(R.drawable.ic_hospital_building)
                        .into(ivHospitalImage);
            } else {
                ivHospitalImage.setImageResource(hospital.getImageResId() != 0 ? hospital.getImageResId() : R.drawable.ic_hospital_building);
            }

            CardBinder.bindHospitalHeart(itemView, hospital);
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onHospitalClick(hospital);
            });
        }
    }

    static class DoctorViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivAvatar;
        final TextView tvName;
        final TextView tvSpecialty;
        final TextView tvRating;
        final TextView tvAvailability;

        DoctorViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivDoctorAvatar);
            tvName = itemView.findViewById(R.id.tvDoctorName);
            tvSpecialty = itemView.findViewById(R.id.tvDoctorSpecialty);
            tvRating = itemView.findViewById(R.id.tvDoctorRating);
            tvAvailability = itemView.findViewById(R.id.tvAvailableBadge);
        }

        void bind(Doctor doctor, OnItemClickListener listener) {
            CardBinder.bindDoctor(itemView, doctor, true);

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onDoctorClick(doctor);
            });
        }
    }
}
