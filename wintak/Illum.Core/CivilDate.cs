// Ported from the corresponding Android core class; formulas and field names retained for comparison.
using System;
using System.Collections.Generic;

namespace Illum.Core
{
/**
 * Calendar helpers on "epoch days" (days since 1970-01-01), avoiding
 * java.time so the plugin runs on every Android version ATAK supports.
 * Algorithms: Howard Hinnant, "chrono-Compatible Low-Level Date Algorithms".
 */
public sealed class CivilDate {

    private static readonly string[] MONTHS = {
            "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
            "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
    };
    private static readonly string[] DOW = {
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
        return zone.localToUtc(epochDay, minutes);
    }

    /** e.g. "Thu 29 OCT 26". */
    public static string label(long epochDay) {
        int[] c = toCivil(epochDay);
        int dow = (int) floorMod(epochDay, 7L);
        return string.Format(System.Globalization.CultureInfo.InvariantCulture, "{0} {1:00} {2} {3:00}", DOW[dow], c[2], MONTHS[c[1] - 1], c[0] % 100);
    }

    /** e.g. "29 OCT". */
    public static string shortLabel(long epochDay) {
        int[] c = toCivil(epochDay);
        return string.Format(System.Globalization.CultureInfo.InvariantCulture, "{0:00} {1}", c[2], MONTHS[c[1] - 1]);
    }

    internal static long floorDiv(long a, long b) {
        long q = a / b;
        if ((a % b != 0) && ((a < 0) != (b < 0)))
            q--;
        return q;
    }

    internal static long floorMod(long a, long b) {
        return a - floorDiv(a, b) * b;
    }

    /** Three-letter weekday. */
    public static string weekday(long epochDay) {
        return DOW[(int) floorMod(epochDay, 7L)];
    }
}

}
