package com.watchman.induetime;

import android.Manifest;
import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.watchman.induetime.shared.Palette;
import com.watchman.induetime.shared.WatchData;

import java.util.Calendar;
import java.util.Locale;

/** Today and lens choice on the wrist. */
public class WatchActivity extends Activity {

    private ScrollView scroll;
    private LinearLayout page;
    private boolean lensScreen, phoneScreen;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable changed = new Runnable() {
        public void run() { h.post(new Runnable() { public void run() { render(); } }); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Dial.serif == null) Dial.serif = load("fraunces.ttf", Typeface.SERIF);
        if (Dial.mono == null) Dial.mono = load("plexmono.ttf", Typeface.MONOSPACE);
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(Palette.NIGHT);
        scroll.setFillViewport(true);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        int side = (int) (getResources().getDisplayMetrics().widthPixels * 0.14f);
        page.setPadding(side, dp(36), side, dp(48));
        scroll.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    private Typeface load(String f, Typeface fb) {
        try { return Typeface.createFromAsset(getAssets(), f); } catch (Exception e) { return fb; }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Link.listen(changed);
        render();
        Link.pullAsync(this, 30000);
        Alarms.schedule(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Link.unlisten(changed);
    }

    @Override
    public void onBackPressed() {
        if (lensScreen || phoneScreen) { lensScreen = false; phoneScreen = false; render(); return; }
        super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int r, String[] p, int[] g) {
        super.onRequestPermissionsResult(r, p, g);
        render();
        Link.pullAsync(this, 0);
    }

    private void render() {
        page.removeAllViews();
        WatchData raw = Link.raw(this);
        String lensId = Link.lensId(this, raw);
        WatchData d = raw.filtered(lensId);

        if (!Link.allowed(this)) {
            page.addView(label("In Due Time"));
            page.addView(text("Allow Bluetooth so your phone can send your day.", 14, Palette.LINEN), lp(8));
            TextView b = button("Allow");
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1); }
            });
            page.addView(b, lp(14));
            return;
        }

        if (phoneScreen || Link.phone(this).length() == 0) {
            page.addView(label("Your phone"));
            page.addView(text("Pick the phone that runs In Due Time.", 13, Palette.MUTED), lp(6));
            try {
                android.bluetooth.BluetoothManager bm = (android.bluetooth.BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
                android.bluetooth.BluetoothAdapter a = bm == null ? null : bm.getAdapter();
                if (a != null) for (final android.bluetooth.BluetoothDevice dev : a.getBondedDevices()) {
                    final String nm = dev.getName() == null ? dev.getAddress() : dev.getName();
                    LinearLayout r = row(dev.getAddress().equals(Link.phone(this)));
                    r.addView(text(nm, 13, Palette.LINEN), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    r.setClickable(true);
                    r.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            Link.setPhone(WatchActivity.this, dev.getAddress(), nm);
                            phoneScreen = false;
                            render();
                            Link.pullAsync(WatchActivity.this, 0);
                        }
                    });
                    page.addView(r, lp(6));
                }
            } catch (SecurityException e) {
                page.addView(text("Bluetooth access is off.", 13, Palette.MUTED), lp(8));
            }
            return;
        }

        if (lensScreen) {
            page.addView(label("Lens"));
            addLens("", "Automatic", lensId.length() == 0 && !Link.p(this).getBoolean("pending", false) || (Link.p(this).getBoolean("pending", false) && lensId.length() == 0));
            for (WatchData.LensDef l : raw.lenses) addLens(l.id, l.name, l.id.equals(lensId));
            if (raw.lenses.isEmpty()) page.addView(text("No lenses yet. Make them on your phone.", 13, Palette.MUTED), lp(8));
            TextView back = button("Back");
            back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { lensScreen = false; render(); } });
            page.addView(back, lp(12));
            return;
        }

        long now = System.currentTimeMillis();
        page.addView(label("Today" + (d.lens.length() > 0 ? " · " + d.lens + " lens" : "")));

        String tap = Link.p(this).getString("tapTitle", null);
        long tapAt = Link.p(this).getLong("lastTap", 0);
        if (tap != null && now - tapAt < 5 * 60000L) {
            LinearLayout card = row(true);
            card.setOrientation(LinearLayout.VERTICAL);
            card.addView(text(tap + " ended", 14, Palette.LINEN));
            card.addView(text("Check in on your phone", 11, Palette.MUTED));
            page.addView(card, lp(8));
        }

        WatchData.Item cur = d.current(now);
        int shown = 0;
        if (cur != null) {
            LinearLayout r = row(true);
            r.addView(text(cur.title, 14, Palette.LINEN), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            r.addView(mono(Math.max(0, (cur.end - now + 59999) / 60000) + " MIN"));
            page.addView(r, lp(8));
            shown++;
        }
        for (WatchData.Item i : d.items) {
            if (i.start <= now || shown >= 4) continue;
            LinearLayout r = row(false);
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            if (i.google) g.setStroke(dp(1), Palette.LINEN); else g.setColor(i.color);
            dot.setBackground(g);
            r.addView(dot, new LinearLayout.LayoutParams(dp(7), dp(7)));
            TextView t = text(i.title, 13, Palette.LINEN);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tl.leftMargin = dp(8);
            r.addView(t, tl);
            r.addView(mono(hm(i.start)));
            page.addView(r, lp(6));
            shown++;
        }
        if (shown == 0) page.addView(text(raw.sentAt == 0 ? "Nothing from your phone yet." : "Nothing planned for now.", 13, Palette.MUTED), lp(10));

        TextView lens = button("Change lens");
        lens.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { lensScreen = true; render(); } });
        page.addView(lens, lp(12));

        TextView sync = button("Sync now");
        sync.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Link.pullAsync(WatchActivity.this, 0); v.postDelayed(new Runnable() { public void run() { render(); } }, 2500); }
        });
        page.addView(sync, lp(8));
        TextView st = text(Link.state(this) + (raw.sentAt == 0 ? "" : " · " + hm(Link.p(this).getLong("got", raw.sentAt))), 10, Palette.FAINT);
        st.setGravity(Gravity.CENTER);
        page.addView(st, lp(10));
        TextView ph = text("Phone: " + Link.p(this).getString("phoneName", ""), 10, Palette.FAINT);
        ph.setGravity(Gravity.CENTER);
        ph.setClickable(true);
        ph.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { phoneScreen = true; render(); } });
        page.addView(ph, lp(4));
    }

    private void addLens(final String id, String name, boolean on) {
        LinearLayout r = row(on);
        r.addView(text(name, 14, Palette.LINEN), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (on) { TextView t = mono("ON"); t.setTextColor(Palette.GOLD); r.addView(t); }
        r.setClickable(true);
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Link.chooseLens(WatchActivity.this, id); lensScreen = false; render(); }
        });
        page.addView(r, lp(6));
    }

    private LinearLayout row(boolean gold) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(12), dp(8), dp(12), dp(8));
        r.setMinimumHeight(dp(40));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(gold ? 0x1FD8B25E : Palette.DUSK);
        g.setStroke(dp(1), gold ? 0x99D8B25E : Palette.LINE);
        r.setBackground(g);
        return r;
    }

    private TextView button(String s) {
        TextView t = text(s, 14, Palette.NIGHT);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(40));
        t.setPadding(dp(16), dp(6), dp(16), dp(6));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(999));
        g.setColor(Palette.LINEN);
        t.setBackground(g);
        t.setClickable(true);
        return t;
    }

    private TextView label(String s) {
        TextView t = mono(s.toUpperCase(Locale.ENGLISH));
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2);
        return t;
    }

    private TextView mono(String s) {
        TextView t = text(s, 10, Palette.MUTED);
        t.setTypeface(Dial.mono);
        t.setLetterSpacing(0.12f);
        return t;
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private LinearLayout.LayoutParams lp(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(top);
        return p;
    }

    private int dp(float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    private static String hm(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return String.format(Locale.ENGLISH, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }
}
