package com.atakmap.android.illum.plugin.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.TimeZone;

public class CivilDateZoneTest {

    @Test
    public void civilRoundTrip() {
        assertEquals(0, CivilDate.fromCivil(1970, 1, 1));
        assertEquals(20755, CivilDate.fromCivil(2026, 10, 29));
        for (long d = -1000; d < 60000; d += 37) {
            int[] c = CivilDate.toCivil(d);
            assertEquals(d, CivilDate.fromCivil(c[0], c[1], c[2]));
        }
        assertArrayEquals(new int[] {2028, 2, 29}, CivilDate.toCivil(CivilDate.fromCivil(2028, 2, 29)));
        assertEquals("Thu 29 OCT 26", CivilDate.label(20755));
    }

    @Test
    public void nauticalZoneLetters() {
        assertEquals("Z", DisplayZone.nautical(3).suffix(0));
        assertEquals("R", DisplayZone.nautical(-77).suffix(0)); // UTC-5
        assertEquals("E", DisplayZone.nautical(69).suffix(0)); // UTC+5 (Kabul area)
        assertEquals("K", DisplayZone.nautical(151).suffix(0)); // UTC+10
        assertEquals("M", DisplayZone.nautical(179).suffix(0));
        assertEquals("Y", DisplayZone.nautical(-179).suffix(0));
        assertEquals(-5 * 3600000L, DisplayZone.nautical(-77).offsetMillis(0));
    }

    @Test
    public void localToUtcHandlesDaylightSaving() {
        DisplayZone ny = DisplayZone.of(TimeZone.getTimeZone("America/New_York"));
        // 29 Oct 2026 noon EDT = 16:00Z; 2 Nov 2026 noon EST = 17:00Z
        long a = CivilDate.localToUtc(CivilDate.fromCivil(2026, 10, 29), 720, ny);
        long b = CivilDate.localToUtc(CivilDate.fromCivil(2026, 11, 2), 720, ny);
        assertEquals(CivilDate.fromCivil(2026, 10, 29) * 86400000L + 16 * 3600000L, a);
        assertEquals(CivilDate.fromCivil(2026, 11, 2) * 86400000L + 17 * 3600000L, b);
        assertEquals(CivilDate.fromCivil(2026, 10, 29), CivilDate.epochDayOf(a, ny));
    }

    @Test
    public void offsetFormatting() {
        assertEquals("UTC+0", DisplayZone.formatOffset(0));
        assertEquals("UTC−4", DisplayZone.formatOffset(-4 * 3600000L));
        assertEquals("UTC+5:30", DisplayZone.formatOffset(330 * 60000L));
    }
}
