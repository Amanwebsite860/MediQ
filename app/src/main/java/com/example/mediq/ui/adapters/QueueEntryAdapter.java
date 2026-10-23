package com.example.mediq.ui.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mediq.R;
import com.example.mediq.data.model.QueueEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying the list of patient queue tokens in LiveQueueActivity.
 */
public class QueueEntryAdapter extends RecyclerView.Adapter<QueueEntryAdapter.QueueViewHolder> {

    private final List<QueueEntry> items = new ArrayList<>();

    public QueueEntryAdapter() {
    }

    public QueueEntryAdapter(List<QueueEntry> initialItems) {
        if (initialItems != null) {
            this.items.addAll(initialItems);
        }
    }

    public void setItems(List<QueueEntry> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public QueueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_queue_entry, parent, false);
        return new QueueViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QueueViewHolder holder, int position) {
        QueueEntry entry = items.get(position);
        holder.bind(entry);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class QueueViewHolder extends RecyclerView.ViewHolder {

        private final View layoutQueueItem;
        private final ImageView ivPersonIcon;
        private final TextView tvTokenNumber;
        private final TextView tvStatusBadge;

        QueueViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutQueueItem = itemView.findViewById(R.id.layoutQueueItem);
            ivPersonIcon = itemView.findViewById(R.id.ivPersonIcon);
            tvTokenNumber = itemView.findViewById(R.id.tvTokenNumber);
            tvStatusBadge = itemView.findViewById(R.id.tvStatusBadge);
        }

        void bind(QueueEntry entry) {
            Context context = itemView.getContext();

            // Token Number
            tvTokenNumber.setText(entry.getTokenNumber());

            // Density pixel helper
            float density = context.getResources().getDisplayMetrics().density;
            int padding4 = (int) (4 * density);
            int padding6 = (int) (6 * density);

            // Row Highlight & Person Icon styling
            if (entry.isCurrentUser()) {
                layoutQueueItem.setBackgroundResource(R.drawable.bg_queue_highlight);
                ivPersonIcon.setBackgroundResource(R.drawable.bg_circle_primary);
                ivPersonIcon.setImageTintList(ColorStateList.valueOf(Color.WHITE));
                ivPersonIcon.setPadding(padding6, padding6, padding6, padding6);
            } else {
                layoutQueueItem.setBackgroundResource(0);
                ivPersonIcon.setBackground(null);
                ivPersonIcon.setImageTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.colorTextSecondary)));
                ivPersonIcon.setPadding(padding4, padding4, padding4, padding4);
            }

            // Status Badge styling
            String status = entry.getStatus();
            if (QueueEntry.STATUS_COMPLETED.equalsIgnoreCase(status)) {
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_success);
                tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.colorSuccess));
                tvStatusBadge.setText(R.string.status_completed);
            } else if (QueueEntry.STATUS_IN_CONSULTATION.equalsIgnoreCase(status)) {
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_info);
                tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.colorInfo));
                tvStatusBadge.setText(R.string.status_in_consultation);
            } else if (QueueEntry.STATUS_YOUR_TURN.equalsIgnoreCase(status)) {
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_your_turn);
                tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.colorQueueYourTurn));
                tvStatusBadge.setText(R.string.status_your_turn);
            } else {
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_waiting);
                tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.colorTextSecondary));
                tvStatusBadge.setText(R.string.status_waiting);
            }
        }
    }
}
