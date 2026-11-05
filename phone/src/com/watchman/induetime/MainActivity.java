package com.watchman.induetime;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.watchman.induetime.shared.FaceRenderer;
import com.watchman.induetime.shared.FaceStyle;
import com.watchman.induetime.shared.Palette;
import com.watchman.induetime.shared.WatchData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {

    // Screen stack

    static final class Screen {
        final String id;
        Cal.Block block;
        Store.Lens lens;
        Store.Category cat;
        long day;
        Edit edit;
        Screen(String id) { this.id = id; }
    }

    /** Working copy for the block editor. */
    static final class Edit {
        Cal.Block old;
        String title = "", notes = "", cat = "work", repeat = "Never";
        long begin, end;
        boolean onlyThis = true;
    }

    private final ArrayList<Screen> stack = new ArrayList<Screen>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Ui u;
    private FrameLayout root, overlay;
    private ScrollView scroll;
    private LinearLayout page, nav;
    private int insetTop, insetBottom, insetLeft, insetRight;
    private long nowDay;

    private final Runnable tick = new Runnable() {
        public void run() {
            Screen s = top();
            if (s != null && "now".equals(s.id) && overlay.getVisibility() != View.VISIBLE) render(false);
            ui.postDelayed(this, 30000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        u = new Ui(this);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        root = new FrameLayout(this);
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientCenter(0f, 0f);
        glow.setGradientRadius(u.dp(420));
        glow.setColors(new int[]{0x4D5B47A8, Palette.NIGHT});
        root.setBackground(glow);

        scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        page = u.col();
        scroll.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        nav = buildNav();
        root.addView(nav);

        overlay = new FrameLayout(this);
        overlay.setVisibility(View.GONE);
        root.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets s = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    android.graphics.Insets ime = in.getInsets(WindowInsets.Type.ime());
                    insetLeft = s.left; insetTop = s.top; insetRight = s.right; insetBottom = Math.max(s.bottom, ime.bottom);
                } else {
                    insetLeft = in.getSystemWindowInsetLeft(); insetTop = in.getSystemWindowInsetTop();
                    insetRight = in.getSystemWindowInsetRight(); insetBottom = in.getSystemWindowInsetBottom();
                }
                applyInsets();
                return in;
            }
        });
        setContentView(root);
        stack.add(new Screen("now"));
        handleIntent(getIntent());
    }

    private void applyInsets() {
        int side = u.dp(20);
        boolean navOn = nav.getVisibility() == View.VISIBLE;
        scroll.setPadding(Math.max(side, insetLeft + u.dp(12)), insetTop + u.dp(14), Math.max(side, insetRight + u.dp(12)),
                insetBottom + (navOn ? u.dp(56 + 14 + 24) : u.dp(28)));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) nav.getLayoutParams();
        lp.leftMargin = Math.max(u.dp(16), insetLeft + u.dp(12));
        lp.rightMargin = Math.max(u.dp(16), insetRight + u.dp(12));
        lp.bottomMargin = insetBottom + u.dp(14);
        nav.setLayoutParams(lp);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        handleIntent(i);
    }

    private void handleIntent(Intent i) {
        if (i == null || !i.hasExtra("eid")) return;
        long eid = i.getLongExtra("eid", -1), begin = i.getLongExtra("begin", 0);
        String mode = i.getStringExtra("mode");
        i.removeExtra("eid");
        final Cal.Block b = find(eid, begin);
        if (b == null) return;
        final boolean notYet = "notyet".equals(mode);
        ui.post(new Runnable() { public void run() { showCheckin(b, notYet); } });
    }

    private Cal.Block find(long eid, long begin) {
        for (Cal.Block b : Cal.between(this, begin - 60000, begin + 60000)) if (b.eventId == eid && b.begin == begin) return b;
        return null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        nowDay = Fmt.startOfDay(System.currentTimeMillis());
        render(false);
        ui.postDelayed(tick, 30000);
        if (Cal.allowed(this)) App.changed(this);
        WatchLink.ensure(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
    }

    @Override
    public void onBackPressed() {
        if (overlay.getVisibility() == View.VISIBLE) { hideOverlay(); return; }
        if (stack.size() > 1) { stack.remove(stack.size() - 1); render(true); return; }
        super.onBackPressed();
    }

    private Screen top() { return stack.isEmpty() ? null : stack.get(stack.size() - 1); }

    void go(Screen s) { stack.add(s); render(true); }
    void go(String id) { go(new Screen(id)); }
    void back() { if (stack.size() > 1) stack.remove(stack.size() - 1); render(true); }
    void replace(Screen s) { stack.set(stack.size() - 1, s); render(true); }
    void tab(String id) { stack.clear(); stack.add(new Screen(id)); render(true); }

    // Rendering

    void render(boolean toTop) {
        page.removeAllViews();
        Screen s = top();
        if (!Cal.allowed(this)) { welcome(); setNav(null); return; }
        String id = s.id;
        if ("now".equals(id)) now();
        else if ("timeline".equals(id)) timeline(s);
        else if ("block".equals(id)) blockDetail(s.block);
        else if ("edit".equals(id)) editBlock(s.edit);
        else if ("agenda".equals(id)) agenda();
        else if ("lenses".equals(id)) lenses();
        else if ("lens".equals(id)) lensEdit(s.lens);
        else if ("more".equals(id)) more();
        else if ("categories".equals(id)) categories();
        else if ("category".equals(id)) categoryEdit(s.cat);
        else if ("calendars".equals(id)) calendars();
        else if ("widget".equals(id)) widgetSettings();
        else if ("watch".equals(id)) watchSettings();
        else if ("checkin".equals(id)) checkinSettings();
        else if ("about".equals(id)) about();
        String t = tabOf(id);
        setNav("edit".equals(id) || "lens".equals(id) || "category".equals(id) ? null : t);
        if (toTop) scroll.scrollTo(0, 0);
    }

    static String tabOf(String id) {
        if ("now".equals(id) || "timeline".equals(id) || "block".equals(id) || "edit".equals(id)) return "day";
        if ("agenda".equals(id)) return "agenda";
        if ("lenses".equals(id) || "lens".equals(id)) return "lenses";
        return "more";
    }

    private LinearLayout buildNav() {
        LinearLayout n = u.row();
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(u.dp(28));
        g.setColor(0xF0171320);
        g.setStroke(u.dp(1), Palette.LINE);
        n.setBackground(g);
        n.setElevation(u.dp(10));
        String[][] tabs = {{"day", "Day", "now"}, {"agenda", "Agenda", "agenda"}, {"lenses", "Lenses", "lenses"}, {"more", "More", "more"}};
        for (final String[] t : tabs) {
            TextView v = u.label(t[1]);
            v.setGravity(Gravity.CENTER);
            v.setTag(t[0]);
            v.setMinHeight(u.dp(56));
            v.setClickable(true);
            v.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { tab(t[2]); } });
            n.addView(v, new LinearLayout.LayoutParams(0, u.dp(56), 1f));
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u.dp(56), Gravity.BOTTOM);
        n.setLayoutParams(lp);
        return n;
    }

    private void setNav(String active) {
        nav.setVisibility(active == null ? View.GONE : View.VISIBLE);
        for (int i = 0; i < nav.getChildCount(); i++) {
            TextView v = (TextView) nav.getChildAt(i);
            v.setTextColor(v.getTag().equals(active) ? Palette.GOLD : Palette.FAINT);
        }
        applyInsets();
    }

    private void add(View v, int top) { page.addView(v, u.lp(top)); }
    private void addWrap(View v, int top) { page.addView(v, u.wrap(top)); }

    private void header(String eyebrow, String title) {
        add(u.label(eyebrow), 6);
        add(u.title(title, 30), 4);
    }

    /** Back on the left, optional action on the right. */
    private void topBar(String backText, String action, View.OnClickListener onAction) {
        LinearLayout r = u.row();
        TextView b = u.button("‹  " + backText, 3);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(0, 0, u.dp(12), 0);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { back(); } });
        r.addView(b, new LinearLayout.LayoutParams(0, u.dp(44), 1f));
        if (action != null) {
            TextView a = u.button(action, 1);
            a.setOnClickListener(onAction);
            r.addView(a);
        }
        add(r, 0);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // Welcome and permissions

    private void welcome() {
        add(u.label("Welcome"), 24);
        add(u.title("In Due Time", 40), 6);
        add(u.quiet("A planner that keeps your day unhurried. It works with your Google Calendar through this phone."), 10);
        LinearLayout card = u.card();
        card.addView(u.label("One step first"));
        card.addView(u.title("Allow calendar access", 20), u.lp(6));
        card.addView(u.quiet("Your blocks are saved to your own Google Calendar. Nothing leaves your devices otherwise."), u.lp(6));
        TextView go = u.button("Allow access", 1);
        go.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                List<String> p = new ArrayList<String>();
                p.add(Manifest.permission.READ_CALENDAR);
                p.add(Manifest.permission.WRITE_CALENDAR);
                if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS);
                requestPermissions(p.toArray(new String[0]), 1);
            }
        });
        card.addView(go, u.wrap(16));
        add(card, 24);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == 1 && Cal.allowed(this)) App.changed(this);
        render(false);
        if (req == 3) WatchLink.ensure(this);
        if (req == 2) {
            Screen s = top();
            if (s != null && "lens".equals(s.id)) render(false);
        }
    }

    // Day: This moment (D2)

    private View dateButton(final long day, final boolean toTimeline) {
        LinearLayout b = u.col();
        b.setPadding(u.dp(12), u.dp(8), u.dp(12), u.dp(8));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(u.dp(12));
        g.setStroke(u.dp(1), 0x40B7A3F0);
        b.setBackground(g);
        b.addView(u.label(Fmt.dayName(day)));
        b.addView(u.title(Fmt.dayMonth(day), 22));
        b.setClickable(true);
        b.setContentDescription(toTimeline ? "Show the timeline" : "Show this moment");
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (toTimeline) { Screen s = new Screen("timeline"); s.day = day; replace(s); }
                else replace(new Screen("now"));
            }
        });
        return b;
    }

    private void now() {
        long now = System.currentTimeMillis();
        long day = Fmt.startOfDay(now);
        Store.Lens lens = Store.activeAny(this);

        LinearLayout top = u.row();
        top.addView(dateButton(day, true));
        View sp = new View(this);
        top.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView ln = u.label(lens == null ? "No lens" : "Lens · " + lens.name);
        ln.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { tab("lenses"); } });
        top.addView(ln);
        add(top, 6);

        List<Cal.Block> soon = Cal.between(this, day, now + 36 * 3600000L);
        Cal.Block cur = null;
        List<Cal.Block> allDay = new ArrayList<Cal.Block>();
        List<Cal.Block> next = new ArrayList<Cal.Block>();
        for (Cal.Block b : soon) {
            if (b.allDay) { if (b.begin <= now && b.end > now) allDay.add(b); continue; }
            if (b.isNow(now) && (cur == null || (cur.google && !b.google))) cur = b;
        }
        for (Cal.Block b : soon) if (!b.allDay && b.begin > now && b != cur && next.size() < 3) next.add(b);

        if (!allDay.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Cal.Block b : allDay) { if (sb.length() > 0) sb.append(" · "); sb.append(b.title); }
            add(u.quiet("All day: " + sb), 10);
        }

        if (cur != null) {
            final Cal.Block b = cur;
            add(u.label("Now · " + App.catName(this, b)), 26);
            add(u.title(b.title, 32), 4);
            LinearLayout hero = u.card(true);
            hero.setPadding(u.dp(20), u.dp(18), u.dp(20), u.dp(18));
            hero.addView(u.label("Minutes left"));
            long left = Math.max(0, (b.end - now + 59999) / 60000), total = Math.max(1, (b.end - b.begin) / 60000);
            LinearLayout num = u.row();
            num.setGravity(Gravity.BOTTOM);
            TextView big = u.text(String.valueOf(left), 64, Palette.LINEN);
            big.setTypeface(Ui.serifLight);
            big.setIncludeFontPadding(false);
            num.addView(big);
            TextView of = u.quiet("of " + total);
            LinearLayout.LayoutParams ol = u.gap(8);
            ol.bottomMargin = u.dp(10);
            num.addView(of, ol);
            hero.addView(num, u.lp(4));
            hero.addView(progress((float) (now - b.begin) / (b.end - b.begin)), u.lp(10));
            hero.addView(u.quiet(Fmt.time(b.begin) + " to " + Fmt.time(b.end)), u.lp(8));
            LinearLayout acts = u.row();
            boolean done = Store.isDone(this, b);
            TextView d = u.button(done ? "Done" : "Mark done", 1);
            d.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { Store.setDone(MainActivity.this, b, !Store.isDone(MainActivity.this, b)); render(false); }
            });
            acts.addView(d);
            TextView mv = u.button("Move", 0);
            mv.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openEditor(b); } });
            acts.addView(mv, u.gap(8));
            TextView det = u.button("Details", 3);
            det.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openBlock(b); } });
            acts.addView(det, u.gap(4));
            hero.addView(acts, u.lp(16));
            add(hero, 16);
        } else {
            add(u.label("Now"), 26);
            add(u.title("Open time", 32), 4);
            LinearLayout hero = u.card();
            if (!next.isEmpty()) {
                Cal.Block n = next.get(0);
                hero.addView(u.label("Next block in"));
                TextView big = u.text(Fmt.duration(n.begin - now), 40, Palette.LINEN);
                big.setTypeface(Ui.serifLight);
                hero.addView(big, u.lp(4));
                hero.addView(u.quiet(n.title + " at " + Fmt.time(n.begin)), u.lp(4));
            } else {
                hero.addView(u.quiet("Nothing planned for now. Shape the next hour with a block."));
            }
            TextView nb = u.button("New block", 1);
            nb.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openNew(System.currentTimeMillis()); } });
            hero.addView(nb, u.wrap(16));
            add(hero, 16);
        }

        add(u.label("Up next"), 28);
        if (next.isEmpty()) add(u.quiet("Nothing else in the next day and a half."), 8);
        for (final Cal.Block b : next) {
            LinearLayout r = blockRow(b, now);
            add(r, 0);
            add(u.rule(), 0);
        }
        if (cur != null) {
            TextView nb = u.button("New block", 0);
            nb.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openNew(System.currentTimeMillis()); } });
            addWrap(nb, 18);
        }
    }

    private LinearLayout blockRow(final Cal.Block b, long now) {
        LinearLayout r = u.row();
        r.setMinimumHeight(u.dp(54));
        r.setPadding(0, u.dp(8), 0, u.dp(8));
        TextView t = u.label(Fmt.time(b.begin));
        r.addView(t, new LinearLayout.LayoutParams(u.dp(52), ViewGroup.LayoutParams.WRAP_CONTENT));
        View dot = u.dot(App.color(this, b), 9);
        if (b.google) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setStroke(u.dp(1), Palette.LINEN);
            dot.setBackground(g);
        }
        r.addView(dot);
        LinearLayout c = u.col();
        TextView n = u.body(b.title);
        n.setMaxLines(1);
        n.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(n);
        String day = Fmt.startOfDay(b.begin) == Fmt.startOfDay(now) ? "" : Fmt.relDay(b.begin) + " · ";
        c.addView(u.label(day + App.catName(this, b) + (Store.isDone(this, b) ? " · Done" : "")));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cl.leftMargin = u.dp(12);
        r.addView(c, cl);
        r.addView(u.text("›", 20, Palette.FAINT));
        r.setClickable(true);
        r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openBlock(b); } });
        return r;
    }

    private View progress(float f) {
        f = Math.max(0, Math.min(1, f));
        FrameLayout bar = new FrameLayout(this);
        GradientDrawable track = new GradientDrawable();
        track.setCornerRadius(u.dp(3));
        track.setColor(0x24B7A3F0);
        bar.setBackground(track);
        View fill = new View(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Palette.AMETHYST, Palette.LAV});
        g.setCornerRadius(u.dp(3));
        fill.setBackground(g);
        final float frac = f;
        bar.addView(fill, new FrameLayout.LayoutParams(0, u.dp(5)));
        bar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u.dp(5)));
        final View fv = fill;
        bar.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                int w = (int) ((r - l) * frac);
                if (fv.getLayoutParams().width != w) {
                    fv.getLayoutParams().width = w;
                    fv.post(new Runnable() { public void run() { fv.requestLayout(); } });
                }
            }
        });
        return bar;
    }

    void openBlock(Cal.Block b) { Screen s = new Screen("block"); s.block = b; go(s); }

    void openEditor(Cal.Block b) {
        Edit e = new Edit();
        e.old = b;
        e.title = b.title;
        e.notes = Cal.userNotes(b.desc);
        e.cat = b.google ? "" : b.cat;
        e.begin = b.begin;
        e.end = b.end;
        e.repeat = Cal.choice(b.rrule);
        Screen s = new Screen("edit");
        s.edit = e;
        go(s);
    }

    void openNew(long at) {
        Edit e = new Edit();
        long m = 15 * 60000L;
        e.begin = ((at + m - 1) / m) * m;
        e.end = e.begin + 60 * 60000L;
        List<Store.Category> cats = Store.categories(this);
        e.cat = cats.isEmpty() ? "" : cats.get(0).id;
        Screen s = new Screen("edit");
        s.edit = e;
        go(s);
    }

    // Day: Timeline (D1)

    private void timeline(final Screen s) {
        final long day = s.day == 0 ? Fmt.startOfDay(System.currentTimeMillis()) : s.day;
        LinearLayout top = u.row();
        boolean isToday = day == Fmt.startOfDay(System.currentTimeMillis());
        top.addView(isToday ? dateButton(day, false) : dateButton(day, false));
        View sp = new View(this);
        top.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView prev = u.button("‹ Prev", 3);
        prev.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Screen n = new Screen("timeline"); n.day = Fmt.addDays(day, -1); replace(n); } });
        TextView next = u.button("Next ›", 3);
        next.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Screen n = new Screen("timeline"); n.day = Fmt.addDays(day, 1); replace(n); } });
        top.addView(prev);
        top.addView(next);
        add(top, 6);

        List<Cal.Block> all = Cal.day(this, day);
        List<Cal.Block> timed = new ArrayList<Cal.Block>();
        StringBuilder allDay = new StringBuilder();
        for (Cal.Block b : all) {
            if (b.allDay) { if (allDay.length() > 0) allDay.append(" · "); allDay.append(b.title); }
            else timed.add(b);
        }
        if (allDay.length() > 0) add(u.quiet("All day: " + allDay), 10);

        int startH = 5, endH = 24;
        for (Cal.Block b : timed) {
            startH = Math.min(startH, Fmt.minuteOfDay(Math.max(b.begin, day)) / 60);
        }
        final int H = u.dp(60), rail = u.dp(46);
        final int fs = startH;
        FrameLayout tl = new FrameLayout(this);
        int height = (endH - startH) * H + u.dp(8);
        for (int h = startH; h <= endH; h++) {
            final int hour = h;
            View line = new View(this);
            line.setBackgroundColor(Palette.LINE);
            FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, u.dp(0.7f)));
            ll.topMargin = (h - startH) * H;
            ll.leftMargin = rail - u.dp(6);
            tl.addView(line, ll);
            TextView lab = u.label(String.format(java.util.Locale.ENGLISH, "%02d", h % 24));
            FrameLayout.LayoutParams labl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labl.topMargin = (h - startH) * H - u.dp(8);
            tl.addView(lab, labl);
            if (h < endH) {
                View slot = new View(this);
                slot.setContentDescription("New block at " + h + ":00");
                slot.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { openNew(day + hour * 3600000L); }
                });
                FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, H);
                sl.topMargin = (h - startH) * H;
                sl.leftMargin = rail;
                tl.addView(slot, sl);
            }
        }
        // Lay out blocks in columns where they overlap
        int n = timed.size();
        int[] colOf = new int[n], cols = new int[n];
        List<long[]> active = new ArrayList<long[]>();
        int clusterStart = 0, maxCols = 0;
        long clusterEnd = 0;
        for (int i = 0; i < n; i++) {
            Cal.Block b = timed.get(i);
            if (i > 0 && b.begin >= clusterEnd) {
                for (int j = clusterStart; j < i; j++) cols[j] = maxCols;
                clusterStart = i; maxCols = 0; active.clear();
            }
            int col = 0;
            boolean placed = false;
            for (int k = 0; k < active.size(); k++) {
                if (active.get(k)[0] <= b.begin) { active.get(k)[0] = b.end; col = k; placed = true; break; }
            }
            if (!placed) { active.add(new long[]{b.end}); col = active.size() - 1; }
            colOf[i] = col;
            maxCols = Math.max(maxCols, active.size());
            clusterEnd = Math.max(clusterEnd, b.end);
        }
        for (int j = clusterStart; j < n; j++) cols[j] = Math.max(1, maxCols);

        final int width = getResources().getDisplayMetrics().widthPixels - scroll.getPaddingLeft() - scroll.getPaddingRight() - rail;
        long now = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            final Cal.Block b = timed.get(i);
            long s0 = Math.max(b.begin, day), e0 = Math.min(b.end, Fmt.addDays(day, 1));
            float top0 = (s0 - day) / 3600000f - fs, dur = (e0 - s0) / 3600000f;
            LinearLayout card = u.col();
            card.setPadding(u.dp(12), u.dp(6), u.dp(10), u.dp(6));
            boolean isNow = b.isNow(now);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(u.dp(12));
            g.setColor(isNow ? 0x1FD8B25E : b.google ? 0x08ECE7F5 : 0x1AB7A3F0);
            g.setStroke(u.dp(1), isNow ? 0x99D8B25E : b.google ? 0x55ECE7F5 : 0x33B7A3F0);
            card.setBackground(g);
            View stripe = new View(this);
            GradientDrawable sg = new GradientDrawable();
            sg.setCornerRadius(u.dp(2));
            sg.setColor(isNow ? Palette.GOLD : App.color(this, b));
            stripe.setBackground(sg);
            TextView t = u.text(b.title, 13.5f, Palette.LINEN);
            t.setMaxLines(dur < 0.6f ? 1 : 2);
            t.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(t);
            if (dur >= 0.6f) {
                TextView l = u.label(Fmt.time(b.begin) + " to " + Fmt.time(b.end) + (b.google ? " · Google" : ""));
                l.setTextSize(9);
                l.setMaxLines(1);
                card.addView(l);
            }
            card.setClickable(true);
            card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openBlock(b); } });
            int w = width / cols[i];
            FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(w - u.dp(4), Math.max(u.dp(26), (int) (dur * H) - u.dp(4)));
            cl.topMargin = (int) (top0 * H) + u.dp(2);
            cl.leftMargin = rail + colOf[i] * w;
            tl.addView(card, cl);
            FrameLayout.LayoutParams stl = new FrameLayout.LayoutParams(u.dp(3), Math.max(u.dp(18), (int) (dur * H) - u.dp(16)));
            stl.topMargin = cl.topMargin + u.dp(6);
            stl.leftMargin = cl.leftMargin + u.dp(4);
            tl.addView(stripe, stl);
        }
        if (day == Fmt.startOfDay(now)) {
            View nl = new View(this);
            nl.setBackgroundColor(Palette.GOLD);
            FrameLayout.LayoutParams nll = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u.dp(1.5f));
            final int y = (int) (((now - day) / 3600000f - fs) * H);
            nll.topMargin = y;
            nll.leftMargin = rail - u.dp(10);
            tl.addView(nl, nll);
            View nd = u.dot(Palette.GOLD, 8);
            FrameLayout.LayoutParams ndl = new FrameLayout.LayoutParams(u.dp(8), u.dp(8));
            ndl.topMargin = y - u.dp(3);
            ndl.leftMargin = rail - u.dp(14);
            tl.addView(nd, ndl);
            scroll.post(new Runnable() { public void run() { scroll.scrollTo(0, Math.max(0, y - u.dp(160))); } });
        }
        page.addView(tl, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) tl.getLayoutParams();
        p.topMargin = u.dp(22);
        TextView nb = u.button("New block", 0);
        nb.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                long t = day == Fmt.startOfDay(System.currentTimeMillis()) ? System.currentTimeMillis() : day + 9 * 3600000L;
                openNew(t);
            }
        });
        addWrap(nb, 16);
    }

    // Block details

    private void blockDetail(final Cal.Block b) {
        topBar("Back", b.google && false ? null : "Edit", new View.OnClickListener() { public void onClick(View v) { openEditor(b); } });
        header(App.catName(this, b), b.title);
        LinearLayout card = u.card();
        card.addView(u.listRow("When", Fmt.relDay(b.begin) + ", " + (b.allDay ? "all day" : Fmt.time(b.begin) + " to " + Fmt.time(b.end)), false));
        card.addView(u.rule());
        card.addView(u.listRow("Repeats", Cal.choice(b.rrule), false));
        card.addView(u.rule());
        String calName = "Google";
        for (Cal.Calendar k : Cal.calendars(this)) if (k.id == b.calId) calName = k.name;
        card.addView(u.listRow("Calendar", calName, false));
        if (!b.google) {
            card.addView(u.rule());
            card.addView(u.listRow("When it ends", Store.bool(this, "tap", true) || Store.bool(this, "checkin", true) ? "Wrist tap and check-in" : "Nothing", false));
        }
        add(card, 20);
        String notes = Cal.userNotes(b.desc);
        LinearLayout nc = u.card();
        nc.addView(u.label("Notes"));
        nc.addView(notes.length() > 0 ? u.body(notes) : u.quiet("Linking a note comes later with Watchman's Notebook."), u.lp(6));
        add(nc, 12);
        LinearLayout acts = u.row();
        final boolean done = Store.isDone(this, b);
        TextView d = u.button(done ? "Mark not done" : "Mark done", 1);
        d.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Store.setDone(MainActivity.this, b, !done); toast(done ? "Marked not done" : "Marked done"); render(false); }
        });
        acts.addView(d);
        TextView del = u.button("Delete", 3);
        del.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { confirmDelete(b); } });
        acts.addView(del, u.gap(8));
        add(acts, 20);
    }

    private void confirmDelete(final Cal.Block b) {
        AlertDialog.Builder d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Delete " + b.title + "?");
        if (b.repeats()) {
            d.setItems(new String[]{"Only this one", "Every one in the series"}, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface di, int w) {
                    boolean ok = w == 0 ? Cal.deleteOne(MainActivity.this, b) : Cal.deleteAll(MainActivity.this, b);
                    afterDelete(ok);
                }
            });
        } else {
            d.setMessage("It will also be removed from Google Calendar.");
            d.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface di, int w) { afterDelete(Cal.deleteAll(MainActivity.this, b)); }
            });
        }
        d.setNegativeButton("Keep it", null).show();
    }

    private void afterDelete(boolean ok) {
        toast(ok ? "Block deleted" : "Could not delete. Check the calendar allows changes.");
        if (ok) { App.changed(this); back(); }
    }

    // Edit or new block

    private void editBlock(final Edit e) {
        final boolean isNew = e.old == null;
        topBar("Cancel", "Save", new View.OnClickListener() { public void onClick(View v) { saveBlock(e); } });
        header(isNew ? "New block" : "Edit block", isNew ? "Shape the time" : e.title);

        add(u.label("Title"), 22);
        final EditText title = u.field(e.title, "What is this time for?", false);
        title.addTextChangedListener(new SimpleWatcher() { void changed(String s) { e.title = s; } });
        add(title, 6);

        add(u.label("Day"), 18);
        final TextView dayF = u.pickField(Fmt.relDay(e.begin) + ", " + Fmt.dayMonth(e.begin));
        dayF.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                java.util.Calendar c = Fmt.cal(e.begin);
                new DatePickerDialog(MainActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert, new DatePickerDialog.OnDateSetListener() {
                    public void onDateSet(DatePicker p, int y, int m, int d) {
                        java.util.Calendar n = Fmt.cal(e.begin);
                        n.set(y, m, d);
                        long shift = n.getTimeInMillis() - e.begin;
                        e.begin += shift; e.end += shift;
                        render(false);
                    }
                }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show();
            }
        });
        add(dayF, 6);

        LinearLayout times = u.row();
        LinearLayout sc = u.col(), ec = u.col();
        sc.addView(u.label("Starts"));
        final TextView sF = u.pickField(Fmt.time(e.begin));
        sc.addView(sF, u.lp(6));
        ec.addView(u.label("Ends"));
        final TextView eF = u.pickField(Fmt.time(e.end));
        ec.addView(eF, u.lp(6));
        times.addView(sc, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        el.leftMargin = u.dp(10);
        times.addView(ec, el);
        add(times, 18);
        sF.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pickTime(e.begin, new TimeCb() { public void set(long t) { long d = e.end - e.begin; e.begin = t; e.end = t + d; render(false); } });
            }
        });
        eF.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pickTime(e.end, new TimeCb() { public void set(long t) { if (t <= e.begin) t += 86400000L; e.end = t; render(false); } });
            }
        });
        add(u.quiet(Fmt.duration(e.end - e.begin)), 6);

        add(u.label("Category"), 20);
        final List<Store.Category> cats = Store.categories(this);
        boolean google = e.old != null && e.old.google;
        int extra = google ? 1 : 0;
        String[] names = new String[cats.size() + extra];
        final boolean[] on = new boolean[names.length];
        for (int i = 0; i < cats.size(); i++) { names[i] = cats.get(i).name; on[i] = cats.get(i).id.equals(e.cat); }
        if (google) { names[cats.size()] = "Leave as Google event"; on[cats.size()] = e.cat.length() == 0; }
        add(u.chips(names, on, false, new Ui.OnPick() {
            public void picked(int i) { e.cat = i < cats.size() ? cats.get(i).id : ""; }
        }), 8);

        boolean repeating = e.old != null && e.old.repeats();
        if (repeating) {
            add(u.label("Change"), 20);
            add(u.segmented(new String[]{"This one", "Every one"}, e.onlyThis ? 0 : 1, new Ui.OnPick() {
                public void picked(int i) { e.onlyThis = i == 0; render(false); }
            }), 8);
            if (e.onlyThis) add(u.quiet("Only the day and time change for this one."), 6);
        }
        if (!repeating || !e.onlyThis) {
            add(u.label("Repeat"), 20);
            final String[] reps = {"Never", "Daily", "Weekdays", "Weekly"};
            int sel = 0;
            for (int i = 0; i < reps.length; i++) if (reps[i].equals(e.repeat)) sel = i;
            add(u.segmented(reps, sel, new Ui.OnPick() { public void picked(int i) { e.repeat = reps[i]; } }), 8);
        }

        add(u.label("Notes"), 20);
        final EditText notes = u.field(e.notes, "Anything to remember", true);
        notes.addTextChangedListener(new SimpleWatcher() { void changed(String s) { e.notes = s; } });
        add(notes, 6);

        String calName = "";
        long cal = e.old != null ? e.old.calId : Cal.blocksCal(this);
        for (Cal.Calendar k : Cal.calendars(this)) if (k.id == cal) calName = k.name;
        add(u.quiet("Saved to " + (calName.length() > 0 ? calName : "your Google Calendar") + "."), 18);

        if (!isNew) {
            TextView del = u.button("Delete block", 3);
            del.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { confirmDelete(e.old); } });
            addWrap(del, 10);
        }
    }

    interface TimeCb { void set(long t); }

    private void pickTime(final long base, final TimeCb cb) {
        java.util.Calendar c = Fmt.cal(base);
        new TimePickerDialog(this, android.R.style.Theme_DeviceDefault_Dialog_Alert, new TimePickerDialog.OnTimeSetListener() {
            public void onTimeSet(TimePicker p, int h, int m) {
                java.util.Calendar n = Fmt.cal(base);
                n.set(java.util.Calendar.HOUR_OF_DAY, h);
                n.set(java.util.Calendar.MINUTE, m);
                n.set(java.util.Calendar.SECOND, 0);
                n.set(java.util.Calendar.MILLISECOND, 0);
                cb.set(n.getTimeInMillis());
            }
        }, c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), true).show();
    }

    private void saveBlock(Edit e) {
        String title = e.title.trim().length() == 0 ? "Untitled block" : e.title.trim();
        if (e.end <= e.begin) { toast("The block needs to end after it starts."); return; }
        boolean ok;
        if (e.old == null) {
            ok = Cal.insert(this, title, e.notes, e.cat, e.begin, e.end, Cal.rule(e.repeat, e.begin)) >= 0;
        } else if (e.old.repeats() && e.onlyThis) {
            ok = Cal.moveOne(this, e.old, e.begin, e.end);
        } else {
            ok = Cal.update(this, e.old, title, e.notes, e.cat, e.begin, e.end, Cal.rule(e.repeat, e.begin));
        }
        if (!ok) { toast("Could not save. Check that the calendar allows changes."); return; }
        toast("Block saved");
        App.changed(this);
        // Return to the screen before the editor and drop stale details
        stack.remove(stack.size() - 1);
        if (top() != null && "block".equals(top().id)) stack.remove(stack.size() - 1);
        if (stack.isEmpty()) stack.add(new Screen("now"));
        render(true);
    }

    abstract static class SimpleWatcher implements android.text.TextWatcher {
        abstract void changed(String s);
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        public void onTextChanged(CharSequence s, int a, int b, int c) {}
        public void afterTextChanged(android.text.Editable s) { changed(s.toString()); }
    }

    // Agenda (V3)

    private void agenda() {
        header("Agenda", "Coming up");
        long now = System.currentTimeMillis();
        long day = Fmt.startOfDay(now);
        List<Cal.Block> all = Cal.between(this, now, Fmt.addDays(day, 14));
        long shown = -1;
        int count = 0;
        LinearLayout group = null;
        for (Cal.Block b : all) {
            if (b.end <= now) continue;
            long d = Fmt.startOfDay(Math.max(b.begin, now));
            if (d != shown) {
                shown = d;
                LinearLayout h = u.row();
                TextView t = u.title(Fmt.relDay(d), 20);
                h.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                h.addView(u.label(Fmt.shortDay(d)));
                add(h, count == 0 ? 22 : 28);
                group = null;
            }
            count++;
            add(agendaRow(b, now), 6);
        }
        if (count == 0) {
            LinearLayout c = u.card();
            c.addView(u.title("A clear fortnight", 20));
            c.addView(u.quiet("Nothing is planned for the next two weeks."), u.lp(6));
            add(c, 22);
        }
    }

    private View agendaRow(final Cal.Block b, long now) {
        LinearLayout r = u.row();
        r.setMinimumHeight(u.dp(54));
        boolean isNow = b.isNow(now) && !b.allDay;
        if (isNow) r.setBackground(u.cardBg(true));
        r.setPadding(u.dp(12), u.dp(8), u.dp(12), u.dp(8));
        TextView t = u.label(b.allDay ? "All day" : Fmt.time(b.begin));
        r.addView(t, new LinearLayout.LayoutParams(u.dp(56), ViewGroup.LayoutParams.WRAP_CONTENT));
        View stripe = new View(this);
        stripe.setBackgroundColor(isNow ? Palette.GOLD : App.color(this, b));
        r.addView(stripe, new LinearLayout.LayoutParams(u.dp(2), u.dp(34)));
        LinearLayout c = u.col();
        TextView n = u.body(b.title);
        n.setMaxLines(2);
        n.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(n);
        c.addView(u.label(App.catName(this, b) + (isNow ? " · now" : "") + (Store.isDone(this, b) ? " · done" : "")));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cl.leftMargin = u.dp(12);
        r.addView(c, cl);
        r.setClickable(true);
        r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openBlock(b); } });
        return r;
    }

    // Lenses (L1)

    private void lenses() {
        header("What your devices see", "Lenses");
        final String o = Store.override(this);
        Store.Lens active = Store.activeAny(this);
        add(u.quiet(o.length() > 0 ? "Chosen by hand." : "Chosen by time and place."), 6);
        if (o.length() > 0) {
            TextView auto = u.button("Go back to automatic", 0);
            auto.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Store.setOverride(MainActivity.this, ""); App.changed(MainActivity.this); render(false); } });
            addWrap(auto, 12);
        }
        final List<Store.Lens> all = Store.lenses(this);
        for (final Store.Lens l : all) {
            boolean on = active != null && active.id.equals(l.id);
            LinearLayout card = u.card(on);
            LinearLayout h = u.row();
            h.addView(u.title(l.name, 22), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView pill = u.label(on ? "Active" : l.targetText());
            if (on) pill.setTextColor(Palette.GOLD);
            h.addView(pill);
            card.addView(h);
            card.addView(u.quiet(l.triggerText()), u.lp(6));
            LinearLayout sw = u.row();
            for (Store.Category k : Store.categories(this)) {
                if (!l.cats.contains(k.id)) continue;
                View bar = new View(this);
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(u.dp(3));
                g.setColor(k.color);
                bar.setBackground(g);
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(u.dp(16), u.dp(5));
                bl.rightMargin = u.dp(5);
                sw.addView(bar, bl);
            }
            if (l.google) {
                View bar = new View(this);
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(u.dp(3));
                g.setStroke(u.dp(1), Palette.LINEN);
                bar.setBackground(g);
                sw.addView(bar, new LinearLayout.LayoutParams(u.dp(16), u.dp(5)));
            }
            LinearLayout foot = u.row();
            foot.addView(sw, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (on) foot.addView(u.label(l.targetText()));
            card.addView(foot, u.lp(12));
            card.setClickable(true);
            card.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { Screen s = new Screen("lens"); s.lens = l; go(s); }
            });
            add(card, 14);
        }
        TextView nl = u.button("New lens", 0);
        nl.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Store.Lens l = new Store.Lens();
                l.id = "lens" + System.currentTimeMillis();
                l.name = "";
                l.useTime = false;
                Screen s = new Screen("lens");
                s.lens = l;
                go(s);
            }
        });
        addWrap(nl, 20);
    }

    // Lens editor (Editor A)

    private void lensEdit(final Store.Lens l) {
        final List<Store.Lens> all = Store.lenses(this);
        boolean exists = false;
        for (Store.Lens x : all) if (x.id.equals(l.id)) exists = true;
        final boolean isNew = !exists;
        topBar("Lenses", "Save", new View.OnClickListener() {
            public void onClick(View v) {
                if (l.name.trim().length() == 0) l.name = "Untitled lens";
                List<Store.Lens> list = Store.lenses(MainActivity.this);
                boolean found = false;
                for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(l.id)) { list.set(i, l); found = true; }
                if (!found) list.add(l);
                Store.saveLenses(MainActivity.this, list);
                App.changed(MainActivity.this);
                toast("Lens saved");
                back();
            }
        });
        header(isNew ? "New lens" : "Edit lens", l.name.length() == 0 ? "A new lens" : l.name);

        add(u.label("Name"), 22);
        EditText name = u.field(l.name, "For example Focus", false);
        name.addTextChangedListener(new SimpleWatcher() { void changed(String s) { l.name = s; } });
        add(name, 6);

        add(u.label("Shows on"), 20);
        final String[] tg = {"watch", "widget", "both"};
        int sel = 2;
        for (int i = 0; i < 3; i++) if (tg[i].equals(l.target)) sel = i;
        add(u.segmented(new String[]{"Watch", "Widget", "Both"}, sel, new Ui.OnPick() { public void picked(int i) { l.target = tg[i]; } }), 8);

        add(u.label("Turns on"), 24);
        add(u.switchRow("At set times", l.useTime, new Ui.OnToggle() { public void toggled(boolean on) { l.useTime = on; render(false); } }), 2);
        if (l.useTime) {
            String[] dn = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
            final boolean[] dOn = new boolean[7];
            for (int i = 0; i < 7; i++) dOn[i] = (l.days & (1 << i)) != 0;
            add(u.chips(dn, dOn, true, new Ui.OnPick() {
                public void picked(int i) { l.days = 0; for (int j = 0; j < 7; j++) if (dOn[j]) l.days |= 1 << j; }
            }), 4);
            LinearLayout times = u.row();
            final TextView f = u.pickField("From  " + Fmt.hm(l.from));
            final TextView t = u.pickField("Until  " + Fmt.hm(l.to));
            times.addView(f, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tl.leftMargin = u.dp(10);
            times.addView(t, tl);
            add(times, 12);
            f.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    new TimePickerDialog(MainActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert, new TimePickerDialog.OnTimeSetListener() {
                        public void onTimeSet(TimePicker p, int h, int m) { l.from = h * 60 + m; render(false); }
                    }, l.from / 60, l.from % 60, true).show();
                }
            });
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    new TimePickerDialog(MainActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert, new TimePickerDialog.OnTimeSetListener() {
                        public void onTimeSet(TimePicker p, int h, int m) { l.to = h * 60 + m; render(false); }
                    }, l.to / 60, l.to % 60, true).show();
                }
            });
        }
        add(u.switchRow("On a Wi-Fi network", l.wifi.length() > 0, new Ui.OnToggle() {
            public void toggled(boolean on) {
                if (on) {
                    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 2);
                        toast("Android shares the Wi-Fi name only with location access.");
                    }
                    String s = Wifi.ssid(MainActivity.this);
                    l.wifi = s == null ? "Work Wi-Fi" : s;
                } else l.wifi = "";
                render(false);
            }
        }), 8);
        if (l.wifi.length() > 0) {
            EditText w = u.field(l.wifi, "Wi-Fi name", false);
            w.addTextChangedListener(new SimpleWatcher() { void changed(String s) { l.wifi = s.trim(); } });
            add(w, 4);
            TextView cur = u.button("Use the Wi-Fi I'm on", 3);
            cur.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    String s = Wifi.ssid(MainActivity.this);
                    if (s == null) toast("Connect to Wi-Fi and allow location access first.");
                    else { l.wifi = s; render(false); }
                }
            });
            addWrap(cur, 4);
        }
        if (l.manualOnly()) add(u.quiet("With no trigger, this lens only turns on by hand."), 8);

        add(u.label("What it sees"), 24);
        for (final Store.Category k : Store.categories(this)) {
            add(u.switchRow(k.name, l.cats.contains(k.id), new Ui.OnToggle() {
                public void toggled(boolean on) { if (on) l.cats.add(k.id); else l.cats.remove(k.id); }
            }), 0);
            add(u.rule(), 0);
        }
        add(u.switchRow("Google events", l.google, new Ui.OnToggle() { public void toggled(boolean on) { l.google = on; } }), 0);

        add(u.label("Watch face"), 24);
        final List<FaceStyle> lf = Store.faces(this);
        String[] lfn = new String[lf.size() + 1];
        final boolean[] lfo = new boolean[lfn.length];
        lfn[0] = "Default";
        lfo[0] = l.face.length() == 0;
        for (int i = 0; i < lf.size(); i++) { lfn[i + 1] = lf.get(i).name; lfo[i + 1] = lf.get(i).id.equals(l.face); }
        add(u.chips(lfn, lfo, false, new Ui.OnPick() { public void picked(int i) { l.face = i == 0 ? "" : lf.get(i - 1).id; } }), 8);
        add(u.quiet("Default uses the face chosen under More, Watch face."), 6);


        if (!isNew) {
            LinearLayout acts = u.row();
            TextView use = u.button("Use this lens now", 0);
            use.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { Store.setOverride(MainActivity.this, l.id); App.changed(MainActivity.this); toast(l.name + " is on"); back(); }
            });
            acts.addView(use);
            TextView del = u.button("Delete lens", 3);
            del.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    List<Store.Lens> list = Store.lenses(MainActivity.this);
                    for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id.equals(l.id)) list.remove(i);
                    Store.saveLenses(MainActivity.this, list);
                    if (Store.override(MainActivity.this).equals(l.id)) Store.setOverride(MainActivity.this, "");
                    App.changed(MainActivity.this);
                    toast("Lens deleted");
                    back();
                }
            });
            acts.addView(del, u.gap(8));
            add(acts, 26);
        }
    }

    // More

    private void more() {
        header("Settings", "More");
        String[][] rows = {
                {"Categories", "categories"}, {"Google Calendar", "calendars"}, {"Clock widget", "widget"},
                {"Watch face", "watch"}, {"When a block ends", "checkin"}, {"About", "about"}};
        LinearLayout card = u.card();
        card.setPadding(u.dp(18), u.dp(4), u.dp(18), u.dp(4));
        for (int i = 0; i < rows.length; i++) {
            final String id = rows[i][1];
            LinearLayout r = u.listRow(rows[i][0], null, true);
            r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { go(id); } });
            card.addView(r);
            if (i < rows.length - 1) card.addView(u.rule());
        }
        add(card, 20);
        add(u.quiet("Tablet sync and the light theme come in a later version."), 16);
    }

    private void categories() {
        topBar("More", null, null);
        header("Colours of your day", "Categories");
        LinearLayout card = u.card();
        card.setPadding(u.dp(18), u.dp(4), u.dp(18), u.dp(4));
        List<Store.Category> cats = Store.categories(this);
        for (int i = 0; i < cats.size(); i++) {
            final Store.Category k = cats.get(i);
            LinearLayout r = u.row();
            r.setMinimumHeight(u.dp(56));
            r.addView(u.orb(k.color, 26));
            TextView n = u.body(k.name);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            nl.leftMargin = u.dp(14);
            r.addView(n, nl);
            r.addView(u.label(k.builtin ? "Default" : "Yours"));
            r.setClickable(true);
            r.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Screen s = new Screen("category"); s.cat = k; go(s); } });
            card.addView(r);
            if (i < cats.size() - 1) card.addView(u.rule());
        }
        add(card, 20);
        TextView nc = u.button("New category", 0);
        nc.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Store.Category k = new Store.Category();
                k.id = "c" + Long.toString(System.currentTimeMillis(), 36);
                k.name = "";
                k.color = Palette.CHOICES[5];
                k.icon = "book";
                Screen s = new Screen("category");
                s.cat = k;
                go(s);
            }
        });
        addWrap(nc, 18);
    }

    private void categoryEdit(final Store.Category k) {
        boolean exists = Store.category(this, k.id) != null;
        final boolean isNew = !exists;
        topBar("Categories", "Save", new View.OnClickListener() {
            public void onClick(View v) {
                if (k.name.trim().length() == 0) { toast("Give the category a name."); return; }
                List<Store.Category> list = Store.categories(MainActivity.this);
                boolean found = false;
                for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(k.id)) { list.set(i, k); found = true; }
                if (!found) list.add(k);
                Store.saveCategories(MainActivity.this, list);
                App.changed(MainActivity.this);
                toast(isNew ? "Category added" : "Category saved");
                back();
            }
        });
        header(isNew ? "New category" : "Edit category", k.name.length() == 0 ? "A new colour" : k.name);
        add(u.label("Name"), 22);
        EditText n = u.field(k.name, "For example Study", false);
        n.addTextChangedListener(new SimpleWatcher() { void changed(String s) { k.name = s.trim(); } });
        add(n, 6);
        add(u.label("Colour"), 20);
        Ui.FlowLayout sw = new Ui.FlowLayout(this, u.dp(12));
        for (final int col : Palette.CHOICES) {
            View o = u.orb(col, 34);
            if (col == k.color) {
                FrameLayout f = new FrameLayout(this);
                GradientDrawable ring = new GradientDrawable();
                ring.setShape(GradientDrawable.OVAL);
                ring.setStroke(u.dp(2), Palette.LINEN);
                f.setBackground(ring);
                f.setPadding(u.dp(4), u.dp(4), u.dp(4), u.dp(4));
                f.addView(o);
                f.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { } });
                sw.addView(f);
            } else {
                o.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { k.color = col; render(false); } });
                sw.addView(o);
            }
        }
        add(sw, 10);
        add(u.label("Icon"), 20);
        final String[] icons = Palette.ICONS;
        String[] labels = {"Bag", "Flame", "Moon", "Pen", "Person", "Book", "Leaf", "Dot"};
        final boolean[] on = new boolean[icons.length];
        for (int i = 0; i < icons.length; i++) on[i] = icons[i].equals(k.icon);
        add(u.chips(labels, on, false, new Ui.OnPick() { public void picked(int i) { k.icon = icons[i]; } }), 8);
        add(u.quiet("The icon shows on your watch face during this kind of block."), 8);
        if (!isNew && !k.builtin) {
            TextView del = u.button("Delete category", 3);
            del.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    List<Store.Category> list = Store.categories(MainActivity.this);
                    for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id.equals(k.id)) list.remove(i);
                    Store.saveCategories(MainActivity.this, list);
                    toast("Category deleted");
                    back();
                }
            });
            addWrap(del, 20);
        }
    }

    private void calendars() {
        topBar("More", null, null);
        header("Two-way", "Google Calendar");
        add(u.quiet("These are the calendars on this phone. Changes sync with Google through your phone's account."), 8);
        List<Cal.Calendar> cals = Cal.calendars(this);
        final Set<String> vis = Store.visibleCals(this);
        final boolean set = Store.visibleSet(this);
        add(u.label("Show in the app"), 22);
        LinearLayout card = u.card();
        card.setPadding(u.dp(18), u.dp(4), u.dp(18), u.dp(4));
        for (int i = 0; i < cals.size(); i++) {
            final Cal.Calendar k = cals.get(i);
            boolean on = !set || vis.contains(String.valueOf(k.id));
            LinearLayout r = u.switchRow(k.name, on, new Ui.OnToggle() {
                public void toggled(boolean on) {
                    Set<String> s = set ? Store.visibleCals(MainActivity.this) : allIds();
                    if (on) s.add(String.valueOf(k.id)); else s.remove(String.valueOf(k.id));
                    Store.setVisibleCals(MainActivity.this, s);
                    App.changed(MainActivity.this);
                    render(false);
                }
            });
            card.addView(r);
            TextView acc = u.label(k.account == null ? "" : k.account);
            acc.setPadding(0, 0, 0, u.dp(8));
            card.addView(acc);
            if (i < cals.size() - 1) card.addView(u.rule());
        }
        if (cals.isEmpty()) card.addView(u.quiet("No calendars found. Add your Google account in the phone's settings."), u.lp(12));
        add(card, 8);

        add(u.label("Save my blocks to"), 26);
        long blocks = Cal.blocksCal(this);
        LinearLayout c2 = u.card();
        c2.setPadding(u.dp(18), u.dp(4), u.dp(18), u.dp(4));
        boolean first = true;
        for (final Cal.Calendar k : cals) {
            if (!k.writable) continue;
            if (!first) c2.addView(u.rule());
            first = false;
            LinearLayout r = u.row();
            r.setMinimumHeight(u.dp(52));
            TextView n = u.body(k.name);
            r.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (k.id == blocks) { TextView sel = u.label("Chosen"); sel.setTextColor(Palette.GOLD); r.addView(sel); }
            r.setClickable(true);
            r.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { Store.setBlocksCal(MainActivity.this, k.id); App.changed(MainActivity.this); render(false); }
            });
            c2.addView(r);
        }
        add(c2, 8);
        add(u.quiet("Blocks you already made stay where they are."), 8);
    }

    private Set<String> allIds() {
        Set<String> s = new HashSet<String>();
        for (Cal.Calendar k : Cal.calendars(this)) s.add(String.valueOf(k.id));
        return s;
    }

    private void widgetSettings() {
        topBar("More", null, null);
        header("Home screen", "Clock widget");
        // Preview
        LinearLayout prev = u.card();
        prev.setPadding(u.dp(20), u.dp(18), u.dp(20), u.dp(18));
        String font = Store.str(this, "wFont", "serif");
        prev.addView(u.label(Fmt.shortDay(System.currentTimeMillis()) + " " + Fmt.MONTHS[Fmt.cal(System.currentTimeMillis()).get(java.util.Calendar.MONTH)].substring(0, 3)));
        TextView time = u.text(Fmt.time(System.currentTimeMillis()), 54, Palette.LINEN);
        time.setTypeface("mono".equals(font) ? Ui.mono : "sans".equals(font) ? Ui.sans : Ui.serifLight);
        time.setIncludeFontPadding(false);
        prev.addView(time, u.lp(4));
        Cal.Block cur = ClockWidget.current(this);
        if (Store.bool(this, "wBlock", true)) prev.addView(u.quiet(cur == null ? "Open time" : cur.title + " until " + Fmt.time(cur.end)), u.lp(6));
        add(prev, 20);
        add(u.quiet("Add it from your home screen: hold an empty spot, tap Widgets, then In Due Time."), 8);

        add(u.label("Font"), 22);
        final String[] fonts = {"serif", "sans", "mono"};
        int fs = 0;
        for (int i = 0; i < 3; i++) if (fonts[i].equals(font)) fs = i;
        add(u.segmented(new String[]{"Serif", "Sans", "Mono"}, fs, new Ui.OnPick() {
            public void picked(int i) { Store.put(MainActivity.this, "wFont", fonts[i]); ClockWidget.updateAll(MainActivity.this); render(false); }
        }), 8);
        add(u.switchRow("Show date", Store.bool(this, "wDate", true), new Ui.OnToggle() {
            public void toggled(boolean on) { Store.put(MainActivity.this, "wDate", on); ClockWidget.updateAll(MainActivity.this); render(false); }
        }), 14);
        add(u.rule(), 0);
        add(u.switchRow("Show current block", Store.bool(this, "wBlock", true), new Ui.OnToggle() {
            public void toggled(boolean on) { Store.put(MainActivity.this, "wBlock", on); ClockWidget.updateAll(MainActivity.this); render(false); }
        }), 0);
        add(u.label("Glass"), 22);
        final String[] gl = {"clear", "soft", "solid"};
        String g = Store.str(this, "wGlass", "soft");
        int gs = 1;
        for (int i = 0; i < 3; i++) if (gl[i].equals(g)) gs = i;
        add(u.segmented(new String[]{"Clear", "Soft", "Solid"}, gs, new Ui.OnPick() {
            public void picked(int i) { Store.put(MainActivity.this, "wGlass", gl[i]); ClockWidget.updateAll(MainActivity.this); }
        }), 8);
        Store.Lens wl = Store.activeLens(this, "widget");
        add(u.quiet("Lens: " + (wl == null ? "none right now, so it shows everything." : wl.name + ".")), 14);
    }

    // Watch face editor: presets, Custom, and every dial option, with a live preview

    private boolean previewDigital;

    private void watchSettings() {
        topBar("More", null, null);
        header("Galaxy Watch", "Watch face");

        final String faceId = Store.faceId(this);
        final FaceStyle cur = Store.face(this, faceId);
        final WatchData all = App.watchData(this);
        final WatchData d = all.filtered(all.lensId);

        // Live preview
        View preview = new View(this) {
            final FaceRenderer r = new FaceRenderer(Ui.serif, Ui.mono);
            boolean amb;
            {
                setOnTouchListener(new View.OnTouchListener() {
                    public boolean onTouch(View v, MotionEvent e) {
                        if (e.getAction() == MotionEvent.ACTION_UP) { amb = !amb; invalidate(); v.performClick(); }
                        return true;
                    }
                });
                setContentDescription("Watch face preview. Tap to see always on display.");
            }
            @Override protected void onDraw(Canvas c) {
                int s = Math.min(getWidth(), getHeight());
                c.save();
                c.translate((getWidth() - s) / 2f, 0);
                android.graphics.Path clip = new android.graphics.Path();
                clip.addCircle(s / 2f, s / 2f, s / 2f, android.graphics.Path.Direction.CW);
                c.clipPath(clip);
                r.draw(c, s, s, d, cur, System.currentTimeMillis(), amb, previewDigital ? FaceRenderer.TIME_DIGITAL : FaceRenderer.TIME_HANDS);
                c.restore();
                if (!amb) postInvalidateDelayed(1000);
            }
        };
        preview.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        add(preview, 18);
        preview.getLayoutParams().height = u.dp(240);
        add(u.quiet("Tap the preview to see always on display."), 6);
        add(u.segmented(new String[]{"Preview analog", "Preview digital"}, previewDigital ? 1 : 0, new Ui.OnPick() {
            public void picked(int i) { previewDigital = i == 1; render(false); }
        }), 10);

        // Faces
        add(u.label("Face"), 24);
        final List<FaceStyle> faces = Store.faces(this);
        String[] names = new String[faces.size()];
        final boolean[] on = new boolean[faces.size()];
        for (int i = 0; i < faces.size(); i++) { names[i] = faces.get(i).name; on[i] = faces.get(i).id.equals(faceId); }
        add(u.chips(names, on, false, new Ui.OnPick() {
            public void picked(int i) { Store.setFaceId(MainActivity.this, faces.get(i).id); App.changed(MainActivity.this); render(false); }
        }), 8);
        add(u.quiet("custom".equals(faceId) ? "You are editing Custom." : "Change anything below and it becomes your Custom face."), 8);
        add(u.quiet("Lenses can each pick their own face in the lens editor."), 4);

        // Ground and accent
        add(u.label("Ground"), 24);
        final String[] grounds = {"night", "black", "glow"};
        add(u.segmented(new String[]{"Night", "Black", "Glow"}, idx(grounds, cur.ground), new Ui.OnPick() {
            public void picked(int i) { FaceStyle f = editable(cur); f.ground = grounds[i]; saveFace(f); }
        }), 8);

        add(u.label("Accent"), 20);
        final int[] accents = {Palette.GOLD, Palette.LAV, Palette.LINEN, 0xFF7FA39A, 0xFFC995A6, 0xFF8EA4CF};
        Ui.FlowLayout sw = new Ui.FlowLayout(this, u.dp(12));
        for (final int col : accents) {
            View o = u.orb(col, 32);
            if (col == cur.accent) {
                FrameLayout f = new FrameLayout(this);
                GradientDrawable ring = new GradientDrawable();
                ring.setShape(GradientDrawable.OVAL);
                ring.setStroke(u.dp(2), Palette.LINEN);
                f.setBackground(ring);
                f.setPadding(u.dp(4), u.dp(4), u.dp(4), u.dp(4));
                f.addView(o);
                sw.addView(f);
            } else {
                o.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { FaceStyle f = editable(cur); f.accent = col; saveFace(f); }
                });
                sw.addView(o);
            }
        }
        add(sw, 10);
        add(u.quiet("The accent marks the block you are in."), 6);

        // Ring
        add(u.label("Ring"), 24);
        final String[] widths = {"thin", "regular", "bold"};
        add(u.segmented(new String[]{"Thin", "Regular", "Bold"}, idx(widths, cur.ringWidth), new Ui.OnPick() {
            public void picked(int i) { FaceStyle f = editable(cur); f.ringWidth = widths[i]; saveFace(f); }
        }), 8);
        add(u.segmented(new String[]{"Next 6 hours", "Next 12 hours"}, cur.ringHours <= 6 ? 0 : 1, new Ui.OnPick() {
            public void picked(int i) { FaceStyle f = editable(cur); f.ringHours = i == 0 ? 6 : 12; saveFace(f); }
        }), 8);
        add(u.label("Gaps between blocks"), 16);
        final String[] gaps = {"none", "small", "wide"};
        add(u.segmented(new String[]{"None", "Small", "Wide"}, idx(gaps, cur.gap), new Ui.OnPick() {
            public void picked(int i) { FaceStyle f = editable(cur); f.gap = gaps[i]; saveFace(f); }
        }), 8);
        add(u.label("Current block"), 16);
        final String[] swells = {"none", "soft", "strong"};
        add(u.segmented(new String[]{"Same size", "Swell", "Big swell"}, idx(swells, cur.swell), new Ui.OnPick() {
            public void picked(int i) { FaceStyle f = editable(cur); f.swell = swells[i]; saveFace(f); }
        }), 8);
        add(u.switchRow("Google events on the ring", cur.ringGoogle, new Ui.OnToggle() {
            public void toggled(boolean v) { FaceStyle f = editable(cur); f.ringGoogle = v; saveFace(f); }
        }), 8);

        // Markers
        add(u.label("Markers"), 24);
        final String[] marks = {"none", "hours", "minutes", "numerals"};
        add(u.segmented(new String[]{"None", "Hours", "Minutes", "12 3 6 9"}, idx(marks, cur.markers), new Ui.OnPick() {
            public void picked(int i) { FaceStyle f = editable(cur); f.markers = marks[i]; saveFace(f); }
        }), 8);

        // Centre lines
        add(u.label("Centre text"), 24);
        add(u.quiet("Up to three lines, top to bottom."), 4);
        final String[] keys = {"", "block", "until", "left", "next", "date", "lens"};
        final String[] keyNames = {"Nothing", "Block", "Until", "Time left", "Next", "Date", "Lens"};
        for (int n = 0; n < 3; n++) {
            final int line = n;
            String have = n < cur.lines.size() ? cur.lines.get(n) : "";
            final boolean[] lOn = new boolean[keys.length];
            for (int i = 0; i < keys.length; i++) lOn[i] = keys[i].equals(have);
            add(u.label("Line " + (n + 1)), 14);
            add(u.chips(keyNames, lOn, false, new Ui.OnPick() {
                public void picked(int i) {
                    FaceStyle f = editable(cur);
                    while (f.lines.size() <= line) f.lines.add("");
                    f.lines.set(line, keys[i]);
                    List<String> clean = new ArrayList<String>();
                    for (String k : f.lines) if (k.length() > 0) clean.add(k);
                    f.lines.clear();
                    f.lines.addAll(clean);
                    saveFace(f);
                }
            }), 6);
        }

        // Icon
        add(u.label("Icon"), 24);
        add(u.switchRow("Show the category icon", cur.icon, new Ui.OnToggle() {
            public void toggled(boolean v) { FaceStyle f = editable(cur); f.icon = v; saveFace(f); }
        }), 2);
        if (cur.icon) {
            final String[] sizes = {"small", "medium", "large"};
            add(u.segmented(new String[]{"Small", "Medium", "Large"}, idx(sizes, cur.iconSize), new Ui.OnPick() {
                public void picked(int i) { FaceStyle f = editable(cur); f.iconSize = sizes[i]; saveFace(f); }
            }), 6);
        }

        // Always on display
        add(u.label("Always on display"), 24);
        add(u.switchRow("Keep the ring", cur.aodRing, new Ui.OnToggle() {
            public void toggled(boolean v) { FaceStyle f = editable(cur); f.aodRing = v; saveFace(f); }
        }), 2);
        add(u.rule(), 0);
        add(u.switchRow("Keep the icon", cur.aodIcon, new Ui.OnToggle() {
            public void toggled(boolean v) { FaceStyle f = editable(cur); f.aodIcon = v; saveFace(f); }
        }), 0);
        add(u.rule(), 0);
        add(u.switchRow("Keep the first line of text", cur.aodText, new Ui.OnToggle() {
            public void toggled(boolean v) { FaceStyle f = editable(cur); f.aodText = v; saveFace(f); }
        }), 0);
        add(u.label("How dim"), 16);
        final String[] dims = {"low", "medium", "high"};
        add(u.segmented(new String[]{"A little", "Medium", "Very"}, idx(dims, cur.aodDim), new Ui.OnPick() {
            public void picked(int i) { FaceStyle f = editable(cur); f.aodDim = dims[i]; saveFace(f); }
        }), 8);
        add(u.label("Ring when dim"), 16);
        final String[] rs = {"outline", "solid"};
        add(u.segmented(new String[]{"Outline", "Solid"}, idx(rs, cur.aodRingStyle), new Ui.OnPick() {
            public void picked(int i) { FaceStyle f = editable(cur); f.aodRingStyle = rs[i]; saveFace(f); }
        }), 8);

        // Start Custom from a preset
        add(u.label("Start Custom from"), 24);
        final List<FaceStyle> presets = FaceStyle.presets();
        String[] pn = new String[presets.size()];
        for (int i = 0; i < pn.length; i++) pn[i] = presets.get(i).name;
        add(u.chips(pn, new boolean[pn.length], false, new Ui.OnPick() {
            public void picked(int i) {
                Store.saveCustom(MainActivity.this, presets.get(i).copy());
                Store.setFaceId(MainActivity.this, "custom");
                App.changed(MainActivity.this);
                toast("Custom now starts from " + presets.get(i).name);
                render(false);
            }
        }), 8);

        // Set by Galaxy Wearable
        LinearLayout gw = u.card();
        gw.addView(u.label("Set in Galaxy Wearable"));
        gw.addView(u.body("Analog or digital, hand style, hand colour, seconds hand and digital font."), u.lp(6));
        gw.addView(u.quiet("Open Galaxy Wearable, tap the In Due Time face, then Customise."), u.lp(6));
        add(gw, 26);

        // The link
        add(u.label("Your watch"), 26);
        LinearLayout card = u.card();
        card.addView(u.switchRow("Link with my watch", WatchLink.enabled(this), new Ui.OnToggle() {
            public void toggled(boolean v) {
                Store.put(MainActivity.this, "watchLink", v);
                if (v) {
                    List<String> need = new ArrayList<String>();
                    if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.BLUETOOTH_CONNECT);
                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.POST_NOTIFICATIONS);
                    if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), 3);
                    WatchLink.ensure(MainActivity.this);
                } else {
                    WatchLink.stop(MainActivity.this);
                }
                render(false);
            }
        }));
        card.addView(u.quiet(WatchLink.enabled(this) ? WatchLink.lastStatus(this) : "Off. Your watch cannot fetch your day."), u.lp(2));
        add(card, 8);
        LinearLayout steps = u.card();
        steps.addView(u.label("First time"));
        steps.addView(u.body("1. Install the In Due Time watch app and the In Due Time face on your watch."), u.lp(8));
        steps.addView(u.body("2. Turn on Link with my watch above."), u.lp(6));
        steps.addView(u.body("3. On the watch, open In Due Time and pick this phone."), u.lp(6));
        steps.addView(u.body("4. In Galaxy Wearable, choose the In Due Time face."), u.lp(6));
        steps.addView(u.quiet("Changes here reach the watch within about five minutes. Tap Sync now on the watch to see them at once."), u.lp(10));
        add(steps, 12);
    }

    private static int idx(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return 0;
    }

    /** Editing a preset turns it into Custom. */
    private FaceStyle editable(FaceStyle cur) {
        FaceStyle f = cur.copy();
        if (!"custom".equals(cur.id)) toast("Saved as your Custom face");
        return f;
    }

    private void saveFace(FaceStyle f) {
        Store.saveCustom(this, f);
        Store.setFaceId(this, "custom");
        App.changed(this);
        render(false);
    }
    private void checkinSettings() {
        topBar("More", null, null);
        header("Block end", "When a block ends");
        add(u.switchRow("Tap on the wrist", Store.bool(this, "tap", true), new Ui.OnToggle() {
            public void toggled(boolean on) { Store.put(MainActivity.this, "tap", on); }
        }), 20);
        add(u.rule(), 0);
        add(u.switchRow("Check-in on the phone", Store.bool(this, "checkin", true), new Ui.OnToggle() {
            public void toggled(boolean on) { Store.put(MainActivity.this, "checkin", on); }
        }), 0);
        add(u.quiet("The check-in asks: Done, or Not yet. Not yet lets you extend by 15 minutes, move it, or skip it. Google events never ask."), 14);
    }

    private void about() {
        topBar("More", null, null);
        header("Version " + version(), "In Due Time");
        LinearLayout c = u.card();
        c.addView(u.body("Your data stays on your devices and in your own Google Calendar."));
        c.addView(u.quiet("Part of the Watchman house of apps."), u.lp(8));
        add(c, 20);
    }

    private String version() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return ""; }
    }

    // Check-in sheet

    void showCheckin(final Cal.Block b, boolean notYet) {
        overlay.removeAllViews();
        View dim = new View(this);
        dim.setBackgroundColor(0x99000000);
        dim.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { hideOverlay(); } });
        overlay.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout sheet = u.card();
        sheet.setPadding(u.dp(22), u.dp(20), u.dp(22), u.dp(22));
        sheet.setClickable(true);
        if (!notYet) {
            sheet.addView(u.label("Block ended · " + Fmt.time(b.end)));
            sheet.addView(u.title(b.title + " has ended", 24), u.lp(6));
            sheet.addView(u.quiet("Are you done?"), u.lp(6));
            LinearLayout acts = u.row();
            TextView done = u.button("Done", 1);
            done.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { Store.setDone(MainActivity.this, b, true); Sched.cancelNote(MainActivity.this); toast("Marked done"); hideOverlay(); }
            });
            acts.addView(done);
            TextView ny = u.button("Not yet", 0);
            ny.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showCheckin(b, true); } });
            acts.addView(ny, u.gap(8));
            sheet.addView(acts, u.lp(18));
        } else {
            sheet.addView(u.label("Not yet"));
            sheet.addView(u.title("What would help?", 24), u.lp(6));
            TextView ext = u.button("Extend 15 min", 0);
            ext.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    boolean ok = Cal.moveOne(MainActivity.this, b, b.begin, Math.max(b.end, System.currentTimeMillis()) + 15 * 60000L);
                    Sched.cancelNote(MainActivity.this);
                    toast(ok ? "Extended by 15 minutes" : "Could not extend");
                    App.changed(MainActivity.this);
                    hideOverlay();
                }
            });
            sheet.addView(ext, u.wrap(16));
            TextView mv = u.button("Move to later", 0);
            mv.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { Sched.cancelNote(MainActivity.this); hideOverlay(); openEditor(b); }
            });
            sheet.addView(mv, u.wrap(10));
            TextView skip = u.button("Skip", 3);
            skip.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { Sched.cancelNote(MainActivity.this); toast("Skipped"); hideOverlay(); }
            });
            sheet.addView(skip, u.wrap(6));
        }
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        sl.leftMargin = Math.max(u.dp(12), insetLeft + u.dp(8));
        sl.rightMargin = Math.max(u.dp(12), insetRight + u.dp(8));
        sl.bottomMargin = insetBottom + u.dp(14);
        overlay.addView(sheet, sl);
        overlay.setVisibility(View.VISIBLE);
    }

    void hideOverlay() {
        overlay.setVisibility(View.GONE);
        overlay.removeAllViews();
        render(false);
    }
}
