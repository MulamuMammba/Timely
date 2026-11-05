package com.watchman.induetime.shared;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Everything the watch needs, sent from the phone as one JSON message. */
public final class WatchData {

    public static final class Item {
        public long start, end;
        public String title = "";
        public String category = "";
        public int color = 0xFF8B78D8;
        public String icon = "dot";
        public boolean google;
    }

    public final List<Item> items = new ArrayList<Item>();
    public String lens = "";
    public String face = "w1";          // w1 Bezel, w2 Ledger
    public boolean showIcon = true;
    public boolean showSeconds = true;
    public String aod = "ring";          // time, ring, ringicon
    public long sentAt;
    public String tapTitle;              // a block just ended
    public long tapAt;
    public String lensId = "";          // lens the phone chose for the watch, empty for none
    public boolean tapOn = true;        // tap the wrist when a block ends
    public String faceId = "classic";   // face used when no lens picks one
    public final java.util.Map<String, FaceStyle> faces = new java.util.LinkedHashMap<String, FaceStyle>();

    /** The face style to draw, after the lens choice. */
    public FaceStyle style() {
        LensDef l = lens(lensId);
        String id = l != null && l.face.length() > 0 ? l.face : faceId;
        FaceStyle f = faces.get(id);
        if (f == null) f = faces.get(faceId);
        if (f == null) for (FaceStyle p : FaceStyle.presets()) if (p.id.equals(id)) f = p;
        return f == null ? new FaceStyle() : f;
    }
    public final List<LensDef> lenses = new ArrayList<LensDef>();

    public static final class LensDef {
        public String id = "", name = "", aod = "ring", face = "";
        public boolean google = true;
        public final java.util.Set<String> cats = new java.util.HashSet<String>();
    }

    public LensDef lens(String id) {
        for (LensDef l : lenses) if (l.id.equals(id)) return l;
        return null;
    }

    /** A copy showing only what the given lens allows. Empty id shows everything. */
    public WatchData filtered(String id) {
        WatchData d = new WatchData();
        LensDef l = id == null ? null : lens(id);
        for (Item i : items) if (l == null || (i.google ? l.google : l.cats.contains(i.category))) d.items.add(i);
        d.lens = l == null ? "" : l.name;
        d.lensId = l == null ? "" : l.id;
        d.face = face; d.showIcon = showIcon; d.showSeconds = showSeconds;
        d.aod = l == null ? aod : l.aod;
        d.sentAt = sentAt; d.tapTitle = tapTitle; d.tapAt = tapAt;
        d.lenses.addAll(lenses);
        d.tapOn = tapOn; d.faceId = faceId; d.faces.putAll(faces);
        return d;
    }

    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            JSONArray a = new JSONArray();
            for (Item i : items) {
                JSONObject j = new JSONObject();
                j.put("s", i.start); j.put("e", i.end); j.put("t", i.title); j.put("c", i.category);
                j.put("k", i.color); j.put("i", i.icon); j.put("g", i.google);
                a.put(j);
            }
            o.put("items", a);
            o.put("lens", lens); o.put("face", face); o.put("icon", showIcon); o.put("sec", showSeconds);
            o.put("aod", aod); o.put("at", sentAt);
            if (tapTitle != null) { o.put("tap", tapTitle); o.put("tapAt", tapAt); }
            o.put("lensId", lensId);
            o.put("tapOn", tapOn); o.put("faceId", faceId);
            JSONObject fs = new JSONObject();
            for (java.util.Map.Entry<String, FaceStyle> e : faces.entrySet()) fs.put(e.getKey(), new JSONObject(e.getValue().toJson()));
            o.put("faces", fs);
            JSONArray l = new JSONArray();
            for (LensDef x : lenses) {
                JSONObject j = new JSONObject();
                j.put("id", x.id); j.put("n", x.name); j.put("g", x.google); j.put("a", x.aod); j.put("f", x.face);
                JSONArray cs = new JSONArray();
                for (String k : x.cats) cs.put(k);
                j.put("c", cs);
                l.put(j);
            }
            o.put("lenses", l);
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    public static WatchData fromJson(String s) {
        WatchData d = new WatchData();
        if (s == null) return d;
        try {
            JSONObject o = new JSONObject(s);
            JSONArray a = o.optJSONArray("items");
            if (a != null) for (int n = 0; n < a.length(); n++) {
                JSONObject j = a.getJSONObject(n);
                Item i = new Item();
                i.start = j.optLong("s"); i.end = j.optLong("e"); i.title = j.optString("t");
                i.category = j.optString("c"); i.color = j.optInt("k", i.color); i.icon = j.optString("i", "dot");
                i.google = j.optBoolean("g");
                d.items.add(i);
            }
            d.lens = o.optString("lens"); d.face = o.optString("face", "w1");
            d.showIcon = o.optBoolean("icon", true); d.showSeconds = o.optBoolean("sec", true);
            d.aod = o.optString("aod", "ring"); d.sentAt = o.optLong("at");
            if (o.has("tap")) { d.tapTitle = o.optString("tap"); d.tapAt = o.optLong("tapAt"); }
            d.lensId = o.optString("lensId");
            d.tapOn = o.optBoolean("tapOn", true); d.faceId = o.optString("faceId", "classic");
            JSONObject fs = o.optJSONObject("faces");
            if (fs != null) {
                java.util.Iterator<String> it = fs.keys();
                while (it.hasNext()) { String k = it.next(); d.faces.put(k, FaceStyle.fromJson(fs.optJSONObject(k).toString())); }
            }
            JSONArray l = o.optJSONArray("lenses");
            if (l != null) for (int n = 0; n < l.length(); n++) {
                JSONObject j = l.optJSONObject(n);
                if (j == null) continue;
                LensDef x = new LensDef();
                x.id = j.optString("id"); x.name = j.optString("n"); x.google = j.optBoolean("g", true); x.aod = j.optString("a", "ring"); x.face = j.optString("f", "");
                JSONArray cs = j.optJSONArray("c");
                if (cs != null) for (int k = 0; k < cs.length(); k++) x.cats.add(cs.optString(k));
                d.lenses.add(x);
            }
        } catch (Exception ignored) {}
        return d;
    }

    public Item current(long now) {
        for (Item i : items) if (!i.google && i.start <= now && i.end > now) return i;
        for (Item i : items) if (i.start <= now && i.end > now) return i;
        return null;
    }

    public Item next(long now) {
        Item best = null;
        for (Item i : items) if (i.start > now && (best == null || i.start < best.start)) best = i;
        return best;
    }
}
