package com.example.mediq.ui.patient;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.mediq.MainActivity;
import com.example.mediq.R;
import com.example.mediq.data.remote.SessionManager;
import com.example.mediq.data.remote.SupabaseClient;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Notification inbox (opened from the bell on Home).
 * - grouped into Today / Earlier, unread dot, tap to open Appointments
 * - swipe left/right to delete (with Undo), pull to refresh, "Mark all read"
 */
public class NotificationsActivity extends AppCompatActivity {

    private static class Item {
        String id, type, title, message, createdAt;
        boolean unread;
        boolean today;
    }

    /** Row model: either a section header (header != null) or a notification. */
    private static class Row {
        final String header; final Item item;
        Row(String h) { header = h; item = null; }
        Row(Item i) { header = null; item = i; }
    }

    private static final int T_HEADER = 0, T_ITEM = 1;

    private final List<Item> items = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private Adapter adapter;
    private RecyclerView rv;
    private SwipeRefreshLayout swipe;
    private TextView tvEmpty, tvAction;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        ((TextView) findViewById(R.id.tvScreenTitle)).setText(R.string.title_notifications);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        tvAction = findViewById(R.id.tvScreenAction);
        tvAction.setText("Mark all read");
        tvAction.setOnClickListener(v -> markAllRead());

        rv = findViewById(R.id.rvList);
        swipe = findViewById(R.id.swipeList);
        tvEmpty = findViewById(R.id.tvEmpty);
        progress = findViewById(R.id.progress);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        rv.setAdapter(adapter);
        swipe.setOnRefreshListener(this::load);

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override public boolean onMove(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder a, @NonNull RecyclerView.ViewHolder b) { return false; }
            @Override public int getSwipeDirs(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder vh) {
                return vh.getItemViewType() == T_HEADER ? 0 : super.getSwipeDirs(r, vh);
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {
                Row row = rows.get(vh.getBindingAdapterPosition());
                if (row.item != null) deleteWithUndo(row.item);
            }
        }).attachToRecyclerView(rv);

        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null && !items.isEmpty()) load(); // pick up anything new while away
    }

    private void load() {
        String uid = SessionManager.getInstance(this).getUserId();
        if (uid == null || uid.isEmpty()) { stopLoading(); refreshUi(); return; }
        SupabaseClient.getInstance().get("notifications?user_id=eq." + uid + "&select=*&order=created_at.desc&limit=60",
                new SupabaseClient.Callback() {
                    @Override public void onSuccess(String json) {
                        stopLoading();
                        try {
                            JSONArray arr = new JSONArray(json);
                            items.clear();
                            long startOfToday = startOfTodayMillis();
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);
                                Item it = new Item();
                                it.id = SupabaseClient.optStringSafe(o, "id");
                                it.type = SupabaseClient.optStringSafe(o, "type");
                                it.title = SupabaseClient.optStringSafe(o, "title");
                                it.message = SupabaseClient.optStringSafe(o, "message");
                                it.createdAt = SupabaseClient.optStringSafe(o, "created_at");
                                it.unread = SupabaseClient.optStringSafe(o, "read_at") == null;
                                Date d = parse(it.createdAt);
                                it.today = d != null && d.getTime() >= startOfToday;
                                items.add(it);
                            }
                        } catch (Exception ignored) { }
                        refreshUi();
                    }
                    @Override public void onError(Exception e) { stopLoading(); refreshUi(); }
                });
    }

    private void stopLoading() {
        progress.setVisibility(View.GONE);
        swipe.setRefreshing(false);
    }

    private void refreshUi() {
        rows.clear();
        boolean addedToday = false, addedEarlier = false;
        for (Item i : items) {
            if (i.today && !addedToday) { rows.add(new Row("Today")); addedToday = true; }
            if (!i.today && !addedEarlier) { rows.add(new Row("Earlier")); addedEarlier = true; }
            rows.add(new Row(i));
        }
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        boolean anyUnread = false;
        for (Item i : items) if (i.unread) { anyUnread = true; break; }
        tvAction.setVisibility(anyUnread ? View.VISIBLE : View.GONE);
    }

    private void markAllRead() {
        SupabaseClient.getInstance().rpc("mark_all_notifications_read", new JSONObject(), new SupabaseClient.Callback() {
            @Override public void onSuccess(String r) {
                for (Item i : items) i.unread = false;
                refreshUi();
            }
            @Override public void onError(Exception e) { }
        });
    }

    private void markRead(Item it) {
        if (!it.unread) return;
        it.unread = false;
        try {
            JSONObject body = new JSONObject();
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            body.put("read_at", f.format(new Date()));
            SupabaseClient.getInstance().patch("notifications?id=eq." + it.id, body, new SupabaseClient.Callback() {
                @Override public void onSuccess(String r) { }
                @Override public void onError(Exception e) { }
            });
        } catch (Exception ignored) { }
        refreshUi();
    }

    /** Removes the row immediately; the server delete only happens if the user doesn't tap Undo. */
    private void deleteWithUndo(Item it) {
        items.remove(it);
        refreshUi();
        Snackbar.make(findViewById(R.id.rvList), "Notification deleted", Snackbar.LENGTH_LONG)
                .setAction("Undo", v -> { })
                .addCallback(new BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    @Override public void onDismissed(Snackbar bar, int event) {
                        if (event == DISMISS_EVENT_ACTION) {
                            load(); // brings it back
                        } else {
                            SupabaseClient.getInstance().delete("notifications?id=eq." + it.id, new SupabaseClient.Callback() {
                                @Override public void onSuccess(String r) { }
                                @Override public void onError(Exception e) { }
                            });
                        }
                    }
                }).show();
    }

    private static long startOfTodayMillis() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static Date parse(String iso) {
        if (iso == null || iso.length() < 19) return null;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(iso.substring(0, 19));
        } catch (Exception e) {
            return null;
        }
    }

    private static String emojiFor(String type) {
        if (type == null) return "🔔";
        switch (type) {
            case "appointment_booked": return "✅";
            case "appointment_cancelled": return "❌";
            case "turn_approaching": return "⏳";
            case "your_turn": return "🎉";
            case "review_prompt": return "⭐";
            case "queue_update": return "➡️";
            case "staff_approved": return "🎉";
            case "staff_rejected": return "⚠️";
            case "new_booking": return "📅";
            case "booking_cancelled": return "❌";
            case "new_review": return "⭐";
            default: return "🔔";
        }
    }

    private static String relative(String iso) {
        Date d = parse(iso);
        if (d == null) return "";
        long mins = (System.currentTimeMillis() - d.getTime()) / 60000L;
        if (mins < 1) return "Just now";
        if (mins < 60) return mins + " min ago";
        if (mins < 60 * 24) return (mins / 60) + " h ago";
        if (mins < 60 * 24 * 7) return (mins / (60 * 24)) + " d ago";
        return new SimpleDateFormat("d MMM", Locale.US).format(d);
    }

    private class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemViewType(int pos) { return rows.get(pos).header != null ? T_HEADER : T_ITEM; }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            LayoutInflater inf = LayoutInflater.from(p.getContext());
            if (t == T_HEADER) return new HeaderVH(inf.inflate(R.layout.item_section_header, p, false));
            return new ItemVH(inf.inflate(R.layout.item_notification, p, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder vh, int pos) {
            Row row = rows.get(pos);
            if (vh instanceof HeaderVH) { ((HeaderVH) vh).title.setText(row.header); return; }
            ItemVH h = (ItemVH) vh;
            Item it = row.item;
            h.emoji.setText(emojiFor(it.type));
            h.title.setText(it.title);
            h.message.setText(it.message);
            h.time.setText(relative(it.createdAt));
            h.dot.setVisibility(it.unread ? View.VISIBLE : View.INVISIBLE);
            h.title.setTypeface(null, it.unread ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            h.itemView.setOnClickListener(v -> {
                markRead(it);
                String role = SessionManager.getInstance(NotificationsActivity.this).getUserRole();
                if ("doctor".equals(role) || "receptionist".equals(role)) { finish(); return; } // back to their dashboard
                Intent i = new Intent(NotificationsActivity.this, MainActivity.class);
                i.putExtra("navigate_tab", R.id.nav_appointments);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
            });
        }

        @Override public int getItemCount() { return rows.size(); }

        class HeaderVH extends RecyclerView.ViewHolder {
            final TextView title;
            HeaderVH(@NonNull View v) { super(v); title = v.findViewById(R.id.tvSectionHeader); }
        }

        class ItemVH extends RecyclerView.ViewHolder {
            final TextView emoji, title, message, time; final View dot;
            ItemVH(@NonNull View v) {
                super(v);
                emoji = v.findViewById(R.id.tvNotifEmoji);
                title = v.findViewById(R.id.tvNotifTitle);
                message = v.findViewById(R.id.tvNotifMessage);
                time = v.findViewById(R.id.tvNotifTime);
                dot = v.findViewById(R.id.viewUnread);
            }
        }
    }
}
