package com.atakmap.android.illum.plugin.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FormatsTest {

    @Test
    public void times() {
        long t = CivilDate.fromCivil(2026, 10, 29) * 86400000L + 22 * 3600000L + 14 * 60000L;
        assertEquals("2214Z", Formats.time(t, DisplayZone.ZULU));
        assertEquals("1714R", Formats.time(t, DisplayZone.nautical(-77)));
        assertEquals("1714R / 2214Z", Formats.timeWithZulu(t, DisplayZone.nautical(-77), true));
        assertEquals("2214Z", Formats.timeWithZulu(t, DisplayZone.ZULU, true));
        assertEquals("----", Formats.time(NightReport.NONE, DisplayZone.ZULU));
        // rounds to the nearest minute
        assertEquals("2215Z", Formats.time(t + 31000, DisplayZone.ZULU));
        // wraps past midnight
        assertEquals("0014Z", Formats.time(t + 2 * 3600000L, DisplayZone.ZULU));
    }

    @Test
    public void luxUnits() {
        assertEquals("2.2 mlux", Formats.lux(0.0022));
        assertEquals("0.21 lux", Formats.lux(0.21));
        assertEquals("<0.1 mlux", Formats.lux(0.00001));
        assertEquals("120 lux", Formats.lux(120));
    }

    @Test
    public void angles() {
        assertEquals("071°", Formats.degrees(71.2));
        assertEquals("000°", Formats.degrees(359.8));
        assertEquals("45°", Formats.altitude(45.2));
        assertEquals("50%", Formats.percent(0.5));
    }
}
