package com.atakmap.android.illum.plugin.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
        long summer = CivilDate.fromCivil(2026, 7, 1) * 86400000L;
        long winter = CivilDate.fromCivil(2026, 12, 1) * 86400000L;
        double nan = Double.NaN;

        // default: local time at the point. Far from the device -> military zone.
        DisplayZone iran = IllumPrefs.zone(p, 27.05, 57.53, 38.89, -77.04, ny, summer);
        assertEquals("D", iran.suffix(summer));
        assertEquals(4 * 3600000L, iran.offsetMillis(summer));
        // no GPS fix -> military zone
        assertEquals("R", IllumPrefs.zone(p, 38.89, -77.04, nan, nan, ny, summer).suffix(0));
        // at the device (My Pos) -> the device clock, daylight saving included
        DisplayZone here = IllumPrefs.zone(p, 38.89, -77.04, 38.89, -77.04, ny, summer);
        assertEquals(-4 * 3600000L, here.offsetMillis(summer));
        assertEquals("Q", here.suffix(summer));
        assertEquals("R", here.suffix(winter));
        // 50 km away still counts as here; 300 km away does not
        assertEquals("Q", IllumPrefs.zone(p, 39.3, -77.2, 38.89, -77.04, ny, summer)
                .suffix(summer));
        assertEquals("R", IllumPrefs.zone(p, 41.5, -77.04, 38.89, -77.04, ny, summer)
                .suffix(summer));
        // device kept on Zulu while deployed: not the local time there
        TimeZone utc = TimeZone.getTimeZone("UTC");
        assertEquals("C", IllumPrefs.zone(p, 33.3, 44.4, 33.3, 44.4, utc, summer)
                .suffix(summer));

        // 1.0 settings: "device" -> auto (no map here: military zone), "ao" -> military
        assertEquals("auto", IllumPrefs.migrateZone("device"));
        assertEquals("military", IllumPrefs.migrateZone("ao"));
        assertNull(IllumPrefs.migrateZone("auto"));
        p.put(IllumPrefs.TIME_ZONE, "ao");
        assertEquals("R", IllumPrefs.zone(p, 38.89, -77.04, 38.89, -77.04, ny, summer)
                .suffix(summer));

        p.put(IllumPrefs.TIME_ZONE, "zulu");
        assertTrue(IllumPrefs.zone(p, 0, -77, nan, nan, ny, summer).isUtc());
        p.put(IllumPrefs.TIME_ZONE, "manual");
        p.put(IllumPrefs.MANUAL_OFFSET, "3.5");
        DisplayZone m = IllumPrefs.zone(p, 27.05, 57.53, nan, nan, ny, summer);
        assertEquals(210 * 60000L, m.offsetMillis(0));
        assertEquals("C*", m.suffix(0));
    }

    @Test
    public void zoneLetters() {
        assertEquals("Z", DisplayZone.letterFor(0));
        assertEquals("D", DisplayZone.letterFor(4 * 3600000L));
        assertEquals("Q", DisplayZone.letterFor(-4 * 3600000L));
        assertEquals("C*", DisplayZone.letterFor(210 * 60000L)); // Iran
        assertEquals("E*", DisplayZone.letterFor(330 * 60000L)); // India
        assertEquals("E*", DisplayZone.letterFor(345 * 60000L)); // Nepal
        assertEquals("P*", DisplayZone.letterFor(-210 * 60000L)); // Newfoundland
        assertEquals("M", DisplayZone.letterFor(12 * 3600000L));
        assertEquals("Y", DisplayZone.letterFor(-12 * 3600000L));
        assertEquals("M*", DisplayZone.letterFor(13 * 3600000L)); // Tonga
        // every displayed time says which zone it is in
        DisplayZone d = DisplayZone.nautical(57.53);
        assertTrue(d.describe(0).startsWith("D = UTC+4"));
        assertTrue(Formats.time(13 * 3600000L + 37 * 60000L, d).equals("1737D"));
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
