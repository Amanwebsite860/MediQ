package com.example.mediq.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediq.R;
import com.example.mediq.data.model.Appointment;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class AppointmentAdapter extends RecyclerView.Adapter<AppointmentAdapter.ViewHolder> {

    public interface OnAppointmentClickListener {
        void onViewDetails(Appointment appointment);
        void onCancel(Appointment appointment);
    }

    private List<Appointment> appointments;
    private OnAppointmentClickListener listener;

    public AppointmentAdapter(List<Appointment> appointments, OnAppointmentClickListener listener) {
        this.appointments = appointments;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_appointment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Appointment item = appointments.get(position);
        holder.tvDoctorName.setText(item.getDoctorName());
        holder.tvDoctorSpecialty.setText(item.getDoctorSpecialty());
        holder.tvHospitalName.setText(item.getHospitalName());
        holder.tvAppointmentDate.setText(com.example.mediq.util.UiUtils.friendlyDate(item.getAppointmentDate()));
        String t = item.getAppointmentTime();
        holder.tvAppointmentTime.setText(t != null && t.matches("\\d{2}:\\d{2}.*")
                ? com.example.mediq.util.UiUtils.formatTime(t) : t);
        holder.tvTokenNumber.setText(item.getTokenNumber());

        // Status chip colours
        String st = item.getStatus() == null ? "" : item.getStatus().toLowerCase(java.util.Locale.ROOT);
        android.content.Context c = holder.itemView.getContext();
        int fg, label;
        if (st.equals("cancelled")) { fg = R.color.colorError; label = R.string.status_cancelled; }
        else if (st.equals("completed")) { fg = R.color.colorPrimary; label = R.string.status_completed; }
        else if (st.equals("no_show")) { fg = R.color.colorWarning; label = R.string.status_no_show; }
        else { fg = R.color.colorSuccess; label = R.string.status_upcoming; }
        holder.tvStatusChip.setText(label);
        holder.tvStatusChip.setTextColor(androidx.core.content.ContextCompat.getColor(c, fg));

        if (item.getDoctorAvatarUrl() != null && !item.getDoctorAvatarUrl().isEmpty()) {
            com.bumptech.glide.Glide.with(holder.itemView.getContext())
                    .load(item.getDoctorAvatarUrl())
                    .placeholder(item.getDoctorAvatarResId() != 0 ? item.getDoctorAvatarResId() : R.drawable.ic_doctor_male_avatar)
                    .into(holder.ivDoctorAvatar);
        } else if (item.getDoctorAvatarResId() != 0) {
            holder.ivDoctorAvatar.setImageResource(item.getDoctorAvatarResId());
        } else {
            holder.ivDoctorAvatar.setImageResource(R.drawable.ic_doctor_male_avatar);
        }

        boolean up = item.isUpcoming();
        holder.btnCancel.setVisibility(up ? View.VISIBLE : View.GONE);
        holder.btnViewDetails.setVisibility(up ? View.VISIBLE : View.GONE);
        holder.layoutActions.setVisibility(up ? View.VISIBLE : View.GONE);

        holder.btnViewDetails.setOnClickListener(v -> {
            if (listener != null) listener.onViewDetails(item);
        });

        holder.btnCancel.setOnClickListener(v -> {
            if (listener != null) listener.onCancel(item);
        });
    }

    @Override
    public int getItemCount() {
        return appointments != null ? appointments.size() : 0;
    }

    public void setAppointments(List<Appointment> newAppointments) {
        this.appointments = newAppointments;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivDoctorAvatar;
        TextView tvDoctorName;
        TextView tvDoctorSpecialty;
        TextView tvHospitalName;
        TextView tvAppointmentDate;
        TextView tvAppointmentTime;
        TextView tvTokenNumber;
        TextView tvStatusChip;
        View layoutActions;
        MaterialButton btnViewDetails;
        MaterialButton btnCancel;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivDoctorAvatar = itemView.findViewById(R.id.ivDoctorAvatar);
            tvDoctorName = itemView.findViewById(R.id.tvDoctorName);
            tvDoctorSpecialty = itemView.findViewById(R.id.tvDoctorSpecialty);
            tvHospitalName = itemView.findViewById(R.id.tvHospitalName);
            tvAppointmentDate = itemView.findViewById(R.id.tvAppointmentDate);
            tvAppointmentTime = itemView.findViewById(R.id.tvAppointmentTime);
            tvTokenNumber = itemView.findViewById(R.id.tvTokenNumber);
            tvStatusChip = itemView.findViewById(R.id.tvStatusChip);
            layoutActions = itemView.findViewById(R.id.layoutActions);
            btnViewDetails = itemView.findViewById(R.id.btnViewDetails);
            btnCancel = itemView.findViewById(R.id.btnCancel);
        }
    }
}
