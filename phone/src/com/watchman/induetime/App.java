package com.watchman.induetime;

import android.content.Context;

import com.watchman.induetime.shared.Palette;
import com.watchman.induetime.shared.WatchData;

import java.util.List;

/** Small shared helpers and the one place that tells everything the data changed. */
final class App {
    private App() {}

    static int color(Context c, Cal.Block b) {
        if (b.google) return Palette.LINEN;
        Store.Category k = Store.category(c, b.cat);
        return k == null ? Palette.CHOICES[0] : k.color;
    }

    static String catName(Context c, Cal.Block b) {
        if (b.google) return "Google";
        Store.Category k = Store.category(c, b.cat);
        return k == null ? "Block" : k.name;
    }

    static String icon(Context c, Cal.Block b) {
        if (b.google) return "calendar";
        Store.Category k = Store.category(c, b.cat);
        return k == null ? "dot" : k.icon;
    }

    /** Call after any change to blocks or settings. */
    static void changed(Context c) {
        Sched.reschedule(c);
        ClockWidget.updateAll(c);
        WatchLink.pushAsync(c, null);
    }

    /** Builds the watch message for the next 12 hours. The watch filters by lens itself. */
    static WatchData watchData(Context c) {
        WatchData d = new WatchData();
        long now = System.currentTimeMillis();
        Store.Lens lens = Store.activeLens(c, "watch");
        List<Cal.Block> list = Cal.between(c, now - 2 * 3600000L, now + 13 * 3600000L);
        for (Cal.Block b : list) {
            if (b.allDay) continue;
            WatchData.Item i = new WatchData.Item();
            i.start = b.begin; i.end = b.end; i.title = b.title; i.category = b.cat;
            i.color = color(c, b); i.icon = icon(c, b); i.google = b.google;
            d.items.add(i);
        }
        d.lens = lens == null ? "" : lens.name;
        d.lensId = lens == null ? "" : lens.id;
        d.face = Store.str(c, "face", "w1");
        d.showIcon = Store.bool(c, "faceIcon", true);
        d.showSeconds = Store.bool(c, "faceSec", true);
        d.aod = Store.str(c, "faceAod", "ring");
        d.tapOn = Store.bool(c, "tap", true);
        d.faceId = Store.faceId(c);
        for (com.watchman.induetime.shared.FaceStyle f : Store.faces(c)) d.faces.put(f.id, f);
        d.sentAt = now;
        for (Store.Lens l : Store.lenses(c)) {
            if (!l.shows("watch")) continue;
            WatchData.LensDef x = new WatchData.LensDef();
            x.id = l.id; x.name = l.name; x.google = l.google; x.aod = l.aod; x.cats.addAll(l.cats); x.face = l.face;
            d.lenses.add(x);
        }
        return d;
    }
}
