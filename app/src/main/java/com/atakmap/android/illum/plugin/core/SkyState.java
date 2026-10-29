package com.atakmap.android.illum.plugin.core;

/** Sun and Moon state for one instant at one place. Angles in degrees. */
public final class SkyState {
    public long utcMillis;
    /** Sun geometric altitude (no refraction). */
    public double sunAltDeg;
    public double sunAzDeg;
    /** Moon topocentric geometric altitude (parallax applied, no refraction). */
    public double moonAltDeg;
    public double moonAzDeg;
    public double moonParallaxDeg;
    public double moonDistKm;
    /** Geocentric Sun-Moon elongation (180 = full). */
    public double elongationDeg;
    /** Phase angle at the Moon (0 = full). */
    public double phaseAngleDeg;
    /** Illuminated fraction of the disk, 0..1. */
    public double illumFraction;
    public boolean waxing;

    /** Moon illumination as a percentage, 0..100. */
    public double illumPercent() {
        return illumFraction * 100.0;
    }
}
