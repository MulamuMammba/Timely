package com.watchman.induetime;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

final class Wifi {
    private Wifi() {}

    /** Current Wi-Fi name, or null. Android needs location permission to share it. */
    static String ssid(Context c) {
        try {
            if (Build.VERSION.SDK_INT >= 23 && c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null;
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return null;
            WifiInfo info = wm.getConnectionInfo();
            if (info == null) return null;
            String s = info.getSSID();
            if (s == null || s.contains("unknown ssid")) return null;
            if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) s = s.substring(1, s.length() - 1);
            return s;
        } catch (Exception e) {
            return null;
        }
    }
}
