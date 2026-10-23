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
import com.example.mediq.data.model.DateItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for horizontal date selector cards in BookAppointmentActivity.
 */
public class DateAdapter extends RecyclerView.Adapter<DateAdapter.DateViewHolder> {

    public interface OnDateClickListener {
        void onDateSelected(DateItem dateItem, int position);
    }

    private final List<DateItem> dateList = new ArrayList<>();
    private int selectedPosition = 0; // Default first date selected
    private final OnDateClickListener listener;

    public DateAdapter(List<DateItem> dates, OnDateClickListener listener) {
        if (dates != null) {
            this.dateList.addAll(dates);
            for (int i = 0; i < dateList.size(); i++) {
                if (dateList.get(i).isSelected()) {
                    selectedPosition = i;
                    break;
                }
            }
        }
        this.listener = listener;
    }

    @NonNull
    @Override
    public DateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_date_card, parent, false);
        return new DateViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DateViewHolder holder, int position) {
        DateItem dateItem = dateList.get(position);
        boolean isSelected = (position == selectedPosition);
        holder.bind(dateItem, isSelected, (v) -> {
            int prevSelected = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            if (prevSelected != RecyclerView.NO_POSITION) {
                dateList.get(prevSelected).setSelected(false);
                notifyItemChanged(prevSelected);
            }
            if (selectedPosition != RecyclerView.NO_POSITION) {
                dateList.get(selectedPosition).setSelected(true);
                notifyItemChanged(selectedPosition);
                if (listener != null) {
                    listener.onDateSelected(dateList.get(selectedPosition), selectedPosition);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return dateList.size();
    }

    public DateItem getSelectedDate() {
        if (selectedPosition >= 0 && selectedPosition < dateList.size()) {
            return dateList.get(selectedPosition);
        }
        return null;
    }

    public void setSelectedPosition(int position) {
        if (position >= 0 && position < dateList.size()) {
            int prev = selectedPosition;
            selectedPosition = position;
            notifyItemChanged(prev);
            notifyItemChanged(selectedPosition);
        }
    }

    static class DateViewHolder extends RecyclerView.ViewHolder {
        private final View cardContainer;
        private final TextView tvDayOfWeek;
        private final TextView tvDayNumber;
        private final TextView tvMonth;

        DateViewHolder(@NonNull View itemView) {
            super(itemView);
            cardContainer = itemView.findViewById(R.id.cardDateContainer);
            tvDayOfWeek = itemView.findViewById(R.id.tvDayOfWeek);
            tvDayNumber = itemView.findViewById(R.id.tvDayNumber);
            tvMonth = itemView.findViewById(R.id.tvMonth);
        }

        void bind(DateItem dateItem, boolean isSelected, View.OnClickListener clickListener) {
            Context context = itemView.getContext();
            tvDayOfWeek.setText(dateItem.getDayOfWeek());
            tvDayNumber.setText(dateItem.getDayNumber());
            tvMonth.setText(dateItem.getMonth());

            cardContainer.setSelected(isSelected);

            if (isSelected) {
                int whiteColor = ContextCompat.getColor(context, R.color.white);
                tvDayOfWeek.setTextColor(whiteColor);
                tvDayNumber.setTextColor(whiteColor);
                tvMonth.setTextColor(whiteColor);
            } else {
                tvDayOfWeek.setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary));
                tvDayNumber.setTextColor(ContextCompat.getColor(context, R.color.colorTextPrimary));
                tvMonth.setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary));
            }

            cardContainer.setOnClickListener(clickListener);
        }
    }
}
