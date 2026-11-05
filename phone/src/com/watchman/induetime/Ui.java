package com.watchman.induetime;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.watchman.induetime.shared.Palette;

import java.util.ArrayList;
import java.util.List;

/** View helpers in the Watchman style. */
final class Ui {

    static Typeface serif, serifLight, mono, sans, sansBold;

    private final Context c;

    Ui(Context c) {
        this.c = c;
        if (serif == null) {
            try { serif = Typeface.createFromAsset(c.getAssets(), "fraunces.ttf"); } catch (Exception e) { serif = Typeface.SERIF; }
            try { mono = Typeface.createFromAsset(c.getAssets(), "plexmono.ttf"); } catch (Exception e) { mono = Typeface.MONOSPACE; }
            try { sans = Typeface.createFromAsset(c.getAssets(), "figtree.ttf"); } catch (Exception e) { sans = Typeface.SANS_SERIF; }
            serifLight = serif;
            sansBold = sans;
            if (Build.VERSION.SDK_INT >= 26) {
                try {
                    serif = new Typeface.Builder(c.getAssets(), "fraunces.ttf").setFontVariationSettings("'wght' 400, 'opsz' 48, 'SOFT' 100").build();
                    serifLight = new Typeface.Builder(c.getAssets(), "fraunces.ttf").setFontVariationSettings("'wght' 300, 'opsz' 144, 'SOFT' 100").build();
                    sans = new Typeface.Builder(c.getAssets(), "figtree.ttf").setFontVariationSettings("'wght' 400").build();
                    sansBold = new Typeface.Builder(c.getAssets(), "figtree.ttf").setFontVariationSettings("'wght' 600").build();
                } catch (Exception ignored) {}
            }
        }
    }

    int dp(float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()); }

    TextView text(String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(sans);
        t.setLineSpacing(0, 1.25f);
        return t;
    }

    TextView body(String s) { return text(s, 15, Palette.LINEN); }
    TextView quiet(String s) { return text(s, 14, Palette.MUTED); }

    TextView label(String s) {
        TextView t = text(s.toUpperCase(), 10.5f, Palette.MUTED);
        t.setTypeface(mono);
        t.setLetterSpacing(0.16f);
        return t;
    }

    TextView title(String s, float sp) {
        TextView t = text(s, sp, Palette.LINEN);
        t.setTypeface(serif);
        t.setLineSpacing(0, 1.08f);
        return t;
    }

    /** kind 0 quiet outline, 1 primary linen, 2 gold, 3 bare text */
    TextView button(String s, int kind) {
        TextView t = text(s, 14, kind == 1 ? Palette.NIGHT : kind == 2 ? Palette.GOLD : kind == 3 ? Palette.MUTED : Palette.LINEN);
        t.setTypeface(sansBold);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(44));
        t.setPadding(dp(kind == 3 ? 8 : 18), dp(10), dp(kind == 3 ? 8 : 18), dp(10));
        if (kind != 3) {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(999));
            if (kind == 1) g.setColor(Palette.LINEN);
            else { g.setColor(Color.TRANSPARENT); g.setStroke(dp(1), kind == 2 ? Palette.GOLD : 0x40B7A3F0); }
            t.setBackground(g);
        }
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    /** Glass card: blur-like tint, lavender edge, one bright top edge. */
    LinearLayout card() { return card(false); }

    LinearLayout card(boolean gold) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(18), dp(16), dp(18), dp(16));
        l.setBackground(cardBg(gold));
        return l;
    }

    Drawable cardBg(boolean gold) {
        GradientDrawable base = new GradientDrawable();
        base.setCornerRadius(dp(18));
        base.setColor(Palette.DUSK);
        base.setStroke(dp(1), gold ? 0x8CD8B25E : Palette.LINE);
        GradientDrawable tint = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{gold ? 0x1FD8B25E : 0x17B7A3F0, 0x06B7A3F0});
        tint.setCornerRadius(dp(18));
        GradientDrawable edge = new GradientDrawable();
        edge.setColor(0x52ECE7F5);
        LayerDrawable ld = new LayerDrawable(new Drawable[]{base, tint, edge});
        ld.setLayerGravity(2, Gravity.TOP | Gravity.FILL_HORIZONTAL);
        ld.setLayerHeight(2, dp(1));
        ld.setLayerInsetLeft(2, dp(18));
        ld.setLayerInsetRight(2, dp(18));
        return ld;
    }

    View dot(int color, int size) {
        View v = new View(c);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        v.setBackground(g);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
        return v;
    }

    /** 3D orb from the standard: light from the top left. */
    View orb(int color, int size) {
        View v = new View(c);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientCenter(0.32f, 0.28f);
        g.setGradientRadius(dp(size) * 0.9f);
        g.setColors(new int[]{0xFFFFFFFF, color, darker(color)});
        v.setBackground(g);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
        return v;
    }

    static int darker(int c) {
        return Color.argb(255, (int) (Color.red(c) * 0.4f), (int) (Color.green(c) * 0.4f), (int) (Color.blue(c) * 0.4f));
    }

    LinearLayout row() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    LinearLayout col() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    View rule() {
        View v = new View(c);
        v.setBackgroundColor(Palette.LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.7f))));
        return v;
    }

    /** A list row: main text, optional detail on the right, optional chevron. */
    LinearLayout listRow(String main, String detail, boolean chevron) {
        LinearLayout r = row();
        r.setMinimumHeight(dp(52));
        r.setPadding(0, dp(8), 0, dp(8));
        TextView m = body(main);
        r.addView(m, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (detail != null) {
            TextView d = quiet(detail);
            d.setGravity(Gravity.END);
            d.setMaxLines(2);
            d.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(12);
            r.addView(d, lp);
        }
        if (chevron) {
            TextView ch = text("›", 20, Palette.FAINT);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(10);
            r.addView(ch, lp);
        }
        r.setClickable(chevron);
        return r;
    }

    /** A switch drawn in the house style. */
    static final class Toggle extends View {
        boolean on;
        final Ui ui;
        Toggle(Ui ui, boolean on) {
            super(ui.c);
            this.ui = ui;
            this.on = on;
            setLayoutParams(new LinearLayout.LayoutParams(ui.dp(40), ui.dp(24)));
            paintState();
        }
        void set(boolean v) { on = v; paintState(); }
        void paintState() {
            GradientDrawable track = new GradientDrawable();
            track.setCornerRadius(ui.dp(12));
            track.setColor(on ? 0x73B7A3F0 : 0x29B7A3F0);
            GradientDrawable knob = new GradientDrawable();
            knob.setShape(GradientDrawable.OVAL);
            knob.setColor(on ? Palette.LINEN : Palette.FAINT);
            LayerDrawable ld = new LayerDrawable(new Drawable[]{track, knob});
            int k = ui.dp(16), pad = ui.dp(4);
            ld.setLayerSize(1, k, k);
            ld.setLayerGravity(1, Gravity.CENTER_VERTICAL | (on ? Gravity.END : Gravity.START));
            ld.setLayerInsetLeft(1, pad);
            ld.setLayerInsetRight(1, pad);
            setBackground(ld);
            setContentDescription(on ? "On" : "Off");
        }
    }

    interface OnPick { void picked(int index); }
    interface OnToggle { void toggled(boolean on); }

    LinearLayout switchRow(String label, boolean on, final OnToggle cb) {
        LinearLayout r = row();
        r.setMinimumHeight(dp(52));
        r.setPadding(0, dp(6), 0, dp(6));
        TextView m = body(label);
        r.addView(m, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Toggle t = new Toggle(this, on);
        r.addView(t);
        r.setClickable(true);
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { t.set(!t.on); cb.toggled(t.on); }
        });
        return r;
    }

    /** Segmented control. */
    LinearLayout segmented(final String[] options, int selected, final OnPick cb) {
        final LinearLayout l = row();
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setStroke(dp(1), Palette.LINE);
        l.setBackground(g);
        l.setPadding(dp(4), dp(4), dp(4), dp(4));
        final List<TextView> items = new ArrayList<TextView>();
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            TextView t = text(options[i].toUpperCase(), 10.5f, Palette.MUTED);
            t.setTypeface(mono);
            t.setLetterSpacing(0.1f);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(dp(38));
            t.setPadding(dp(4), 0, dp(4), 0);
            t.setMaxLines(2);
            t.setClickable(true);
            items.add(t);
            l.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { paintSeg(items, idx); cb.picked(idx); }
            });
        }
        paintSeg(items, selected);
        return l;
    }

    private void paintSeg(List<TextView> items, int sel) {
        for (int i = 0; i < items.size(); i++) {
            TextView t = items.get(i);
            if (i == sel) {
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(dp(10));
                g.setColor(0x29B7A3F0);
                t.setBackground(g);
                t.setTextColor(Palette.LINEN);
            } else {
                t.setBackground(null);
                t.setTextColor(Palette.MUTED);
            }
        }
    }

    /** Wrapping chips. Single or multiple choice. */
    FlowLayout chips(String[] options, final boolean[] on, final boolean multi, final OnPick cb) {
        final FlowLayout f = new FlowLayout(c, dp(8));
        final List<TextView> items = new ArrayList<TextView>();
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            TextView t = text(options[i], 13.5f, Palette.MUTED);
            t.setPadding(dp(14), dp(9), dp(14), dp(9));
            t.setClickable(true);
            items.add(t);
            f.addView(t);
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (multi) on[idx] = !on[idx];
                    else for (int j = 0; j < on.length; j++) on[j] = j == idx;
                    paintChips(items, on);
                    cb.picked(idx);
                }
            });
        }
        paintChips(items, on);
        return f;
    }

    private void paintChips(List<TextView> items, boolean[] on) {
        for (int i = 0; i < items.size(); i++) {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(999));
            g.setStroke(dp(1), on[i] ? Palette.LINEN : Palette.LINE);
            g.setColor(on[i] ? 0x1AECE7F5 : Color.TRANSPARENT);
            items.get(i).setBackground(g);
            items.get(i).setTextColor(on[i] ? Palette.LINEN : Palette.MUTED);
        }
    }

    EditText field(String value, String hint, boolean multiline) {
        EditText e = new EditText(c);
        e.setText(value);
        e.setHint(hint);
        e.setHintTextColor(Palette.FAINT);
        e.setTextColor(Palette.LINEN);
        e.setTypeface(sans);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setStroke(dp(1), Palette.LINE);
        g.setColor(0x0AB7A3F0);
        e.setBackground(g);
        e.setInputType(multiline ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (multiline) { e.setMinLines(3); e.setGravity(Gravity.TOP | Gravity.START); }
        return e;
    }

    /** A tappable field that looks like an input. */
    TextView pickField(String value) {
        TextView t = body(value);
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        t.setMinHeight(dp(48));
        t.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setStroke(dp(1), Palette.LINE);
        t.setBackground(g);
        t.setClickable(true);
        return t;
    }

    LinearLayout.LayoutParams lp(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(top);
        return p;
    }

    LinearLayout.LayoutParams wrap(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(top);
        return p;
    }

    LinearLayout.LayoutParams gap(int left) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.leftMargin = dp(left);
        return p;
    }

    /** Simple wrapping row for chips. */
    static final class FlowLayout extends ViewGroup {
        final int gap;
        FlowLayout(Context c, int gap) { super(c); this.gap = gap; }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int max = MeasureSpec.getSize(wSpec);
            int x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                v.measure(MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED);
                if (x > 0 && x + v.getMeasuredWidth() > max) { x = 0; y += rowH + gap; rowH = 0; }
                x += v.getMeasuredWidth() + gap;
                rowH = Math.max(rowH, v.getMeasuredHeight());
            }
            setMeasuredDimension(max, y + rowH);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int max = r - l, x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (x > 0 && x + v.getMeasuredWidth() > max) { x = 0; y += rowH + gap; rowH = 0; }
                v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
                x += v.getMeasuredWidth() + gap;
                rowH = Math.max(rowH, v.getMeasuredHeight());
            }
        }
    }
}
