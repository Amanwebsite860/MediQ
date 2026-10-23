package com.example.mediq.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediq.R;
import com.example.mediq.data.model.Doctor;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying list of Doctors in MediQ.
 */
public class DoctorAdapter extends RecyclerView.Adapter<DoctorAdapter.DoctorViewHolder> {

    public interface OnDoctorClickListener {
        void onDoctorClick(Doctor doctor);
    }

    private final List<Doctor> doctorList = new ArrayList<>();
    private final OnDoctorClickListener listener;

    public DoctorAdapter(List<Doctor> doctors, OnDoctorClickListener listener) {
        if (doctors != null) {
            this.doctorList.addAll(doctors);
        }
        this.listener = listener;
    }

    public void updateDoctors(List<Doctor> newDoctors) {
        this.doctorList.clear();
        if (newDoctors != null) {
            this.doctorList.addAll(newDoctors);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DoctorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_doctor, parent, false);
        return new DoctorViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DoctorViewHolder holder, int position) {
        Doctor doctor = doctorList.get(position);
        holder.bind(doctor, listener);
    }

    @Override
    public int getItemCount() {
        return doctorList.size();
    }

    static class DoctorViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivDoctorAvatar;
        private final TextView tvDoctorName;
        private final TextView tvDoctorSpecialty;
        private final TextView tvDoctorRating;
        private final TextView tvAvailableBadge;
        private final View cardDoctor;

        DoctorViewHolder(@NonNull View itemView) {
            super(itemView);
            cardDoctor = itemView.findViewById(R.id.cardDoctor);
            ivDoctorAvatar = itemView.findViewById(R.id.ivDoctorAvatar);
            tvDoctorName = itemView.findViewById(R.id.tvDoctorName);
            tvDoctorSpecialty = itemView.findViewById(R.id.tvDoctorSpecialty);
            tvDoctorRating = itemView.findViewById(R.id.tvDoctorRating);
            tvAvailableBadge = itemView.findViewById(R.id.tvAvailableBadge);
        }

        void bind(final Doctor doctor, final OnDoctorClickListener listener) {
            CardBinder.bindDoctor(itemView, doctor, true);
            cardDoctor.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDoctorClick(doctor);
                }
            });
        }
    }
}
