package com.atakmap.android.illum.plugin.core;

import java.util.Map;
import java.util.TimeZone;

/**
 * Preference keys and parsing. Works on {@code SharedPreferences.getAll()} so
 * it can be unit-tested without Android. Every value is read defensively:
 * a bad entry falls back to its default rather than breaking the plugin.
 */
public final class IllumPrefs {

    public static final String STANDARD = "illum_standard";
    public static final String CUSTOM_METHOD = "illum_custom_method";
    public static final String CUSTOM_LUX = "illum_custom_mlux";
    public static final String CUSTOM_ILLUM = "illum_custom_min_illum";
    public static final String CUSTOM_ALT = "illum_custom_min_alt";
    public static final String MISSION = "illum_mission";
    public static final String WINDOW = "illum_window";
    public static final String WINDOW_START = "illum_window_start";
    public static final String WINDOW_END = "illum_window_end";
    public static final String TOLERANCE = "illum_tolerance";
    public static final String SKY = "illum_sky";
    public static final String NIGHT_SKY = "illum_night_sky";
    public static final String TIME_ZONE = "illum_time_zone";
    public static final String MANUAL_OFFSET = "illum_manual_offset";
    public static final String SHOW_ZULU = "illum_show_zulu";
    public static final String PLAN_NIGHTS = "illum_plan_nights";
    public static final String ALL_TWILIGHT = "illum_all_twilight";

    // remembered state (not shown in settings)
    public static final String LAST_LAT = "illum_last_lat";
    public static final String LAST_LON = "illum_last_lon";
    public static final String LAST_SOURCE = "illum_last_source";
    public static final String LAST_NAME = "illum_last_name";

    public static final String PREFIX = "illum_";

    private IllumPrefs() {
    }

    public static PlanSettings settings(Map<String, ?> p) {
        PlanSettings.Builder b = new PlanSettings.Builder();

        String std = str(p, STANDARD, "usaf");
        if ("custom".equals(std)) {
            LightStandard.Method m = "moon".equals(str(p, CUSTOM_METHOD, "lux"))
                    ? LightStandard.Method.MOON_RULE
                    : LightStandard.Method.LUX;
            double mlux = clamp(num(p, CUSTOM_LUX, 2.2), 0.01, 1000);
            double ill = clamp(num(p, CUSTOM_ILLUM, 20), 0, 100);
            double alt = clamp(num(p, CUSTOM_ALT, 30), 0, 90);
            b.standard(LightStandard.custom(m, mlux / 1000.0, ill, alt));
        } else {
            b.standard(LightStandard.byId(std));
        }

        b.mission("conceal".equals(str(p, MISSION, "nvg")) ? PlanSettings.Mission.CONCEAL
                : PlanSettings.Mission.NVG);

        String w = str(p, WINDOW, "nautical");
        b.window("sun".equals(w) ? PlanSettings.WindowMode.SUNSET_SUNRISE
                : "custom".equals(w) ? PlanSettings.WindowMode.CUSTOM
                        : PlanSettings.WindowMode.NAUTICAL_DARK);
        b.customWindow(hhmmToMinutes(str(p, WINDOW_START, "2200"), 22 * 60),
                hhmmToMinutes(str(p, WINDOW_END, "0400"), 4 * 60));

        b.tolerance(clamp(num(p, TOLERANCE, 10), 0, 49) / 100.0);

        String sky = str(p, SKY, "clear");
        b.sky("thin".equals(sky) ? PlanSettings.Sky.THIN_CLOUDS
                : "average".equals(sky) ? PlanSettings.Sky.AVERAGE_CLOUDS
                        : "stratus".equals(sky) ? PlanSettings.Sky.DARK_STRATUS
                                : PlanSettings.Sky.CLEAR);
        b.includeNightSky(bool(p, NIGHT_SKY, false));
        return b.build();
    }

    /** Display zone for a location. */
    public static DisplayZone zone(Map<String, ?> p, double lonDeg, TimeZone deviceZone) {
        String z = str(p, TIME_ZONE, "device");
        switch (z) {
            case "zulu":
                return DisplayZone.ZULU;
            case "ao":
                return DisplayZone.nautical(lonDeg);
            case "manual": {
                double h = clamp(num(p, MANUAL_OFFSET, 0), -14, 14);
                return DisplayZone.fixedMinutes((int) Math.round(h * 60));
            }
            default:
                return DisplayZone.of(deviceZone);
        }
    }

    public static boolean showZulu(Map<String, ?> p) {
        return bool(p, SHOW_ZULU, true);
    }

    public static boolean allTwilight(Map<String, ?> p) {
        return bool(p, ALL_TWILIGHT, false);
    }

    public static int planNights(Map<String, ?> p) {
        return (int) clamp(num(p, PLAN_NIGHTS, 30), 1, 90);
    }

    /** "2200", "22:00", "2200L" -> minutes after midnight; fallback on garbage. */
    public static int hhmmToMinutes(String s, int fallback) {
        if (s == null)
            return fallback;
        String digits = s.replaceAll("[^0-9]", "");
        if (digits.length() < 3 || digits.length() > 4)
            return fallback;
        try {
            int v = Integer.parseInt(digits);
            int h = v / 100, m = v % 100;
            if (h > 23 || m > 59)
                return fallback;
            return h * 60 + m;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------

    static String str(Map<String, ?> p, String key, String def) {
        Object o = p == null ? null : p.get(key);
        if (o == null)
            return def;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? def : s;
    }

    static double num(Map<String, ?> p, String key, double def) {
        Object o = p == null ? null : p.get(key);
        if (o instanceof Number)
            return ((Number) o).doubleValue();
        if (o == null)
            return def;
        try {
            double d = Double.parseDouble(String.valueOf(o).trim());
            return Double.isNaN(d) || Double.isInfinite(d) ? def : d;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static boolean bool(Map<String, ?> p, String key, boolean def) {
        Object o = p == null ? null : p.get(key);
        if (o instanceof Boolean)
            return (Boolean) o;
        if (o == null)
            return def;
        return "true".equalsIgnoreCase(String.valueOf(o));
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
