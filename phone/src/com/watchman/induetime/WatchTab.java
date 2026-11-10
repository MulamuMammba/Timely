package com.watchman.induetime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.watchman.induetime.shared.FaceRenderer;
import com.watchman.induetime.shared.FaceStyle;
import com.watchman.induetime.shared.Palette;
import com.watchman.induetime.shared.WatchData;

import java.util.List;

/**
 * The Watch tab. The face stays fixed at the top. Tap a part of it to edit that part.
 * Options sit underneath as small pictures. Changing one never reloads the page.
 */
final class WatchTab {

    static final int[] ACCENTS = {0xFFD8B25E, 0xFFB7A3F0, 0xFFECE7F5, 0xFF7FA39A, 0xFFC995A6, 0xFF8EA4CF};
    static final String[] ACCENT_NAMES = {"Gold", "Lavender", "Linen", "Sage", "Rose", "Blue"};
    static final int[] INKS = {0xFFECE7F5, 0xFFB7A3F0, 0xFFD8B25E, 0xFF9FC7B6, 0xFFE0B4C2};
    static final String[] INK_NAMES = {"Linen", "Lavender", "Gold", "Sage", "Rose"};
    static final String[] GROUNDS = {"night", "black", "navy", "amethyst"};
    static final String[] GROUND_NAMES = {"Night", "Black", "Navy", "Amethyst"};
    static final int[][] GROUND_COLS = {{0xFF1E1829, 0xFF0B0910}, {0xFF111111, 0xFF000000}, {0xFF24365A, 0xFF0F1828}, {0xFF3A2C6E, 0xFF120D22}};
    static final String[] LINE_KEYS = {"block", "until", "left", "next", "date", "lens", ""};
    static final String[] LINE_NAMES = {"Block", "Until", "Time left", "Next", "Date", "Lens", "Nothing"};

    private final MainActivity a;
    private final Ui u;
    final LinearLayout root;
    private final Preview preview;
    private final TextView tag;
    private final LinearLayout tabRow;
    private final LinearLayout opts;
    private final ScrollView optScroll;
    private String tab = "face";
    private String region = null;
    private int slot = 0;
    private WatchData data;
    private boolean toldCustom;

    WatchTab(MainActivity act, Ui ui) {
        a = act;
        u = ui;
        root = u.col();

        // Header
        LinearLayout head = u.row();
        LinearLayout titles = u.col();
        titles.addView(u.label("Your face"));
        titles.addView(u.title("Watch", 30), u.lp(2));
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView link = u.button("Watch link", 0);
        link.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { a.go("watchlink"); } });
        head.addView(link);
        root.addView(head, u.lp(0));

        // Fixed preview
        preview = new Preview(a);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u.dp(250));
        pl.topMargin = u.dp(10);
        root.addView(preview, pl);

        tag = u.label("");
        tag.setGravity(Gravity.CENTER);
        tag.setTextColor(Palette.LAV);
        tag.setMinHeight(u.dp(24));
        root.addView(tag, u.lp(6));

        // Tabs
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        tabRow = u.row();
        hs.addView(tabRow);
        root.addView(hs, u.lp(6));

        // Options, the only part that scrolls
        optScroll = new ScrollView(a);
        optScroll.setClipToPadding(false);
        opts = u.col();
        opts.setPadding(0, u.dp(10), 0, u.dp(20));
        optScroll.addView(opts, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(optScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    /** Called each time the tab opens. */
    void show() {
        data = App.watchData(a);
        data = data.filtered(data.lensId);
        drawTabs();
        drawOpts();
        preview.invalidate();
    }

    FaceStyle cur() { return Store.face(a, Store.faceId(a)); }

    // ---------- Tabs ----------

    private void drawTabs() {
        tabRow.removeAllViews();
        String[][] t = {{"face", "Face"}, {"time", "Time"}, {"colour", "Colour"}, {"ring", "Ring"}, {"text", "Text"}, {"dim", "Dim"}};
        boolean noRing = "horizon".equals(cur().family);
        for (final String[] x : t) {
            if (noRing && "ring".equals(x[0])) continue;
            TextView v = u.label(x[1]);
            v.setGravity(Gravity.CENTER);
            v.setMinHeight(u.dp(40));
            v.setPadding(u.dp(14), 0, u.dp(14), 0);
            boolean on = x[0].equals(tab);
            v.setTextColor(on ? Palette.LINEN : Palette.FAINT);
            if (on) {
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(u.dp(999));
                g.setColor(0x1FB7A3F0);
                v.setBackground(g);
            }
            v.setClickable(true);
            v.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    tab = x[0];
                    region = "ring".equals(tab) ? "ring" : "time".equals(tab) ? "time" : "text".equals(tab) ? "text" : null;
                    refresh();
                }
            });
            tabRow.addView(v);
        }
    }

    private void refresh() {
        drawTabs();
        drawOpts();
        preview.invalidate();
        optScroll.scrollTo(0, 0);
    }

    // ---------- Options ----------

    private void drawOpts() {
        opts.removeAllViews();
        final FaceStyle st = cur();
        String fam = st.family;
        if ("face".equals(tab)) {
            if ("custom".equals(st.id) || Store.str(a, "customFace", "").length() > 0) {
                final FaceStyle mine = Store.custom(a);
                LinearLayout row = thumbs("Yours");
                row.addView(thumb(face(mine), "Custom", "custom".equals(st.id), new Runnable() {
                    public void run() { Store.setFaceId(a, "custom"); changed(); }
                }));
            }
            List<FaceStyle> ps = FaceStyle.presets();
            for (int f = 0; f < FaceStyle.FAMILIES.length; f++) {
                LinearLayout row = thumbs(FaceStyle.FAMILY_NAMES[f]);
                for (final FaceStyle p : ps) {
                    if (!p.family.equals(FaceStyle.FAMILIES[f])) continue;
                    row.addView(thumb(face(p), p.name, p.id.equals(st.id), new Runnable() {
                        public void run() { Store.setFaceId(a, p.id); changed(); }
                    }));
                }
            }
            hint("Fifteen defaults in five families. Change any of them and it saves as your own.");
        } else if ("time".equals(tab)) {
            LinearLayout row = thumbs("Time size");
            String[] sizes = {"medium", "large"};
            for (final String z : sizes) {
                FaceStyle v = st.copy();
                v.size = z;
                row.addView(thumb(face(v), cap(z), z.equals(st.size), new Runnable() {
                    public void run() { FaceStyle f = edit(st); f.size = z; save(f); }
                }));
            }
            LinearLayout cols = thumbs("sundial".equals(fam) ? "Hand and time colour" : "Time colour");
            for (int i = 0; i < INKS.length; i++) {
                final int c = INKS[i];
                cols.addView(thumb(orb(c), INK_NAMES[i], c == st.ink, new Runnable() {
                    public void run() { FaceStyle f = edit(st); f.ink = c; save(f); }
                }));
            }
        } else if ("colour".equals(tab)) {
            LinearLayout acc = thumbs("Accent");
            for (int i = 0; i < ACCENTS.length; i++) {
                final int c = ACCENTS[i];
                acc.addView(thumb(orb(c), ACCENT_NAMES[i], c == st.accent, new Runnable() {
                    public void run() { FaceStyle f = edit(st); f.accent = c; save(f); }
                }));
            }
            hint("The accent marks the block you are in.");
            LinearLayout gr = thumbs("Ground");
            for (int i = 0; i < GROUNDS.length; i++) {
                final String g = GROUNDS[i];
                gr.addView(thumb(groundOrb(GROUND_COLS[i]), GROUND_NAMES[i], g.equals(st.ground), new Runnable() {
                    public void run() { FaceStyle f = edit(st); f.ground = g; save(f); }
                }));
            }
        } else if ("ring".equals(tab)) {
            if ("signal".equals(fam)) {
                hint("Signal's ring is 72 segments for the next 12 hours, ten minutes each. It has no width to change.");
            } else {
                LinearLayout row = thumbs("Width");
                String[] ws = {"thin", "regular", "bold"};
                for (final String w : ws) {
                    FaceStyle v = st.copy();
                    v.ring = w;
                    row.addView(thumb(face(v), cap(w), w.equals(st.ring), new Runnable() {
                        public void run() { FaceStyle f = edit(st); f.ring = w; save(f); }
                    }));
                }
            }
            if ("orbit".equals(fam)) {
                LinearLayout row = thumbs("Hours on the ring");
                int[] hs = {6, 12, 24};
                for (final int h : hs) {
                    FaceStyle v = st.copy();
                    v.hours = h;
                    row.addView(thumb(face(v), h + " h", h == st.hours, new Runnable() {
                        public void run() { FaceStyle f = edit(st); f.hours = h; save(f); }
                    }));
                }
            }
        } else if ("text".equals(tab)) {
            opts.addView(u.segmented(new String[]{"Line 1", "Line 2"}, slot, new Ui.OnPick() {
                public void picked(int i) { slot = i; region = "text"; drawOpts(); preview.invalidate(); }
            }), u.lp(0));
            LinearLayout row = thumbs("Line " + (slot + 1));
            String have = st.line(slot);
            for (int i = 0; i < LINE_KEYS.length; i++) {
                final String k = LINE_KEYS[i];
                row.addView(thumb(sample(k), LINE_NAMES[i], k.equals(have), new Runnable() {
                    public void run() { FaceStyle f = edit(st); f.setLine(slot, k); save(f); }
                }));
            }
            hint("Tap a line on the face to choose it.");
        } else if ("dim".equals(tab)) {
            hint("The face above shows always on display. Ring blocks dim, your current block stays, extra lines hide.");
            LinearLayout row = thumbs("Compare");
            row.addView(thumb(face(st, false), "Bright", false, null));
            row.addView(thumb(face(st, true), "Always on", true, null));
        }
    }

    private void changed() {
        App.changed(a);
        drawTabs();
        drawOpts();
        preview.invalidate();
    }

    /** Editing a default turns it into your Custom face, in the same family. */
    private FaceStyle edit(FaceStyle cur) {
        FaceStyle f = cur.copy();
        if (!"custom".equals(cur.id) && !toldCustom) {
            Toast.makeText(a, "Saved as your Custom face", Toast.LENGTH_SHORT).show();
            toldCustom = true;
        }
        return f;
    }

    private void save(FaceStyle f) {
        Store.saveCustom(a, f);
        Store.setFaceId(a, "custom");
        changed();
    }

    // ---------- Pieces ----------

    private LinearLayout thumbs(String label) {
        opts.addView(u.label(label), u.lp(opts.getChildCount() == 0 ? 0 : 18));
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = u.row();
        row.setGravity(Gravity.TOP);
        hs.addView(row);
        opts.addView(hs, u.lp(8));
        return row;
    }

    private void hint(String s) { opts.addView(u.quiet(s), u.lp(14)); }

    private View thumb(View inner, String caption, boolean on, final Runnable click) {
        LinearLayout col = u.col();
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout ring = new FrameLayout(a);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0xFF0A0910);
        g.setStroke(u.dp(2), on ? Palette.LINEN : 0x00000000);
        ring.setBackground(g);
        int pad = u.dp(3);
        ring.setPadding(pad, pad, pad, pad);
        ring.addView(inner, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        col.addView(ring, new LinearLayout.LayoutParams(u.dp(64), u.dp(64)));
        TextView t = u.text(caption, 12, on ? Palette.LINEN : Palette.MUTED);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(t, new LinearLayout.LayoutParams(u.dp(72), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(u.dp(76), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = u.dp(6);
        col.setLayoutParams(lp);
        if (click != null) {
            col.setClickable(true);
            col.setContentDescription(caption);
            col.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { click.run(); } });
        }
        return col;
    }

    private View face(FaceStyle st) { return face(st, false); }

    private View face(final FaceStyle st, final boolean amb) {
        View v = new View(a) {
            final FaceRenderer r = new FaceRenderer(figtree(a), Ui.mono);
            @Override protected void onDraw(Canvas c) {
                int s = Math.min(getWidth(), getHeight());
                Path clip = new Path();
                clip.addCircle(getWidth() / 2f, getHeight() / 2f, s / 2f, Path.Direction.CW);
                c.save();
                c.clipPath(clip);
                r.draw(c, getWidth(), getHeight(), data, st, System.currentTimeMillis(), amb);
                c.restore();
            }
        };
        v.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        return v;
    }

    private View orb(int colour) {
        View v = u.orb(colour, 52);
        v.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return v;
    }

    private View groundOrb(int[] cols) {
        View v = new View(a);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientCenter(0.35f, 0.3f);
        g.setGradientRadius(u.dp(44));
        g.setColors(cols);
        g.setStroke(1, 0x33B7A3F0);
        v.setBackground(g);
        return v;
    }

    private View sample(String key) {
        String s;
        if ("block".equals(key)) s = "Q4\nreport";
        else if ("until".equals(key)) s = "until\n11:00";
        else if ("left".equals(key)) s = "40 min\nleft";
        else if ("next".equals(key)) s = "Next\nin 40";
        else if ("date".equals(key)) s = "Tue\n10 Nov";
        else if ("lens".equals(key)) s = "Work\nlens";
        else s = "None";
        TextView t = u.text(s, 10, Palette.LINEN);
        t.setTypeface(Ui.mono);
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(0, 1.05f);
        return t;
    }

    private static android.graphics.Typeface figtree;

    static android.graphics.Typeface figtree(Context c) {
        if (figtree == null) {
            try { figtree = android.graphics.Typeface.createFromAsset(c.getAssets(), "figtree.ttf"); }
            catch (Exception e) { figtree = android.graphics.Typeface.SANS_SERIF; }
        }
        return figtree;
    }

    private static String cap(String s) { return s.substring(0, 1).toUpperCase() + s.substring(1); }

    // ---------- The fixed preview ----------

    final class Preview extends View {
        final FaceRenderer r = new FaceRenderer(figtree(a), Ui.mono);
        final Paint bezel = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint hl = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Runnable tick = new Runnable() { public void run() { invalidate(); postDelayed(this, 30000); } };

        Preview(Context c) {
            super(c);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            hl.setStyle(Paint.Style.STROKE);
            setContentDescription("Watch face. Tap the ring, the time or the text to edit it.");
        }

        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); postDelayed(tick, 30000); }
        @Override protected void onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow(); }

        float size() { return Math.min(getWidth(), getHeight()); }

        @Override
        protected void onDraw(Canvas c) {
            if (data == null) return;
            float s = size(), cx = getWidth() / 2f, cy = getHeight() / 2f;
            float bez = s * 0.045f;
            bezel.setShader(new RadialGradient(cx - s * .2f, cy - s * .25f, s * .8f, 0xFF2C2636, 0xFF09080C, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, s / 2f, bezel);
            float inner = s - bez * 2;
            c.save();
            c.translate(cx - inner / 2f, cy - inner / 2f);
            Path clip = new Path();
            clip.addCircle(inner / 2f, inner / 2f, inner / 2f, Path.Direction.CW);
            c.clipPath(clip);
            FaceStyle st = cur();
            boolean amb = "dim".equals(tab);
            r.draw(c, (int) inner, (int) inner, data, st, System.currentTimeMillis(), amb);
            // highlight the part being edited
            float k = inner / 200f;
            c.scale(k, k);
            hl.setPathEffect(new DashPathEffect(new float[]{3, 3}, 0));
            hl.setColor(0xFFB7A3F0);
            hl.setStrokeWidth(1.2f);
            if ("ring".equals(region) && !"horizon".equals(st.family)) {
                hl.setStrokeWidth(10);
                hl.setAlpha(90);
                c.drawCircle(100, 100, FaceRenderer.ringRadius(st), hl);
            } else if ("time".equals(region)) {
                float[] b = FaceRenderer.timeBox(st);
                c.drawRoundRect(new RectF(b[0], b[1], b[0] + b[2], b[1] + b[3]), 8, 8, hl);
            } else if ("text".equals(region)) {
                float y = FaceRenderer.textTop(st);
                for (int i = 0; i < 2; i++) {
                    boolean sel = i == slot;
                    hl.setColor(sel ? Palette.GOLD : 0xFFB7A3F0);
                    hl.setPathEffect(sel ? null : new DashPathEffect(new float[]{3, 3}, 0));
                    c.drawRoundRect(new RectF(52, y + i * 12, 148, y + i * 12 + 11), 5.5f, 5.5f, hl);
                }
            }
            c.restore();
            String t = "ring".equals(region) ? "RING" : "time".equals(region) ? "TIME" : "text".equals(region) ? "LINE " + (slot + 1)
                    : "dim".equals(tab) ? "ALWAYS ON PREVIEW" : "";
            tag.setText(t);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
            if (e.getAction() != MotionEvent.ACTION_UP) return super.onTouchEvent(e);
            float s = size(), bez = s * 0.045f, inner = s - bez * 2;
            float x = (e.getX() - (getWidth() / 2f - inner / 2f)) / inner * 200;
            float y = (e.getY() - (getHeight() / 2f - inner / 2f)) / inner * 200;
            String reg = FaceRenderer.regionAt(cur(), x, y);
            if (reg == null) return true;
            if (reg.startsWith("text")) { slot = "text1".equals(reg) ? 0 : 1; region = "text"; tab = "text"; }
            else { region = reg; tab = reg; }
            performClick();
            refresh();
            return true;
        }

        @Override public boolean performClick() { return super.performClick(); }
    }
}
