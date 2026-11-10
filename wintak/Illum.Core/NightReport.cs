// Ported from the corresponding Android core class; formulas and field names retained for comparison.
using System;
using System.Collections.Generic;

namespace Illum.Core
{
/**
 * Light data for one night at one place: the evening of {@link #epochDay}
 * through the next morning (local noon to local noon in the display zone).
 * Times are UTC milliseconds; {@link #NONE} means "does not happen".
 */
public sealed class NightReport {

    public const long NONE = long.MinValue;

    public enum LightLevel {
        HLL, MIXED, LLL, NONE
    }

    public enum Rating {
        GREEN, AMBER, RED, NONE
    }

    /** A moonrise or moonset. */
    public sealed class MoonEvent {
        public readonly long time;
        public readonly bool rise;
        /** True azimuth, degrees. */
        public readonly double azimuthDeg;

        public MoonEvent(long time, bool rise, double azimuthDeg) {
            this.time = time;
            this.rise = rise;
            this.azimuthDeg = azimuthDeg;
        }
    }

    /** A stretch of the window that is HLL. */
    public sealed class HllPeriod {
        public readonly long start, end;
        /** Bright only because of twilight (the moon alone would not make it HLL). */
        public readonly bool twilightOnly;

        public HllPeriod(long start, long end, bool twilightOnly) {
            this.start = start;
            this.end = end;
            this.twilightOnly = twilightOnly;
        }
    }

    // where / when
    public double latDeg, lonDeg;
    public long epochDay;
    public long spanStart, spanEnd;

    // sun (evening then morning)
    public long sunset = NONE, eect = NONE, eent = NONE, eeat = NONE;
    public long bmat = NONE, bmnt = NONE, bmct = NONE, sunrise = NONE;
    /** Sun never reaches 12 deg below the horizon this night (high-latitude summer). */
    public bool noNauticalDarkness;
    /** Sun stays more than 12 deg below the horizon for the whole span (polar night). */
    public bool allNauticalDark;
    /** Sun never sets / never rises in the span. */
    public bool sunUpAllSpan, sunDownAllSpan;

    // moon
    public List<MoonEvent> moonEvents = new List<MoonEvent>();
    /** No rise/set in the span; whether the moon is up all of it. */
    public bool moonUpAllSpan, moonDownAllSpan;
    /** Moon at the reference time (window midpoint, else local midnight). */
    public double illumPercent;
    public bool waxing;
    public string phaseName = "";
    public long refTime;

    // rated window
    public long windowStart = NONE, windowEnd = NONE;
    public string windowLabel = "";
    /** Fraction of the window that is HLL, 0..1 (NaN when there is no window). */
    public double hllFraction = double.NaN;
    /** Fraction of the window with the moon up but below the standard's minimum elevation. */
    public double lowMoonFraction = double.NaN;
    public double peakLux = double.NaN, minLux = double.NaN;
    public long peakLuxTime = NONE;
    /** Brightest moonlight alone within the window (sky factor applied). */
    public double peakMoonLux = double.NaN;
    public long peakMoonLuxTime = NONE;
    /** Moon's highest altitude within the window. */
    public double moonMaxAltDeg = double.NaN;
    public long moonMaxAltTime = NONE;
    /** First HLL / last HLL instant inside the window (NONE if never HLL). */
    public long firstHll = NONE, lastHll = NONE;
    /** HLL stretches inside the window, in time order. */
    public List<HllPeriod> hllPeriods = new List<HllPeriod>();
    public LightLevel level = LightLevel.NONE;
    public Rating rating = Rating.NONE;

    // terrain (only when a horizon profile was used)
    /** True when level/rating/HLL figures include terrain masking. */
    public bool terrainApplied;
    /** Moon clears / drops behind the local terrain (empty without terrain). */
    public List<MoonEvent> terrainMoonEvents = new List<MoonEvent>();
    /** The doctrine (flat terrain) result, always available. */
    public LightLevel doctrineLevel = LightLevel.NONE;
    public Rating doctrineRating = Rating.NONE;
    public double doctrineHllFraction = double.NaN;

    /** Plain-language shadow outlook for the window. */
    public ShadowOutlook shadows = ShadowOutlook.EMPTY;

    // span samples (for the timeline graphic)
    public long sampleStep;
    public float[] sampleLux;
    public float[] sampleSunAlt;
    public float[] sampleMoonAlt;
    public bool[] sampleHll;
    /** Moon above the horizon (or the terrain skyline, with terrain on). */
    public bool[] sampleMoonVisible;

    public long sampleTime(int i) {
        return spanStart + i * sampleStep;
    }

    public bool hasWindow() {
        return windowStart != NONE && windowEnd != NONE && windowEnd > windowStart;
    }
}

}
