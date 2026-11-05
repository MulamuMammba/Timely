package com.watchman.induetime;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.List;

/** Wakes up at block boundaries to update the widget, tell the watch, and ask how it went. */
public final class Sched {

    static final String CHANNEL = "checkin";
    static final int NOTE_ID = 7;

    private Sched() {}

    static void reschedule(Context c) {
        long now = System.currentTimeMillis();
        long next = now + 30 * 60000L; // also re-check lenses every half hour
        for (Cal.Block b : Cal.between(c, now - 60000, now + 36 * 3600000L)) {
            if (b.allDay) continue;
            if (b.begin > now + 1000 && b.begin < next) next = b.begin;
            if (b.end > now + 1000 && b.end < next) next = b.end;
        }
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, new Intent(c, Tick.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
        }
    }

    /** Fires at a boundary. */
    public static class Tick extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent intent) {
            final PendingResult pr = goAsync();
            final Context app = c.getApplicationContext();
            new Thread(new Runnable() {
                public void run() {
                    try { handle(app); } finally { pr.finish(); }
                }
            }).start();
        }
    }

    static void handle(Context c) {
        long now = System.currentTimeMillis();
        long last = Store.p(c).getLong("lastTick", now - 60000);
        Store.p(c).edit().putLong("lastTick", now).apply();
        Cal.Block ended = null;
        List<Cal.Block> list = Cal.between(c, last - 6 * 3600000L, now + 60000);
        for (Cal.Block b : list) {
            if (b.allDay || b.google) continue;
            if (b.end > last && b.end <= now + 30000 && !Store.isDone(c, b)) {
                if (ended == null || b.end > ended.end) ended = b;
            }
        }
        if (ended != null && now - ended.end < 10 * 60000L) {
            if (Store.bool(c, "checkin", true)) note(c, ended);
            if (Store.bool(c, "tap", true)) WatchLink.push(c, ended.title);
            else WatchLink.push(c, null);
        } else {
            WatchLink.push(c, null);
        }
        ClockWidget.updateAll(c);
        reschedule(c);
    }

    static void note(Context c, Cal.Block b) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Block check-ins", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Asks if you are done when a block ends.");
            nm.createNotificationChannel(ch);
        }
        int f = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        Intent open = new Intent(c, MainActivity.class).putExtra("eid", b.eventId).putExtra("begin", b.begin).putExtra("mode", "ask")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Intent later = new Intent(c, MainActivity.class).putExtra("eid", b.eventId).putExtra("begin", b.begin).putExtra("mode", "notyet")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Intent done = new Intent(c, Done.class).putExtra("eid", b.eventId).putExtra("begin", b.begin).putExtra("end", b.end);
        Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        nb.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(b.title + " has ended")
                .setContentText("Are you done?")
                .setColor(0xFFB7A3F0)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(PendingIntent.getActivity(c, 10, open, f))
                .addAction(new Notification.Action.Builder(null, "Done", PendingIntent.getBroadcast(c, 11, done, f)).build())
                .addAction(new Notification.Action.Builder(null, "Not yet", PendingIntent.getActivity(c, 12, later, f)).build());
        if (Build.VERSION.SDK_INT < 26) nb.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_VIBRATE);
        try { nm.notify(NOTE_ID, nb.build()); } catch (SecurityException ignored) {}
    }

    static void cancelNote(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTE_ID);
    }

    /** The Done button on the check-in. */
    public static class Done extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            Cal.Block b = new Cal.Block();
            b.eventId = i.getLongExtra("eid", -1);
            b.begin = i.getLongExtra("begin", 0);
            Store.setDone(c, b, true);
            cancelNote(c);
        }
    }

    /** Re-arms after a restart or a clock change. */
    public static class Boot extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            Store.p(c).edit().putLong("lastTick", System.currentTimeMillis()).apply();
            final Context app = c.getApplicationContext();
            final PendingResult pr = goAsync();
            new Thread(new Runnable() {
                public void run() { try { App.changed(app); } finally { pr.finish(); } }
            }).start();
        }
    }
}
