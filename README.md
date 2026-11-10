# In Due Time

A planner that keeps your day unhurried. Time blocks live in your own Google Calendar.
Part of the Watchman house of apps, built to the Watchman's Document.

## What is here

- `phone/` The phone app. Now, Timeline, Agenda, Lenses, categories, block check-ins, clock widget.
- `watch/` The Galaxy Watch app. Today list, lens switch, wrist tap, live dial for the face.
- `face/` The Watch Face Format face (com.watchman.induetime.face). Resource only, no code.
- `shared/` Code used by both phone and watch: watch data, face styles, dial renderer, palette.
- `tools/` Build and signing scripts.

## Versions

- Phone 0.4 (versionCode 7) and watch 0.4 (versionCode 5): five face families (Sundial, Orbit, Horizon, Signal, Still), fifteen defaults plus Custom, edited in the Watch tab.
- `face/` (Watch Face Format) is parked. It did not appear on the watch. See status below.

## Building

No Gradle or Android SDK. Java, plain Android views, built with aapt2, javac and dx.

    tools/build.sh phone 7 0.4 InDueTime.apk
    tools/build.sh watch 5 0.4 InDueTime-Watch.apk 30
    tools/build.sh face 1 1.0 InDueTime-Face.apk 33

The script expects `aapt2`, `android.jar` (API 35), `dx.jar` and the signing key `watchman.p12` next to it.
The signing key is never committed. Every build must use the same key or updates fail.

## Status

The Watch Face Format face installs but does not show. A minimal test face does show, so installing works.
Next: install split test faces (options, dial, font) to find the rejected part, or read the watch log:

    logcat -d | grep -iE "watchface|wff|induetime" | tail -40
