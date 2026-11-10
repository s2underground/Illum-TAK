// Ported from the corresponding Android core class; formulas and field names retained for comparison.
using System;
using System.Collections.Generic;

namespace Illum.Core
{
/**
 * Plain-language shadow outlook for the rated window, in the spirit of the
 * NVG brief "shadowing" item: will there be shadows, how long, which way do
 * they point, and when does a low moon cause NVG glare.
 *
 * Classes (moon elevation, while the moon gives enough light to cast shadows):
 * SHORT at 45 deg or higher, MEDIUM 20-45 deg, LONG below 20 deg.
 * Shadow length is about 1 / tan(elevation): 1x object height or less at
 * 45 deg, about 3x at 20 deg, about 6x at 10 deg.
 */
public sealed class ShadowOutlook {

    /** Moonlight needed to cast a usable shadow (after the sky factor), lux. */
    public static readonly double CAST_MIN_LUX = 0.001;
    public static readonly double LONG_BELOW_DEG = 20;
    public static readonly double SHORT_FROM_DEG = 45;
    /** Moon this low (or lower) in view: NVGs gain down looking toward it. */
    public static readonly double GLARE_BELOW_DEG = 15;
    /** Pieces shorter than this are folded into the previous one. */
    internal static readonly long MIN_SEGMENT_MS = 15 * 60000L;

    public enum Kind {
        /** Moon down or too dim: no moon shadows. */
        NONE,
        /** Moon up but behind terrain at this point: the spot is in shadow. */
        TERRAIN,
        LONG, MEDIUM, SHORT
    }

    public enum Level {
        NONE, LIGHT, MODERATE, HEAVY
    }

    public sealed class Segment {
        public readonly long start, end;
        public readonly Kind kind;
        /** Moon azimuth near the middle of the segment, degrees true. */
        public readonly double moonAzDeg;
        public readonly bool glare;

        internal Segment(long start, long end, Kind kind, double moonAzDeg, bool glare) {
            this.start = start;
            this.end = end;
            this.kind = kind;
            this.moonAzDeg = moonAzDeg;
            this.glare = glare;
        }
    }

    public readonly Level level;
    public readonly System.Collections.ObjectModel.ReadOnlyCollection<Segment> segments;
    /** Share of the window with long shadows / with any moon shadows / moon behind terrain. */
    public readonly double longFraction, castFraction, terrainFraction;

    private ShadowOutlook(Level level, List<Segment> segments, double longFraction,
            double castFraction, double terrainFraction) {
        this.level = level;
        this.segments = segments.AsReadOnly();
        this.longFraction = longFraction;
        this.castFraction = castFraction;
        this.terrainFraction = terrainFraction;
    }

    public static readonly ShadowOutlook EMPTY = new ShadowOutlook(Level.NONE,
            new List<Segment>(), 0, 0, 0);

    /** Classify one moment. */
    public static Kind classify(double moonAltDeg, bool moonVisible, bool moonUp,
            double moonLux) {
        if (!moonUp || moonAltDeg <= 0)
            return Kind.NONE;
        if (!moonVisible)
            return Kind.TERRAIN;
        if (moonLux < CAST_MIN_LUX)
            return Kind.NONE;
        if (moonAltDeg < LONG_BELOW_DEG)
            return Kind.LONG;
        if (moonAltDeg < SHORT_FROM_DEG)
            return Kind.MEDIUM;
        return Kind.SHORT;
    }

    /** Collects samples in time order, then builds the outlook. */
    public sealed class Builder {
        private readonly List<long[]> times = new List<long[]>();
        private readonly List<Kind> kinds = new List<Kind>();
        private readonly List<double[]> azs = new List<double[]>();
        private readonly List<bool> glares = new List<bool>();

        public void add(long t, Kind kind, double moonAzDeg, bool glare) {
            times.Add(new long[] {
                    t
            });
            kinds.Add(kind);
            azs.Add(new double[] {
                    moonAzDeg
            });
            glares.Add(glare);
        }

        public ShadowOutlook build(long windowEnd) {
            int n = kinds.Count;
            if (n == 0)
                return EMPTY;
            int nLong = 0, nMed = 0, nShort = 0, nTerrain = 0;
            foreach (Kind k in kinds) {
                if (k == Kind.LONG)
                    nLong++;
                else if (k == Kind.MEDIUM)
                    nMed++;
                else if (k == Kind.SHORT)
                    nShort++;
                else if (k == Kind.TERRAIN)
                    nTerrain++;
            }

            // raw runs of the same kind
            List<Segment> raw = new List<Segment>();
            int s = 0;
            for (int i = 1; i <= n; i++) {
                if (i == n || kinds[i] != kinds[s]) {
                    long end = i == n ? windowEnd : times[i][0];
                    bool glare = false;
                    for (int j = s; j < i; j++)
                        glare |= glares[j];
                    raw.Add(new Segment(times[s][0], end, kinds[s],
                            azs[(s + i - 1) / 2][0], glare));
                    s = i;
                }
            }

            // fold short pieces into the previous segment, then re-merge
            List<Segment> output = new List<Segment>();
            foreach (Segment g in raw) {
                if (output.Count != 0 && (g.end - g.start < MIN_SEGMENT_MS
                        || output[output.Count - 1].kind == g.kind)) {
                    Segment p = JavaLists.RemoveAt(output, output.Count - 1);
                    output.Add(new Segment(p.start, g.end, p.kind, p.moonAzDeg, p.glare
                            || (g.kind == p.kind && g.glare)));
                } else {
                    output.Add(g);
                }
            }
            // a short first piece: fold it forward
            if (output.Count > 1 && output[0].end - output[0].start < MIN_SEGMENT_MS) {
                Segment a = JavaLists.RemoveAt(output, 0), b = JavaLists.RemoveAt(output, 0);
                output.Insert(0, new Segment(a.start, b.end, b.kind, b.moonAzDeg, b.glare));
            }

            double longF = nLong / (double) n;
            double castF = (nLong + nMed + nShort) / (double) n;
            double terrF = nTerrain / (double) n;
            Level level;
            if (castF == 0)
                level = Level.NONE;
            else if (longF >= 0.40)
                level = Level.HEAVY;
            else if (longF >= 0.15 || (nLong + nMed) / (double) n >= 0.40)
                level = Level.MODERATE;
            else
                level = Level.LIGHT;
            return new ShadowOutlook(level, output, longF, castF, terrF);
        }
    }

    // ------------------------------------------------------------------
    // Plain-language text
    // ------------------------------------------------------------------

    /** e.g. "HEAVY: long, dark shadows for much of the night". */
    public string headline() {
        switch (level) {
            case Level.HEAVY:
                return "HEAVY: long, dark shadows for much of the night";
            case Level.MODERATE:
                return "MODERATE: some long shadows";
            case Level.LIGHT:
                return "LIGHT: mostly short shadows";
            default:
                return terrainFraction > 0 ? "NONE: moon is down or behind terrain"
                        : "NONE: no moonlight to cast shadows";
        }
    }

    /** One line per segment, e.g. "2052L–2310L  LONG shadows pointing W. ...". */
    public List<string> lines(DisplayZone zone) {
        List<string> output = new List<string>();
        foreach (Segment g in segments) {
            string when = Formats.time(g.start, zone) + "–" + Formats.time(g.end, zone)
                    + "  ";
            output.Add(when + describe(g));
        }
        return output;
    }

    internal static string describe(Segment g) {
        string toward = compass8(g.moonAzDeg + 180);
        string moonDir = compass8(g.moonAzDeg);
        switch (g.kind) {
            case Kind.TERRAIN:
                return "Moon behind terrain to the " + moonDir + ". This spot is in shadow.";
            case Kind.LONG:
                return "LONG shadows, pointing " + toward + "."
                        + (g.glare ? " Moon low in the " + moonDir + ": NVG glare looking "
                                + moonDir + "." : "");
            case Kind.MEDIUM:
                return "MEDIUM shadows, pointing " + toward + ".";
            case Kind.SHORT:
                return "SHORT shadows (moon high).";
            default:
                return "No moon shadows (moon down or too dim).";
        }
    }

    private static readonly string[] COMPASS = {
            "N", "NE", "E", "SE", "S", "SW", "W", "NW"
    };

    /** 8-point compass word for a true azimuth. */
    public static string compass8(double azDeg) {
        int i = (int) JavaMath.round(Ephemeris.norm360(azDeg) / 45.0) % 8;
        return COMPASS[i];
    }
}

}
