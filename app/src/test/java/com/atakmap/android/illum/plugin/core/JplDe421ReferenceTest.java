package com.atakmap.android.illum.plugin.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
 * Checks Illum against NASA JPL's DE421 planetary ephemeris (via Skyfield
 * 1.55): 10 sites from 55S to 70N (including Ushuaia, Reykjavik, Tromso and
 * Fairbanks), 30 dates from Nov 2026 to 2036. USNO event conventions. See
 * jpl_de421_reference.csv.
 */
public class JplDe421ReferenceTest {

    static final long SUN_TOL = 60_000; // 1 minute (typical error is under 6 s)
    /**
     * Nights where the sun only just dips below a twilight level (EENT and BMNT
     * close together, high latitude): its altitude changes so slowly that a
     * 0.003 deg difference moves the crossing by about a minute.
     */
    static final long GRAZING_SUN_TOL = 180_000;
    static final long MOON_TOL = 120_000; // 2 minutes
    static final double ILLUM_TOL = 0.005; // 0.5 percentage points

    static final class Row {
        String name, kind;
        double lat, lon, value;
        long day, t;
    }

    static List<Row> load() throws Exception {
        List<Row> rows = new ArrayList<>();
        InputStream in = JplDe421ReferenceTest.class.getClassLoader()
                .getResourceAsStream("jpl_de421_reference.csv");
        assertTrue("reference file missing", in != null);
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty())
                    continue;
                String[] p = line.split(",", -1);
                Row r = new Row();
                r.name = p[0];
                r.lat = Double.parseDouble(p[1]);
                r.lon = Double.parseDouble(p[2]);
                r.day = Long.parseLong(p[3]);
                r.kind = p[4];
                r.t = "NONE".equals(p[5]) ? NightReport.NONE : Long.parseLong(p[5]);
                r.value = p[6].isEmpty() ? Double.NaN : Double.parseDouble(p[6]);
                rows.add(r);
            }
        }
        return rows;
    }

    @Test
    public void matchesJplDe421() throws Exception {
        Map<String, List<Row>> nights = new LinkedHashMap<>();
        for (Row r : load())
            nights.computeIfAbsent(r.name + "/" + r.day, k -> new ArrayList<>()).add(r);
        assertEquals(300, nights.size());

        long worstSun = 0, worstMoon = 0;
        double worstIllum = 0;
        int events = 0;
        for (List<Row> rows : nights.values()) {
            Row f = rows.get(0);
            NightReport n = NightCalculator.compute(f.lat, f.lon, f.day,
                    DisplayZone.nautical(f.lon), PlanSettings.defaults());
            String where = f.name + " day " + f.day + ": ";
            List<Long> rises = new ArrayList<>(), sets = new ArrayList<>();
            for (Row r : rows) {
                switch (r.kind) {
                    case "sunset":
                        worstSun = Math.max(worstSun, check(where + "sunset", r.t, n.sunset, sunTol(n)));
                        break;
                    case "eent":
                        worstSun = Math.max(worstSun, check(where + "EENT", r.t, n.eent, sunTol(n)));
                        break;
                    case "bmnt":
                        worstSun = Math.max(worstSun, check(where + "BMNT", r.t, n.bmnt, sunTol(n)));
                        break;
                    case "sunrise":
                        worstSun = Math.max(worstSun, check(where + "sunrise", r.t, n.sunrise, sunTol(n)));
                        break;
                    case "moonrise":
                        rises.add(r.t);
                        break;
                    case "moonset":
                        sets.add(r.t);
                        break;
                    case "illum": {
                        double k = Ephemeris.compute(r.t, r.lat, r.lon).illumFraction;
                        double d = Math.abs(k - r.value);
                        assertTrue(where + "illum off by " + d, d <= ILLUM_TOL);
                        worstIllum = Math.max(worstIllum, d);
                        break;
                    }
                    default:
                        throw new AssertionError("unknown kind " + r.kind);
                }
                events++;
            }
            worstMoon = Math.max(worstMoon, moon(where + "moonrise", rises, n, true));
            worstMoon = Math.max(worstMoon, moon(where + "moonset", sets, n, false));
        }
        System.out.println("JPL DE421 rows checked: " + events + ", worst sun " + worstSun / 1000
                + " s, worst moon " + worstMoon / 1000 + " s, worst illum "
                + String.format(java.util.Locale.US, "%.2f", worstIllum * 100) + " pts");
    }

    static long sunTol(NightReport n) {
        boolean grazing = n.eent != NightReport.NONE && n.bmnt != NightReport.NONE
                && n.bmnt - n.eent < 3 * 3600000L;
        return grazing ? GRAZING_SUN_TOL : SUN_TOL;
    }

    /** Sun events: must agree on presence, and within tolerance. */
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

    /** Moon events: one-to-one within 2 minutes, ignoring events right at the span edges. */
    private static long moon(String what, List<Long> expected, NightReport n, boolean rise) {
        List<Long> actual = new ArrayList<>();
        for (NightReport.MoonEvent e : n.moonEvents)
            if (e.rise == rise)
                actual.add(e.time);
        long worst = 0;
        for (long e : expected) {
            long best = Long.MAX_VALUE;
            for (long a : actual)
                best = Math.min(best, Math.abs(a - e));
            if (best == Long.MAX_VALUE && nearEdge(e, n))
                continue;
            assertTrue(what + " missing/off by " + best / 1000 + " s", best <= MOON_TOL);
            worst = Math.max(worst, best);
        }
        for (long a : actual) {
            long best = Long.MAX_VALUE;
            for (long e : expected)
                best = Math.min(best, Math.abs(a - e));
            assertTrue(what + " extra event", best <= MOON_TOL || nearEdge(a, n));
        }
        return worst;
    }

    private static boolean nearEdge(long t, NightReport n) {
        return Math.abs(t - n.spanStart) < MOON_TOL || Math.abs(t - n.spanEnd) < MOON_TOL;
    }
}
