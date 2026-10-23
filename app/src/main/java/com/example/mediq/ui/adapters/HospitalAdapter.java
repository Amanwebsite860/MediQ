package com.example.mediq.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediq.R;
import com.example.mediq.data.model.Hospital;

import java.util.List;

public class HospitalAdapter extends RecyclerView.Adapter<HospitalAdapter.ViewHolder> {

    public interface OnHospitalClickListener {
        void onHospitalClick(Hospital hospital);
    }

    private List<Hospital> hospitals;
    private OnHospitalClickListener listener;

    public HospitalAdapter(List<Hospital> hospitals, OnHospitalClickListener listener) {
        this.hospitals = hospitals;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hospital, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Hospital hospital = hospitals.get(position);
        holder.tvHospitalName.setText(hospital.getName());
        holder.tvHospitalType.setText(hospital.getType());
        holder.tvHospitalLocation.setText(hospital.getDistanceAndCity());
        holder.tvHospitalRating.setText(hospital.getFormattedRating());
        holder.tvHospitalStatus.setText(hospital.getOpenStatusText());

        if (hospital.getImageUrl() != null && !hospital.getImageUrl().isEmpty()) {
            com.bumptech.glide.Glide.with(holder.itemView.getContext())
                    .load(hospital.getImageUrl())
                    .placeholder(hospital.getImageResId() != 0 ? hospital.getImageResId() : R.drawable.ic_hospital_building)
                    .into(holder.ivHospitalImage);
        } else if (hospital.getImageResId() != 0) {
            holder.ivHospitalImage.setImageResource(hospital.getImageResId());
        } else {
            holder.ivHospitalImage.setImageResource(R.drawable.ic_hospital_building);
        }

        CardBinder.bindHospitalHeart(holder.itemView, hospital);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onHospitalClick(hospital);
        });
    }

    @Override
    public int getItemCount() {
        return hospitals != null ? hospitals.size() : 0;
    }

    public void filterList(List<Hospital> filtered) {
        this.hospitals = filtered;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivHospitalImage;
        TextView tvHospitalName;
        TextView tvHospitalType;
        TextView tvHospitalLocation;
        TextView tvHospitalRating;
        TextView tvHospitalStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivHospitalImage = itemView.findViewById(R.id.ivHospitalImage);
            tvHospitalName = itemView.findViewById(R.id.tvHospitalName);
            tvHospitalType = itemView.findViewById(R.id.tvHospitalType);
            tvHospitalLocation = itemView.findViewById(R.id.tvHospitalLocation);
            tvHospitalRating = itemView.findViewById(R.id.tvHospitalRating);
            tvHospitalStatus = itemView.findViewById(R.id.tvHospitalStatus);
        }
    }
}
