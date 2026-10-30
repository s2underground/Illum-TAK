package com.atakmap.android.illum.plugin.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.TimeZone;

/**
 * Regression test for the 1.0 bug found on Oct 30, 2026: nights were framed
 * noon-to-noon in the DEVICE time zone, so a device far from the area of
 * interest (e.g. US Eastern viewing Iran) cut the night short (no BMNT or
 * sunrise, window ending at device noon) and rated it wrong.
 *
 * Every night must come out identical (in UTC) whatever display zone is
 * used; only the labels change.
 */
public class DeviceTimeZoneTest {

    static final double[][] SITES = {
            {27.05, 57.53}, // southern Iran (the reported case)
            {38.89, -77.04}, // Washington DC
            {34.53, 69.17}, // Kabul
            {-33.87, 151.21}, // Sydney
            {21.31, -157.86}, // Honolulu
            {64.84, -147.72}, // Fairbanks
            {-1.29, 36.82}, // Nairobi
            {51.5, -0.1}, // London
            {13.4, 144.8}, // Guam
            {-17.7, -149.4}, // Tahiti
    };

    @Test
    public void nightIsTheSameInEveryDeviceTimeZone() {
        long[] days = {
                CivilDate.fromCivil(2026, 10, 30), CivilDate.fromCivil(2027, 1, 15),
                CivilDate.fromCivil(2027, 6, 21)
        };
        for (double[] s : SITES) {
            for (long day : days) {
                NightReport ref = NightCalculator.compute(s[0], s[1], day,
                        DisplayZone.nautical(s[1]), PlanSettings.defaults());
                for (int off = -12; off <= 14; off++) {
                    DisplayZone z = DisplayZone.fixedMinutes(off * 60);
                    NightReport n = NightCalculator.compute(s[0], s[1], day, z,
                            PlanSettings.defaults());
                    String w = s[0] + "," + s[1] + " day " + day + " UTC" + off + ": ";
                    assertEquals(w + "sunset", ref.sunset, n.sunset);
                    assertEquals(w + "EENT", ref.eent, n.eent);
                    assertEquals(w + "BMNT", ref.bmnt, n.bmnt);
                    assertEquals(w + "sunrise", ref.sunrise, n.sunrise);
                    assertEquals(w + "window start", ref.windowStart, n.windowStart);
                    assertEquals(w + "window end", ref.windowEnd, n.windowEnd);
                    assertEquals(w + "moon events", ref.moonEvents.size(), n.moonEvents.size());
                    assertEquals(w + "HLL", ref.hllFraction, n.hllFraction, 0.0);
                    assertEquals(w + "rating", ref.rating, n.rating);
                }
            }
        }
    }

    @Test
    public void reportedCaseUsEasternViewingIran() {
        // device in US Eastern time, location in southern Iran (40R CS 53245 04352)
        DisplayZone eastern = DisplayZone.of(TimeZone.getTimeZone("America/New_York"));
        long day = CivilDate.fromCivil(2026, 10, 30);
        NightReport n = NightCalculator.compute(27.05, 57.53, day, eastern,
                PlanSettings.defaults());
        assertTrue("BMNT must exist", n.bmnt != NightReport.NONE);
        assertTrue("sunrise must exist", n.sunrise != NightReport.NONE);
        assertEquals(n.eent, n.windowStart);
        assertEquals(n.bmnt, n.windowEnd);
        // about 11 hours of nautical darkness at 27N in late October
        long hours = (n.windowEnd - n.windowStart) / 3600000L;
        assertTrue("window hours " + hours, hours >= 10 && hours <= 12);
        assertEquals(NightReport.Rating.AMBER, n.rating);
    }

    @Test
    public void tonightFollowsTheLocationNotTheDevice() {
        // 30 Oct 2026 17:39Z: evening in Iran (21:09 local) -> night of 30 Oct there
        long now = CivilDate.fromCivil(2026, 10, 30) * 86400000L + 17 * 3600000L + 39 * 60000L;
        assertEquals(CivilDate.fromCivil(2026, 10, 30), NightCalculator.nightOf(now, 57.53));
        // same instant in Hawaii (07:39 local): still last night there (29 Oct)
        assertEquals(CivilDate.fromCivil(2026, 10, 29), NightCalculator.nightOf(now, -157.86));
    }

    @Test
    public void customWindowBelongsToThisNightInAnyZone() {
        // 2200-0400 in the display zone; whichever zone, the window lies inside the night frame
        long day = CivilDate.fromCivil(2026, 10, 30);
        PlanSettings ps = PlanSettings.defaults().toBuilder()
                .window(PlanSettings.WindowMode.CUSTOM).customWindow(22 * 60, 4 * 60).build();
        for (int off = -12; off <= 14; off++) {
            NightReport n = NightCalculator.compute(27.05, 57.53, day,
                    DisplayZone.fixedMinutes(off * 60), ps);
            assertEquals(6 * 3600000L, n.windowEnd - n.windowStart);
            long mid = n.windowStart + 3 * 3600000L;
            // a window centered exactly on noon at the location sits on the frame edge
            assertTrue("UTC" + off, mid >= n.spanStart && mid < n.spanEnd);
        }
        // in the location's own zone it is 2200-0400 local there
        NightReport ao = NightCalculator.compute(27.05, 57.53, day, DisplayZone.nautical(57.53),
                ps);
        assertEquals(CivilDate.localToUtc(day, 22 * 60, DisplayZone.nautical(57.53)),
                ao.windowStart);
    }
}
