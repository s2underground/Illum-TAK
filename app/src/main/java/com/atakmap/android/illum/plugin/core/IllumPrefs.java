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
    public static final String TERRAIN = "illum_terrain";

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

    /**
     * Within this distance of the device's own position, the device's clock
     * is taken to be the local time at the point.
     */
    public static final double NEAR_SELF_METERS = 100000;
    /**
     * ... provided the device's zone is within this many hours of the
     * military zone there (so a device kept on Zulu or home time while
     * deployed is not mistaken for local time).
     */
    public static final double NEAR_SELF_MAX_DIFF_HOURS = 2;

    /**
     * Zone used to show times for a point:
     * <ul>
     * <li>"auto" (default): civil local time at the point, from the offline
     * time zone map with the device's daylight saving rules. Without the map
     * (or at sea) it falls back to {@link #localAt}.</li>
     * <li>"military": the military time zone for the longitude (no daylight
     * saving).</li>
     * <li>"zulu", or "manual" (a fixed offset).</li>
     * </ul>
     *
     * @param selfLat device position, NaN if unknown
     * @param map the time zone map, or null if unavailable
     */
    public static DisplayZone zone(Map<String, ?> p, double latDeg, double lonDeg,
            double selfLat, double selfLon, TimeZone deviceZone, long nowUtc, TzMap map) {
        String z = str(p, TIME_ZONE, "auto");
        switch (z) {
            case "zulu":
                return DisplayZone.ZULU;
            case "manual": {
                double h = clamp(num(p, MANUAL_OFFSET, 0), -14, 14);
                return DisplayZone.fixedMinutes((int) Math.round(h * 60));
            }
            case "military":
            case "ao": // 1.0
                return DisplayZone.nautical(lonDeg);
            default: { // "auto", and 1.0's "device"
                TimeZone tz = civilZone(map, latDeg, lonDeg);
                if (tz != null)
                    return DisplayZone.localAtPoint(tz);
                return localAt(latDeg, lonDeg, selfLat, selfLon, deviceZone, nowUtc);
            }
        }
    }

    /** Without the map (unit tests and old callers). */
    public static DisplayZone zone(Map<String, ?> p, double latDeg, double lonDeg,
            double selfLat, double selfLon, TimeZone deviceZone, long nowUtc) {
        return zone(p, latDeg, lonDeg, selfLat, selfLon, deviceZone, nowUtc, null);
    }

    /**
     * The civil time zone at a point from the map, or null (no map, at sea,
     * or a zone id this device's time zone database does not know).
     */
    public static TimeZone civilZone(TzMap map, double latDeg, double lonDeg) {
        if (map == null)
            return null;
        String id = map.zoneAt(latDeg, lonDeg);
        if (id == null || id.startsWith("Etc/"))
            return null;
        TimeZone tz = TimeZone.getTimeZone(id);
        // unknown ids come back as "GMT"
        return id.equals(tz.getID()) ? tz : null;
    }

    /** The local time zone at a point (see {@link #zone}). */
    public static DisplayZone localAt(double latDeg, double lonDeg, double selfLat,
            double selfLon, TimeZone deviceZone, long nowUtc) {
        DisplayZone mil = DisplayZone.nautical(lonDeg);
        if (deviceZone == null || Double.isNaN(selfLat) || Double.isNaN(selfLon))
            return mil;
        if (distanceMeters(latDeg, lonDeg, selfLat, selfLon) > NEAR_SELF_METERS)
            return mil;
        double diffH = Math.abs(deviceZone.getOffset(nowUtc) - mil.offsetMillis(nowUtc))
                / 3600000.0;
        return diffH <= NEAR_SELF_MAX_DIFF_HOURS ? DisplayZone.of(deviceZone) : mil;
    }

    /**
     * The current value for a stored 1.0 zone setting ("device" -> "auto",
     * "ao" -> "military"), or null if the value is current.
     */
    public static String migrateZone(Object v) {
        if ("device".equals(v))
            return "auto";
        if ("ao".equals(v))
            return "military";
        return null;
    }

    static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = p2 - p1, dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * 6371000.0 * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    public static boolean showZulu(Map<String, ?> p) {
        return bool(p, SHOW_ZULU, true);
    }

    public static boolean allTwilight(Map<String, ?> p) {
        return bool(p, ALL_TWILIGHT, false);
    }

    /** Beyond doctrine: mask moonlight with local terrain. Off by default. */
    public static boolean terrain(Map<String, ?> p) {
        return bool(p, TERRAIN, false);
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
