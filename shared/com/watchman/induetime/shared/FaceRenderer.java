package com.watchman.induetime.shared;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;

import java.util.Calendar;
import java.util.Locale;

/**
 * Draws the In Due Time dial in a 200 x 200 space.
 * On the watch only the dial is drawn; the Watch Face Format face adds hands or digital time on top.
 * On the phone preview the hands or time are drawn too.
 */
public final class FaceRenderer {

    public static final int TIME_NONE = 0, TIME_HANDS = 1, TIME_DIGITAL = 2;

    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface serif, mono;
    private final RectF oval = new RectF();

    public FaceRenderer(Typeface serif, Typeface mono) {
        this.serif = serif;
        this.mono = mono;
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeCap(Paint.Cap.ROUND);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
    }

    /** Draws the whole face, time included, the way the style asks. */
    public void draw(Canvas c, int w, int h, WatchData d, long now, boolean ambient) {
        FaceStyle st = d.style();
        draw(c, w, h, d, st, now, ambient, "digital".equals(st.timeMode) ? TIME_DIGITAL : TIME_HANDS);
    }

    public void draw(Canvas c, int w, int h, WatchData d, FaceStyle st, long now, boolean ambient, int timeMode) {
        float k = Math.min(w, h) / 200f;
        c.save();
        c.translate((w - 200 * k) / 2f, (h - 200 * k) / 2f);
        c.scale(k, k);

        int dim = ambient ? st.aodAlpha() : 255;

        // Ground
        if (ambient || "black".equals(st.ground)) {
            c.drawColor(Color.BLACK);
        } else if ("glow".equals(st.ground)) {
            c.drawColor(Palette.NIGHT);
            fill.setShader(new RadialGradient(70, 40, 170, 0x995B47A8, 0x000D0B14, Shader.TileMode.CLAMP));
            c.drawCircle(100, 100, 142, fill);
            fill.setShader(null);
        } else if ("navy".equals(st.ground)) {
            fill.setShader(new RadialGradient(80, 60, 160, 0xFF24365A, 0xFF101A2C, Shader.TileMode.CLAMP));
            c.drawCircle(100, 100, 142, fill);
            fill.setShader(null);
        } else if ("amethyst".equals(st.ground)) {
            fill.setShader(new RadialGradient(80, 60, 160, 0xFF3A2C6E, 0xFF140F24, Shader.TileMode.CLAMP));
            c.drawCircle(100, 100, 142, fill);
            fill.setShader(null);
        } else {
            fill.setShader(new RadialGradient(80, 60, 160, Palette.DUSK2, 0xFF0B0910, Shader.TileMode.CLAMP));
            c.drawCircle(100, 100, 142, fill);
            fill.setShader(null);
        }
        glow.setMaskFilter(ambient ? null : new BlurMaskFilter(4, BlurMaskFilter.Blur.NORMAL));

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        int hh = cal.get(Calendar.HOUR_OF_DAY), mm = cal.get(Calendar.MINUTE), ss = cal.get(Calendar.SECOND);
        float hourNow = (hh % 12) + mm / 60f + ss / 3600f;
        WatchData.Item cur = d.current(now), nxt = d.next(now);

        // Ring
        if (!ambient || st.aodRing) ring(c, d, st, now, hourNow, ambient, dim);

        // Markers
        markers(c, st, ambient, dim);

        // Icon
        boolean iconOn = st.icon && (!ambient || st.aodIcon);
        if (iconOn && cur != null) {
            float s = st.iconPx();
            line.setStrokeWidth(s > 20 ? 1.8f : 1.5f);
            line.setColor(alpha(ambient ? 0xFFA49DB8 : Palette.LINEN, dim));
            Palette.icon(c, cur.icon, 100, 52 + (24 - s) / 2f, s, line);
        }

        // Centre lines
        if (!ambient || st.aodText) {
            float y = 136;
            int n = 0;
            for (String key : st.lines) {
                if (n >= 3) break;
                if (ambient && n >= 1) break;
                String s = lineText(key, d, cur, nxt, cal, now);
                if (s == null || s.length() == 0) continue;
                boolean first = n == 0;
                int col = first ? Palette.LINEN : Palette.MUTED;
                if ("next".equals(key)) col = Palette.FAINT;
                label(c, s, 100, y, first ? 8f : 6.6f, alpha(ambient ? 0xFFA49DB8 : col, dim), first ? 1.6f : 1.2f);
                y += first ? 12 : 10.5f;
                n++;
            }
        }

        // Time
        if (timeMode == TIME_HANDS) {
            int hc = ambient ? alpha(0xFFA49DB8, Math.max(dim, 150)) : st.handColor;
            hand(c, st.hands, hourNow * 30, 42, true, hc, ambient);
            hand(c, st.hands, (mm + ss / 60f) * 6, 62, false, hc, ambient);
            if (!ambient && st.seconds) {
                double a = Math.toRadians(ss * 6 - 90);
                line.setStrokeWidth(1f);
                line.setColor(st.secondsColor);
                c.drawLine(100 - 13 * (float) Math.cos(a), 100 - 13 * (float) Math.sin(a), 100 + 66 * (float) Math.cos(a), 100 + 66 * (float) Math.sin(a), line);
            }
            fill.setColor(ambient ? Color.BLACK : Palette.NIGHT);
            c.drawCircle(100, 100, 3.4f, fill);
            line.setStrokeWidth(1.6f);
            line.setColor(hc);
            c.drawCircle(100, 100, 3.4f, line);
        } else if (timeMode == TIME_DIGITAL) {
            Typeface tf = "mono".equals(st.font) ? mono : "sans".equals(st.font) ? Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) : serif;
            text.setTypeface(tf);
            text.setLetterSpacing("mono".equals(st.font) ? 0.02f : 0);
            float size = "small".equals(st.timeSize) ? 36 : "large".equals(st.timeSize) ? 58 : 46;
            if ("mono".equals(st.font)) size *= 0.82f;
            text.setTextSize(size);
            text.setColor(ambient ? alpha(0xFFA49DB8, Math.max(dim, 150)) : st.handColor);
            if (tf == serif) weight(text, ambient ? 200 : 300);
            int hour = st.h24 ? hh : (hh % 12 == 0 ? 12 : hh % 12);
            c.drawText(String.format(Locale.ENGLISH, st.h24 ? "%02d:%02d" : "%d:%02d", hour, mm), 100, 100 + size * 0.36f, text);
            if (tf == serif) weight(text, 400);
            if (!ambient && st.seconds) {
                double a = Math.toRadians(ss * 6 - 90);
                fill.setColor(st.secondsColor);
                c.drawCircle(100 + 89 * (float) Math.cos(a), 100 + 89 * (float) Math.sin(a), 2.2f, fill);
            }
        }
        c.restore();
    }

    private String lineText(String key, WatchData d, WatchData.Item cur, WatchData.Item nxt, Calendar cal, long now) {
        if ("block".equals(key)) return cur != null ? cur.title : nxt == null ? "Open time" : "Open time";
        if ("until".equals(key)) return cur != null ? "Until " + hm(cur.end) : null;
        if ("left".equals(key)) return cur != null ? Math.max(0, (cur.end - now + 59999) / 60000) + " min left" : null;
        if ("next".equals(key)) return nxt != null ? "Next  " + nxt.title + " " + hm(nxt.start) : null;
        if ("date".equals(key)) return String.format(Locale.ENGLISH, "%1$ta %1$te %1$tb", cal);
        if ("lens".equals(key)) return d.lens.length() > 0 ? d.lens + " lens" : null;
        return null;
    }

    private void ring(Canvas c, WatchData d, FaceStyle st, long now, float hourNow, boolean ambient, int dim) {
        float r = 89;
        oval.set(100 - r, 100 - r, 100 + r, 100 + r);
        long back = st.ringHours <= 6 ? 30 * 60000L : 90 * 60000L;
        long from = now - back, to = now + st.ringHours * 3600000L - back;
        float gap = st.gapDeg();
        boolean outline = ambient && "outline".equals(st.aodRingStyle);
        for (WatchData.Item i : d.items) {
            if (i.google && !st.ringGoogle) continue;
            long s = Math.max(i.start, from), e = Math.min(i.end, to);
            if (e <= s) continue;
            float a0 = hourNow * 30 + (s - now) / 120000f + gap;
            float a1 = hourNow * 30 + (e - now) / 120000f - gap;
            if (a1 <= a0) continue;
            boolean isNow = i.start <= now && i.end > now && !i.google;
            boolean past = st.fadePast && i.end <= now;
            if (isNow) {
                if (!ambient) {
                    glow.setColor(st.accent);
                    glow.setStrokeWidth(st.swellStroke() + 4);
                    glow.setAlpha(130);
                    c.drawArc(oval, a0 - 90, a1 - a0, false, glow);
                }
                arc.setStrokeWidth(outline ? 3f : st.swellStroke());
                arc.setColor(alpha(st.accent, ambient ? Math.min(255, dim + 40) : 255));
                arc.setPathEffect(null);
            } else {
                arc.setStrokeWidth(outline ? 1.6f : st.ringStroke());
                int col = i.google ? Palette.LINEN : i.color;
                int a = i.google ? 140 : 230;
                if (past) a = a * 40 / 100;
                if (ambient) a = a * dim / 255;
                arc.setColor((col & 0x00FFFFFF) | (a << 24));
                arc.setPathEffect(i.google ? new DashPathEffect(new float[]{2, 3}, 0) : null);
            }
            c.drawArc(oval, a0 - 90, a1 - a0, false, arc);
        }
        arc.setPathEffect(null);
    }

    private void markers(Canvas c, FaceStyle st, boolean ambient, int dim) {
        if ("none".equals(st.markers)) return;
        boolean minutes = "minutes".equals(st.markers) && !ambient;
        for (int i = 0; i < 60; i++) {
            boolean hr = i % 5 == 0;
            if (!hr && !minutes) continue;
            if ("numerals".equals(st.markers) && i % 15 == 0) continue;
            double a = Math.toRadians(i * 6 - 90);
            float r0 = 80, r1 = hr ? 74 : 77.5f;
            line.setStrokeWidth(hr ? 1.4f : 0.7f);
            line.setColor(alpha(hr ? Palette.MUTED : Palette.FAINT, ambient ? dim * 3 / 4 : 255));
            c.drawLine(100 + r0 * (float) Math.cos(a), 100 + r0 * (float) Math.sin(a), 100 + r1 * (float) Math.cos(a), 100 + r1 * (float) Math.sin(a), line);
        }
        if ("numerals".equals(st.markers)) {
            text.setTypeface(serif);
            text.setLetterSpacing(0);
            text.setTextSize(14);
            text.setColor(alpha(Palette.LINEN, ambient ? dim : 230));
            weight(text, 300);
            c.drawText("12", 100, 34, text);
            c.drawText("3", 172, 105, text);
            c.drawText("6", 100, 176, text);
            c.drawText("9", 28, 105, text);
            weight(text, 400);
        }
    }

    private void hand(Canvas c, String style, float deg, float len, boolean hour, int color, boolean ambient) {
        c.save();
        c.rotate(deg, 100, 100);
        fill.setColor(color);
        line.setColor(color);
        if ("tapered".equals(style)) {
            float w = hour ? 3.2f : 2.4f;
            android.graphics.Path p = new android.graphics.Path();
            p.moveTo(100 - w, 104);
            p.lineTo(100 - w * 0.25f, 100 - len);
            p.lineTo(100 + w * 0.25f, 100 - len);
            p.lineTo(100 + w, 104);
            p.close();
            if (ambient) { line.setStrokeWidth(1f); c.drawPath(p, line); } else c.drawPath(p, fill);
        } else if ("baton".equals(style)) {
            float w = hour ? 3.4f : 2.6f;
            RectF r = new RectF(100 - w, 100 - len, 100 + w, 104);
            if (ambient) { line.setStrokeWidth(1f); c.drawRoundRect(r, 1.5f, 1.5f, line); }
            else {
                c.drawRoundRect(r, 1.5f, 1.5f, fill);
                fill.setColor(0x660D0B14);
                c.drawRoundRect(new RectF(100 - w * 0.3f, 100 - len + 5, 100 + w * 0.3f, 100 - 8), 1, 1, fill);
            }
        } else {
            line.setStrokeWidth(hour ? 4.5f : 2.8f);
            c.drawLine(100, 100, 100, 100 - len, line);
        }
        c.restore();
    }

    private void label(Canvas c, String s, float x, float y, float size, int color, float tracking) {
        text.setTypeface(mono);
        text.setTextSize(size);
        text.setColor(color);
        text.setLetterSpacing(tracking / 10f);
        String t = s.toUpperCase(Locale.ENGLISH);
        float max = 118;
        while (t.length() > 3 && text.measureText(t) > max) t = t.substring(0, t.length() - 2) + "…";
        c.drawText(t, x, y, text);
        text.setLetterSpacing(0);
    }

    static int alpha(int color, int a) {
        int base = (color >>> 24) & 0xFF;
        return (color & 0x00FFFFFF) | ((base * Math.max(0, Math.min(255, a)) / 255) << 24);
    }

    private static void weight(Paint p, int wght) {
        if (Build.VERSION.SDK_INT >= 26) p.setFontVariationSettings("'wght' " + wght + ", 'opsz' 72, 'SOFT' 100");
    }

    static String hm(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return String.format(Locale.ENGLISH, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }
}
