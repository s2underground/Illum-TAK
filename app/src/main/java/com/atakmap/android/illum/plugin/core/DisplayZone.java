package com.atakmap.android.illum.plugin.core;

import java.util.Locale;
import java.util.TimeZone;

/**
 * The time zone used to label times and to decide which calendar night a
 * report belongs to.
 */
public abstract class DisplayZone {

    /** Offset from UTC in milliseconds at the given instant. */
    public abstract long offsetMillis(long utcMillis);

    /**
     * Short suffix shown after a time: the military zone letter for the
     * offset, e.g. "Z", "R" (UTC-5), "D" (UTC+4), "C*" (UTC+3:30).
     */
    public abstract String suffix(long utcMillis);

    /** Longer description, e.g. "D = UTC+4". */
    public abstract String describe(long utcMillis);

    public boolean isUtc() {
        return false;
    }

    public static final DisplayZone ZULU = new Fixed(0, "Z", "Zulu (UTC)") {
        @Override
        public boolean isUtc() {
            return true;
        }
    };

    /** The device's own zone, daylight saving aware. */
    public static DisplayZone device() {
        return new Device(TimeZone.getDefault());
    }

    public static DisplayZone of(TimeZone tz) {
        return new Device(tz);
    }

    /**
     * Civil local time at a point, from the time zone map: the zone's own
     * offset and daylight saving rules from the device's time zone database.
     */
    public static DisplayZone localAtPoint(TimeZone tz) {
        return new Device(tz, "local time at this point");
    }

    /**
     * The first instant in (from, to] where the zone's offset changes (a
     * daylight saving change), or {@link NightReport#NONE}.
     */
    public long offsetChange(long from, long to) {
        if (to <= from || offsetMillis(from) == offsetMillis(to))
            return NightReport.NONE;
        long lo = from, hi = to; // offset(lo) != offset(hi)
        long before = offsetMillis(from);
        while (hi - lo > 1) {
            long mid = lo + (hi - lo) / 2;
            if (offsetMillis(mid) == before)
                lo = mid;
            else
                hi = mid;
        }
        return hi; // first instant with the new offset
    }

    /** A fixed offset, e.g. a manual setting. */
    public static DisplayZone fixedMinutes(int offsetMinutes) {
        long off = offsetMinutes * 60000L;
        String letter = letterFor(off);
        return new Fixed(off, letter, letter + " = " + formatOffset(off) + ", manual setting");
    }

    /**
     * Nautical (military) time zone for a longitude: 15 degree bands centered
     * on multiples of 15 degrees, lettered A-M east (no J) and N-Y west.
     * Ignores political boundaries and daylight saving.
     */
    public static DisplayZone nautical(double lonDeg) {
        int hours = (int) Math.round(lonDeg / 15.0);
        if (hours > 12)
            hours = 12;
        if (hours < -12)
            hours = -12;
        String letter = militaryLetter(hours);
        return new Fixed(hours * 3600000L, letter, letter + " = "
                + formatOffset(hours * 3600000L) + ", military time zone at this longitude");
    }

    /**
     * Military zone letter for any offset. Offsets that are not whole hours
     * take the letter of the whole-hour part plus an asterisk, e.g.
     * UTC+3:30 = "C*", UTC+5:30 = "E*", UTC-3:30 = "P*".
     */
    public static String letterFor(long offsetMillis) {
        long mins = offsetMillis / 60000L;
        int hours = (int) (mins / 60); // toward zero
        hours = Math.max(-12, Math.min(14, hours));
        String letter = hours > 12 ? "M" : militaryLetter(hours);
        return mins % 60 == 0 && hours <= 12 ? letter : letter + "*";
    }

    static String militaryLetter(int hours) {
        if (hours == 0)
            return "Z";
        if (hours > 0) {
            // A..I = +1..+9, K..M = +10..+12 (J is not used)
            return hours <= 9 ? String.valueOf((char) ('A' + hours - 1))
                    : String.valueOf((char) ('K' + hours - 10));
        }
        // N..Y = -1..-12
        return String.valueOf((char) ('N' + (-hours) - 1));
    }

    /** "EDT", "CET"... or null when the system only has a "GMT+03:30" style name. */
    static String shortName(TimeZone tz, boolean daylight) {
        String n;
        try {
            n = tz.getDisplayName(daylight, TimeZone.SHORT, Locale.US);
        } catch (RuntimeException e) {
            return null;
        }
        if (n == null || n.length() < 2 || n.length() > 5 || n.startsWith("GMT")
                || n.startsWith("UTC"))
            return null;
        for (int i = 0; i < n.length(); i++)
            if (!Character.isLetter(n.charAt(i)))
                return null;
        return n;
    }

    public static String formatOffset(long offsetMillis) {
        long mins = Math.abs(offsetMillis) / 60000L;
        String sign = offsetMillis < 0 ? "−" : "+";
        if (mins % 60 == 0)
            return "UTC" + sign + (mins / 60);
        return String.format(Locale.US, "UTC%s%d:%02d", sign, mins / 60, mins % 60);
    }

    // ------------------------------------------------------------------

    static class Fixed extends DisplayZone {
        final long offset;
        final String suffix;
        final String description;

        Fixed(long offset, String suffix, String description) {
            this.offset = offset;
            this.suffix = suffix;
            this.description = description;
        }

        @Override
        public long offsetMillis(long utcMillis) {
            return offset;
        }

        @Override
        public String suffix(long utcMillis) {
            return suffix;
        }

        @Override
        public String describe(long utcMillis) {
            return description;
        }
    }

    static final class Device extends DisplayZone {
        final TimeZone tz;
        final String label;

        Device(TimeZone tz) {
            this(tz, "local time here (device clock)");
        }

        Device(TimeZone tz, String label) {
            this.tz = tz;
            this.label = label;
        }

        @Override
        public long offsetMillis(long utcMillis) {
            return tz.getOffset(utcMillis);
        }

        @Override
        public String suffix(long utcMillis) {
            return letterFor(offsetMillis(utcMillis));
        }

        @Override
        public String describe(long utcMillis) {
            long off = offsetMillis(utcMillis);
            boolean dst = tz.inDaylightTime(new java.util.Date(utcMillis));
            String name = shortName(tz, dst);
            String extra = name != null ? (dst ? name + ", daylight saving" : name)
                    : (dst ? "daylight saving" : null);
            return letterFor(off) + " = " + formatOffset(off)
                    + (extra != null ? " (" + extra + ")" : "") + ", " + label;
        }
    }
}
