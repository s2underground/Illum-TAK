package com.atakmap.android.illum.plugin.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.TimeZone;

public class TerrainShadowTest {

    static final double LAT = 38.89, LON = -77.04;
    static final DisplayZone NY = DisplayZone.of(TimeZone.getTimeZone("America/New_York"));

    /** Flat plain at 100 m, with an optional north-south ridge to the east. */
    static HorizonProfile.ElevationSampler ridgeEast(final double ridgeHeightM,
            final double ridgeDistM) {
        return new HorizonProfile.ElevationSampler() {
            @Override
            public void sample(double[] lat, double[] lon, double[] out) {
                double mPerDegLon = 111320 * Math.cos(Math.toRadians(LAT));
                for (int i = 0; i < lat.length; i++) {
                    double east = (lon[i] - LON) * mPerDegLon;
                    double h = 100;
                    // 2 km wide ridge
                    if (Math.abs(east - ridgeDistM) < 1000)
                        h += ridgeHeightM;
                    out[i] = h;
                }
            }
        };
    }

    @Test
    public void flatTerrainGivesNearZeroSkyline() {
        HorizonProfile p = HorizonProfile.build(LAT, LON, ridgeEast(0, 5000),
                HorizonProfile.DEFAULT_RADIUS_M);
        assertNotNull(p);
        assertEquals(1.0, p.coverage, 1e-9);
        for (int az = 0; az < 360; az += 10)
            assertTrue("az " + az, Math.abs(p.altitudeAt(az)) < 0.1);
    }

    @Test
    public void ridgeRaisesSkylineOnlyToTheEast() {
        // 900 m ridge 5 km east: about atan(898/4000) = 12.6 deg at the near face
        HorizonProfile p = HorizonProfile.build(LAT, LON, ridgeEast(900, 5000),
                HorizonProfile.DEFAULT_RADIUS_M);
        assertNotNull(p);
        double east = p.altitudeAt(90);
        assertTrue("east skyline " + east, east > 10 && east < 14);
        assertTrue(Math.abs(p.altitudeAt(270)) < 0.1);
        assertTrue(Math.abs(p.altitudeAt(0)) < 0.5);
        double[] hi = p.highest();
        assertEquals(90, hi[1], 4);
    }

    @Test
    public void noDataAtPointMeansNoProfile() {
        HorizonProfile p = HorizonProfile.build(LAT, LON, new HorizonProfile.ElevationSampler() {
            @Override
            public void sample(double[] lat, double[] lon, double[] out) {
                // leave everything NaN
            }
        }, HorizonProfile.DEFAULT_RADIUS_M);
        assertNull(p);
    }

    @Test
    public void ridgeDelaysEffectiveMoonriseAndDarkensTheNight() {
        // 29 Oct 2026: 79% moon rising ~2052L in the ENE
        long day = CivilDate.fromCivil(2026, 10, 29);
        HorizonProfile ridge = HorizonProfile.build(LAT, LON, ridgeEast(900, 5000),
                HorizonProfile.DEFAULT_RADIUS_M);
        PlanSettings ps = PlanSettings.defaults();
        NightReport flat = NightCalculator.compute(LAT, LON, day, NY, ps);
        NightReport terr = NightCalculator.compute(LAT, LON, day, NY, ps, ridge);

        assertFalse(flat.terrainApplied);
        assertTrue(terr.terrainApplied);
        // astronomical moonrise is the same either way
        assertEquals(flat.moonEvents.get(0).time, terr.moonEvents.get(0).time);
        // the moon clears the ridge well after it rises
        long trueRise = flat.moonEvents.get(0).time;
        long terrainRise = NightReport.NONE;
        for (NightReport.MoonEvent e : terr.terrainMoonEvents)
            if (e.rise) {
                terrainRise = e.time;
                break;
            }
        assertTrue(terrainRise != NightReport.NONE);
        long delayMin = (terrainRise - trueRise) / 60000;
        assertTrue("delay " + delayMin, delayMin > 60 && delayMin < 180);
        // less HLL with the ridge; doctrine numbers preserved
        assertTrue(terr.hllFraction < flat.hllFraction);
        assertEquals(flat.hllFraction, terr.doctrineHllFraction, 1e-12);
        assertEquals(flat.rating, terr.doctrineRating);
        // shadow outlook reports the terrain shadow
        boolean sawTerrain = false;
        for (ShadowOutlook.Segment g : terr.shadows.segments)
            sawTerrain |= g.kind == ShadowOutlook.Kind.TERRAIN;
        assertTrue(sawTerrain);
        assertTrue(terr.shadows.terrainFraction > 0);
    }

    @Test
    public void flatProfileMatchesDoctrine() {
        long day = CivilDate.fromCivil(2026, 11, 20);
        HorizonProfile flatTerrain = HorizonProfile.build(LAT, LON, ridgeEast(0, 5000),
                HorizonProfile.DEFAULT_RADIUS_M);
        NightReport a = NightCalculator.compute(LAT, LON, day, NY, PlanSettings.defaults());
        NightReport b = NightCalculator.compute(LAT, LON, day, NY, PlanSettings.defaults(),
                flatTerrain);
        // a flat skyline changes almost nothing (within a sample or two)
        assertEquals(a.hllFraction, b.hllFraction, 0.02);
        assertEquals(a.rating, b.rating);
        for (int i = 0; i < a.moonEvents.size(); i++) {
            NightReport.MoonEvent t = b.terrainMoonEvents.get(i);
            assertTrue(Math.abs(t.time - a.moonEvents.get(i).time) < 5 * 60000L);
        }
    }

    @Test
    public void shadowClassesAndText() {
        assertEquals(ShadowOutlook.Kind.NONE, ShadowOutlook.classify(-5, false, false, 0));
        assertEquals(ShadowOutlook.Kind.TERRAIN, ShadowOutlook.classify(10, false, true, 0.05));
        assertEquals(ShadowOutlook.Kind.NONE, ShadowOutlook.classify(40, true, true, 0.0002));
        assertEquals(ShadowOutlook.Kind.LONG, ShadowOutlook.classify(12, true, true, 0.01));
        assertEquals(ShadowOutlook.Kind.MEDIUM, ShadowOutlook.classify(30, true, true, 0.01));
        assertEquals(ShadowOutlook.Kind.SHORT, ShadowOutlook.classify(60, true, true, 0.1));

        assertEquals("N", ShadowOutlook.compass8(359));
        assertEquals("E", ShadowOutlook.compass8(95));
        assertEquals("SW", ShadowOutlook.compass8(222));
        assertEquals("NW", ShadowOutlook.compass8(310));

        // two hours long (moon low in the E), then 4 h short
        ShadowOutlook.Builder b = new ShadowOutlook.Builder();
        long t0 = 1_800_000_000_000L, step = 300000L;
        for (int i = 0; i < 24; i++)
            b.add(t0 + i * step, ShadowOutlook.Kind.LONG, 90, i < 12);
        for (int i = 24; i < 72; i++)
            b.add(t0 + i * step, ShadowOutlook.Kind.SHORT, 180, false);
        ShadowOutlook o = b.build(t0 + 72 * step);
        assertEquals(ShadowOutlook.Level.MODERATE, o.level);
        assertEquals(2, o.segments.size());
        List<String> lines = o.lines(DisplayZone.ZULU);
        assertTrue(lines.get(0), lines.get(0).contains("LONG shadows, pointing W"));
        assertTrue(lines.get(0), lines.get(0).contains("NVG glare looking E"));
        assertTrue(lines.get(1).contains("SHORT"));
    }

    @Test
    public void briefBlipsAreFoldedAway() {
        ShadowOutlook.Builder b = new ShadowOutlook.Builder();
        long t0 = 1_800_000_000_000L, step = 300000L;
        for (int i = 0; i < 30; i++)
            b.add(t0 + i * step, ShadowOutlook.Kind.LONG, 90, false);
        b.add(t0 + 30 * step, ShadowOutlook.Kind.MEDIUM, 120, false); // 5 min blip
        for (int i = 31; i < 60; i++)
            b.add(t0 + i * step, ShadowOutlook.Kind.LONG, 120, false);
        ShadowOutlook o = b.build(t0 + 60 * step);
        assertEquals(1, o.segments.size());
        assertEquals(ShadowOutlook.Level.HEAVY, o.level);
    }

    @Test
    public void newMoonNightHasNoShadows() {
        NightReport n = NightCalculator.compute(LAT, LON, CivilDate.fromCivil(2026, 11, 9), NY,
                PlanSettings.defaults());
        assertEquals(ShadowOutlook.Level.NONE, n.shadows.level);
        NightReport full = NightCalculator.compute(LAT, LON, CivilDate.fromCivil(2026, 11, 24),
                NY, PlanSettings.defaults());
        assertTrue(full.shadows.level != ShadowOutlook.Level.NONE);
        assertFalse(full.shadows.segments.isEmpty());
    }
}
