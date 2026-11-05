package com.watchman.induetime.shared;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Everything about how the dial looks. Hands and digital time are set in Galaxy Wearable. */
public final class FaceStyle {

    public String id = "custom";
    public String name = "Custom";

    // Ground: night, black, glow
    public String ground = "night";
    public int accent = Palette.GOLD;

    // Ring
    public String ringWidth = "regular";   // thin, regular, bold
    public int ringHours = 12;             // 6 or 12 hours shown
    public String gap = "small";           // none, small, wide
    public String swell = "soft";          // none, soft, strong
    public boolean ringGoogle = true;

    // Markers: none, hours, minutes, numerals
    public String markers = "minutes";

    // Centre lines, top to bottom. block, until, left, next, date, lens
    public final List<String> lines = new ArrayList<String>();

    // Icon
    public boolean icon = true;
    public String iconSize = "medium";     // small, medium, large

    // Always on display
    public boolean aodRing = true;
    public boolean aodIcon = false;
    public boolean aodText = true;
    public String aodDim = "medium";       // low, medium, high (how dim)
    public String aodRingStyle = "outline"; // outline, solid

    public FaceStyle() {
        lines.add("block");
        lines.add("until");
    }

    public FaceStyle copy() { return fromJson(toJson()); }

    public static List<FaceStyle> presets() {
        List<FaceStyle> out = new ArrayList<FaceStyle>();
        FaceStyle a = new FaceStyle();
        a.id = "classic"; a.name = "Classic";
        out.add(a);

        FaceStyle q = new FaceStyle();
        q.id = "quiet"; q.name = "Quiet";
        q.ringWidth = "thin"; q.markers = "none"; q.swell = "soft"; q.icon = false;
        q.lines.clear(); q.lines.add("block");
        q.aodText = false;
        out.add(q);

        FaceStyle f = new FaceStyle();
        f.id = "full"; f.name = "Full";
        f.ringWidth = "bold"; f.markers = "numerals"; f.swell = "strong"; f.iconSize = "large";
        f.lines.clear(); f.lines.add("date"); f.lines.add("block"); f.lines.add("left"); f.lines.add("next");
        f.aodIcon = true;
        out.add(f);

        FaceStyle n = new FaceStyle();
        n.id = "night"; n.name = "Night";
        n.ground = "black"; n.ringWidth = "thin"; n.markers = "hours"; n.accent = Palette.LAV;
        n.lines.clear(); n.lines.add("block");
        n.aodDim = "high"; n.aodText = false;
        out.add(n);

        FaceStyle w = new FaceStyle();
        w.id = "ledger"; w.name = "Ledger";
        w.ringWidth = "thin"; w.markers = "none"; w.ringHours = 12;
        w.lines.clear(); w.lines.add("date"); w.lines.add("block"); w.lines.add("left");
        out.add(w);
        return out;
    }

    public float ringStroke() { return "thin".equals(ringWidth) ? 3.2f : "bold".equals(ringWidth) ? 8f : 5.5f; }

    public float swellStroke() {
        float w = ringStroke();
        return "none".equals(swell) ? w : "strong".equals(swell) ? w * 2.4f : w * 1.8f;
    }

    public float gapDeg() { return "none".equals(gap) ? 0.3f : "wide".equals(gap) ? 3f : 1.4f; }

    public float iconPx() { return "small".equals(iconSize) ? 13 : "large".equals(iconSize) ? 24 : 18; }

    public int aodAlpha() { return "high".equals(aodDim) ? 70 : "low".equals(aodDim) ? 170 : 115; }

    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("n", name); o.put("gr", ground); o.put("ac", accent);
            o.put("rw", ringWidth); o.put("rh", ringHours); o.put("gp", gap); o.put("sw", swell); o.put("rg", ringGoogle);
            o.put("mk", markers);
            JSONArray l = new JSONArray();
            for (String s : lines) l.put(s);
            o.put("ln", l);
            o.put("ic", icon); o.put("is", iconSize);
            o.put("ar", aodRing); o.put("ai", aodIcon); o.put("at", aodText); o.put("ad", aodDim); o.put("as", aodRingStyle);
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    public static FaceStyle fromJson(String s) {
        FaceStyle f = new FaceStyle();
        if (s == null || s.length() == 0) return f;
        try {
            JSONObject o = new JSONObject(s);
            f.id = o.optString("id", f.id); f.name = o.optString("n", f.name);
            f.ground = o.optString("gr", f.ground); f.accent = o.optInt("ac", f.accent);
            f.ringWidth = o.optString("rw", f.ringWidth); f.ringHours = o.optInt("rh", f.ringHours);
            f.gap = o.optString("gp", f.gap); f.swell = o.optString("sw", f.swell); f.ringGoogle = o.optBoolean("rg", f.ringGoogle);
            f.markers = o.optString("mk", f.markers);
            JSONArray l = o.optJSONArray("ln");
            if (l != null) { f.lines.clear(); for (int i = 0; i < l.length(); i++) f.lines.add(l.optString(i)); }
            f.icon = o.optBoolean("ic", f.icon); f.iconSize = o.optString("is", f.iconSize);
            f.aodRing = o.optBoolean("ar", f.aodRing); f.aodIcon = o.optBoolean("ai", f.aodIcon); f.aodText = o.optBoolean("at", f.aodText);
            f.aodDim = o.optString("ad", f.aodDim); f.aodRingStyle = o.optString("as", f.aodRingStyle);
        } catch (Exception ignored) {}
        return f;
    }
}
