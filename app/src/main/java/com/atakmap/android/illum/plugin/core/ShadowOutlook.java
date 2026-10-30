package com.atakmap.android.illum.plugin.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
public final class ShadowOutlook {

    /** Moonlight needed to cast a usable shadow (after the sky factor), lux. */
    public static final double CAST_MIN_LUX = 0.001;
    public static final double LONG_BELOW_DEG = 20;
    public static final double SHORT_FROM_DEG = 45;
    /** Moon this low (or lower) in view: NVGs gain down looking toward it. */
    public static final double GLARE_BELOW_DEG = 15;
    /** Pieces shorter than this are folded into the previous one. */
    static final long MIN_SEGMENT_MS = 15 * 60000L;

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

    public static final class Segment {
        public final long start, end;
        public final Kind kind;
        /** Moon azimuth near the middle of the segment, degrees true. */
        public final double moonAzDeg;
        public final boolean glare;

        Segment(long start, long end, Kind kind, double moonAzDeg, boolean glare) {
            this.start = start;
            this.end = end;
            this.kind = kind;
            this.moonAzDeg = moonAzDeg;
            this.glare = glare;
        }
    }

    public final Level level;
    public final List<Segment> segments;
    /** Share of the window with long shadows / with any moon shadows / moon behind terrain. */
    public final double longFraction, castFraction, terrainFraction;

    private ShadowOutlook(Level level, List<Segment> segments, double longFraction,
            double castFraction, double terrainFraction) {
        this.level = level;
        this.segments = Collections.unmodifiableList(segments);
        this.longFraction = longFraction;
        this.castFraction = castFraction;
        this.terrainFraction = terrainFraction;
    }

    public static final ShadowOutlook EMPTY = new ShadowOutlook(Level.NONE,
            new ArrayList<Segment>(), 0, 0, 0);

    /** Classify one moment. */
    public static Kind classify(double moonAltDeg, boolean moonVisible, boolean moonUp,
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
    public static final class Builder {
        private final List<long[]> times = new ArrayList<>();
        private final List<Kind> kinds = new ArrayList<>();
        private final List<double[]> azs = new ArrayList<>();
        private final List<Boolean> glares = new ArrayList<>();

        public void add(long t, Kind kind, double moonAzDeg, boolean glare) {
            times.add(new long[] {
                    t
            });
            kinds.add(kind);
            azs.add(new double[] {
                    moonAzDeg
            });
            glares.add(glare);
        }

        public ShadowOutlook build(long windowEnd) {
            int n = kinds.size();
            if (n == 0)
                return EMPTY;
            int nLong = 0, nMed = 0, nShort = 0, nTerrain = 0;
            for (Kind k : kinds) {
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
            List<Segment> raw = new ArrayList<>();
            int s = 0;
            for (int i = 1; i <= n; i++) {
                if (i == n || kinds.get(i) != kinds.get(s)) {
                    long end = i == n ? windowEnd : times.get(i)[0];
                    boolean glare = false;
                    for (int j = s; j < i; j++)
                        glare |= glares.get(j);
                    raw.add(new Segment(times.get(s)[0], end, kinds.get(s),
                            azs.get((s + i - 1) / 2)[0], glare));
                    s = i;
                }
            }

            // fold short pieces into the previous segment, then re-merge
            List<Segment> out = new ArrayList<>();
            for (Segment g : raw) {
                if (!out.isEmpty() && (g.end - g.start < MIN_SEGMENT_MS
                        || out.get(out.size() - 1).kind == g.kind)) {
                    Segment p = out.remove(out.size() - 1);
                    out.add(new Segment(p.start, g.end, p.kind, p.moonAzDeg, p.glare
                            || (g.kind == p.kind && g.glare)));
                } else {
                    out.add(g);
                }
            }
            // a short first piece: fold it forward
            if (out.size() > 1 && out.get(0).end - out.get(0).start < MIN_SEGMENT_MS) {
                Segment a = out.remove(0), b = out.remove(0);
                out.add(0, new Segment(a.start, b.end, b.kind, b.moonAzDeg, b.glare));
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
            return new ShadowOutlook(level, out, longF, castF, terrF);
        }
    }

    // ------------------------------------------------------------------
    // Plain-language text
    // ------------------------------------------------------------------

    /** e.g. "HEAVY: long, dark shadows for much of the night". */
    public String headline() {
        switch (level) {
            case HEAVY:
                return "HEAVY: long, dark shadows for much of the night";
            case MODERATE:
                return "MODERATE: some long shadows";
            case LIGHT:
                return "LIGHT: mostly short shadows";
            default:
                return terrainFraction > 0 ? "NONE: moon is down or behind terrain"
                        : "NONE: no moonlight to cast shadows";
        }
    }

    /** One line per segment, e.g. "2052L–2310L  LONG shadows pointing W. ...". */
    public List<String> lines(DisplayZone zone) {
        List<String> out = new ArrayList<>();
        for (Segment g : segments) {
            String when = Formats.time(g.start, zone) + "–" + Formats.time(g.end, zone)
                    + "  ";
            out.add(when + describe(g));
        }
        return out;
    }

    static String describe(Segment g) {
        String toward = compass8(g.moonAzDeg + 180);
        String moonDir = compass8(g.moonAzDeg);
        switch (g.kind) {
            case TERRAIN:
                return "Moon behind terrain to the " + moonDir + ". This spot is in shadow.";
            case LONG:
                return "LONG shadows, pointing " + toward + "."
                        + (g.glare ? " Moon low in the " + moonDir + ": NVG glare looking "
                                + moonDir + "." : "");
            case MEDIUM:
                return "MEDIUM shadows, pointing " + toward + ".";
            case SHORT:
                return "SHORT shadows (moon high).";
            default:
                return "No moon shadows (moon down or too dim).";
        }
    }

    private static final String[] COMPASS = {
            "N", "NE", "E", "SE", "S", "SW", "W", "NW"
    };

    /** 8-point compass word for a true azimuth. */
    public static String compass8(double azDeg) {
        int i = (int) Math.round(Ephemeris.norm360(azDeg) / 45.0) % 8;
        return COMPASS[i];
    }
}
