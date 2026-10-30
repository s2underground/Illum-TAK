package com.atakmap.android.illum.plugin.core;

import java.util.Collections;
import java.util.List;

/**
 * Light data for one night at one place: the evening of {@link #epochDay}
 * through the next morning (local noon to local noon in the display zone).
 * Times are UTC milliseconds; {@link #NONE} means "does not happen".
 */
public final class NightReport {

    public static final long NONE = Long.MIN_VALUE;

    public enum LightLevel {
        HLL, MIXED, LLL, NONE
    }

    public enum Rating {
        GREEN, AMBER, RED, NONE
    }

    /** A moonrise or moonset. */
    public static final class MoonEvent {
        public final long time;
        public final boolean rise;
        /** True azimuth, degrees. */
        public final double azimuthDeg;

        public MoonEvent(long time, boolean rise, double azimuthDeg) {
            this.time = time;
            this.rise = rise;
            this.azimuthDeg = azimuthDeg;
        }
    }

    /** A stretch of the window that is HLL. */
    public static final class HllPeriod {
        public final long start, end;
        /** Bright only because of twilight (the moon alone would not make it HLL). */
        public final boolean twilightOnly;

        public HllPeriod(long start, long end, boolean twilightOnly) {
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
    public boolean noNauticalDarkness;
    /** Sun stays more than 12 deg below the horizon for the whole span (polar night). */
    public boolean allNauticalDark;
    /** Sun never sets / never rises in the span. */
    public boolean sunUpAllSpan, sunDownAllSpan;

    // moon
    public List<MoonEvent> moonEvents = Collections.emptyList();
    /** No rise/set in the span; whether the moon is up all of it. */
    public boolean moonUpAllSpan, moonDownAllSpan;
    /** Moon at the reference time (window midpoint, else local midnight). */
    public double illumPercent;
    public boolean waxing;
    public String phaseName = "";
    public long refTime;

    // rated window
    public long windowStart = NONE, windowEnd = NONE;
    public String windowLabel = "";
    /** Fraction of the window that is HLL, 0..1 (NaN when there is no window). */
    public double hllFraction = Double.NaN;
    /** Fraction of the window with the moon up but below the standard's minimum elevation. */
    public double lowMoonFraction = Double.NaN;
    public double peakLux = Double.NaN, minLux = Double.NaN;
    public long peakLuxTime = NONE;
    /** Brightest moonlight alone within the window (sky factor applied). */
    public double peakMoonLux = Double.NaN;
    public long peakMoonLuxTime = NONE;
    /** Moon's highest altitude within the window. */
    public double moonMaxAltDeg = Double.NaN;
    public long moonMaxAltTime = NONE;
    /** First HLL / last HLL instant inside the window (NONE if never HLL). */
    public long firstHll = NONE, lastHll = NONE;
    /** HLL stretches inside the window, in time order. */
    public List<HllPeriod> hllPeriods = Collections.emptyList();
    public LightLevel level = LightLevel.NONE;
    public Rating rating = Rating.NONE;

    // terrain (only when a horizon profile was used)
    /** True when level/rating/HLL figures include terrain masking. */
    public boolean terrainApplied;
    /** Moon clears / drops behind the local terrain (empty without terrain). */
    public List<MoonEvent> terrainMoonEvents = Collections.emptyList();
    /** The doctrine (flat terrain) result, always available. */
    public LightLevel doctrineLevel = LightLevel.NONE;
    public Rating doctrineRating = Rating.NONE;
    public double doctrineHllFraction = Double.NaN;

    /** Plain-language shadow outlook for the window. */
    public ShadowOutlook shadows = ShadowOutlook.EMPTY;

    // span samples (for the timeline graphic)
    public long sampleStep;
    public float[] sampleLux;
    public float[] sampleSunAlt;
    public float[] sampleMoonAlt;
    public boolean[] sampleHll;
    /** Moon above the horizon (or the terrain skyline, with terrain on). */
    public boolean[] sampleMoonVisible;

    public long sampleTime(int i) {
        return spanStart + i * sampleStep;
    }

    public boolean hasWindow() {
        return windowStart != NONE && windowEnd != NONE && windowEnd > windowStart;
    }
}
