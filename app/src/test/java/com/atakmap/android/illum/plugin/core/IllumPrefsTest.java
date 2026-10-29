package com.atakmap.android.illum.plugin.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class IllumPrefsTest {

    @Test
    public void defaultsWhenEmpty() {
        PlanSettings s = IllumPrefs.settings(new HashMap<String, Object>());
        assertSame(LightStandard.USAF, s.standard);
        assertEquals(PlanSettings.Mission.NVG, s.mission);
        assertEquals(PlanSettings.WindowMode.NAUTICAL_DARK, s.windowMode);
        assertEquals(PlanSettings.Sky.CLEAR, s.sky);
        assertEquals(0.10, s.tolerance, 1e-9);
        assertFalse(s.includeNightSky);
        assertEquals(30, IllumPrefs.planNights(null));
        assertTrue(IllumPrefs.showZulu(null));
    }

    @Test
    public void parsesAllChoices() {
        Map<String, Object> p = new HashMap<>();
        p.put(IllumPrefs.STANDARD, "army");
        p.put(IllumPrefs.MISSION, "conceal");
        p.put(IllumPrefs.WINDOW, "custom");
        p.put(IllumPrefs.WINDOW_START, "21:30");
        p.put(IllumPrefs.WINDOW_END, "0315");
        p.put(IllumPrefs.TOLERANCE, "20");
        p.put(IllumPrefs.SKY, "stratus");
        p.put(IllumPrefs.NIGHT_SKY, true);
        p.put(IllumPrefs.PLAN_NIGHTS, "45");
        PlanSettings s = IllumPrefs.settings(p);
        assertSame(LightStandard.ARMY, s.standard);
        assertEquals(PlanSettings.Mission.CONCEAL, s.mission);
        assertEquals(PlanSettings.WindowMode.CUSTOM, s.windowMode);
        assertEquals(21 * 60 + 30, s.customStartMin);
        assertEquals(3 * 60 + 15, s.customEndMin);
        assertEquals(0.20, s.tolerance, 1e-9);
        assertEquals(PlanSettings.Sky.DARK_STRATUS, s.sky);
        assertTrue(s.includeNightSky);
        assertEquals(45, IllumPrefs.planNights(p));
    }

    @Test
    public void customStandardAndGarbage() {
        Map<String, Object> p = new HashMap<>();
        p.put(IllumPrefs.STANDARD, "custom");
        p.put(IllumPrefs.CUSTOM_METHOD, "moon");
        p.put(IllumPrefs.CUSTOM_ILLUM, "35");
        p.put(IllumPrefs.CUSTOM_ALT, "abc"); // garbage -> default 30
        p.put(IllumPrefs.WINDOW_START, "99:99"); // invalid -> default
        PlanSettings s = IllumPrefs.settings(p);
        assertEquals(LightStandard.Method.MOON_RULE, s.standard.method);
        assertEquals(35, s.standard.minIllumPercent, 1e-9);
        assertEquals(30, s.standard.minMoonAltDeg, 1e-9);
        assertEquals(22 * 60, s.customStartMin);

        p.put(IllumPrefs.CUSTOM_METHOD, "lux");
        p.put(IllumPrefs.CUSTOM_LUX, "5");
        assertEquals(0.005, IllumPrefs.settings(p).standard.luxThreshold, 1e-12);
    }

    @Test
    public void zones() {
        Map<String, Object> p = new HashMap<>();
        TimeZone ny = TimeZone.getTimeZone("America/New_York");
        assertEquals("L", IllumPrefs.zone(p, -77, ny).suffix(0));
        p.put(IllumPrefs.TIME_ZONE, "zulu");
        assertTrue(IllumPrefs.zone(p, -77, ny).isUtc());
        p.put(IllumPrefs.TIME_ZONE, "ao");
        assertEquals("R", IllumPrefs.zone(p, -77, ny).suffix(0));
        p.put(IllumPrefs.TIME_ZONE, "manual");
        p.put(IllumPrefs.MANUAL_OFFSET, "4.5");
        assertEquals(270 * 60000L, IllumPrefs.zone(p, 0, ny).offsetMillis(0));
    }

    @Test
    public void hhmm() {
        assertEquals(0, IllumPrefs.hhmmToMinutes("0000", -1));
        assertEquals(23 * 60 + 59, IllumPrefs.hhmmToMinutes("2359L", -1));
        assertEquals(9 * 60 + 5, IllumPrefs.hhmmToMinutes("905", -1));
        assertEquals(-1, IllumPrefs.hhmmToMinutes("12", -1));
        assertEquals(-1, IllumPrefs.hhmmToMinutes("2460", -1));
        assertEquals(-1, IllumPrefs.hhmmToMinutes(null, -1));
    }
}
