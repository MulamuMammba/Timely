package com.watchman.induetime.shared;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Watchman palette and simple line icons shared by phone and watch. */
public final class Palette {
    public static final int NIGHT = 0xFF0D0B14, DUSK = 0xFF17131F, DUSK2 = 0xFF1E1829, AMETHYST = 0xFF5B47A8;
    public static final int LAV = 0xFFB7A3F0, GOLD = 0xFFD8B25E, LINEN = 0xFFECE7F5;
    public static final int MUTED = 0xFFA69DBB, FAINT = 0xFF6F6785, LINE = 0x24B7A3F0, GLASS = 0x12B7A3F0;

    /** Calm category colours to pick from. */
    public static final int[] CHOICES = {0xFF8B78D8, 0xFFC9B8F5, 0xFF7FA39A, 0xFFC995A6, 0xFF8EA4CF, 0xFFD4A373, 0xFF9FB4A0, 0xFFB98FD0};
    public static final String[] ICONS = {"bag", "flame", "moon", "pen", "person", "book", "leaf", "dot"};

    private Palette() {}

    /** Draws a small line icon centred on cx, cy, about s pixels wide. */
    public static void icon(Canvas c, String name, float cx, float cy, float s, Paint p) {
        float h = s / 2f;
        Path path = new Path();
        if ("bag".equals(name)) {
            RectF r = new RectF(cx - h, cy - h * 0.45f, cx + h, cy + h * 0.8f);
            c.drawRoundRect(r, s * 0.12f, s * 0.12f, p);
            path.moveTo(cx - h * 0.35f, cy - h * 0.45f);
            path.lineTo(cx - h * 0.35f, cy - h * 0.8f);
            path.lineTo(cx + h * 0.35f, cy - h * 0.8f);
            path.lineTo(cx + h * 0.35f, cy - h * 0.45f);
            path.moveTo(cx - h, cy + h * 0.1f);
            path.lineTo(cx + h, cy + h * 0.1f);
        } else if ("flame".equals(name)) {
            path.moveTo(cx, cy - h);
            path.cubicTo(cx + h * 0.9f, cy - h * 0.1f, cx + h * 0.8f, cy + h, cx, cy + h);
            path.cubicTo(cx - h * 0.8f, cy + h, cx - h * 0.9f, cy - h * 0.1f, cx, cy - h);
            path.moveTo(cx, cy + h * 0.1f);
            path.cubicTo(cx + h * 0.35f, cy + h * 0.45f, cx + h * 0.3f, cy + h, cx, cy + h);
            path.cubicTo(cx - h * 0.3f, cy + h, cx - h * 0.35f, cy + h * 0.45f, cx, cy + h * 0.1f);
        } else if ("moon".equals(name)) {
            RectF r = new RectF(cx - h, cy - h, cx + h, cy + h);
            path.addArc(r, 60, 240);
            path.quadTo(cx - h * 0.1f, cy, cx + h * 0.5f, cy + h * 0.87f);
        } else if ("pen".equals(name)) {
            path.moveTo(cx - h, cy + h);
            path.lineTo(cx - h * 0.8f, cy + h * 0.3f);
            path.lineTo(cx + h * 0.5f, cy - h);
            path.lineTo(cx + h, cy - h * 0.5f);
            path.lineTo(cx - h * 0.3f, cy + h * 0.8f);
            path.close();
        } else if ("person".equals(name)) {
            c.drawCircle(cx, cy - h * 0.45f, h * 0.38f, p);
            path.moveTo(cx - h * 0.8f, cy + h);
            path.cubicTo(cx - h * 0.7f, cy + h * 0.15f, cx + h * 0.7f, cy + h * 0.15f, cx + h * 0.8f, cy + h);
        } else if ("book".equals(name)) {
            path.moveTo(cx, cy - h * 0.6f);
            path.cubicTo(cx - h * 0.4f, cy - h * 0.9f, cx - h * 0.8f, cy - h * 0.8f, cx - h, cy - h * 0.6f);
            path.lineTo(cx - h, cy + h * 0.8f);
            path.cubicTo(cx - h * 0.6f, cy + h * 0.6f, cx - h * 0.3f, cy + h * 0.6f, cx, cy + h * 0.85f);
            path.cubicTo(cx + h * 0.3f, cy + h * 0.6f, cx + h * 0.6f, cy + h * 0.6f, cx + h, cy + h * 0.8f);
            path.lineTo(cx + h, cy - h * 0.6f);
            path.cubicTo(cx + h * 0.8f, cy - h * 0.8f, cx + h * 0.4f, cy - h * 0.9f, cx, cy - h * 0.6f);
            path.lineTo(cx, cy + h * 0.85f);
        } else if ("leaf".equals(name)) {
            path.moveTo(cx - h, cy + h);
            path.cubicTo(cx - h, cy - h * 0.4f, cx - h * 0.2f, cy - h, cx + h, cy - h);
            path.cubicTo(cx + h, cy + h * 0.2f, cx + h * 0.3f, cy + h, cx - h, cy + h);
            path.moveTo(cx - h, cy + h);
            path.lineTo(cx + h * 0.3f, cy - h * 0.3f);
        } else if ("calendar".equals(name)) {
            RectF r = new RectF(cx - h, cy - h * 0.7f, cx + h, cy + h * 0.9f);
            c.drawRoundRect(r, s * 0.1f, s * 0.1f, p);
            path.moveTo(cx - h, cy - h * 0.15f);
            path.lineTo(cx + h, cy - h * 0.15f);
            path.moveTo(cx - h * 0.45f, cy - h);
            path.lineTo(cx - h * 0.45f, cy - h * 0.5f);
            path.moveTo(cx + h * 0.45f, cy - h);
            path.lineTo(cx + h * 0.45f, cy - h * 0.5f);
        } else {
            c.drawCircle(cx, cy, h * 0.5f, p);
        }
        c.drawPath(path, p);
    }
}
