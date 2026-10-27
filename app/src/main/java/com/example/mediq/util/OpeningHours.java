package com.example.mediq.util;

import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the free-text opening hours a receptionist types ("24x7", "Open 24 hours", "9 AM - 8 PM",
 * "Mon-Sat 9:00 AM to 9:00 PM") and says whether the place is open right now.
 * Day-of-week is not interpreted; the first time range found is used. Unknown text -> known = false.
 */
public final class OpeningHours {
    private OpeningHours() { }

    public static final class Status {
        public final boolean known;
        public final boolean open;
        public final String text;
        Status(boolean known, boolean open, String text) { this.known = known; this.open = open; this.text = text; }
    }

    private static final Pattern RANGE = Pattern.compile(
            "(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?\\s*(?:-|–|—|to|till|until)\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?");

    public static Status status(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new Status(false, true, "Hours not listed");
        String s = raw.toLowerCase(Locale.ROOT).trim();
        if (s.contains("24x7") || s.contains("24/7") || s.contains("24 x 7") || s.contains("24 hour") || s.contains("24hr") || s.contains("24 hr")
                || s.contains("round the clock") || s.contains("always open")) {
            return new Status(true, true, "Open 24 hours");
        }
        Matcher m = RANGE.matcher(s);
        if (!m.find()) return new Status(false, true, raw.trim());

        int h1 = Integer.parseInt(m.group(1)), m1 = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        int h2 = Integer.parseInt(m.group(4)), m2 = m.group(5) == null ? 0 : Integer.parseInt(m.group(5));
        String ap1 = meridiem(m.group(3)), ap2 = meridiem(m.group(6));
        if (h1 > 23 || h2 > 23 || m1 > 59 || m2 > 59) return new Status(false, true, raw.trim());

        int start, end;
        if (ap1 == null && ap2 == null) {          // 24-hour clock, e.g. "09:00 - 20:00"
            start = h1 * 60 + m1;
            end = h2 * 60 + m2;
        } else {
            if (ap2 == null) ap2 = ap1;
            end = to24(h2, ap2) * 60 + m2;
            if (ap1 == null) {                      // "9 - 5 pm": start is whichever side of noon makes it earlier
                start = to24(h1, ap2) * 60 + m1;
                if (start > end) start = to24(h1, "am") * 60 + m1;
            } else {
                start = to24(h1, ap1) * 60 + m1;
            }
        }
        if (start == end) return new Status(true, true, "Open 24 hours");

        Calendar c = Calendar.getInstance();
        int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        boolean open = start <= end ? (now >= start && now < end) : (now >= start || now < end); // overnight shifts
        return open ? new Status(true, true, "Open \u2022 Closes " + fmt(end))
                    : new Status(true, false, "Closed \u2022 Opens " + fmt(start));
    }

    private static String meridiem(String g) {
        if (g == null) return null;
        return g.startsWith("p") ? "pm" : "am";
    }

    private static int to24(int h, String ap) {
        int x = h % 12;
        return "pm".equals(ap) ? x + 12 : x;
    }

    private static String fmt(int minutes) {
        int h = minutes / 60 % 24, m = minutes % 60;
        String ap = h >= 12 ? "PM" : "AM";
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return String.format(Locale.US, "%d:%02d %s", h12, m, ap);
    }
}
