package com.atakmap.android.illum.plugin.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/** The bundled time zone map and civil local times with daylight saving. */
public class TzMapTest {

    static TzMap map;

    @BeforeClass
    public static void load() throws Exception {
        File f = new File("src/main/assets/tzmap.bin");
        if (!f.exists())
            f = new File("app/src/main/assets/tzmap.bin");
        map = TzMap.read(new FileInputStream(f));
    }

    @Test
    public void knownPlaces() {
        assertTrue(map.zoneCount() > 400);
        String[][] cases = {
                {"38.89", "-77.04", "America/New_York"}, // Washington DC
                {"39.96", "-83.00", "America/New_York"}, // Columbus OH
                {"41.88", "-87.63", "America/Chicago"},
                {"39.74", "-104.99", "America/Denver"},
                {"33.45", "-112.07", "America/Phoenix"}, // no DST
                {"35.67", "-109.05", "America/Denver"}, // Navajo Nation keeps DST
                {"34.05", "-118.24", "America/Los_Angeles"},
                {"61.22", "-149.90", "America/Anchorage"},
                {"21.31", "-157.86", "Pacific/Honolulu"},
                {"35.69", "51.39", "Asia/Tehran"},
                {"27.05", "57.53", "Asia/Tehran"}, // the reported grid
                {"34.53", "69.17", "Asia/Kabul"},
                {"28.61", "77.21", "Asia/Kolkata"},
                {"39.47", "75.99", "Asia/Shanghai"}, // Kashgar: official China time
                {"50.45", "30.52", "Europe/Kyiv"},
                {"51.50", "-0.12", "Europe/London"},
                {"-33.87", "151.21", "Australia/Sydney"},
        };
        for (String[] c : cases)
            assertEquals(c[2], map.zoneAt(Double.parseDouble(c[0]), Double.parseDouble(c[1])));
        // open ocean: a nautical "Etc" zone, which Illum shows as the military zone
        assertEquals("Etc/GMT+3", map.zoneAt(30, -40));
        assertNull(IllumPrefs.civilZone(map, 30, -40));
        // longitude wraps
        assertEquals(map.zoneAt(21.31, -157.86), map.zoneAt(21.31, 202.14));
        assertNull(map.zoneAt(Double.NaN, 0));
    }

    @Test
    public void localTimeFollowsDaylightSaving() {
        Map<String, Object> p = new HashMap<>();
        TimeZone ny = TimeZone.getTimeZone("America/New_York");
        double nan = Double.NaN;
        long oct31 = CivilDate.fromCivil(2026, 10, 31) * 86400000L + 12 * 3600000L;
        long nov2 = CivilDate.fromCivil(2026, 11, 2) * 86400000L + 12 * 3600000L;
        long jul1 = CivilDate.fromCivil(2026, 7, 1) * 86400000L + 12 * 3600000L;

        // the user's case: US East, no GPS fix, viewed by map center -> still EDT
        DisplayZone dc = IllumPrefs.zone(p, 38.89, -77.04, nan, nan, ny, oct31, map);
        assertEquals("Q", dc.suffix(oct31)); // EDT, UTC-4
        assertEquals("R", dc.suffix(nov2)); // EST after 1 Nov
        assertTrue(dc.describe(oct31), dc.describe(oct31).startsWith("Q = UTC−4 (EDT, daylight saving)"));
        // Phoenix: no daylight saving
        DisplayZone phx = IllumPrefs.zone(p, 33.45, -112.07, nan, nan, ny, jul1, map);
        assertEquals("T", phx.suffix(jul1));
        // Iran and Afghanistan: half-hour zones
        assertEquals("C*", IllumPrefs.zone(p, 27.05, 57.53, nan, nan, ny, oct31, map).suffix(oct31));
        assertEquals("D*", IllumPrefs.zone(p, 34.53, 69.17, nan, nan, ny, oct31, map).suffix(oct31));
        // military option ignores daylight saving
        p.put(IllumPrefs.TIME_ZONE, "military");
        assertEquals("R", IllumPrefs.zone(p, 38.89, -77.04, nan, nan, ny, oct31, map).suffix(oct31));
        // 1.0's "ao" means military; "device" means local
        p.put(IllumPrefs.TIME_ZONE, "ao");
        assertEquals("R", IllumPrefs.zone(p, 38.89, -77.04, nan, nan, ny, oct31, map).suffix(oct31));
        p.put(IllumPrefs.TIME_ZONE, "device");
        assertEquals("Q", IllumPrefs.zone(p, 38.89, -77.04, nan, nan, ny, oct31, map).suffix(oct31));
    }

    @Test
    public void sunriseMatchesTheClockOnTheWall() {
        // 31 Oct 2026, Washington DC: sunrise is about 0735 EDT, not 0635
        DisplayZone dc = IllumPrefs.zone(new HashMap<String, Object>(), 38.89, -77.04,
                Double.NaN, Double.NaN, TimeZone.getTimeZone("UTC"), 0, map);
        NightReport n = NightCalculator.compute(38.89, -77.04, CivilDate.fromCivil(2026, 10, 30),
                dc, PlanSettings.defaults());
        assertEquals("0734Q", Formats.time(n.sunrise, dc));
        assertEquals("1810Q", Formats.time(n.sunset, dc));
    }

    @Test
    public void clocksChangeTonight() {
        // US daylight saving ends 1 Nov 2026 at 0200 EDT = 0600Z
        DisplayZone dc = DisplayZone.localAtPoint(TimeZone.getTimeZone("America/New_York"));
        NightReport n = NightCalculator.compute(38.89, -77.04, CivilDate.fromCivil(2026, 10, 31),
                dc, PlanSettings.defaults());
        long change = dc.offsetChange(n.spanStart, n.spanEnd);
        assertEquals((CivilDate.fromCivil(2026, 11, 1) * 24 + 6) * 3600000L, change);
        assertEquals("Q", dc.suffix(n.sunset));
        assertEquals("R", dc.suffix(n.sunrise));
        // no change the night before
        NightReport prev = NightCalculator.compute(38.89, -77.04,
                CivilDate.fromCivil(2026, 10, 30), dc, PlanSettings.defaults());
        assertEquals(NightReport.NONE, dc.offsetChange(prev.spanStart, prev.spanEnd));
        assertEquals("\u2022 Clocks go back 1 h at 0200Q (0600Z), to 0100R. Times after that"
                + " are in R (UTC\u22125).\n", Formats.clockChange(n, dc));
        assertNull(Formats.clockChange(prev, dc));
    }
}
