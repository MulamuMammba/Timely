package com.watchman.induetime;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads and writes Google Calendar through the phone's own calendar store. */
public final class Cal {

    public static final class Calendar {
        public long id;
        public String name, account;
        public int color;
        public boolean writable, google;
    }

    public static final class Block {
        public long eventId, begin, end, calId;
        public String title = "", desc = "", cat = "", rrule;
        public boolean google, allDay;
        public int calColor;

        public String key() { return eventId + ":" + begin; }
        public boolean repeats() { return rrule != null && rrule.length() > 0; }
        public boolean isNow(long now) { return begin <= now && end > now; }
    }

    static final Pattern TAG = Pattern.compile("\\[idt:([a-z0-9_-]+)\\]");

    private Cal() {}

    static boolean allowed(Context c) {
        return c.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                && c.checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    static List<Calendar> calendars(Context c) {
        List<Calendar> out = new ArrayList<Calendar>();
        if (!allowed(c)) return out;
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, new String[]{
                    CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.CALENDAR_COLOR,
                    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.ACCOUNT_TYPE},
                    null, null, CalendarContract.Calendars.ACCOUNT_NAME + " ASC");
            while (cur != null && cur.moveToNext()) {
                Calendar k = new Calendar();
                k.id = cur.getLong(0);
                k.name = cur.getString(1);
                k.account = cur.getString(2);
                k.color = cur.getInt(3) | 0xFF000000;
                k.writable = cur.getInt(4) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR;
                k.google = "com.google".equals(cur.getString(5));
                out.add(k);
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return out;
    }

    /** The calendar new blocks go to. Picks the first writable Google calendar if none is set. */
    static long blocksCal(Context c) {
        long id = Store.blocksCal(c);
        List<Calendar> all = calendars(c);
        for (Calendar k : all) if (k.id == id && k.writable) return id;
        for (Calendar k : all) if (k.google && k.writable && k.name != null && k.name.equals(k.account)) { Store.setBlocksCal(c, k.id); return k.id; }
        for (Calendar k : all) if (k.writable) { Store.setBlocksCal(c, k.id); return k.id; }
        return -1;
    }

    /** All timed and all-day events between from and to, for the calendars you chose to show. */
    static List<Block> between(Context c, long from, long to) {
        List<Block> out = new ArrayList<Block>();
        if (!allowed(c)) return out;
        long blocks = blocksCal(c);
        Set<String> vis = Store.visibleCals(c);
        boolean filter = Store.visibleSet(c);
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, from);
        ContentUris.appendId(b, to);
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(b.build(), new String[]{
                    CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                    CalendarContract.Instances.TITLE, CalendarContract.Instances.DESCRIPTION, CalendarContract.Instances.CALENDAR_ID,
                    CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.RRULE, CalendarContract.Instances.DISPLAY_COLOR,
                    CalendarContract.Instances.STATUS},
                    null, null, CalendarContract.Instances.BEGIN + " ASC");
            while (cur != null && cur.moveToNext()) {
                if (!cur.isNull(9) && cur.getInt(9) == CalendarContract.Events.STATUS_CANCELED) continue;
                Block k = new Block();
                k.eventId = cur.getLong(0);
                k.begin = cur.getLong(1);
                k.end = cur.getLong(2);
                k.title = cur.getString(3) == null ? "(No title)" : cur.getString(3);
                k.desc = cur.getString(4) == null ? "" : cur.getString(4);
                k.calId = cur.getLong(5);
                k.allDay = cur.getInt(6) == 1;
                k.rrule = cur.getString(7);
                k.calColor = cur.getInt(8) | 0xFF000000;
                if (k.calId != blocks && filter && !vis.contains(String.valueOf(k.calId))) continue;
                Matcher m = TAG.matcher(k.desc);
                if (m.find()) { k.cat = m.group(1); k.google = false; }
                else { k.google = true; k.cat = ""; }
                if (k.allDay) {
                    // All-day instances are stored in UTC midnights. Shift to local days.
                    int off = TimeZone.getDefault().getOffset(k.begin);
                    k.begin -= off; k.end -= off;
                }
                out.add(k);
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return out;
    }

    static List<Block> day(Context c, long dayStart) { return between(c, dayStart, Fmt.addDays(dayStart, 1)); }

    static String userNotes(String desc) {
        return TAG.matcher(desc == null ? "" : desc).replaceAll("").trim();
    }

    static String withTag(String notes, String cat) {
        String n = notes == null ? "" : notes.trim();
        if (cat == null || cat.length() == 0) return n;
        return (n.length() > 0 ? n + "\n\n" : "") + "[idt:" + cat + "]";
    }

    /** Creates a block. Returns the new event id or -1. */
    static long insert(Context c, String title, String notes, String cat, long begin, long end, String rrule) {
        long cal = blocksCal(c);
        if (cal < 0) return -1;
        ContentValues v = new ContentValues();
        v.put(CalendarContract.Events.CALENDAR_ID, cal);
        v.put(CalendarContract.Events.TITLE, title);
        v.put(CalendarContract.Events.DESCRIPTION, withTag(notes, cat));
        v.put(CalendarContract.Events.DTSTART, begin);
        v.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
        if (rrule != null && rrule.length() > 0) {
            v.put(CalendarContract.Events.RRULE, rrule);
            v.put(CalendarContract.Events.DURATION, "P" + ((end - begin) / 1000) + "S");
        } else {
            v.put(CalendarContract.Events.DTEND, end);
        }
        try {
            Uri u = c.getContentResolver().insert(CalendarContract.Events.CONTENT_URI, v);
            return u == null ? -1 : ContentUris.parseId(u);
        } catch (Exception e) {
            return -1;
        }
    }

    /** Changes a single event, or a whole series when it repeats. */
    static boolean update(Context c, Block old, String title, String notes, String cat, long begin, long end, String rrule) {
        ContentResolver cr = c.getContentResolver();
        Uri u = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, old.eventId);
        ContentValues v = new ContentValues();
        v.put(CalendarContract.Events.TITLE, title);
        v.put(CalendarContract.Events.DESCRIPTION, old.google && (cat == null || cat.length() == 0) ? notes : withTag(notes, cat));
        long seriesStart = begin;
        if (old.repeats()) {
            long dt = seriesStart(c, old.eventId);
            if (dt > 0) seriesStart = dt + (begin - old.begin);
        }
        v.put(CalendarContract.Events.DTSTART, seriesStart);
        if (rrule != null && rrule.length() > 0) {
            v.put(CalendarContract.Events.RRULE, rrule);
            v.put(CalendarContract.Events.DURATION, "P" + ((end - begin) / 1000) + "S");
            v.putNull(CalendarContract.Events.DTEND);
        } else {
            v.putNull(CalendarContract.Events.RRULE);
            v.putNull(CalendarContract.Events.DURATION);
            v.put(CalendarContract.Events.DTEND, old.repeats() ? seriesStart + (end - begin) : end);
        }
        try {
            return cr.update(u, v, null, null) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Moves or resizes one occurrence. Repeating events get an exception for that day only. */
    static boolean moveOne(Context c, Block b, long begin, long end) {
        ContentValues v = new ContentValues();
        if (!b.repeats()) {
            v.put(CalendarContract.Events.DTSTART, begin);
            v.put(CalendarContract.Events.DTEND, end);
            try {
                return c.getContentResolver().update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, b.eventId), v, null, null) > 0;
            } catch (Exception e) { return false; }
        }
        v.put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, b.begin);
        v.put(CalendarContract.Events.DTSTART, begin);
        v.put(CalendarContract.Events.DTEND, end);
        v.put(CalendarContract.Events.TITLE, b.title);
        v.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
        return exception(c, b, v);
    }

    static boolean deleteOne(Context c, Block b) {
        if (!b.repeats()) return deleteAll(c, b);
        ContentValues v = new ContentValues();
        v.put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, b.begin);
        v.put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED);
        return exception(c, b, v);
    }

    static boolean deleteAll(Context c, Block b) {
        try {
            return c.getContentResolver().delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, b.eventId), null, null) > 0;
        } catch (Exception e) { return false; }
    }

    private static boolean exception(Context c, Block b, ContentValues v) {
        try {
            Uri u = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, b.eventId);
            return c.getContentResolver().insert(u, v) != null;
        } catch (Exception e) { return false; }
    }

    private static long seriesStart(Context c, long eventId) {
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                    new String[]{CalendarContract.Events.DTSTART}, null, null, null);
            if (cur != null && cur.moveToFirst()) return cur.getLong(0);
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return -1;
    }

    /** Repeat rule from a simple choice. */
    static String rule(String choice, long begin) {
        if ("Daily".equals(choice)) return "FREQ=DAILY";
        if ("Weekdays".equals(choice)) return "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR";
        if ("Weekly".equals(choice)) {
            String[] d = {"SU", "MO", "TU", "WE", "TH", "FR", "SA"};
            return "FREQ=WEEKLY;BYDAY=" + d[Fmt.cal(begin).get(java.util.Calendar.DAY_OF_WEEK) - 1];
        }
        return null;
    }

    static String choice(String rrule) {
        if (rrule == null || rrule.length() == 0) return "Never";
        if (rrule.contains("FREQ=DAILY")) return "Daily";
        if (rrule.contains("BYDAY=MO,TU,WE,TH,FR")) return "Weekdays";
        if (rrule.contains("FREQ=WEEKLY")) return "Weekly";
        return "Custom";
    }
}
