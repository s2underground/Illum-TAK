package com.atakmap.android.illum.plugin.core;

/**
 * Natural ground illuminance (lux) from the Sun and Moon.
 *
 * Model: U.S. Naval Observatory Circular 171, "Computer Programs for Sun and
 * Moon Illuminance" (Janiczek and DeYoung, 1987), public domain:
 * <ul>
 * <li>ATMOS: atmospheric transmission factor as a function of altitude.</li>
 * <li>Sun: 133775 lux x ATMOS.</li>
 * <li>Moon: relative brightness from elongation, x 0.418 lux x ATMOS.</li>
 * <li>Night sky (stars + airglow): 0.0005 lux.</li>
 * <li>Sky condition divides the result: 1 clear, 2 thin clouds,
 * 3 average clouds, 10 dark stratus.</li>
 * </ul>
 * Deviation from the circular: the circular's small "0.03 sin Z" term in the
 * Moon formula is replaced by an explicit inverse-square Earth-Moon distance
 * factor (mean 384,400 km), which accounts for the +/-14% perigee/apogee
 * brightness change.
 *
 * Sanity check: a 20%-illuminated Moon 30 degrees up gives about 2.1 millilux
 * under a clear sky, matching the USAF rule of thumb that 2.2 millilux is
 * "roughly a 20% moon disk at 30 degrees or higher".
 */
public final class Illuminance {

    /** USNO night-sky (starlight + airglow) term, lux. */
    public static final double NIGHT_SKY_LUX = 0.0005;
    /** Below this altitude the Sun/Moon contribution is treated as zero. */
    static final double MIN_ALT_DEG = -18.0;
    static final double MEAN_MOON_DIST_KM = 384400.0;

    private Illuminance() {
    }

    /** Circular 171 ATMOS subroutine; altitude in degrees. */
    static double atmos(double altDeg) {
        double u = Math.sin(altDeg * Ephemeris.D2R);
        double x = 753.66156;
        double s = Math.asin(x * Math.cos(altDeg * Ephemeris.D2R) / (x + 1.0));
        double m = x * (Math.cos(s) - u) + Math.cos(s);
        m = Math.exp(-0.21 * m) * u
                + 0.0289 * Math.exp(-0.042 * m) * (1.0 + (altDeg + 90.0) * u / 57.29577951);
        return Math.max(0.0, m);
    }

    /** Clear-sky solar illuminance, lux. */
    public static double sunLux(double sunAltDeg) {
        if (sunAltDeg < MIN_ALT_DEG)
            return 0.0;
        return 133775.0 * atmos(sunAltDeg);
    }

    /**
     * Clear-sky lunar illuminance, lux.
     *
     * @param moonAltDeg topocentric altitude, degrees
     * @param elongationDeg geocentric Sun-Moon elongation, degrees (180 = full)
     * @param distKm Earth-Moon distance, km
     */
    public static double moonLux(double moonAltDeg, double elongationDeg, double distKm) {
        if (moonAltDeg < MIN_ALT_DEG || elongationDeg <= 0.0)
            return 0.0;
        double e = elongationDeg * Ephemeris.D2R;
        double p = 0.892 * Math.exp(-3.343 / Math.pow(Math.tan(e / 2.0), 0.632))
                + 0.0344 * (Math.sin(e) - e * Math.cos(e));
        p = 0.418 * p / (1.0 - 0.005 * Math.cos(e));
        double r = MEAN_MOON_DIST_KM / distKm;
        return Math.max(0.0, p * r * r * atmos(moonAltDeg));
    }

    /** Clear-sky Moon illuminance for a computed sky state. */
    public static double moonLux(SkyState s) {
        return moonLux(s.moonAltDeg, s.elongationDeg, s.moonDistKm);
    }

    /** Clear-sky Sun illuminance for a computed sky state. */
    public static double sunLux(SkyState s) {
        return sunLux(s.sunAltDeg);
    }
}
