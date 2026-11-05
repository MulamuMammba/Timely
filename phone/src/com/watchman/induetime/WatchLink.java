package com.watchman.induetime;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.Charset;
import java.util.UUID;

/**
 * Keeps a quiet Bluetooth door open so the watch can ask for the day whenever it needs to.
 * The watch connects; the phone answers with blocks, lenses and face styles.
 */
public final class WatchLink {

    public static final UUID ID = UUID.fromString("6b1f0e4a-5d1c-4b8e-9a3e-1d2c3b4a5f60");
    static final String CHANNEL = "watch_link";

    private WatchLink() {}

    static boolean enabled(Context c) { return Store.bool(c, "watchLink", false); }

    static boolean allowed(Context c) {
        return Build.VERSION.SDK_INT < 31 || c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    /** Older call sites: make sure the link is running. The watch fetches changes itself. */
    static void pushAsync(Context c, Runnable done) {
        ensure(c);
        if (done != null) done.run();
    }

    static void push(Context c, String tapTitle) { ensure(c); }

    static void ensure(Context c) {
        if (!enabled(c) || !allowed(c)) return;
        try {
            Intent i = new Intent(c, Door.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception e) {
            status(c, "Could not start the watch link.");
        }
    }

    static void stop(Context c) {
        try { c.stopService(new Intent(c, Door.class)); } catch (Exception ignored) {}
    }

    static void status(Context c, String s) { Store.put(c, "watchStatus", s); }

    static String lastStatus(Context c) {
        String s = Store.str(c, "watchStatus", "");
        return s.length() == 0 ? "Waiting for your watch." : s;
    }

    /** The service that listens. */
    public static class Door extends Service {
        private Thread thread;
        private volatile boolean running;
        private volatile BluetoothServerSocket server;

        @Override public IBinder onBind(Intent intent) { return null; }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            if (!allowed(this)) { stopSelf(); return START_NOT_STICKY; }
            try {
                Notification n = notification();
                if (Build.VERSION.SDK_INT >= 29) startForeground(9, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
                else startForeground(9, n);
            } catch (Exception e) {
                status(this, "Android stopped the watch link from starting.");
                stopSelf();
                return START_NOT_STICKY;
            }
            if (thread == null || !thread.isAlive()) {
                running = true;
                thread = new Thread(new Runnable() { public void run() { loop(); } }, "idt-door");
                thread.start();
            }
            return START_STICKY;
        }

        @Override
        public void onDestroy() {
            running = false;
            try { if (server != null) server.close(); } catch (Exception ignored) {}
            super.onDestroy();
        }

        private Notification notification() {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26 && nm != null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "Watch link", NotificationManager.IMPORTANCE_MIN);
                ch.setDescription("Keeps your watch face up to date. You can hide this notification.");
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
            Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
            b.setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle("Linked to your watch")
                    .setContentText("Keeps your watch face up to date.")
                    .setOngoing(true)
                    .setColor(0xFFB7A3F0)
                    .setContentIntent(PendingIntent.getActivity(this, 30, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_MIN);
            return b.build();
        }

        private void loop() {
            final Context c = getApplicationContext();
            while (running) {
                try {
                    BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                    BluetoothAdapter a = bm == null ? null : bm.getAdapter();
                    if (a == null || !a.isEnabled()) { status(c, "Bluetooth is off."); sleep(30000); continue; }
                    server = a.listenUsingRfcommWithServiceRecord("In Due Time", ID);
                    if (Store.str(c, "watchStatus", "").length() == 0 || lastStatus(c).startsWith("Bluetooth")) status(c, "Waiting for your watch.");
                    while (running) {
                        BluetoothSocket s = server.accept();
                        try { answer(c, s); } catch (Exception ignored) {} finally { try { s.close(); } catch (Exception ignored) {} }
                    }
                } catch (Exception e) {
                    if (!running) return;
                    sleep(5000);
                } finally {
                    try { if (server != null) server.close(); } catch (Exception ignored) {}
                }
            }
        }

        private void answer(Context c, BluetoothSocket s) throws Exception {
            DataInputStream in = new DataInputStream(s.getInputStream());
            String ask = in.readUTF();
            if (ask.startsWith("lens=")) {
                String id = ask.substring(5);
                if (!id.equals(Store.override(c))) {
                    Store.setOverride(c, id);
                    ClockWidget.updateAll(c);
                }
            }
            byte[] payload = App.watchData(c).toJson().getBytes(Charset.forName("UTF-8"));
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            out.writeInt(payload.length);
            out.write(payload);
            out.flush();
            String name = "your watch";
            try { if (s.getRemoteDevice().getName() != null) name = s.getRemoteDevice().getName(); } catch (SecurityException ignored) {}
            Store.put(c, "watchName", name);
            status(c, "Synced with " + name + " at " + Fmt.time(System.currentTimeMillis()) + ".");
            // Give the watch a moment to read before closing
            try { in.read(); } catch (Exception ignored) {}
        }

        private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }
    }
}
