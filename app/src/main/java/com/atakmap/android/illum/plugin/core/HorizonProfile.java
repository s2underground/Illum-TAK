package com.atakmap.android.illum.plugin.core;

/**
 * The local skyline around one point: for every compass direction, how high
 * (in degrees) the terrain rises above the flat horizon. Built from elevation
 * data, so the moon can be treated as hidden while it is behind a ridge.
 *
 * Method: rays every 2 degrees of azimuth, each sampled at 48 distances
 * spaced geometrically from 60 m to the search radius (20 km by default).
 * The skyline angle is the largest elevation angle along the ray, after
 * Earth curvature with standard refraction (k = 0.13). The observer's eye
 * is 2 m above the ground.
 *
 * Bare terrain only: trees and buildings are not in DTED/SRTM data.
 */
public final class HorizonProfile {

    /** Supplies terrain elevations (meters); NaN where no data is loaded. */
    public interface ElevationSampler {
        void sample(double[] lat, double[] lon, double[] out);
    }

    public static final int AZ_STEP_DEG = 2;
    public static final int BINS = 360 / AZ_STEP_DEG;
    public static final int SAMPLES_PER_RAY = 48;
    public static final double DEFAULT_RADIUS_M = 20000;
    static final double MIN_DIST_M = 60;
    static final double EYE_HEIGHT_M = 2.0;
    static final double EARTH_RADIUS_M = 6371000.0;
    static final double REFRACTION_K = 0.13;

    private final double[] skylineDeg;
    public final double lat, lon;
    public final double groundElevM;
    /** Share of terrain samples that had elevation data, 0..1. */
    public final double coverage;
    public final double radiusM;

    private HorizonProfile(double lat, double lon, double groundElevM, double[] skylineDeg,
            double coverage, double radiusM) {
        this.lat = lat;
        this.lon = lon;
        this.groundElevM = groundElevM;
        this.skylineDeg = skylineDeg;
        this.coverage = coverage;
        this.radiusM = radiusM;
    }

    /**
     * Build the profile. Returns null when there is no elevation data at the
     * point itself (nothing to measure from).
     */
    public static HorizonProfile build(double lat, double lon, ElevationSampler sampler,
            double radiusM) {
        int n = 1 + BINS * SAMPLES_PER_RAY;
        double[] la = new double[n], lo = new double[n], el = new double[n];
        double[] dist = new double[SAMPLES_PER_RAY];
        for (int j = 0; j < SAMPLES_PER_RAY; j++)
            dist[j] = MIN_DIST_M * Math.pow(radiusM / MIN_DIST_M, j / (double) (SAMPLES_PER_RAY - 1));
        la[0] = lat;
        lo[0] = lon;
        int k = 1;
        for (int b = 0; b < BINS; b++) {
            double az = b * AZ_STEP_DEG;
            for (int j = 0; j < SAMPLES_PER_RAY; j++, k++) {
                double[] p = destination(lat, lon, az, dist[j]);
                la[k] = p[0];
                lo[k] = p[1];
            }
        }
        for (int i = 0; i < n; i++)
            el[i] = Double.NaN;
        sampler.sample(la, lo, el);

        double ground = el[0];
        if (Double.isNaN(ground))
            return null;
        double eye = ground + EYE_HEIGHT_M;

        double[] sky = new double[BINS];
        int valid = 0;
        k = 1;
        for (int b = 0; b < BINS; b++) {
            double best = Double.NaN;
            for (int j = 0; j < SAMPLES_PER_RAY; j++, k++) {
                double h = el[k];
                if (Double.isNaN(h))
                    continue;
                valid++;
                double d = dist[j];
                double drop = d * d / (2 * EARTH_RADIUS_M) * (1 - REFRACTION_K);
                double ang = Math.toDegrees(Math.atan2(h - eye - drop, d));
                if (Double.isNaN(best) || ang > best)
                    best = ang;
            }
            // no data along this ray: assume a flat horizon
            sky[b] = Double.isNaN(best) ? 0.0 : best;
        }
        double coverage = valid / (double) (BINS * SAMPLES_PER_RAY);
        return new HorizonProfile(lat, lon, ground, sky, coverage, radiusM);
    }

    /** For tests and callers that already have a skyline. */
    public static HorizonProfile of(double lat, double lon, double[] skylineDegPerBin) {
        if (skylineDegPerBin.length != BINS)
            throw new IllegalArgumentException("need " + BINS + " bins");
        return new HorizonProfile(lat, lon, 0, skylineDegPerBin.clone(), 1.0, DEFAULT_RADIUS_M);
    }

    /** Skyline elevation angle (degrees) toward a true azimuth, interpolated. */
    public double altitudeAt(double azDeg) {
        double a = Ephemeris.norm360(azDeg) / AZ_STEP_DEG;
        int i = (int) Math.floor(a);
        double f = a - i;
        double v0 = skylineDeg[i % BINS], v1 = skylineDeg[(i + 1) % BINS];
        return v0 + (v1 - v0) * f;
    }

    /** Highest skyline angle and its azimuth: {deg, azDeg}. */
    public double[] highest() {
        int best = 0;
        for (int i = 1; i < BINS; i++)
            if (skylineDeg[i] > skylineDeg[best])
                best = i;
        return new double[] {
                skylineDeg[best], best * AZ_STEP_DEG
        };
    }

    /** Point at a distance and bearing on a sphere: {lat, lon}. */
    static double[] destination(double latDeg, double lonDeg, double azDeg, double distM) {
        double phi = Math.toRadians(latDeg), lam = Math.toRadians(lonDeg);
        double th = Math.toRadians(azDeg), dl = distM / EARTH_RADIUS_M;
        double phi2 = Math.asin(Math.sin(phi) * Math.cos(dl)
                + Math.cos(phi) * Math.sin(dl) * Math.cos(th));
        double lam2 = lam + Math.atan2(Math.sin(th) * Math.sin(dl) * Math.cos(phi),
                Math.cos(dl) - Math.sin(phi) * Math.sin(phi2));
        double lonOut = Math.toDegrees(lam2);
        lonOut = ((lonOut + 540) % 360) - 180;
        return new double[] {
                Math.toDegrees(phi2), lonOut
        };
    }
}
