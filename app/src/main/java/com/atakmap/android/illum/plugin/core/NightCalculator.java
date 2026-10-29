package com.atakmap.android.illum.plugin.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@link NightReport}s: twilight and rise/set times, moon data and the
 * HLL/LLL stoplight for a rated window.
 *
 * Event conventions (same as USNO):
 * <ul>
 * <li>Sunrise/sunset: Sun's upper limb on the horizon with standard refraction
 * (center 0.833 deg below the horizon).</li>
 * <li>Civil / nautical / astronomical twilight: Sun's center 6 / 12 / 18 deg
 * below the horizon. EENT/BMNT are the nautical ones.</li>
 * <li>Moonrise/moonset: Moon's upper limb on the horizon, topocentric, with
 * standard refraction (34').</li>
 * </ul>
 */
public final class NightCalculator {

    static final double SUN_RISESET = -0.8333;
    static final double CIVIL = -6.0;
    static final double NAUTICAL = -12.0;
    static final double ASTRONOMICAL = -18.0;
    static final double REFRACTION = 0.5667;

    private NightCalculator() {
    }

    /** Height of the Moon's center (topocentric, geometric) at rise/set. */
    static double moonHorizon(double parallaxDeg) {
        return -REFRACTION - 0.2725 * parallaxDeg;
    }

    /** Compute one night. */
    public static NightReport compute(double latDeg, double lonDeg, long epochDay,
            DisplayZone zone, PlanSettings settings) {
        NightReport r = new NightReport();
        r.latDeg = latDeg;
        r.lonDeg = lonDeg;
        r.epochDay = epochDay;
        r.spanStart = CivilDate.localToUtc(epochDay, 12 * 60, zone);
        r.spanEnd = CivilDate.localToUtc(epochDay + 1, 12 * 60, zone);

        long step = settings.sampleMinutes * 60000L;
        int n = (int) ((r.spanEnd - r.spanStart) / step) + 1;
        r.sampleStep = step;
        r.sampleLux = new float[n];
        r.sampleSunAlt = new float[n];
        r.sampleMoonAlt = new float[n];
        r.sampleHll = new boolean[n];
        double[] sunAlt = new double[n];
        double[] moonF = new double[n]; // moon altitude minus its rise/set height

        for (int i = 0; i < n; i++) {
            long t = r.sampleTime(i);
            SkyState s = Ephemeris.compute(t, latDeg, lonDeg);
            Lux lux = lux(s, settings);
            sunAlt[i] = s.sunAltDeg;
            moonF[i] = s.moonAltDeg - moonHorizon(s.moonParallaxDeg);
            r.sampleSunAlt[i] = (float) s.sunAltDeg;
            r.sampleMoonAlt[i] = (float) s.moonAltDeg;
            r.sampleLux[i] = (float) lux.total;
            r.sampleHll[i] = settings.standard.isHighLight(lux.total, lux.sun, s.moonAltDeg,
                    s.illumPercent());
        }

        sunEvents(r, sunAlt, latDeg, lonDeg);
        moonEvents(r, moonF, latDeg, lonDeg);
        window(r, zone, settings);
        rate(r, latDeg, lonDeg, settings);
        return r;
    }

    /** Compute consecutive nights starting at a local date. */
    public static List<NightReport> range(double latDeg, double lonDeg, long firstEpochDay,
            int nights, DisplayZone zone, PlanSettings settings) {
        List<NightReport> out = new ArrayList<>(nights);
        for (int i = 0; i < nights; i++)
            out.add(compute(latDeg, lonDeg, firstEpochDay + i, zone, settings));
        return out;
    }

    // ------------------------------------------------------------------
    // Illuminance for a sample
    // ------------------------------------------------------------------

    static final class Lux {
        double sun, moon, total;
    }

    static Lux lux(SkyState s, PlanSettings settings) {
        Lux l = new Lux();
        double div = settings.sky.divisor;
        l.sun = Illuminance.sunLux(s) / div;
        l.moon = Illuminance.moonLux(s) / div;
        l.total = l.sun + l.moon
                + (settings.includeNightSky ? Illuminance.NIGHT_SKY_LUX / div : 0.0);
        return l;
    }

    // ------------------------------------------------------------------
    // Sun
    // ------------------------------------------------------------------

    private static void sunEvents(NightReport r, double[] alt, double lat, double lon) {
        r.sunset = firstCrossing(r, alt, SUN_RISESET, false, r.spanStart, lat, lon);
        r.eect = firstCrossing(r, alt, CIVIL, false, r.spanStart, lat, lon);
        r.eent = firstCrossing(r, alt, NAUTICAL, false, r.spanStart, lat, lon);
        r.eeat = firstCrossing(r, alt, ASTRONOMICAL, false, r.spanStart, lat, lon);
        r.bmat = firstCrossing(r, alt, ASTRONOMICAL, true, after(r.eeat, r.spanStart), lat, lon);
        r.bmnt = firstCrossing(r, alt, NAUTICAL, true, after(r.eent, r.spanStart), lat, lon);
        r.bmct = firstCrossing(r, alt, CIVIL, true, after(r.eect, r.spanStart), lat, lon);
        r.sunrise = firstCrossing(r, alt, SUN_RISESET, true, after(r.sunset, r.spanStart),
                lat, lon);

        boolean anyBelowNautical = false, allBelowNautical = true;
        boolean anyBelowHorizon = false, allBelowHorizon = true;
        for (double a : alt) {
            if (a < NAUTICAL)
                anyBelowNautical = true;
            else
                allBelowNautical = false;
            if (a < SUN_RISESET)
                anyBelowHorizon = true;
            else
                allBelowHorizon = false;
        }
        r.noNauticalDarkness = !anyBelowNautical;
        r.allNauticalDark = allBelowNautical;
        r.sunUpAllSpan = !anyBelowHorizon;
        r.sunDownAllSpan = allBelowHorizon;
    }

    private static long after(long t, long fallback) {
        return t == NightReport.NONE ? fallback : t;
    }

    /**
     * First crossing of the sun's altitude through a level, in the given
     * direction, at or after a time. Refined by bisection to about a second.
     */
    private static long firstCrossing(NightReport r, double[] alt, double level, boolean rising,
            long notBefore, double lat, double lon) {
        for (int i = 0; i + 1 < alt.length; i++) {
            if (r.sampleTime(i + 1) < notBefore)
                continue;
            double a = alt[i] - level, b = alt[i + 1] - level;
            boolean hit = rising ? (a < 0 && b >= 0) : (a >= 0 && b < 0);
            if (!hit)
                continue;
            long lo = r.sampleTime(i), hi = r.sampleTime(i + 1);
            for (int k = 0; k < 14; k++) {
                long mid = (lo + hi) >>> 1;
                double m = Ephemeris.sunAltitude(mid, lat, lon) - level;
                boolean midBelow = m < 0;
                // rising: below -> above. setting: above -> below.
                if (rising == midBelow)
                    lo = mid;
                else
                    hi = mid;
            }
            long t = (lo + hi) >>> 1;
            if (t >= notBefore)
                return t;
        }
        return NightReport.NONE;
    }

    // ------------------------------------------------------------------
    // Moon
    // ------------------------------------------------------------------

    private static void moonEvents(NightReport r, double[] f, double lat, double lon) {
        List<NightReport.MoonEvent> ev = new ArrayList<>();
        for (int i = 0; i + 1 < f.length; i++) {
            double a = f[i], b = f[i + 1];
            boolean rise = a < 0 && b >= 0;
            boolean set = a >= 0 && b < 0;
            if (!rise && !set)
                continue;
            long lo = r.sampleTime(i), hi = r.sampleTime(i + 1);
            SkyState s = null;
            for (int k = 0; k < 14; k++) {
                long mid = (lo + hi) >>> 1;
                s = Ephemeris.compute(mid, lat, lon);
                boolean midBelow = s.moonAltDeg - moonHorizon(s.moonParallaxDeg) < 0;
                if (rise == midBelow)
                    lo = mid;
                else
                    hi = mid;
            }
            long t = (lo + hi) >>> 1;
            s = Ephemeris.compute(t, lat, lon);
            ev.add(new NightReport.MoonEvent(t, rise, s.moonAzDeg));
        }
        r.moonEvents = ev;
        if (ev.isEmpty()) {
            r.moonUpAllSpan = f[0] >= 0;
            r.moonDownAllSpan = f[0] < 0;
        }
    }

    // ------------------------------------------------------------------
    // Rated window
    // ------------------------------------------------------------------

    private static void window(NightReport r, DisplayZone zone, PlanSettings ps) {
        switch (ps.windowMode) {
            case CUSTOM: {
                // A start before noon means the following morning.
                long startDay = ps.customStartMin >= 12 * 60 ? r.epochDay : r.epochDay + 1;
                long start = CivilDate.localToUtc(startDay, ps.customStartMin, zone);
                long end = CivilDate.localToUtc(startDay, ps.customEndMin, zone);
                if (end <= start)
                    end = CivilDate.localToUtc(startDay + 1, ps.customEndMin, zone);
                r.windowStart = start;
                r.windowEnd = end;
                r.windowLabel = "Custom";
                break;
            }
            case SUNSET_SUNRISE:
                darkWindow(r, r.sunset, r.sunrise, r.sunDownAllSpan, r.sunUpAllSpan);
                r.windowLabel = "Sunset–Sunrise";
                break;
            case NAUTICAL_DARK:
            default:
                darkWindow(r, r.eent, r.bmnt, r.allNauticalDark, r.noNauticalDarkness);
                r.windowLabel = "EENT–BMNT";
                break;
        }
    }

    private static void darkWindow(NightReport r, long start, long end, boolean allDark,
            boolean neverDark) {
        if (neverDark)
            return;
        if (allDark) {
            r.windowStart = r.spanStart;
            r.windowEnd = r.spanEnd;
            return;
        }
        // already dark at noon (polar edge): start at span start
        r.windowStart = start != NightReport.NONE ? start : r.spanStart;
        r.windowEnd = end != NightReport.NONE ? end : r.spanEnd;
    }

    private static void rate(NightReport r, double lat, double lon, PlanSettings ps) {
        // Reference time for "tonight's illumination": window middle, else local midnight.
        r.refTime = r.hasWindow() ? r.windowStart + (r.windowEnd - r.windowStart) / 2
                : r.spanStart + (r.spanEnd - r.spanStart) / 2;
        SkyState ref = Ephemeris.compute(r.refTime, lat, lon);
        r.illumPercent = ref.illumPercent();
        r.waxing = ref.waxing;
        r.phaseName = phaseName(ref.illumFraction, ref.waxing);

        if (!r.hasWindow()) {
            r.level = NightReport.LightLevel.NONE;
            r.rating = NightReport.Rating.NONE;
            return;
        }

        long step = r.sampleStep;
        int total = 0, hll = 0, lowMoon = 0;
        double peak = -1, peakMoon = -1, min = Double.MAX_VALUE, maxAlt = -91;
        List<NightReport.HllPeriod> periods = new ArrayList<>();
        long segStart = NightReport.NONE;
        boolean segMoon = false;
        for (long t = r.windowStart; t < r.windowEnd; t += step) {
            SkyState s = Ephemeris.compute(t, lat, lon);
            Lux l = lux(s, ps);
            boolean isHll = ps.standard.isHighLight(l.total, l.sun, s.moonAltDeg,
                    s.illumPercent());
            // would it be HLL from the moon (and sky) alone, without twilight?
            boolean moonHll = ps.standard.isHighLight(l.total - l.sun, 0, s.moonAltDeg,
                    s.illumPercent());
            total++;
            if (isHll) {
                hll++;
                if (r.firstHll == NightReport.NONE)
                    r.firstHll = t;
                r.lastHll = t;
                if (segStart == NightReport.NONE) {
                    segStart = t;
                    segMoon = false;
                }
                segMoon |= moonHll;
            } else if (segStart != NightReport.NONE) {
                periods.add(new NightReport.HllPeriod(segStart, t, !segMoon));
                segStart = NightReport.NONE;
            }
            if (s.moonAltDeg > 0 && s.moonAltDeg < ps.standard.minMoonAltDeg)
                lowMoon++;
            if (l.total > peak) {
                peak = l.total;
                r.peakLuxTime = t;
            }
            if (l.moon > peakMoon) {
                peakMoon = l.moon;
                r.peakMoonLuxTime = t;
            }
            min = Math.min(min, l.total);
            if (s.moonAltDeg > maxAlt) {
                maxAlt = s.moonAltDeg;
                r.moonMaxAltTime = t;
            }
        }
        if (segStart != NightReport.NONE)
            periods.add(new NightReport.HllPeriod(segStart, r.windowEnd, !segMoon));
        r.hllPeriods = periods;
        if (total == 0) {
            r.level = NightReport.LightLevel.NONE;
            r.rating = NightReport.Rating.NONE;
            return;
        }
        r.hllFraction = hll / (double) total;
        r.lowMoonFraction = lowMoon / (double) total;
        r.peakLux = peak;
        r.peakMoonLux = peakMoon;
        r.minLux = min;
        r.moonMaxAltDeg = maxAlt;

        if (r.hllFraction >= 1.0 - ps.tolerance)
            r.level = NightReport.LightLevel.HLL;
        else if (r.hllFraction <= ps.tolerance)
            r.level = NightReport.LightLevel.LLL;
        else
            r.level = NightReport.LightLevel.MIXED;
        r.rating = ratingFor(r.level, ps.mission);
    }

    static NightReport.Rating ratingFor(NightReport.LightLevel level, PlanSettings.Mission m) {
        switch (level) {
            case HLL:
                return m == PlanSettings.Mission.NVG ? NightReport.Rating.GREEN
                        : NightReport.Rating.RED;
            case LLL:
                return m == PlanSettings.Mission.NVG ? NightReport.Rating.RED
                        : NightReport.Rating.GREEN;
            case MIXED:
                return NightReport.Rating.AMBER;
            default:
                return NightReport.Rating.NONE;
        }
    }

    static String phaseName(double fraction, boolean waxing) {
        if (fraction < 0.02)
            return "New";
        if (fraction > 0.98)
            return "Full";
        if (fraction >= 0.45 && fraction <= 0.55)
            return waxing ? "First Quarter" : "Last Quarter";
        if (fraction < 0.5)
            return waxing ? "Waxing Crescent" : "Waning Crescent";
        return waxing ? "Waxing Gibbous" : "Waning Gibbous";
    }
}
