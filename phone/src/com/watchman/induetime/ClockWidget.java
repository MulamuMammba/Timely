package com.watchman.induetime;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

/** The C1 Serif clock widget. The clock ticks by itself; block text updates at boundaries. */
public class ClockWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) { updateAll(c); }

    static Cal.Block current(Context c) {
        long now = System.currentTimeMillis();
        Store.Lens lens = Store.activeLens(c, "widget");
        Cal.Block best = null;
        for (Cal.Block b : Cal.between(c, now - 12 * 3600000L, now + 60000)) {
            if (b.allDay || !b.isNow(now) || !Store.lensAllows(lens, b)) continue;
            if (best == null || (best.google && !b.google)) best = b;
        }
        return best;
    }

    static Cal.Block next(Context c) {
        long now = System.currentTimeMillis();
        Store.Lens lens = Store.activeLens(c, "widget");
        List<Cal.Block> list = Cal.between(c, now, now + 18 * 3600000L);
        for (Cal.Block b : list) if (!b.allDay && b.begin > now && Store.lensAllows(lens, b)) return b;
        return null;
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        if (m == null) return;
        int[] ids = m.getAppWidgetIds(new ComponentName(c, ClockWidget.class));
        if (ids == null || ids.length == 0) return;
        String font = Store.str(c, "wFont", "serif");
        int layout = "mono".equals(font) ? R.layout.widget_mono : "sans".equals(font) ? R.layout.widget_sans : R.layout.widget_serif;
        String glass = Store.str(c, "wGlass", "soft");
        int bg = "clear".equals(glass) ? R.drawable.glass_clear : "solid".equals(glass) ? R.drawable.glass_solid : R.drawable.glass_soft;
        String line, until = "";
        boolean now = false;
        boolean showBlock = Store.bool(c, "wBlock", true);
        if (Cal.allowed(c)) {
            Cal.Block cur = current(c);
            if (cur != null) { line = cur.title; until = "until " + Fmt.time(cur.end); now = true; }
            else {
                Cal.Block n = next(c);
                if (n == null) line = "Open time";
                else { line = "Next: " + n.title; until = "at " + Fmt.time(n.begin); }
            }
        } else line = "Open the app to connect your calendar";
        for (int id : ids) {
            RemoteViews v = new RemoteViews(c.getPackageName(), layout);
            v.setInt(R.id.root, "setBackgroundResource", bg);
            v.setViewVisibility(R.id.date, Store.bool(c, "wDate", true) ? View.VISIBLE : View.GONE);
            v.setViewVisibility(R.id.blockRow, showBlock ? View.VISIBLE : View.GONE);
            v.setViewVisibility(R.id.dot, now ? View.VISIBLE : View.GONE);
            v.setTextViewText(R.id.block, line);
            v.setTextViewText(R.id.until, until);
            v.setTextColor(R.id.block, now ? 0xFFECE7F5 : 0xFFA69DBB);
            Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, 20, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            m.updateAppWidget(id, v);
        }
    }
}
