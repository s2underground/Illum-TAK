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

    /** Short suffix shown after a time, e.g. "Z", "L", "R". */
    public abstract String suffix(long utcMillis);

    /** Longer description, e.g. "Local (UTC-4)". */
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

    /** A fixed offset, e.g. a manual setting. */
    public static DisplayZone fixedMinutes(int offsetMinutes) {
        return new Fixed(offsetMinutes * 60000L, "L",
                "Manual (" + formatOffset(offsetMinutes * 60000L) + ")");
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
        return new Fixed(hours * 3600000L, letter,
                "AO zone " + letter + " (" + formatOffset(hours * 3600000L) + ")");
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

        Device(TimeZone tz) {
            this.tz = tz;
        }

        @Override
        public long offsetMillis(long utcMillis) {
            return tz.getOffset(utcMillis);
        }

        @Override
        public String suffix(long utcMillis) {
            return "L";
        }

        @Override
        public String describe(long utcMillis) {
            return "Local (" + formatOffset(offsetMillis(utcMillis)) + ")";
        }
    }
}
