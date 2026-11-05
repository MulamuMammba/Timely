package com.watchman.induetime;

import java.util.Calendar;
import java.util.Locale;

final class Fmt {
    private Fmt() {}

    static final String[] DAYS = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
    static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};

    static String hm(int minutes) {
        minutes = ((minutes % 1440) + 1440) % 1440;
        return String.format(Locale.ENGLISH, "%02d:%02d", minutes / 60, minutes % 60);
    }

    static String time(long t) {
        Calendar c = cal(t);
        return String.format(Locale.ENGLISH, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    static String dayName(long t) { return DAYS[cal(t).get(Calendar.DAY_OF_WEEK) - 1]; }

    static String dayMonth(long t) {
        Calendar c = cal(t);
        return c.get(Calendar.DAY_OF_MONTH) + " " + MONTHS[c.get(Calendar.MONTH)];
    }

    static String shortDay(long t) {
        Calendar c = cal(t);
        return DAYS[c.get(Calendar.DAY_OF_WEEK) - 1].substring(0, 3) + " " + c.get(Calendar.DAY_OF_MONTH);
    }

    static String relDay(long t) {
        long today = startOfDay(System.currentTimeMillis());
        long d = startOfDay(t);
        int diff = (int) Math.round((d - today) / 86400000.0);
        if (diff == 0) return "Today";
        if (diff == 1) return "Tomorrow";
        if (diff == -1) return "Yesterday";
        return dayName(t);
    }

    static String duration(long ms) {
        long m = Math.max(0, ms / 60000);
        if (m < 60) return m + " min";
        long h = m / 60, r = m % 60;
        return r == 0 ? h + " h" : h + " h " + r + " min";
    }

    static Calendar cal(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return c;
    }

    static long startOfDay(long t) {
        Calendar c = cal(t);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    static long addDays(long t, int n) {
        Calendar c = cal(t);
        c.add(Calendar.DAY_OF_MONTH, n);
        return c.getTimeInMillis();
    }

    static int minuteOfDay(long t) {
        Calendar c = cal(t);
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }
}
