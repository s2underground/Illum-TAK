package com.atakmap.android.illum.plugin.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.TimeZone;

public class NightCalculatorTest {

    static final double DC_LAT = 38.89, DC_LON = -77.04;

    @Test
    public void illuminanceMatchesPublishedRuleOfThumb() {
        // USAF: 2.2 millilux is "roughly a 20% moon disk at 30 degrees or higher"
        double elong = Math.toDegrees(Math.acos(1 - 2 * 0.20)); // 20% illuminated
        double lux = Illuminance.moonLux(30, elong, Illuminance.MEAN_MOON_DIST_KM);
        assertEquals(0.0022, lux, 0.0003);
        // Full moon at the zenith: a few tenths of a lux
        double full = Illuminance.moonLux(90, 180, Illuminance.MEAN_MOON_DIST_KM);
        assertTrue(full > 0.25 && full < 0.45);
        // Nautical twilight: a few millilux from the sun
        double naut = Illuminance.sunLux(-12);
        assertTrue(naut > 0.001 && naut < 0.01);
        // Sun at zenith ~ 1e5 lux
        assertTrue(Illuminance.sunLux(90) > 9e4);
        // Below the horizon / new moon -> nothing
        assertEquals(0, Illuminance.moonLux(-30, 120, 384400), 0);
        assertEquals(0, Illuminance.moonLux(45, 0, 384400), 0);
        // Perigee is brighter than apogee
        assertTrue(Illuminance.moonLux(45, 180, 357000) > Illuminance.moonLux(45, 180, 406000));
    }

    @Test
    public void typicalNightHasOrderedTimes() {
        NightReport n = NightCalculator.compute(DC_LAT, DC_LON,
                CivilDate.fromCivil(2026, 10, 29), DisplayZone.of(TimeZone.getTimeZone("America/New_York")),
                PlanSettings.defaults());
        assertTrue(n.sunset < n.eect && n.eect < n.eent && n.eent < n.eeat);
        assertTrue(n.eeat < n.bmat && n.bmat < n.bmnt && n.bmnt < n.bmct && n.bmct < n.sunrise);
        assertEquals(n.eent, n.windowStart);
        assertEquals(n.bmnt, n.windowEnd);
        assertTrue(n.hasWindow());
        assertFalse(Double.isNaN(n.hllFraction));
        assertNotEquals(NightReport.Rating.NONE, n.rating);
        // EENT about 50-60 minutes after sunset at 39N in autumn
        long gap = (n.eent - n.sunset) / 60000;
        assertTrue("gap " + gap, gap > 45 && gap < 65);
    }

    @Test
    public void fullMoonNightIsHllAndNewMoonNightIsLll() {
        DisplayZone z = DisplayZone.nautical(DC_LON);
        PlanSettings ps = PlanSettings.defaults();
        // Full moon 24 Nov 2026 (approx), new moon 9 Nov 2026 (approx)
        NightReport full = NightCalculator.compute(DC_LAT, DC_LON,
                CivilDate.fromCivil(2026, 11, 24), z, ps);
        NightReport dark = NightCalculator.compute(DC_LAT, DC_LON,
                CivilDate.fromCivil(2026, 11, 9), z, ps);
        assertTrue(full.illumPercent > 95);
        assertTrue(dark.illumPercent < 5);
        assertEquals(NightReport.LightLevel.HLL, full.level);
        assertEquals(NightReport.Rating.GREEN, full.rating);
        assertEquals(NightReport.LightLevel.LLL, dark.level);
        assertEquals(NightReport.Rating.RED, dark.rating);

        // Concealment profile flips the colors
        PlanSettings conceal = ps.toBuilder().mission(PlanSettings.Mission.CONCEAL).build();
        assertEquals(NightReport.Rating.GREEN, NightCalculator.compute(DC_LAT, DC_LON,
                CivilDate.fromCivil(2026, 11, 9), z, conceal).rating);
        assertEquals(NightReport.Rating.RED, NightCalculator.compute(DC_LAT, DC_LON,
                CivilDate.fromCivil(2026, 11, 24), z, conceal).rating);
    }

    @Test
    public void heavyCloudTurnsFullMoonIntoLowLight() {
        DisplayZone z = DisplayZone.nautical(DC_LON);
        PlanSettings stratus = PlanSettings.defaults().toBuilder()
                .sky(PlanSettings.Sky.DARK_STRATUS).build();
        NightReport n = NightCalculator.compute(DC_LAT, DC_LON,
                CivilDate.fromCivil(2026, 11, 24), z, stratus);
        // full moon ~0.2-0.3 lux / 10 is still above 2.2 mlux when high; but a
        // low winter moon early and late should drop some of the night below
        assertTrue(n.peakLux < 0.05);
    }

    @Test
    public void customWindowCrossesMidnight() {
        DisplayZone z = DisplayZone.nautical(DC_LON);
        long day = CivilDate.fromCivil(2026, 10, 29);
        PlanSettings ps = PlanSettings.defaults().toBuilder()
                .window(PlanSettings.WindowMode.CUSTOM).customWindow(22 * 60, 4 * 60).build();
        NightReport n = NightCalculator.compute(DC_LAT, DC_LON, day, z, ps);
        assertEquals(6 * 3600000L, n.windowEnd - n.windowStart);
        assertEquals(CivilDate.localToUtc(day, 22 * 60, z), n.windowStart);

        // A window that starts after midnight belongs to the next morning
        PlanSettings early = ps.toBuilder().customWindow(60, 4 * 60).build();
        NightReport m = NightCalculator.compute(DC_LAT, DC_LON, day, z, early);
        assertEquals(CivilDate.localToUtc(day + 1, 60, z), m.windowStart);
        assertEquals(3 * 3600000L, m.windowEnd - m.windowStart);
    }

    @Test
    public void midnightSunHasNoDarknessWindow() {
        // Tromso, 21 June: sun never sets
        NightReport n = NightCalculator.compute(69.65, 18.96, CivilDate.fromCivil(2027, 6, 21),
                DisplayZone.nautical(18.96), PlanSettings.defaults());
        assertTrue(n.sunUpAllSpan);
        assertTrue(n.noNauticalDarkness);
        assertEquals(NightReport.NONE, n.sunset);
        assertEquals(NightReport.NONE, n.eent);
        assertFalse(n.hasWindow());
        assertEquals(NightReport.Rating.NONE, n.rating);
    }

    @Test
    public void polarNightStillHasNauticalTwilightAtTromso() {
        NightReport n = NightCalculator.compute(69.65, 18.96, CivilDate.fromCivil(2026, 12, 21),
                DisplayZone.nautical(18.96), PlanSettings.defaults());
        assertEquals(NightReport.NONE, n.sunset);
        assertTrue(n.sunDownAllSpan);
        assertTrue(n.hasWindow());
    }

    @Test
    public void armyRuleNeedsBothIllumAndElevation() {
        LightStandard a = LightStandard.ARMY;
        assertTrue(a.isHighLight(0, 0, 35, 30));
        assertFalse(a.isHighLight(1, 0, 25, 90)); // low moon, even if bright
        assertFalse(a.isHighLight(1, 0, 60, 20)); // high but thin
        assertTrue(a.isHighLight(0, 0.01, -5, 0)); // twilight
        assertTrue(LightStandard.USAF.isHighLight(0.0022, 0, 0, 0));
        assertFalse(LightStandard.USN.isHighLight(0.0021, 0, 90, 100));
    }

    @Test
    public void thirtyNightPlanIsFast() {
        long t0 = System.nanoTime();
        List<NightReport> r = NightCalculator.range(DC_LAT, DC_LON,
                CivilDate.fromCivil(2026, 10, 29), 30, DisplayZone.nautical(DC_LON),
                PlanSettings.defaults());
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertEquals(30, r.size());
        System.out.println("30-night plan computed in " + ms + " ms");
        assertTrue(ms < 5000);
        // a lunar month contains green, red and amber nights at mid-latitudes
        boolean g = false, red = false, amb = false;
        for (NightReport n : r) {
            g |= n.rating == NightReport.Rating.GREEN;
            red |= n.rating == NightReport.Rating.RED;
            amb |= n.rating == NightReport.Rating.AMBER;
        }
        assertTrue(g && red && amb);
    }

    @Test
    public void phaseNames() {
        assertEquals("New", NightCalculator.phaseName(0.01, true));
        assertEquals("Full", NightCalculator.phaseName(0.99, false));
        assertEquals("First Quarter", NightCalculator.phaseName(0.5, true));
        assertEquals("Last Quarter", NightCalculator.phaseName(0.5, false));
        assertEquals("Waxing Crescent", NightCalculator.phaseName(0.2, true));
        assertEquals("Waning Gibbous", NightCalculator.phaseName(0.8, false));
    }
}
