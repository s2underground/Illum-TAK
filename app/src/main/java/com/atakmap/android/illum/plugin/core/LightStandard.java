package com.atakmap.android.illum.plugin.core;

/**
 * How a service decides whether a moment counts as High Light Level (HLL).
 *
 * Built-in standards come from published definitions; Custom lets a unit
 * enter its own SOP numbers.
 */
public final class LightStandard {

    public enum Method {
        /** HLL when computed natural illuminance is at or above a lux threshold. */
        LUX,
        /** HLL when moon illumination and moon elevation both meet minimums. */
        MOON_RULE
    }

    public static final double HLL_LUX = 0.0022;

    public static final LightStandard USAF = new LightStandard("usaf", "USAF",
            Method.LUX, HLL_LUX, 20, 30,
            "AFI 11-214: high illumination is at least 2.2 millilux, roughly a 20% moon"
                    + " disk at 30° or higher.");

    public static final LightStandard USN = new LightStandard("usn", "USN/USMC",
            Method.LUX, HLL_LUX, 20, 30,
            "Navy/USMC (MCO 3500.14): HLL is 0.0022 lux or above; LLL is below 0.0022 lux."
                    + " Planning tools do not account for low moon angle, terrain or shadows.");

    public static final LightStandard ARMY = new LightStandard("army", "Army",
            Method.MOON_RULE, HLL_LUX, 23, 30,
            "Army aviation rule of thumb: at least 23% moon illumination and the moon at"
                    + " least 30° above the horizon. Verify against your unit SOP.");

    public final String id;
    public final String label;
    public final Method method;
    /** Lux threshold (LUX method; also used for twilight in MOON_RULE). */
    public final double luxThreshold;
    /** Minimum moon illumination, percent (MOON_RULE). */
    public final double minIllumPercent;
    /** Minimum moon elevation, degrees (MOON_RULE; also the low-moon caution angle). */
    public final double minMoonAltDeg;
    public final String reference;

    public LightStandard(String id, String label, Method method, double luxThreshold,
            double minIllumPercent, double minMoonAltDeg, String reference) {
        this.id = id;
        this.label = label;
        this.method = method;
        this.luxThreshold = luxThreshold;
        this.minIllumPercent = minIllumPercent;
        this.minMoonAltDeg = minMoonAltDeg;
        this.reference = reference;
    }

    public static LightStandard custom(Method method, double luxThreshold,
            double minIllumPercent, double minMoonAltDeg) {
        String ref = method == Method.LUX
                ? String.format(java.util.Locale.US,
                        "Custom: HLL at or above %.2f millilux.", luxThreshold * 1000)
                : String.format(java.util.Locale.US,
                        "Custom: HLL when the moon is at least %.0f%% illuminated and at"
                                + " least %.0f° up.",
                        minIllumPercent, minMoonAltDeg);
        return new LightStandard("custom", "Custom", method, luxThreshold, minIllumPercent,
                minMoonAltDeg, ref);
    }

    public static LightStandard byId(String id) {
        if ("usn".equals(id))
            return USN;
        if ("army".equals(id))
            return ARMY;
        return USAF;
    }

    /**
     * Whether one sample counts as HLL.
     *
     * @param totalLux sun + moon (+ sky, if enabled) after the sky-condition factor
     * @param sunLux sun part only, after the sky-condition factor
     */
    public boolean isHighLight(double totalLux, double sunLux, double moonAltDeg,
            double illumPercent) {
        if (method == Method.LUX)
            return totalLux >= luxThreshold;
        if (sunLux >= luxThreshold)
            return true; // still twilight-bright
        return moonAltDeg >= minMoonAltDeg && illumPercent >= minIllumPercent;
    }
}
