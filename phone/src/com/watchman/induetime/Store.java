package com.watchman.induetime;

import android.content.Context;
import android.content.SharedPreferences;

import com.watchman.induetime.shared.FaceStyle;
import com.watchman.induetime.shared.Palette;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** All app settings, kept on the device. */
public final class Store {

    public static final class Category {
        public String id, name, icon;
        public int color;
        public boolean builtin;
    }

    public static final class Lens {
        public String id, name;
        public boolean useTime = true;
        public int days = 0x1F;           // bit 0 Monday ... bit 6 Sunday
        public int from = 8 * 60, to = 17 * 60;
        public String wifi = "";          // empty means no Wi-Fi trigger
        public Set<String> cats = new HashSet<String>();
        public boolean google = true;
        public String target = "both";    // watch, widget, both
        public String aod = "ring";       // time, ring, ringicon
        public String face = "";          // face style id, empty uses the default face

        public boolean manualOnly() { return !useTime && wifi.length() == 0; }

        public boolean shows(String where) { return "both".equals(target) || target.equals(where); }

        public String triggerText() {
            List<String> parts = new ArrayList<String>();
            if (useTime) parts.add(daysText() + " " + Fmt.hm(from) + " to " + Fmt.hm(to));
            if (wifi.length() > 0) parts.add("On " + wifi + " Wi-Fi");
            if (parts.isEmpty()) return "By hand only";
            StringBuilder b = new StringBuilder();
            for (String p : parts) { if (b.length() > 0) b.append(" · "); b.append(p); }
            return b.toString();
        }

        public String daysText() {
            if (days == 0x7F) return "Every day";
            if (days == 0x1F) return "Weekdays";
            if (days == 0x60) return "Weekends";
            String[] n = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 7; i++) if ((days & (1 << i)) != 0) { if (b.length() > 0) b.append(", "); b.append(n[i]); }
            return b.length() == 0 ? "No days" : b.toString();
        }

        public String targetText() {
            return "watch".equals(target) ? "Watch only" : "widget".equals(target) ? "Widget only" : "Watch + widget";
        }

        boolean matches(long now, String ssid) {
            boolean any = false;
            if (useTime) {
                any = true;
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(now);
                int dow = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Monday 0
                int m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
                boolean timeOk;
                if (from <= to) timeOk = m >= from && m < to && (days & (1 << dow)) != 0;
                else {
                    int prev = (dow + 6) % 7;
                    timeOk = (m >= from && (days & (1 << dow)) != 0) || (m < to && (days & (1 << prev)) != 0);
                }
                if (!timeOk) return false;
            }
            if (wifi.length() > 0) {
                any = true;
                if (ssid == null || !wifi.equalsIgnoreCase(ssid)) return false;
            }
            return any;
        }
    }

    private Store() {}

    static SharedPreferences p(Context c) { return c.getSharedPreferences("idt", Context.MODE_PRIVATE); }

    // Categories

    static List<Category> categories(Context c) {
        List<Category> out = new ArrayList<Category>();
        String s = p(c).getString("cats", null);
        if (s == null) {
            out.add(cat("work", "Work", Palette.CHOICES[0], "bag", true));
            out.add(cat("prayer", "Prayer", Palette.CHOICES[1], "flame", true));
            out.add(cat("rest", "Rest", Palette.CHOICES[2], "moon", true));
            out.add(cat("writing", "Writing", Palette.CHOICES[3], "pen", true));
            out.add(cat("personal", "Personal", Palette.CHOICES[4], "person", true));
            return out;
        }
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(cat(o.getString("id"), o.getString("n"), o.getInt("k"), o.optString("i", "dot"), o.optBoolean("b")));
            }
        } catch (Exception ignored) {}
        return out;
    }

    static void saveCategories(Context c, List<Category> list) {
        JSONArray a = new JSONArray();
        try {
            for (Category k : list) {
                JSONObject o = new JSONObject();
                o.put("id", k.id); o.put("n", k.name); o.put("k", k.color); o.put("i", k.icon); o.put("b", k.builtin);
                a.put(o);
            }
        } catch (Exception ignored) {}
        p(c).edit().putString("cats", a.toString()).apply();
    }

    static Category category(Context c, String id) {
        for (Category k : categories(c)) if (k.id.equals(id)) return k;
        return null;
    }

    private static Category cat(String id, String name, int color, String icon, boolean builtin) {
        Category k = new Category();
        k.id = id; k.name = name; k.color = color; k.icon = icon; k.builtin = builtin;
        return k;
    }

    // Lenses

    static List<Lens> lenses(Context c) {
        List<Lens> out = new ArrayList<Lens>();
        String s = p(c).getString("lenses", null);
        if (s == null) {
            Lens w = new Lens(); w.id = "work"; w.name = "Work"; w.cats.add("work"); out.add(w);
            Lens h = new Lens(); h.id = "home"; h.name = "Home"; h.days = 0x7F; h.from = 17 * 60; h.to = 8 * 60;
            h.cats.add("prayer"); h.cats.add("rest"); h.cats.add("writing"); h.cats.add("personal"); h.target = "watch"; out.add(h);
            Lens r = new Lens(); r.id = "rest"; r.name = "Rest"; r.useTime = false; r.google = false; r.aod = "time"; out.add(r);
            return out;
        }
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Lens l = new Lens();
                l.id = o.getString("id"); l.name = o.getString("n"); l.useTime = o.optBoolean("ut", true);
                l.days = o.optInt("d", 0x1F); l.from = o.optInt("f"); l.to = o.optInt("t"); l.wifi = o.optString("w", "");
                JSONArray cs = o.optJSONArray("c");
                if (cs != null) for (int j = 0; j < cs.length(); j++) l.cats.add(cs.getString(j));
                l.google = o.optBoolean("g", true); l.target = o.optString("tg", "both"); l.aod = o.optString("a", "ring");
                l.face = o.optString("fc", "");
                out.add(l);
            }
        } catch (Exception ignored) {}
        return out;
    }

    static void saveLenses(Context c, List<Lens> list) {
        JSONArray a = new JSONArray();
        try {
            for (Lens l : list) {
                JSONObject o = new JSONObject();
                o.put("id", l.id); o.put("n", l.name); o.put("ut", l.useTime); o.put("d", l.days); o.put("f", l.from); o.put("t", l.to);
                o.put("w", l.wifi); JSONArray cs = new JSONArray(); for (String k : l.cats) cs.put(k); o.put("c", cs);
                o.put("g", l.google); o.put("tg", l.target); o.put("a", l.aod); o.put("fc", l.face);
                a.put(o);
            }
        } catch (Exception ignored) {}
        p(c).edit().putString("lenses", a.toString()).apply();
    }

    static String override(Context c) { return p(c).getString("override", ""); }
    static void setOverride(Context c, String id) { p(c).edit().putString("override", id == null ? "" : id).apply(); }

    /** The lens that applies to the watch or widget right now, or null to show everything. */
    static Lens activeLens(Context c, String where) {
        List<Lens> all = lenses(c);
        String o = override(c);
        if (o.length() > 0) for (Lens l : all) if (l.id.equals(o) && l.shows(where)) return l;
        String ssid = Wifi.ssid(c);
        long now = System.currentTimeMillis();
        for (Lens l : all) if (l.shows(where) && l.matches(now, ssid)) return l;
        return null;
    }

    /** The lens shown as active in the app. */
    static Lens activeAny(Context c) {
        List<Lens> all = lenses(c);
        String o = override(c);
        if (o.length() > 0) for (Lens l : all) if (l.id.equals(o)) return l;
        String ssid = Wifi.ssid(c);
        long now = System.currentTimeMillis();
        for (Lens l : all) if (l.matches(now, ssid)) return l;
        return null;
    }

    static boolean lensAllows(Lens l, Cal.Block b) {
        if (l == null) return true;
        if (b.google) return l.google;
        return l.cats.contains(b.cat);
    }

    // Watch faces

    static String faceId(Context c) { return str(c, "faceId", "dawn"); }
    static void setFaceId(Context c, String id) { put(c, "faceId", id); }

    static FaceStyle custom(Context c) {
        String s = str(c, "customFace", "");
        FaceStyle f = s.length() == 0 ? FaceStyle.presets().get(0).copy() : FaceStyle.fromJson(s);
        f.id = "custom";
        f.name = "Custom";
        return f;
    }

    static void saveCustom(Context c, FaceStyle f) {
        f.id = "custom";
        f.name = "Custom";
        put(c, "customFace", f.toJson());
    }

    static List<FaceStyle> faces(Context c) {
        List<FaceStyle> out = FaceStyle.presets();
        out.add(custom(c));
        return out;
    }

    static FaceStyle face(Context c, String id) {
        for (FaceStyle f : faces(c)) if (f.id.equals(id)) return f;
        return FaceStyle.presets().get(0);
    }

    // Calendars

    static Set<String> visibleCals(Context c) { return new HashSet<String>(p(c).getStringSet("vis", new HashSet<String>())); }
    static void setVisibleCals(Context c, Set<String> s) { p(c).edit().putStringSet("vis", s).putBoolean("visSet", true).apply(); }
    static boolean visibleSet(Context c) { return p(c).getBoolean("visSet", false); }
    static long blocksCal(Context c) { return p(c).getLong("blocksCal", -1); }
    static void setBlocksCal(Context c, long id) { p(c).edit().putLong("blocksCal", id).apply(); }

    // Simple values

    static String str(Context c, String k, String def) { return p(c).getString(k, def); }
    static void put(Context c, String k, String v) { p(c).edit().putString(k, v).apply(); }
    static boolean bool(Context c, String k, boolean def) { return p(c).getBoolean(k, def); }
    static void put(Context c, String k, boolean v) { p(c).edit().putBoolean(k, v).apply(); }

    // Done marks, local only

    static boolean isDone(Context c, Cal.Block b) { return p(c).getStringSet("done", new HashSet<String>()).contains(b.key()); }

    static void setDone(Context c, Cal.Block b, boolean done) {
        Set<String> s = new HashSet<String>(p(c).getStringSet("done", new HashSet<String>()));
        if (done) s.add(b.key()); else s.remove(b.key());
        // Keep the set small: drop marks older than two weeks
        long cutoff = System.currentTimeMillis() - 14L * 86400000L;
        Set<String> keep = new HashSet<String>();
        for (String k : s) {
            try { if (Long.parseLong(k.substring(k.indexOf(':') + 1)) > cutoff) keep.add(k); } catch (Exception e) { keep.add(k); }
        }
        p(c).edit().putStringSet("done", keep).apply();
    }
}
