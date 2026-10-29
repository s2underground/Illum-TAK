package com.atakmap.android.illum.plugin.core;

import java.util.Locale;

/**
 * Calendar helpers on "epoch days" (days since 1970-01-01), avoiding
 * java.time so the plugin runs on every Android version ATAK supports.
 * Algorithms: Howard Hinnant, "chrono-Compatible Low-Level Date Algorithms".
 */
public final class CivilDate {

    private static final String[] MONTHS = {
            "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
            "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
    };
    private static final String[] DOW = {
            "Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed"
    };

    private CivilDate() {
    }

    public static long fromCivil(int y, int m, int d) {
        y -= m <= 2 ? 1 : 0;
        long era = (y >= 0 ? y : y - 399) / 400;
        long yoe = y - era * 400;
        long doy = (153L * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    /** @return {year, month (1-12), day} */
    public static int[] toCivil(long epochDay) {
        long z = epochDay + 719468;
        long era = (z >= 0 ? z : z - 146096) / 146097;
        long doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long y = yoe + era * 400;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        long d = doy - (153 * mp + 2) / 5 + 1;
        long m = mp + (mp < 10 ? 3 : -9);
        return new int[] {
                (int) (y + (m <= 2 ? 1 : 0)), (int) m, (int) d
        };
    }

    /** Local calendar day containing the instant, in the zone. */
    public static long epochDayOf(long utcMillis, DisplayZone zone) {
        return floorDiv(utcMillis + zone.offsetMillis(utcMillis), 86400000L);
    }

    /** UTC instant of a local wall-clock time (minutes after midnight) on a local day. */
    public static long localToUtc(long epochDay, int minutes, DisplayZone zone) {
        long local = epochDay * 86400000L + minutes * 60000L;
        long guess = local - zone.offsetMillis(local);
        // second pass settles daylight saving transitions
        guess = local - zone.offsetMillis(guess);
        return guess;
    }

    /** e.g. "Thu 29 OCT 26". */
    public static String label(long epochDay) {
        int[] c = toCivil(epochDay);
        int dow = (int) floorMod(epochDay, 7L);
        return String.format(Locale.US, "%s %02d %s %02d", DOW[dow], c[2], MONTHS[c[1] - 1],
                c[0] % 100);
    }

    /** e.g. "29 OCT". */
    public static String shortLabel(long epochDay) {
        int[] c = toCivil(epochDay);
        return String.format(Locale.US, "%02d %s", c[2], MONTHS[c[1] - 1]);
    }

    static long floorDiv(long a, long b) {
        long q = a / b;
        if ((a % b != 0) && ((a < 0) != (b < 0)))
            q--;
        return q;
    }

    static long floorMod(long a, long b) {
        return a - floorDiv(a, b) * b;
    }

    /** Three-letter weekday. */
    public static String weekday(long epochDay) {
        return DOW[(int) floorMod(epochDay, 7L)];
    }
}
