package com.atakmap.android.illum.plugin.core;

import java.util.Locale;

/** Text formatting shared by the pane and tests. Pure Java. */
public final class Formats {

    public static final String NO_TIME = "----";

    private Formats() {
    }

    /** 24-hour "HHmm" in the zone, or "----". */
    public static String hhmm(long utc, DisplayZone zone) {
        if (utc == NightReport.NONE)
            return NO_TIME;
        long local = utc + zone.offsetMillis(utc);
        long minOfDay = CivilDate.floorMod(CivilDate.floorDiv(local + 30000L, 60000L), 1440L);
        return String.format(Locale.US, "%02d%02d", minOfDay / 60, minOfDay % 60);
    }

    /** "1814L", "2214Z", or "----". */
    public static String time(long utc, DisplayZone zone) {
        if (utc == NightReport.NONE)
            return NO_TIME;
        return hhmm(utc, zone) + zone.suffix(utc);
    }

    /** "1814L / 2214Z" (or just "2214Z" when the zone is UTC). */
    public static String timeWithZulu(long utc, DisplayZone zone, boolean showZulu) {
        if (utc == NightReport.NONE)
            return NO_TIME;
        if (!showZulu || zone.isUtc())
            return time(utc, zone);
        return time(utc, zone) + " / " + time(utc, DisplayZone.ZULU);
    }

    /** Illuminance with sensible units: "2.2 mlux", "0.21 lux", "54 lux". */
    public static String lux(double lux) {
        if (Double.isNaN(lux))
            return "--";
        if (lux < 0.0001)
            return "<0.1 mlux";
        if (lux < 0.1)
            return String.format(Locale.US, "%.1f mlux", lux * 1000.0);
        if (lux < 10)
            return String.format(Locale.US, "%.2f lux", lux);
        return String.format(Locale.US, "%.0f lux", lux);
    }

    public static String percent(double fraction0to1) {
        if (Double.isNaN(fraction0to1))
            return "--";
        return String.format(Locale.US, "%.0f%%", fraction0to1 * 100.0);
    }

    public static String degrees(double deg) {
        if (Double.isNaN(deg))
            return "--";
        long d = Math.round(Ephemeris.norm360(deg)) % 360;
        return String.format(Locale.US, "%03d°", d);
    }

    public static String altitude(double deg) {
        if (Double.isNaN(deg))
            return "--";
        return String.format(Locale.US, "%.0f°", deg);
    }

    /** "Moonrise 1932L @ 071°" style label for a moon event. */
    public static String moonEvent(NightReport.MoonEvent e, DisplayZone zone) {
        return time(e.time, zone) + " @ " + degrees(e.azimuthDeg);
    }

    /**
     * A note when the clocks change (daylight saving starts or ends) during
     * the night, e.g. "Clocks go back 1 h at 0200Q (0600Z), to 0100R. Times
     * after that are in R (UTC−5)." Null when they do not.
     */
    public static String clockChange(NightReport n, DisplayZone z) {
        long t = z.offsetChange(n.spanStart, n.spanEnd);
        if (t == NightReport.NONE)
            return null;
        long before = z.offsetMillis(t - 1), after = z.offsetMillis(t);
        long mins = Math.abs(after - before) / 60000L;
        String amount = mins % 60 == 0 ? (mins / 60) + " h" : mins + " min";
        DisplayZone was = DisplayZone.fixedMinutes((int) (before / 60000L));
        DisplayZone now = DisplayZone.fixedMinutes((int) (after / 60000L));
        return "\u2022 Clocks go " + (after < before ? "back " : "forward ") + amount
                + " at " + time(t, was) + " (" + time(t, DisplayZone.ZULU) + "), to "
                + time(t, now) + ". Times after that are in " + now.suffix(t) + " ("
                + DisplayZone.formatOffset(after) + ").\n";
    }
}
