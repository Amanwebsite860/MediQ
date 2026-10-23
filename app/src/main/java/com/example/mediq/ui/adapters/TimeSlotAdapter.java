package com.example.mediq.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediq.R;
import com.example.mediq.data.model.TimeSlotItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for 2-column grid time slot chips in BookAppointmentActivity.
 */
public class TimeSlotAdapter extends RecyclerView.Adapter<TimeSlotAdapter.TimeSlotViewHolder> {

    public interface OnTimeSlotClickListener {
        void onTimeSlotSelected(TimeSlotItem slotItem, int position);
    }

    private final List<TimeSlotItem> slotList = new ArrayList<>();
    private int selectedPosition = -1;
    private final OnTimeSlotClickListener listener;

    public TimeSlotAdapter(List<TimeSlotItem> slots, int defaultSelectedIndex, OnTimeSlotClickListener listener) {
        if (slots != null) {
            this.slotList.addAll(slots);
        }
        this.selectedPosition = defaultSelectedIndex;
        this.listener = listener;
    }

    public TimeSlotAdapter(List<TimeSlotItem> slots, OnTimeSlotClickListener listener) {
        this(slots, 0, listener);
    }

    public void updateSlots(List<TimeSlotItem> newSlots) {
        slotList.clear();
        selectedPosition = -1;
        if (newSlots != null) {
            slotList.addAll(newSlots);
            // Select first available slot
            for (int i = 0; i < slotList.size(); i++) {
                if (slotList.get(i).isAvailable()) {
                    selectedPosition = i;
                    slotList.get(i).setSelected(true);
                    break;
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TimeSlotViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_time_slot, parent, false);
        return new TimeSlotViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TimeSlotViewHolder holder, int position) {
        TimeSlotItem slotItem = slotList.get(position);
        boolean isSelected = (position == selectedPosition);
        holder.bind(slotItem, isSelected, (v) -> {
            if (!slotItem.isAvailable()) return;
            int prevSelected = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            if (prevSelected != RecyclerView.NO_POSITION && prevSelected < slotList.size()) {
                slotList.get(prevSelected).setSelected(false);
                notifyItemChanged(prevSelected);
            }
            if (selectedPosition != RecyclerView.NO_POSITION && selectedPosition < slotList.size()) {
                slotList.get(selectedPosition).setSelected(true);
                notifyItemChanged(selectedPosition);
                if (listener != null) {
                    listener.onTimeSlotSelected(slotList.get(selectedPosition), selectedPosition);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return slotList.size();
    }

    public TimeSlotItem getSelectedTimeSlot() {
        if (selectedPosition >= 0 && selectedPosition < slotList.size()) {
            return slotList.get(selectedPosition);
        }
        return null;
    }

    public void setSelectedPosition(int position) {
        if (position >= 0 && position < slotList.size()) {
            int prev = selectedPosition;
            selectedPosition = position;
            if (prev >= 0 && prev < slotList.size()) notifyItemChanged(prev);
            notifyItemChanged(selectedPosition);
        }
    }

    static class TimeSlotViewHolder extends RecyclerView.ViewHolder {
        private final View container;
        private final TextView tvTimeSlot;

        TimeSlotViewHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.containerTimeSlot);
            tvTimeSlot = itemView.findViewById(R.id.tvTimeSlot);
        }

        void bind(TimeSlotItem slotItem, boolean isSelected, View.OnClickListener clickListener) {
            Context context = itemView.getContext();
            tvTimeSlot.setText(slotItem.getTime());
            container.setSelected(isSelected);

            if (!slotItem.isAvailable()) {
                container.setEnabled(false);
                container.setAlpha(0.35f);
                tvTimeSlot.setTextColor(ContextCompat.getColor(context, R.color.colorTextHint));
                container.setOnClickListener(null);
            } else {
                container.setEnabled(true);
                container.setAlpha(1.0f);
                if (isSelected) {
                    tvTimeSlot.setTextColor(ContextCompat.getColor(context, R.color.white));
                } else {
                    tvTimeSlot.setTextColor(ContextCompat.getColor(context, R.color.colorTextPrimary));
                }
                container.setOnClickListener(clickListener);
            }
        }
    }
}
