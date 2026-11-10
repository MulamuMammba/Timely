package com.watchman.induetime.shared;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * How the watch face looks. One of five families, plus colours, ring, size and two text lines.
 * Fifteen defaults ship; any change saves as Custom.
 */
public final class FaceStyle {

    public static final String[] FAMILIES = {"sundial", "orbit", "horizon", "signal", "still"};
    public static final String[] FAMILY_NAMES = {"Sundial", "Orbit", "Horizon", "Signal", "Still"};

    public String id = "dawn";
    public String name = "Dawn";
    public String family = "sundial";

    public String ground = "night";   // night, black, navy, amethyst
    public int accent = 0xFFD8B25E;   // marks the block you are in
    public int ink = 0xFFECE7F5;      // time, hand and main text
    public String ring = "regular";   // thin, regular, bold
    public int hours = 12;            // Orbit only: 6, 12 or 24
    public String size = "medium";    // medium, large
    public final List<String> lines = new ArrayList<String>(); // block, until, left, next, date, or empty

    public FaceStyle() {
        lines.add("block");
        lines.add("until");
    }

    public String familyName() {
        for (int i = 0; i < FAMILIES.length; i++) if (FAMILIES[i].equals(family)) return FAMILY_NAMES[i];
        return "Face";
    }

    public String line(int i) { return i < lines.size() ? lines.get(i) : ""; }

    public void setLine(int i, String key) {
        while (lines.size() <= i) lines.add("");
        lines.set(i, key == null ? "" : key);
    }

    public FaceStyle copy() { return fromJson(toJson()); }

    private static FaceStyle p(String id, String fam, String name, String ground, int accent, int ink, String ring, int hours, String size, String l1, String l2) {
        FaceStyle f = new FaceStyle();
        f.id = id; f.family = fam; f.name = name; f.ground = ground; f.accent = accent; f.ink = ink;
        f.ring = ring; f.hours = hours; f.size = size;
        f.lines.clear(); f.lines.add(l1); f.lines.add(l2);
        return f;
    }

    static final int GOLD = 0xFFD8B25E, LAV = 0xFFB7A3F0, LINEN = 0xFFECE7F5, SAGE = 0xFF7FA39A, SAGE_INK = 0xFF9FC7B6,
            ROSE = 0xFFC995A6, ROSE_INK = 0xFFE0B4C2, BLUE = 0xFF8EA4CF;

    /** The fifteen defaults, three per family. */
    public static List<FaceStyle> presets() {
        List<FaceStyle> o = new ArrayList<FaceStyle>();
        o.add(p("dawn", "sundial", "Dawn", "night", GOLD, LINEN, "regular", 12, "medium", "block", "until"));
        o.add(p("noon", "sundial", "Noon", "navy", GOLD, LINEN, "thin", 12, "large", "block", ""));
        o.add(p("vesper", "sundial", "Vesper", "black", LAV, LAV, "bold", 12, "medium", "block", "left"));
        o.add(p("orbit", "orbit", "Orbit", "night", GOLD, LINEN, "regular", 12, "medium", "block", ""));
        o.add(p("drift", "orbit", "Drift", "amethyst", LINEN, LINEN, "thin", 6, "large", "block", ""));
        o.add(p("deep", "orbit", "Deep", "black", SAGE, SAGE_INK, "bold", 24, "medium", "block", ""));
        o.add(p("tide", "horizon", "Tide", "night", GOLD, LINEN, "regular", 12, "medium", "block", "left"));
        o.add(p("dusk", "horizon", "Dusk", "amethyst", ROSE, LINEN, "regular", 12, "large", "block", "until"));
        o.add(p("frost", "horizon", "Frost", "navy", BLUE, LINEN, "regular", 12, "medium", "block", "next"));
        o.add(p("signal", "signal", "Signal", "night", GOLD, LINEN, "regular", 12, "medium", "block", "next"));
        o.add(p("terminal", "signal", "Terminal", "black", SAGE, SAGE_INK, "regular", 12, "large", "block", "left"));
        o.add(p("amber", "signal", "Amber", "black", LINEN, GOLD, "regular", 12, "medium", "block", "until"));
        o.add(p("still", "still", "Still", "night", GOLD, LINEN, "regular", 12, "medium", "block", "left"));
        o.add(p("mist", "still", "Mist", "navy", LAV, LINEN, "regular", 12, "large", "block", ""));
        o.add(p("ember", "still", "Ember", "black", ROSE, ROSE_INK, "regular", 12, "medium", "left", "next"));
        return o;
    }

    public static FaceStyle preset(String id) {
        for (FaceStyle f : presets()) if (f.id.equals(id)) return f;
        return null;
    }

    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("n", name); o.put("fm", family); o.put("gr", ground); o.put("ac", accent); o.put("ik", ink);
            o.put("rg", ring); o.put("hr", hours); o.put("sz", size);
            JSONArray l = new JSONArray();
            for (String s : lines) l.put(s);
            o.put("ln", l);
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    public static FaceStyle fromJson(String s) {
        FaceStyle f = presets().get(0);
        if (s == null || s.length() == 0) return f;
        try {
            JSONObject o = new JSONObject(s);
            if (!o.has("fm")) return f; // older style format: start fresh
            f.id = o.optString("id", f.id); f.name = o.optString("n", f.name); f.family = o.optString("fm", f.family);
            f.ground = o.optString("gr", f.ground); f.accent = o.optInt("ac", f.accent); f.ink = o.optInt("ik", f.ink);
            f.ring = o.optString("rg", f.ring); f.hours = o.optInt("hr", f.hours); f.size = o.optString("sz", f.size);
            JSONArray l = o.optJSONArray("ln");
            if (l != null) { f.lines.clear(); for (int i = 0; i < l.length(); i++) f.lines.add(l.optString(i)); }
        } catch (Exception ignored) {}
        return f;
    }
}
