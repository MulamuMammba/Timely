package com.watchman.induetime;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.watchman.induetime.shared.WatchData;

/** Wakes the watch at block edges: taps the wrist, redraws the dial, asks the phone for news. */
public class Alarms extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        final Context c = ctx.getApplicationContext();
        final PendingResult pr = goAsync();
        new Thread(new Runnable() {
            public void run() {
                try { fire(c); } finally { pr.finish(); }
            }
        }).start();
    }

    static void fire(Context c) {
        long now = System.currentTimeMillis();
        SharedPreferences sp = Link.p(c);
        long last = sp.getLong("checked", now - 60000);
        sp.edit().putLong("checked", now).apply();
        WatchData d = Link.data(c);
        WatchData.Item ended = null;
        for (WatchData.Item i : d.items) {
            if (i.google) continue;
            if (i.end > last && i.end <= now + 30000 && (ended == null || i.end > ended.end)) ended = i;
        }
        if (ended != null && now - ended.end < 5 * 60000L) {
            String key = ended.title + "@" + ended.end;
            if (!key.equals(sp.getString("tapKey", ""))) {
                sp.edit().putString("tapKey", key).putString("tapTitle", ended.title).putLong("lastTap", now).apply();
                if (Link.raw(c).tapOn) Link.tap(c);
            }
        }
        Link.changed(c);
        Link.pull(c);
        schedule(c);
    }

    static void schedule(Context c) {
        long now = System.currentTimeMillis();
        long next = now + 15 * 60000L;
        for (WatchData.Item i : Link.raw(c).items) {
            if (i.start > now + 1000 && i.start < next) next = i.start;
            if (i.end > now + 1000 && i.end < next) next = i.end;
        }
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, new Intent(c, Alarms.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
        }
    }

    /** Re-arms after a restart. */
    public static class Boot extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            Link.p(c).edit().putLong("checked", System.currentTimeMillis()).apply();
            schedule(c.getApplicationContext());
            Link.pullAsync(c, 0);
        }
    }
}
