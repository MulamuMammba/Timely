package com.watchman.induetime;

import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.os.Parcel;
import android.os.RemoteException;

import com.watchman.induetime.shared.FaceRenderer;
import com.watchman.induetime.shared.WatchData;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Feeds the In Due Time face with the dial as a photo image.
 * Written against the complication binder protocol directly, without androidx.
 */
public class Dial extends Service {

    static final String ACTION = "android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST";
    static final String DESC_PROVIDER = "android.support.wearable.complications.IComplicationProvider";
    static final String DESC_MANAGER = "android.support.wearable.complications.IComplicationManager";
    static final int TYPE_LARGE_IMAGE = 8, TYPE_NO_DATA = 10;
    static final int SIZE = 450;

    static Typeface serif, mono;
    private static final Executor EXEC = Executors.newSingleThreadExecutor();

    /** The always on display version. */
    public static class Aod extends Dial {
        @Override boolean ambient() { return true; }
    }

    boolean ambient() { return false; }

    @Override
    public IBinder onBind(Intent intent) {
        if (intent != null && ACTION.equals(intent.getAction())) return binder;
        return null;
    }

    private final Binder binder = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) { reply.writeString(DESC_PROVIDER); return true; }
            if (code < FIRST_CALL_TRANSACTION || code > FIRST_CALL_TRANSACTION + 9) return super.onTransact(code, data, reply, flags);
            data.enforceInterface(DESC_PROVIDER);
            int m = code - FIRST_CALL_TRANSACTION;
            switch (m) {
                case 0: { // onUpdate(id, type, manager)
                    int id = data.readInt(), type = data.readInt();
                    IBinder mgr = data.readStrongBinder();
                    if (reply != null) reply.writeNoException();
                    answer(id, type, mgr);
                    return true;
                }
                case 8: { // onUpdate2(id, type, manager, bundle)
                    int id = data.readInt(), type = data.readInt();
                    IBinder mgr = data.readStrongBinder();
                    if (reply != null) reply.writeNoException();
                    answer(id, type, mgr);
                    return true;
                }
                case 1: // onComplicationDeactivated(id)
                    data.readInt();
                    if (reply != null) reply.writeNoException();
                    return true;
                case 2: { // onComplicationActivated(id, type, manager)
                    data.readInt(); data.readInt(); data.readStrongBinder();
                    if (reply != null) reply.writeNoException();
                    return true;
                }
                case 3: // getApiVersion
                    if (reply != null) { reply.writeNoException(); reply.writeInt(4); }
                    return true;
                case 4: { // getComplicationPreviewData(type)
                    int type = data.readInt();
                    if (reply != null) { reply.writeNoException(); writeTyped(reply, build(Dial.this, type, ambient())); }
                    return true;
                }
                case 5: case 6: // start or stop synchronous requests
                    data.readInt();
                    if (reply != null) reply.writeNoException();
                    return true;
                case 7: { // onSynchronousComplicationRequest(id, type)
                    data.readInt();
                    int type = data.readInt();
                    if (reply != null) { reply.writeNoException(); writeTyped(reply, build(Dial.this, type, ambient())); }
                    return true;
                }
                case 9: { // onSynchronousComplicationRequest2(id, type, bundle)
                    data.readInt();
                    int type = data.readInt();
                    if (reply != null) { reply.writeNoException(); writeTyped(reply, build(Dial.this, type, ambient())); }
                    return true;
                }
            }
            return super.onTransact(code, data, reply, flags);
        }
    };

    private void answer(final int id, final int type, final IBinder mgr) {
        final Context c = getApplicationContext();
        final boolean amb = ambient();
        EXEC.execute(new Runnable() {
            public void run() {
                Parcel d = Parcel.obtain(), r = Parcel.obtain();
                try {
                    Wire w = build(c, type, amb);
                    d.writeInterfaceToken(DESC_MANAGER);
                    d.writeInt(id);
                    writeTyped(d, w);
                    mgr.transact(IBinder.FIRST_CALL_TRANSACTION, d, r, 0);
                    r.readException();
                } catch (Exception ignored) {
                } finally {
                    d.recycle();
                    r.recycle();
                }
            }
        });
        // Refresh from the phone now and then; a change asks for another update.
        Link.pullAsync(c, 4 * 60000L);
    }

    /** A complication data value in the wire format: type, then a bundle of fields. */
    static final class Wire {
        int type;
        Bundle fields = new Bundle();
        Icon image;
        PendingIntent tap;
    }

    static void writeTyped(Parcel p, Wire w) {
        p.writeInt(1);
        p.writeInt(w.type);
        p.writeBundle(w.fields);
    }

    static Wire build(Context c, int type, boolean ambient) {
        Wire w = new Wire();
        if (type != TYPE_LARGE_IMAGE) { w.type = TYPE_NO_DATA; return w; }
        w.type = TYPE_LARGE_IMAGE;
        w.image = render(c, ambient);
        w.tap = tapIntent(c);
        w.fields.putParcelable("LARGE_IMAGE", w.image);
        w.fields.putParcelable("TAP_ACTION", w.tap);
        return w;
    }

    static PendingIntent tapIntent(Context c) {
        Intent i = new Intent(c, WatchActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, 3, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static synchronized Icon render(Context c, boolean ambient) {
        if (serif == null) {
            try { serif = Typeface.createFromAsset(c.getAssets(), "fraunces.ttf"); } catch (Exception e) { serif = Typeface.SERIF; }
            try { mono = Typeface.createFromAsset(c.getAssets(), "plexmono.ttf"); } catch (Exception e) { mono = Typeface.MONOSPACE; }
        }
        WatchData d = Link.data(c);
        Bitmap b = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        new FaceRenderer(serif, mono).draw(cv, SIZE, SIZE, d, d.style(), System.currentTimeMillis(), ambient, FaceRenderer.TIME_NONE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, out);
        b.recycle();
        byte[] png = out.toByteArray();
        return Icon.createWithData(png, 0, png.length);
    }

    // Asking the system to fetch fresh dial images

    static void requestUpdate(Context ctx) {
        final Context c = ctx.getApplicationContext();
        ComponentName[] comps = {new ComponentName(c, Dial.class), new ComponentName(c, Aod.class)};
        for (ComponentName cn : comps) {
            try {
                Intent i = new Intent("android.support.wearable.complications.ACTION_REQUEST_UPDATE_ALL");
                i.setPackage("com.google.android.wearable.app");
                i.putExtra("android.support.wearable.complications.EXTRA_PROVIDER_COMPONENT", cn);
                i.putExtra("android.support.wearable.complications.EXTRA_PENDING_INTENT",
                        PendingIntent.getActivity(c, 0, new Intent(""), PendingIntent.FLAG_IMMUTABLE));
                c.sendBroadcast(i);
            } catch (Exception ignored) {}
        }
        if (Build.VERSION.SDK_INT >= 36) {
            EXEC.execute(new Runnable() { public void run() { wearSdkUpdate(c); } });
        }
    }

    /** Wear OS 6 and later push updates through the Wear SDK. Reached by reflection. */
    @SuppressWarnings("unchecked")
    static void wearSdkUpdate(final Context c) {
        try {
            Class<?> sdk = Class.forName("com.google.wear.Sdk");
            final Class<?> cmClass = Class.forName("com.google.wear.services.complications.ComplicationsManager");
            final Object mgr = sdk.getMethod("getWearManager", Context.class, Class.class).invoke(null, c, cmClass);
            if (mgr == null) return;
            final ComponentName active = new ComponentName(c, Dial.class), aod = new ComponentName(c, Aod.class);
            Method get = cmClass.getMethod("getActiveComplicationConfigsAsync", Executor.class, OutcomeReceiver.class);
            get.invoke(mgr, EXEC, new OutcomeReceiver<Object, Throwable>() {
                public void onResult(Object result) {
                    if (!(result instanceof Collection)) return;
                    for (Object item : (Collection<Object>) result) {
                        try {
                            Object cfg = item.getClass().getMethod("getConfig").invoke(item);
                            ComponentName pc = (ComponentName) cfg.getClass().getMethod("getProviderComponent").invoke(cfg);
                            int id = (Integer) cfg.getClass().getMethod("getId").invoke(cfg);
                            boolean isActive = active.equals(pc), isAod = aod.equals(pc);
                            if (!isActive && !isAod) continue;
                            Object data = sdkData(c, isAod);
                            Method up = null;
                            for (Method m : cmClass.getMethods()) if (m.getName().equals("updateComplication") && m.getParameterTypes().length == 4) up = m;
                            if (up == null) return;
                            up.invoke(mgr, id, data, EXEC, new OutcomeReceiver<Object, Throwable>() {
                                public void onResult(Object r) {}
                                public void onError(Throwable t) {}
                            });
                        } catch (Throwable ignored) {}
                    }
                }
                public void onError(Throwable t) {}
            });
        } catch (Throwable ignored) {
            // No Wear SDK on this watch. The broadcast and the regular refresh still apply.
        }
    }

    private static Object sdkData(Context c, boolean ambient) throws Exception {
        Class<?> b = Class.forName("com.google.wear.services.complications.ComplicationData$Builder");
        Object builder = b.getConstructor(int.class).newInstance(TYPE_LARGE_IMAGE);
        b.getMethod("setLargeImage", Icon.class).invoke(builder, render(c, ambient));
        try { b.getMethod("setTapAction", PendingIntent.class).invoke(builder, tapIntent(c)); } catch (Throwable ignored) {}
        return b.getMethod("build").invoke(builder);
    }
}
