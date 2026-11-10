package com.watchman.induetime;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.Display;
import android.view.SurfaceHolder;

import com.watchman.induetime.shared.FaceRenderer;
import com.watchman.induetime.shared.FaceStyle;
import com.watchman.induetime.shared.WatchData;

/** The In Due Time watch face. Every part of its look is set in the phone app. */
public class FaceService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        Dial.fonts(this);
        Alarms.schedule(this);
        return new Face();
    }

    final class Face extends Engine implements DisplayManager.DisplayListener {
        final Handler h = new Handler(Looper.getMainLooper());
        final FaceRenderer renderer = new FaceRenderer(Dial.sans, Dial.mono);
        boolean visible, ambient;
        WatchData data;
        DisplayManager dm;

        final Runnable tick = new Runnable() {
            public void run() { draw(); schedule(); }
        };

        final Runnable changed = new Runnable() {
            public void run() {
                h.post(new Runnable() { public void run() { data = Link.data(FaceService.this); draw(); schedule(); } });
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setTouchEventsEnabled(true);
            data = Link.data(FaceService.this);
            Link.listen(changed);
            dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            if (dm != null) dm.registerDisplayListener(this, h);
            readDisplay();
        }

        @Override
        public void onDestroy() {
            h.removeCallbacks(tick);
            Link.unlisten(changed);
            if (dm != null) dm.unregisterDisplayListener(this);
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            visible = v;
            if (v) {
                data = Link.data(FaceService.this);
                draw();
                schedule();
                Link.pullAsync(FaceService.this, 60000);
            } else h.removeCallbacks(tick);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            draw();
        }

        @Override
        public Bundle onCommand(String action, int x, int y, int z, Bundle extras, boolean resultRequested) {
            if (WallpaperManager.COMMAND_TAP.equals(action)) {
                Intent i = new Intent(FaceService.this, WatchActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(i); } catch (Exception ignored) {}
            } else if (extras != null && extras.containsKey("ambient_mode")) {
                setAmbient(extras.getBoolean("ambient_mode"));
            } else if ("com.google.android.wearable.action.AMBIENT_UPDATE".equals(action)) {
                draw();
            }
            return null;
        }

        void setAmbient(boolean a) {
            if (a == ambient) return;
            ambient = a;
            data = Link.data(FaceService.this);
            draw();
            schedule();
            if (!a) Link.pullAsync(FaceService.this, 60000);
        }

        void readDisplay() {
            if (dm == null) return;
            Display d = dm.getDisplay(Display.DEFAULT_DISPLAY);
            if (d == null) return;
            int s = d.getState();
            setAmbient(s == Display.STATE_DOZE || s == Display.STATE_DOZE_SUSPEND);
        }

        public void onDisplayAdded(int id) {}
        public void onDisplayRemoved(int id) {}
        public void onDisplayChanged(int id) { if (id == Display.DEFAULT_DISPLAY) readDisplay(); }

        void schedule() {
            h.removeCallbacks(tick);
            if (!visible) return;
            long now = System.currentTimeMillis();
            long step = 60000; // no seconds on any face
            h.postDelayed(tick, step - (now % step) + 5);
        }

        void draw() {
            SurfaceHolder holder = getSurfaceHolder();
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c == null) return;
                Rect r = holder.getSurfaceFrame();
                if (data == null) data = Link.data(FaceService.this);
                renderer.draw(c, r.width(), r.height(), data, System.currentTimeMillis(), ambient);
            } catch (Exception ignored) {
            } finally {
                if (c != null) try { holder.unlockCanvasAndPost(c); } catch (Exception ignored) {}
            }
        }
    }
}
