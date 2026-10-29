package com.atakmap.android.illum.plugin.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares computed events against an independent ephemeris (PyEphem 4.2,
 * VSOP87/ELP2000 based) for 8 places from the equator to 70N, 8 dates each,
 * including polar night and midnight-sun cases. Reference generated with
 * USNO conventions; see pyephem_reference.csv.
 */
public class ReferenceEphemerisTest {

    /** Sun events: within 1 minute. */
    static final long SUN_TOL = 60_000;
    /** Moon events: within 2 minutes. */
    static final long MOON_TOL = 120_000;

    static final class Row {
        String name;
        double lat, lon;
        long day;
        String kind;
        long t;
    }

    static List<Row> load() throws Exception {
        List<Row> rows = new ArrayList<>();
        InputStream in = ReferenceEphemerisTest.class.getClassLoader()
                .getResourceAsStream("pyephem_reference.csv");
        assertTrue("reference file missing", in != null);
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty())
                    continue;
                String[] p = line.split(",");
                Row r = new Row();
                r.name = p[0];
                r.lat = Double.parseDouble(p[1]);
                r.lon = Double.parseDouble(p[2]);
                r.day = Long.parseLong(p[3]);
                r.kind = p[4];
                r.t = "NONE".equals(p[5]) ? NightReport.NONE : Long.parseLong(p[5]);
                rows.add(r);
            }
        }
        return rows;
    }

    @Test
    public void sunAndMoonEventsMatchReference() throws Exception {
        Map<String, List<Row>> nights = new LinkedHashMap<>();
        for (Row r : load())
            nights.computeIfAbsent(r.name + "/" + r.day, k -> new ArrayList<>()).add(r);
        assertTrue(nights.size() >= 60);

        int checked = 0;
        long worstSun = 0, worstMoon = 0;
        for (List<Row> rows : nights.values()) {
            Row f = rows.get(0);
            NightReport n = NightCalculator.compute(f.lat, f.lon, f.day,
                    DisplayZone.nautical(f.lon), PlanSettings.defaults());
            String where = f.name + " day " + f.day + ": ";
            List<Long> rises = new ArrayList<>(), sets = new ArrayList<>();
            for (Row r : rows) {
                switch (r.kind) {
                    case "sunset":
                        worstSun = Math.max(worstSun, check(where + "sunset", r.t, n.sunset, SUN_TOL));
                        break;
                    case "eent":
                        worstSun = Math.max(worstSun, check(where + "EENT", r.t, n.eent, SUN_TOL));
                        break;
                    case "bmnt":
                        worstSun = Math.max(worstSun, check(where + "BMNT", r.t, n.bmnt, SUN_TOL));
                        break;
                    case "sunrise":
                        worstSun = Math.max(worstSun, check(where + "sunrise", r.t, n.sunrise, SUN_TOL));
                        break;
                    case "moonrise":
                        rises.add(r.t);
                        break;
                    case "moonset":
                        sets.add(r.t);
                        break;
                    default:
                        fail("unknown kind " + r.kind);
                }
                checked++;
            }
            worstMoon = Math.max(worstMoon, matchMoon(where + "moonrise", rises, n, true));
            worstMoon = Math.max(worstMoon, matchMoon(where + "moonset", sets, n, false));
        }
        System.out.println("Reference events checked: " + checked + ", worst sun "
                + worstSun / 1000 + " s, worst moon " + worstMoon / 1000 + " s");
    }

    private static long check(String what, long expected, long actual, long tol) {
        if (expected == NightReport.NONE || actual == NightReport.NONE) {
            assertEquals(what + " presence", expected == NightReport.NONE,
                    actual == NightReport.NONE);
            return 0;
        }
        long d = Math.abs(expected - actual);
        assertTrue(what + " off by " + d / 1000 + " s", d <= tol);
        return d;
    }

    private static long matchMoon(String what, List<Long> expected, NightReport n, boolean rise) {
        List<Long> actual = new ArrayList<>();
        for (NightReport.MoonEvent e : n.moonEvents)
            if (e.rise == rise)
                actual.add(e.time);
        long worst = 0;
        for (long e : expected) {
            long best = Long.MAX_VALUE;
            for (long a : actual)
                best = Math.min(best, Math.abs(a - e));
            boolean nearEdge = Math.abs(e - n.spanStart) < MOON_TOL
                    || Math.abs(e - n.spanEnd) < MOON_TOL;
            if (best == Long.MAX_VALUE && nearEdge)
                continue;
            assertTrue(what + " missing/off by " + best / 1000 + " s", best <= MOON_TOL);
            worst = Math.max(worst, best);
        }
        for (long a : actual) {
            long best = Long.MAX_VALUE;
            for (long e : expected)
                best = Math.min(best, Math.abs(a - e));
            boolean nearEdge = Math.abs(a - n.spanStart) < MOON_TOL
                    || Math.abs(a - n.spanEnd) < MOON_TOL;
            assertTrue(what + " extra event", best <= MOON_TOL || nearEdge);
        }
        return worst;
    }

    @Test
    public void moonIlluminationMatchesReference() {
        // PyEphem moon_phase at these instants (fraction illuminated)
        long[] t = {1793318400000L, 1800000000000L, 1700000000000L, 1767225600000L};
        double[] ref = {0.8078, 0.4467, 0.0280, 0.9154};
        for (int i = 0; i < t.length; i++) {
            SkyState s = Ephemeris.compute(t[i], 38.9, -77.0);
            assertEquals("illum at " + t[i], ref[i], s.illumFraction, 0.005);
        }
    }

    @Test
    public void positionsMatchReference() {
        // PyEphem alt/az (pressure 0) for 2026-10-29T12:00Z... and others
        SkyState a = Ephemeris.compute(1793318400000L, 35.0, 69.0);
        assertEquals(-21.5681, a.sunAltDeg, 0.02);
        assertEquals(91.9707, a.sunAzDeg, 0.02);
        assertEquals(72.1419, a.moonAltDeg, 0.02);
        assertEquals(250.7233, a.moonAzDeg, 0.05);
        SkyState b = Ephemeris.compute(1767225600000L, 38.9, -77.0);
        assertEquals(53.6140, b.moonAltDeg, 0.02);
        assertEquals(98.1688, b.moonAzDeg, 0.02);
    }
}
