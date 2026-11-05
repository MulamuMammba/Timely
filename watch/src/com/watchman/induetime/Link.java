package com.watchman.induetime;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import com.watchman.induetime.shared.WatchData;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The watch asks the phone for the day over Bluetooth and keeps the latest copy. */
final class Link {

    static final UUID ID = UUID.fromString("6b1f0e4a-5d1c-4b8e-9a3e-1d2c3b4a5f60");
    private static final Object LOCK = new Object();
    private static final List<Runnable> listeners = new ArrayList<Runnable>();
    private static volatile boolean pulling;

    private Link() {}

    static SharedPreferences p(Context c) { return c.getSharedPreferences("w", Context.MODE_PRIVATE); }

    static boolean allowed(Context c) {
        return Build.VERSION.SDK_INT < 31 || c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    static String phone(Context c) { return p(c).getString("phone", ""); }

    static void setPhone(Context c, String addr, String name) {
        p(c).edit().putString("phone", addr).putString("phoneName", name).apply();
    }

    static WatchData raw(Context c) { return WatchData.fromJson(p(c).getString("data", null)); }

    /** What the watch should show now, after its own lens choice. */
    static WatchData data(Context c) {
        WatchData d = raw(c);
        return d.filtered(lensId(c, d));
    }

    static String lensId(Context c, WatchData d) {
        SharedPreferences sp = p(c);
        if (sp.getBoolean("pending", false)) return sp.getString("local", "");
        return d.lensId;
    }

    /** Choose a lens on the watch. Empty means automatic. Sent to the phone right away. */
    static void chooseLens(Context c, String id) {
        p(c).edit().putString("local", id).putBoolean("pending", true).apply();
        changed(c);
        pullAsync(c, 0);
    }

    static String state(Context c) { return p(c).getString("state", "Not linked yet"); }

    static synchronized void listen(Runnable r) { if (!listeners.contains(r)) listeners.add(r); }
    static synchronized void unlisten(Runnable r) { listeners.remove(r); }

    static void changed(Context c) {
        Dial.requestUpdate(c);
        List<Runnable> copy;
        synchronized (Link.class) { copy = new ArrayList<Runnable>(listeners); }
        for (Runnable r : copy) r.run();
    }

    /** Asks the phone for fresh data, unless it asked within minGap. */
    static void pullAsync(final Context ctx, final long minGap) {
        final Context c = ctx.getApplicationContext();
        if (pulling) return;
        if (minGap > 0 && System.currentTimeMillis() - p(c).getLong("tried", 0) < minGap) return;
        new Thread(new Runnable() {
            public void run() { pull(c); }
        }, "idt-pull").start();
    }

    static boolean pull(Context c) {
        String addr = phone(c);
        if (addr.length() == 0) { state(c, "Choose your phone"); return false; }
        if (!allowed(c)) { state(c, "Allow Bluetooth in In Due Time"); return false; }
        synchronized (LOCK) {
            pulling = true;
            p(c).edit().putLong("tried", System.currentTimeMillis()).apply();
            BluetoothSocket s = null;
            try {
                BluetoothManager bm = (BluetoothManager) c.getSystemService(Context.BLUETOOTH_SERVICE);
                BluetoothAdapter a = bm == null ? null : bm.getAdapter();
                if (a == null || !a.isEnabled()) { state(c, "Bluetooth is off"); return false; }
                BluetoothDevice d = a.getRemoteDevice(addr);
                s = d.createRfcommSocketToServiceRecord(ID);
                s.connect();
                SharedPreferences sp = p(c);
                boolean pending = sp.getBoolean("pending", false);
                String local = sp.getString("local", "");
                DataOutputStream out = new DataOutputStream(s.getOutputStream());
                out.writeUTF(pending ? "lens=" + local : "pull");
                out.flush();
                DataInputStream in = new DataInputStream(s.getInputStream());
                int len = in.readInt();
                if (len <= 0 || len > 1 << 20) { state(c, "The phone sent nothing"); return false; }
                byte[] buf = new byte[len];
                in.readFully(buf);
                String json = new String(buf, Charset.forName("UTF-8"));
                String old = sp.getString("data", "");
                SharedPreferences.Editor ed = sp.edit().putString("data", json).putLong("got", System.currentTimeMillis())
                        .putString("state", "Linked");
                if (pending && local.equals(sp.getString("local", ""))) ed.putBoolean("pending", false);
                ed.apply();
                Alarms.schedule(c);
                if (!stripTime(old).equals(stripTime(json)) || pending) changed(c);
                return true;
            } catch (Exception e) {
                state(c, "Could not reach the phone");
                return false;
            } finally {
                pulling = false;
                try { if (s != null) s.close(); } catch (Exception ignored) {}
            }
        }
    }

    /** Ignores the send time so an unchanged day does not redraw. */
    private static String stripTime(String json) { return json == null ? "" : json.replaceAll("\"at\":\\d+", ""); }

    private static void state(Context c, String s) { p(c).edit().putString("state", s).apply(); }

    static void tap(Context c) {
        long[] pattern = {0, 140, 110, 140};
        try {
            Vibrator v;
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                v = vm.getDefaultVibrator();
            } else {
                v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (v != null) v.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } catch (Exception ignored) {}
    }
}
