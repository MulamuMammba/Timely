package com.watchman.induetime.shared;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;

import java.util.Calendar;
import java.util.Locale;

/**
 * Draws the five In Due Time faces in a 200 x 200 space: Sundial, Orbit, Horizon, Signal and Still.
 * Time first, blocks second. No serif and no seconds. Shared by the watch and the phone preview.
 */
public final class FaceRenderer {

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface sans, mono;
    private final RectF oval = new RectF();
    private final BlurMaskFilter blur = new BlurMaskFilter(3.5f, BlurMaskFilter.Blur.NORMAL);
    private final DashPathEffect dash = new DashPathEffect(new float[]{1.6f, 3f}, 0);

    static final int MUTED = 0xFFA69DBB, FAINT = 0xFF6F6785, SOFT = 0xFFCFC7E3, DIMTXT = 0xFF8A829F, AODTXT = 0xFF8D86A0;

    public FaceRenderer(Typeface sans, Typeface mono) {
        this.sans = sans == null ? Typeface.SANS_SERIF : sans;
        this.mono = mono == null ? Typeface.MONOSPACE : mono;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        fill.setStyle(Paint.Style.FILL);
        text.setTextAlign(Paint.Align.CENTER);
    }

    /** Draws the face the data asks for, after its lens. */
    public void draw(Canvas c, int w, int h, WatchData d, long now, boolean ambient) {
        draw(c, w, h, d, d.style(), now, ambient);
    }

    public void draw(Canvas c, int w, int h, WatchData d, FaceStyle st, long now, boolean ambient) {
        float k = Math.min(w, h) / 200f;
        c.save();
        c.translate((w - 200 * k) / 2f, (h - 200 * k) / 2f);
        c.scale(k, k);
        ground(c, st, ambient);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        double t = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0 + cal.get(Calendar.SECOND) / 3600.0;
        long dayStart = now - (long) (t * 3600000L);
        Ctx x = new Ctx(d, st, now, t, dayStart, ambient, cal);
        if ("orbit".equals(st.family)) orbit(c, x);
        else if ("horizon".equals(st.family)) horizon(c, x);
        else if ("signal".equals(st.family)) signal(c, x);
        else if ("still".equals(st.family)) still(c, x);
        else sundial(c, x);
        c.restore();
    }

    /** Everything one draw needs. Times are in hours of today; blocks can run past 24. */
    static final class Ctx {
        final WatchData d; final FaceStyle st; final long now; final double t; final long day; final boolean aod; final Calendar cal;
        final WatchData.Item cur, next;
        Ctx(WatchData d, FaceStyle st, long now, double t, long day, boolean aod, Calendar cal) {
            this.d = d; this.st = st; this.now = now; this.t = t; this.day = day; this.aod = aod; this.cal = cal;
            this.cur = d.current(now); this.next = d.next(now);
        }
        double h(long ms) { return (ms - day) / 3600000.0; }
    }

    // ---------- Ground ----------

    private void ground(Canvas c, FaceStyle st, boolean aod) {
        if (aod || "black".equals(st.ground)) { c.drawColor(Color.BLACK); return; }
        int a, b;
        if ("navy".equals(st.ground)) { a = 0xFF24365A; b = 0xFF0F1828; }
        else if ("amethyst".equals(st.ground)) { a = 0xFF3A2C6E; b = 0xFF120D22; }
        else { a = 0xFF1E1829; b = 0xFF0B0910; }
        c.drawColor(b);
        fill.setShader(new RadialGradient(80, 60, 160, a, b, Shader.TileMode.CLAMP));
        c.drawRect(-20, -20, 220, 220, fill);
        fill.setShader(null);
    }

    // ---------- Sundial: one hour hand over 24 hours ----------

    private static float sunA(double h) { return (float) (((h - 12) / 24.0) * 360.0); }

    private void sundial(Canvas c, Ctx x) {
        FaceStyle st = x.st;
        if (!x.aod) { // night half
            fill.setColor(0x38000000);
            oval.set(2, 2, 198, 198);
            c.drawArc(oval, 0, 180, true, fill);
        }
        float r = 82, w = "bold".equals(st.ring) ? 8 : "thin".equals(st.ring) ? 3.2f : 5.2f;
        for (WatchData.Item b : x.d.items) {
            double s = x.h(b.start), e = x.h(b.end);
            if (e < x.t - 12 || s > x.t + 12) continue;
            boolean now = isNow(b, x.now), past = b.end <= x.now;
            float a0 = sunA(s) + .9f, a1 = sunA(e) - .9f;
            if (a1 <= a0) continue;
            if (now && !x.aod) glowArc(c, r, a0, a1, w + 6, st.accent, 150);
            int col = now ? st.accent : colour(b);
            int alpha = x.aod ? (now ? 190 : 75) : past ? 75 : b.google ? 140 : 230;
            arc(c, r, a0, a1, now ? w * 1.7f : w, col, alpha, b.google && !now);
        }
        for (int hr = 2; hr <= 22; hr++) {
            boolean big = hr % 6 == 0;
            line(c, 70, big ? 65 : 67.5f, sunA(hr), big ? MUTED : 0xFF4D465E, big ? 1.3f : .8f, x.aod ? 128 : 255);
        }
        if (!x.aod) {
            int[] hs = {12, 18, 6};
            for (int hr : hs) { float[] p = pt(57, sunA(hr)); label(c, String.valueOf(hr), p[0], p[1] + 3, 7, FAINT, 0, 255); }
        }
        boolean large = "large".equals(st.size);
        sans(c, hm(x.cal), 100, 138, large ? 32 : 26, 300, -.5f, st.ink, x.aod ? 180 : 255);
        String l1 = lineText(st.line(0), x), l2 = lineText(st.line(1), x);
        if (l1.length() > 0) sans(c, l1, 100, 152, 8, 500, 0, x.aod ? AODTXT : SOFT, 255);
        if (l2.length() > 0 && !x.aod) label(c, l2, 100, 161, 5.8f, DIMTXT, 1, 255);
        // the hand
        float a = sunA(x.t);
        float[] tip = pt(92, a), tail = pt(14, a + 180), dot = pt(86, a);
        stroke.setPathEffect(null);
        stroke.setMaskFilter(null);
        stroke.setStrokeWidth(2.4f);
        stroke.setColor(x.aod ? AODTXT : st.ink);
        c.drawLine(tail[0], tail[1], tip[0], tip[1], stroke);
        fill.setColor(st.accent);
        c.drawCircle(dot[0], dot[1], x.aod ? 2 : 3, fill);
        fill.setColor(x.aod ? Color.BLACK : 0xFF0D0B14);
        c.drawCircle(100, 100, 3.6f, fill);
        stroke.setStrokeWidth(1.6f);
        c.drawCircle(100, 100, 3.6f, stroke);
    }

    // ---------- Orbit: the day turns under a fixed marker ----------

    private void orbit(Canvas c, Ctx x) {
        FaceStyle st = x.st;
        double span = st.hours <= 0 ? 12 : st.hours;
        float r = 80, w = "bold".equals(st.ring) ? 9 : "thin".equals(st.ring) ? 4 : 6.5f;
        stroke.setPathEffect(null);
        stroke.setMaskFilter(null);
        stroke.setStrokeCap(Paint.Cap.BUTT);
        stroke.setStrokeWidth(w);
        stroke.setColor(0x0DFFFFFF);
        c.drawCircle(100, 100, r, stroke);
        for (WatchData.Item b : x.d.items) {
            float a0 = (float) ((x.h(b.start) - x.t) / span * 360), a1 = (float) ((x.h(b.end) - x.t) / span * 360);
            if (a1 < -90 || a0 > 270) continue;
            a0 = Math.max(a0, -90); a1 = Math.min(a1, 270);
            boolean now = isNow(b, x.now);
            if (a1 - a0 < 1.7f) continue;
            if (now && !x.aod) glowArc(c, r, a0 + .8f, a1 - .8f, w + 6, st.accent, 140);
            int alpha = x.aod ? (now ? 190 : 75) : b.end <= x.now ? 64 : b.google ? 128 : 230;
            stroke.setStrokeCap(Paint.Cap.BUTT);
            arcRaw(c, r, a0 + .8f, a1 - .8f, w, now ? st.accent : colour(b), alpha, b.google && !now);
        }
        stroke.setStrokeCap(Paint.Cap.ROUND);
        for (int hr = (int) Math.ceil(x.t - span / 4); hr <= x.t + span * .75; hr++) {
            float a = (float) ((hr - x.t) / span * 360);
            line(c, r - w / 2 - 3, r - w / 2 - 7, a, FAINT, 1, x.aod ? 100 : 255);
            int step = span >= 24 ? 6 : 3;
            if (!x.aod && ((hr % step) + step) % step == 0) {
                float[] p = pt(r - w / 2 - 14, a);
                label(c, String.format(Locale.ENGLISH, "%02d", ((hr % 24) + 24) % 24), p[0], p[1] + 2.5f, 6, FAINT, 0, 255);
            }
        }
        // fixed marker
        float apex = 100 - r - w / 2 - 4;
        Path tri = new Path();
        tri.moveTo(100, apex);
        tri.lineTo(95.5f, apex - 6);
        tri.lineTo(104.5f, apex - 6);
        tri.close();
        fill.setColor(st.accent);
        c.drawPath(tri, fill);
        stroke.setStrokeWidth(1.6f);
        stroke.setColor(st.accent);
        c.drawLine(100, apex, 100, 100 - r + w / 2 + 4, stroke);
        // centre
        String small = "", cap;
        if (x.cur != null) { small = left(x.cur.end - x.now); cap = x.cur.title; }
        else if (x.next != null) { small = left(x.next.start - x.now).replace(" left", "") + " to " + x.next.title; cap = "Open time"; }
        else cap = "Day is done";
        boolean large = "large".equals(st.size);
        sans(c, hm(x.cal), 100, 111, large ? 44 : 38, 200, -1, st.ink, x.aod ? 180 : 255);
        if (small.length() > 0) label(c, small.toUpperCase(Locale.ENGLISH), 100, 127, 6.2f, x.aod ? 0xFF6D6780 : st.accent, 1.2f, 255);
        if (!x.aod) sans(c, cap, 100, 140, 8.5f, 500, 0, SOFT, 255);
    }

    // ---------- Horizon: the block rises like a tide ----------

    private void horizon(Canvas c, Ctx x) {
        FaceStyle st = x.st;
        double p = 0;
        if (x.cur != null) p = (double) (x.now - x.cur.start) / Math.max(1, x.cur.end - x.cur.start);
        int col = x.cur == null ? 0xFF5B47A8 : x.cur.google ? Palette.LINEN : st.accent;
        float top = (float) (178 - p * 60);
        if (!x.aod) {
            fill.setShader(new LinearGradient(0, top, 0, 200, new int[]{withAlpha(col, 140), withAlpha(col, 46), withAlpha(col, 0)},
                    new float[]{0, .35f, 1}, Shader.TileMode.CLAMP));
            c.drawRect(-20, top, 220, 220, fill);
            fill.setShader(null);
        }
        stroke.setPathEffect(null);
        stroke.setMaskFilter(null);
        stroke.setStrokeWidth(1.2f);
        stroke.setColor(withAlpha(col, x.aod ? 150 : 255));
        c.drawLine(x.aod ? 40 : -20, top, x.aod ? 160 : 220, top, stroke);
        if (!x.aod) {
            float y = top + 12;
            int n = 0;
            for (WatchData.Item b : x.d.items) {
                if (b.start <= x.now || b.start > x.now + 6 * 3600000L || n >= 3) continue;
                float wd = Math.max(4, (float) ((b.end - b.start) / 3600000.0 / 6 * 120));
                fill.setColor(withAlpha(colour(b), 180));
                c.drawRoundRect(new RectF(100 - wd / 2, y, 100 + wd / 2, y + 2.2f), 1.1f, 1.1f, fill);
                y += 7;
                n++;
            }
        }
        boolean large = "large".equals(st.size);
        sans(c, hm(x.cal), 100, 78, large ? 46 : 40, 200, -1, st.ink, x.aod ? 180 : 255);
        String l1 = lineText(st.line(0), x), l2 = lineText(st.line(1), x);
        if (l1.length() > 0) sans(c, l1, 100, 95, 9, 500, 0, x.aod ? AODTXT : SOFT, 255);
        if (l2.length() > 0 && !x.aod) label(c, l2, 100, 106, 6, DIMTXT, 1.2f, 255);
    }

    // ---------- Signal: dot matrix and seventy-two segments ----------

    private static final String[][] DM = new String[11][];
    static {
        String[] g = {
                "01110,10001,10011,10101,11001,10001,01110", "00100,01100,00100,00100,00100,00100,01110",
                "01110,10001,00001,00010,00100,01000,11111", "11110,00001,00001,01110,00001,00001,11110",
                "00010,00110,01010,10010,11111,00010,00010", "11111,10000,11110,00001,00001,10001,01110",
                "00110,01000,10000,11110,10001,10001,01110", "11111,00001,00010,00100,01000,01000,01000",
                "01110,10001,10001,01110,10001,10001,01110", "01110,10001,10001,01111,00001,00010,01100",
                "0,0,1,0,1,0,0"};
        for (int i = 0; i < g.length; i++) DM[i] = g[i].split(",");
    }

    private void dots(Canvas c, String s, float cx, float cy, float d, int col, boolean aod) {
        float w = -1;
        for (char ch : s.toCharArray()) w += glyph(ch)[0].length() + 1;
        float x0 = cx - w * d / 2;
        for (char ch : s.toCharArray()) {
            String[] m = glyph(ch);
            for (int r = 0; r < 7; r++) for (int k = 0; k < m[r].length(); k++) {
                boolean on = m[r].charAt(k) == '1';
                if (!on && aod) continue;
                fill.setColor(on ? col : 0x0DFFFFFF);
                c.drawCircle(x0 + k * d + d / 2, cy - 3.5f * d + r * d + d / 2, d * .36f, fill);
            }
            x0 += (m[0].length() + 1) * d;
        }
    }

    private static String[] glyph(char ch) { return ch == ':' ? DM[10] : DM[Math.max(0, Math.min(9, ch - '0'))]; }

    private void signal(Canvas c, Ctx x) {
        FaceStyle st = x.st;
        int N = 72;
        for (int i = 0; i < N; i++) {
            long hms = x.now + (long) (i * (12.0 / N) * 3600000L);
            WatchData.Item b = at(x.d, hms);
            boolean now = b != null && isNow(b, x.now);
            if (x.aod && b == null) continue;
            int col = b == null ? 0xFF2A2536 : now ? st.accent : colour(b);
            int alpha = x.aod ? (now ? 205 : 75) : (b != null && b.google ? 150 : 255);
            line(c, 86, 78, i * 5f + .9f, col, now ? 3 : 2.2f, alpha);
        }
        boolean large = "large".equals(st.size);
        dots(c, hm(x.cal), 100, 98, large ? 4.4f : 3.6f, x.aod ? 0xFFA49DB8 : st.ink, x.aod);
        if (!x.aod) label(c, String.format(Locale.ENGLISH, "%1$ta %1$te %1$tb", x.cal).toUpperCase(Locale.ENGLISH), 100, 52, 6.8f, FAINT, 1.6f, 255);
        String l1 = lineText(st.line(0), x), l2 = lineText(st.line(1), x);
        if (l1.length() > 0) label(c, l1.toUpperCase(Locale.ENGLISH), 100, 136, 7.6f, st.ink, 1.4f, x.aod ? 150 : 255);
        if (l2.length() > 0 && !x.aod) label(c, l2.toUpperCase(Locale.ENGLISH), 100, 148, 6.4f, MUTED, 1.2f, 255);
    }

    // ---------- Still: one arc and thin time ----------

    private void still(Canvas c, Ctx x) {
        FaceStyle st = x.st;
        if (x.cur != null) {
            double p = (double) (x.now - x.cur.start) / Math.max(1, x.cur.end - x.cur.start);
            int col = x.cur.google ? Palette.LINEN : st.accent;
            stroke.setPathEffect(null);
            stroke.setMaskFilter(null);
            stroke.setStrokeWidth(2);
            stroke.setColor(withAlpha(col, 36));
            c.drawCircle(100, 100, 86, stroke);
            float sweep = (float) Math.max(1, p * 360);
            if (!x.aod) glowArc(c, 86, 0, sweep, 5, st.accent, 128);
            arc(c, 86, 0, sweep, x.aod ? 1.6f : 2.6f, col, x.aod ? 150 : 255, false);
        }
        boolean large = "large".equals(st.size);
        sans(c, hm(x.cal), 100, 116, large ? 56 : 46, 200, -1, st.ink, x.aod ? 166 : 255);
        String l1 = lineText(st.line(0), x), l2 = lineText(st.line(1), x);
        if (l1.length() > 0) sans(c, l1, 100, 138, 9, 500, 0, st.ink, x.aod ? 140 : 242);
        if (l2.length() > 0 && !x.aod) label(c, l2, 100, 150, 6.4f, MUTED, 1.2f, 255);
    }

    // ---------- Text lines ----------

    private String lineText(String key, Ctx x) {
        if (key == null || key.length() == 0) return "";
        if ("block".equals(key)) return x.cur != null ? x.cur.title : "Open time";
        if ("until".equals(key)) return x.cur != null ? "until " + hm(x.cur.end) : x.next != null ? "next at " + hm(x.next.start) : "";
        if ("left".equals(key)) return x.cur != null ? left(x.cur.end - x.now) : "";
        if ("next".equals(key)) return x.next != null ? x.next.title + " in " + dur(x.next.start - x.now) : "";
        if ("date".equals(key)) return String.format(Locale.ENGLISH, "%1$ta %1$te %1$tb", x.cal);
        if ("lens".equals(key)) return x.d.lens.length() > 0 ? x.d.lens + " lens" : "";
        return "";
    }

    static String dur(long ms) {
        long m = Math.max(0, Math.round(ms / 60000.0));
        if (m < 60) return m + " min";
        return (m / 60) + " h" + (m % 60 == 0 ? "" : " " + (m % 60) + " min");
    }

    static String left(long ms) { return dur(ms) + " left"; }

    // ---------- Drawing helpers ----------

    private static boolean isNow(WatchData.Item b, long now) { return !b.google && b.start <= now && b.end > now; }

    private static WatchData.Item at(WatchData d, long t) {
        WatchData.Item g = null;
        for (WatchData.Item i : d.items) if (i.start <= t && i.end > t) { if (!i.google) return i; g = i; }
        return g;
    }

    private static int colour(WatchData.Item b) { return b.google ? Palette.LINEN : b.color; }

    static float[] pt(float r, float a) {
        double t = Math.toRadians(a - 90);
        return new float[]{100 + r * (float) Math.cos(t), 100 + r * (float) Math.sin(t)};
    }

    private void arc(Canvas c, float r, float a0, float a1, float w, int col, int alpha, boolean dashed) {
        stroke.setStrokeCap(Paint.Cap.ROUND);
        arcRaw(c, r, a0, a1, w, col, alpha, dashed);
    }

    private void arcRaw(Canvas c, float r, float a0, float a1, float w, int col, int alpha, boolean dashed) {
        oval.set(100 - r, 100 - r, 100 + r, 100 + r);
        stroke.setMaskFilter(null);
        stroke.setPathEffect(dashed ? dash : null);
        stroke.setStrokeWidth(w);
        stroke.setColor(withAlpha(col, alpha));
        c.drawArc(oval, a0 - 90, Math.min(359.9f, a1 - a0), false, stroke);
        stroke.setPathEffect(null);
    }

    private void glowArc(Canvas c, float r, float a0, float a1, float w, int col, int alpha) {
        oval.set(100 - r, 100 - r, 100 + r, 100 + r);
        stroke.setPathEffect(null);
        stroke.setMaskFilter(blur);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeWidth(w);
        stroke.setColor(withAlpha(col, alpha));
        c.drawArc(oval, a0 - 90, Math.min(359.9f, a1 - a0), false, stroke);
        stroke.setMaskFilter(null);
    }

    private void line(Canvas c, float r0, float r1, float a, int col, float w, int alpha) {
        float[] p0 = pt(r0, a), p1 = pt(r1, a);
        stroke.setPathEffect(null);
        stroke.setMaskFilter(null);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeWidth(w);
        stroke.setColor(withAlpha(col, alpha));
        c.drawLine(p0[0], p0[1], p1[0], p1[1], stroke);
    }

    private void sans(Canvas c, String s, float x, float y, float size, int weight, float tracking, int col, int alpha) {
        text.setTypeface(sans);
        if (Build.VERSION.SDK_INT >= 26) text.setFontVariationSettings("'wght' " + weight);
        else text.setFakeBoldText(weight >= 600);
        text.setTextSize(size);
        text.setLetterSpacing(tracking / size);
        text.setColor(withAlpha(col, alpha));
        c.drawText(fit(s, 130), x, y, text);
        text.setLetterSpacing(0);
    }

    private void label(Canvas c, String s, float x, float y, float size, int col, float tracking, int alpha) {
        text.setTypeface(mono);
        if (Build.VERSION.SDK_INT >= 26) text.setFontVariationSettings("");
        text.setTextSize(size);
        text.setLetterSpacing(tracking / size);
        text.setColor(withAlpha(col, alpha));
        c.drawText(fit(s, 124), x, y, text);
        text.setLetterSpacing(0);
    }

    private String fit(String s, float max) {
        String t = s;
        while (t.length() > 3 && text.measureText(t) > max) t = t.substring(0, t.length() - 2) + "…";
        return t;
    }

    static int withAlpha(int color, int a) {
        int base = (color >>> 24) & 0xFF;
        return (color & 0x00FFFFFF) | ((base * Math.max(0, Math.min(255, a)) / 255) << 24);
    }

    static String hm(Calendar c) {
        return String.format(Locale.ENGLISH, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    static String hm(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return hm(c);
    }

    // ---------- Where things sit, for the phone editor ----------

    /** Region at a point in 200 space: ring, time, text1, text2, or null. */
    public static String regionAt(FaceStyle st, float x, float y) {
        float dx = x - 100, dy = y - 100, d = (float) Math.sqrt(dx * dx + dy * dy);
        boolean hasRing = !"horizon".equals(st.family);
        if (hasRing && d > 72 && d < 96) return "ring";
        float[] tb = timeBox(st);
        if (x >= tb[0] && x <= tb[0] + tb[2] && y >= tb[1] && y <= tb[1] + tb[3]) return "time";
        float ty = textTop(st);
        if (x >= 40 && x <= 160 && y >= ty && y < ty + 12) return "text1";
        if (x >= 40 && x <= 160 && y >= ty + 12 && y < ty + 24) return "text2";
        return null;
    }

    public static float[] timeBox(FaceStyle st) {
        if ("orbit".equals(st.family)) return new float[]{56, 80, 88, 36};
        if ("horizon".equals(st.family)) return new float[]{56, 44, 88, 40};
        if ("signal".equals(st.family)) return new float[]{50, 80, 100, 38};
        if ("still".equals(st.family)) return new float[]{46, 74, 108, 48};
        return new float[]{60, 116, 80, 28};
    }

    public static float textTop(FaceStyle st) {
        if ("orbit".equals(st.family)) return 132;
        if ("horizon".equals(st.family)) return 87;
        if ("signal".equals(st.family)) return 128;
        if ("still".equals(st.family)) return 130;
        return 144;
    }

    public static float ringRadius(FaceStyle st) {
        if ("orbit".equals(st.family)) return 80;
        if ("still".equals(st.family)) return 86;
        return 82;
    }
}
