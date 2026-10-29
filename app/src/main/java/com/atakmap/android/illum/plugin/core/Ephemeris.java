package com.atakmap.android.illum.plugin.core;

/**
 * Sun and Moon positions for an observer, computed entirely on the device.
 *
 * Algorithms: Jean Meeus, "Astronomical Algorithms" (2nd ed.)
 * - Sun: chapter 25, low-accuracy method (about 0.01 deg).
 * - Moon: chapter 47, the full 60+60 term series of tables 47.A and 47.B
 *   (about 10 arcsec in longitude, 4 arcsec in latitude).
 * - Sidereal time: chapter 12. Nutation: chapter 22, main terms.
 *
 * No data files and no network: any date in roughly 1900-2100 works, so the
 * plugin keeps working offline indefinitely.
 */
public final class Ephemeris {

    static final double D2R = Math.PI / 180.0;
    static final double R2D = 180.0 / Math.PI;
    /** Earth equatorial radius, km (for the Moon's horizontal parallax). */
    static final double EARTH_RADIUS_KM = 6378.14;
    /** Astronomical unit, km. */
    static final double AU_KM = 149597870.7;

    private Ephemeris() {
    }

    /** Julian Day (UT) for a Unix time in milliseconds. */
    public static double julianDay(long utcMillis) {
        return utcMillis / 86400000.0 + 2440587.5;
    }

    /**
     * Approximate Delta T (TT - UT) in seconds. Espenak and Meeus polynomials;
     * the result only shifts lunar positions by a few hundredths of a degree.
     */
    static double deltaTSeconds(double jdUT) {
        double y = 2000.0 + (jdUT - 2451545.0) / 365.25;
        if (y >= 2005 && y < 2050) {
            double t = y - 2000;
            return 62.92 + 0.32217 * t + 0.005589 * t * t;
        }
        if (y >= 2050 && y < 2150) {
            double u = (y - 1820) / 100.0;
            return -20 + 32 * u * u - 0.5628 * (2150 - y);
        }
        if (y >= 1986 && y < 2005) {
            double t = y - 2000;
            return 63.86 + 0.3345 * t - 0.060374 * t * t + 0.0017275 * t * t * t;
        }
        double u = (y - 1820) / 100.0;
        return -20 + 32 * u * u;
    }

    static double norm360(double deg) {
        double d = deg % 360.0;
        return d < 0 ? d + 360.0 : d;
    }

    static double norm180(double deg) {
        double d = norm360(deg);
        return d > 180 ? d - 360 : d;
    }

    // ------------------------------------------------------------------
    // Results
    // ------------------------------------------------------------------

    /** Geocentric apparent equatorial position. */
    static final class Equatorial {
        /** Right ascension, radians. */
        double ra;
        /** Declination, radians. */
        double dec;
        /** Distance, km. */
        double distKm;
        /** Apparent ecliptic longitude, degrees. */
        double lonDeg;
        /** Ecliptic latitude, degrees. */
        double latDeg;
    }

    /** Nutation and obliquity for a TT instant. */
    static final class Nutation {
        /** Nutation in longitude, degrees. */
        double dPsiDeg;
        /** True obliquity, radians. */
        double eps;
    }

    // ------------------------------------------------------------------
    // Nutation / obliquity (Meeus ch. 22, main terms, ~0.5 arcsec)
    // ------------------------------------------------------------------

    static Nutation nutation(double T) {
        double omega = (125.04452 - 1934.136261 * T) * D2R;
        double lSun = (280.4665 + 36000.7698 * T) * D2R;
        double lMoon = (218.3165 + 481267.8813 * T) * D2R;
        double dPsiArcsec = -17.20 * Math.sin(omega) - 1.32 * Math.sin(2 * lSun)
                - 0.23 * Math.sin(2 * lMoon) + 0.21 * Math.sin(2 * omega);
        double dEpsArcsec = 9.20 * Math.cos(omega) + 0.57 * Math.cos(2 * lSun)
                + 0.10 * Math.cos(2 * lMoon) - 0.09 * Math.cos(2 * omega);
        double eps0Arcsec = 84381.448 - 46.8150 * T - 0.00059 * T * T
                + 0.001813 * T * T * T;
        Nutation n = new Nutation();
        n.dPsiDeg = dPsiArcsec / 3600.0;
        n.eps = ((eps0Arcsec + dEpsArcsec) / 3600.0) * D2R;
        return n;
    }

    // ------------------------------------------------------------------
    // Sun (Meeus ch. 25)
    // ------------------------------------------------------------------

    static Equatorial sun(double T, Nutation nut) {
        double L0 = 280.46646 + 36000.76983 * T + 0.0003032 * T * T;
        double M = 357.52911 + 35999.05029 * T - 0.0001537 * T * T;
        double e = 0.016708634 - 0.000042037 * T - 0.0000001267 * T * T;
        double Mr = M * D2R;
        double C = (1.914602 - 0.004817 * T - 0.000014 * T * T) * Math.sin(Mr)
                + (0.019993 - 0.000101 * T) * Math.sin(2 * Mr)
                + 0.000289 * Math.sin(3 * Mr);
        double trueLon = L0 + C;
        double nu = (M + C) * D2R;
        double R = 1.000001018 * (1 - e * e) / (1 + e * Math.cos(nu));
        // apparent longitude: nutation + aberration (-20.4898" / R)
        double lambda = trueLon + nut.dPsiDeg - 0.005691611 / R;
        double lr = lambda * D2R;
        Equatorial q = new Equatorial();
        q.ra = Math.atan2(Math.cos(nut.eps) * Math.sin(lr), Math.cos(lr));
        q.dec = Math.asin(Math.sin(nut.eps) * Math.sin(lr));
        q.distKm = R * AU_KM;
        q.lonDeg = norm360(lambda);
        q.latDeg = 0;
        return q;
    }

    // ------------------------------------------------------------------
    // Moon (Meeus ch. 47)
    // ------------------------------------------------------------------

    // Table 47.A: D, M, M', F, Sigma-l (1e-6 deg), Sigma-r (1e-3 km)
    private static final int[][] LR = {
            {0, 0, 1, 0, 6288774, -20905355},
            {2, 0, -1, 0, 1274027, -3699111},
            {2, 0, 0, 0, 658314, -2955968},
            {0, 0, 2, 0, 213618, -569925},
            {0, 1, 0, 0, -185116, 48888},
            {0, 0, 0, 2, -114332, -3149},
            {2, 0, -2, 0, 58793, 246158},
            {2, -1, -1, 0, 57066, -152138},
            {2, 0, 1, 0, 53322, -170733},
            {2, -1, 0, 0, 45758, -204586},
            {0, 1, -1, 0, -40923, -129620},
            {1, 0, 0, 0, -34720, 108743},
            {0, 1, 1, 0, -30383, 104755},
            {2, 0, 0, -2, 15327, 10321},
            {0, 0, 1, 2, -12528, 0},
            {0, 0, 1, -2, 10980, 79661},
            {4, 0, -1, 0, 10675, -34782},
            {0, 0, 3, 0, 10034, -23210},
            {4, 0, -2, 0, 8548, -21636},
            {2, 1, -1, 0, -7888, 24208},
            {2, 1, 0, 0, -6766, 30824},
            {1, 0, -1, 0, -5163, -8379},
            {1, 1, 0, 0, 4987, -16675},
            {2, -1, 1, 0, 4036, -12831},
            {2, 0, 2, 0, 3994, -10445},
            {4, 0, 0, 0, 3861, -11650},
            {2, 0, -3, 0, 3665, 14403},
            {0, 1, -2, 0, -2689, -7003},
            {2, 0, -1, 2, -2602, 0},
            {2, -1, -2, 0, 2390, 10056},
            {1, 0, 1, 0, -2348, 6322},
            {2, -2, 0, 0, 2236, -9884},
            {0, 1, 2, 0, -2120, 5751},
            {0, 2, 0, 0, -2069, 0},
            {2, -2, -1, 0, 2048, -4950},
            {2, 0, 1, -2, -1773, 4130},
            {2, 0, 0, 2, -1595, 0},
            {4, -1, -1, 0, 1215, -3958},
            {0, 0, 2, 2, -1110, 0},
            {3, 0, -1, 0, -892, 3258},
            {2, 1, 1, 0, -810, 2616},
            {4, -1, -2, 0, 759, -1897},
            {0, 2, -1, 0, -713, -2117},
            {2, 2, -1, 0, -700, 2354},
            {2, 1, -2, 0, 691, 0},
            {2, -1, 0, -2, 596, 0},
            {4, 0, 1, 0, 549, -1423},
            {0, 0, 4, 0, 537, -1117},
            {4, -1, 0, 0, 520, -1571},
            {1, 0, -2, 0, -487, -1739},
            {2, 1, 0, -2, -399, 0},
            {0, 0, 2, -2, -381, -4421},
            {1, 1, 1, 0, 351, 0},
            {3, 0, -2, 0, -340, 0},
            {4, 0, -3, 0, 330, 0},
            {2, -1, 2, 0, 327, 0},
            {0, 2, 1, 0, -323, 1165},
            {1, 1, -1, 0, 299, 0},
            {2, 0, 3, 0, 294, 0},
            {2, 0, -1, -2, 0, 8752},
    };

    // Table 47.B: D, M, M', F, Sigma-b (1e-6 deg)
    private static final int[][] B = {
            {0, 0, 0, 1, 5128122},
            {0, 0, 1, 1, 280602},
            {0, 0, 1, -1, 277693},
            {2, 0, 0, -1, 173237},
            {2, 0, -1, 1, 55413},
            {2, 0, -1, -1, 46271},
            {2, 0, 0, 1, 32573},
            {0, 0, 2, 1, 17198},
            {2, 0, 1, -1, 9266},
            {0, 0, 2, -1, 8822},
            {2, -1, 0, -1, 8216},
            {2, 0, -2, -1, 4324},
            {2, 0, 1, 1, 4200},
            {2, 1, 0, -1, -3359},
            {2, -1, -1, 1, 2463},
            {2, -1, 0, 1, 2211},
            {2, -1, -1, -1, 2065},
            {0, 1, -1, -1, -1870},
            {4, 0, -1, -1, 1828},
            {0, 1, 0, 1, -1794},
            {0, 0, 0, 3, -1749},
            {0, 1, -1, 1, -1565},
            {1, 0, 0, 1, -1491},
            {0, 1, 1, 1, -1475},
            {0, 1, 1, -1, -1410},
            {0, 1, 0, -1, -1344},
            {1, 0, 0, -1, -1335},
            {0, 0, 3, 1, 1107},
            {4, 0, 0, -1, 1021},
            {4, 0, -1, 1, 833},
            {0, 0, 1, -3, 777},
            {4, 0, -2, 1, 671},
            {2, 0, 0, -3, 607},
            {2, 0, 2, -1, 596},
            {2, -1, 1, -1, 491},
            {2, 0, -2, 1, -451},
            {0, 0, 3, -1, 439},
            {2, 0, 2, 1, 422},
            {2, 0, -3, -1, 421},
            {2, 1, -1, 1, -366},
            {2, 1, 0, 1, -351},
            {4, 0, 0, 1, 331},
            {2, -1, 1, 1, 315},
            {2, -2, 0, -1, 302},
            {0, 0, 1, 3, -283},
            {2, 1, 1, -1, -229},
            {1, 1, 0, -1, 223},
            {1, 1, 0, 1, 223},
            {0, 1, -2, -1, -220},
            {2, 1, -1, -1, -220},
            {1, 0, 1, 1, -185},
            {2, -1, -2, -1, 181},
            {0, 1, 2, 1, -177},
            {4, 0, -2, -1, 176},
            {4, -1, -1, -1, 166},
            {1, 0, 1, -1, -164},
            {4, 0, 1, -1, 132},
            {1, 0, -1, -1, -119},
            {4, -1, 0, -1, 115},
            {2, -2, 0, 1, 107},
    };

    static Equatorial moon(double T, Nutation nut) {
        double T2 = T * T, T3 = T2 * T, T4 = T3 * T;
        double Lp = 218.3164477 + 481267.88123421 * T - 0.0015786 * T2
                + T3 / 538841.0 - T4 / 65194000.0;
        double D = 297.8501921 + 445267.1114034 * T - 0.0018819 * T2
                + T3 / 545868.0 - T4 / 113065000.0;
        double M = 357.5291092 + 35999.0502909 * T - 0.0001536 * T2
                + T3 / 24490000.0;
        double Mp = 134.9633964 + 477198.8675055 * T + 0.0087414 * T2
                + T3 / 69699.0 - T4 / 14712000.0;
        double F = 93.2720950 + 483202.0175233 * T - 0.0036539 * T2
                - T3 / 3526000.0 + T4 / 863310000.0;
        double A1 = 119.75 + 131.849 * T;
        double A2 = 53.09 + 479264.290 * T;
        double A3 = 313.45 + 481266.484 * T;
        double E = 1 - 0.002516 * T - 0.0000074 * T2;
        double E2 = E * E;

        double Dr = norm360(D) * D2R, Mr = norm360(M) * D2R;
        double Mpr = norm360(Mp) * D2R, Fr = norm360(F) * D2R;

        double sl = 0, sr = 0, sb = 0;
        for (int[] t : LR) {
            double arg = t[0] * Dr + t[1] * Mr + t[2] * Mpr + t[3] * Fr;
            double f = Math.abs(t[1]) == 1 ? E : (Math.abs(t[1]) == 2 ? E2 : 1);
            if (t[4] != 0)
                sl += f * t[4] * Math.sin(arg);
            if (t[5] != 0)
                sr += f * t[5] * Math.cos(arg);
        }
        for (int[] t : B) {
            double arg = t[0] * Dr + t[1] * Mr + t[2] * Mpr + t[3] * Fr;
            double f = Math.abs(t[1]) == 1 ? E : (Math.abs(t[1]) == 2 ? E2 : 1);
            sb += f * t[4] * Math.sin(arg);
        }
        double Lpr = norm360(Lp) * D2R;
        sl += 3958 * Math.sin(norm360(A1) * D2R) + 1962 * Math.sin(Lpr - Fr)
                + 318 * Math.sin(norm360(A2) * D2R);
        sb += -2235 * Math.sin(Lpr) + 382 * Math.sin(norm360(A3) * D2R)
                + 175 * Math.sin(norm360(A1) * D2R - Fr)
                + 175 * Math.sin(norm360(A1) * D2R + Fr)
                + 127 * Math.sin(Lpr - Mpr) - 115 * Math.sin(Lpr + Mpr);

        double lambda = Lp + sl / 1e6 + nut.dPsiDeg;
        double beta = sb / 1e6;
        double dist = 385000.56 + sr / 1000.0;

        double lr = lambda * D2R, br = beta * D2R;
        double se = Math.sin(nut.eps), ce = Math.cos(nut.eps);
        Equatorial q = new Equatorial();
        q.ra = Math.atan2(Math.sin(lr) * ce - Math.tan(br) * se, Math.cos(lr));
        q.dec = Math.asin(Math.sin(br) * ce + Math.cos(br) * se * Math.sin(lr));
        q.distKm = dist;
        q.lonDeg = norm360(lambda);
        q.latDeg = beta;
        return q;
    }

    // ------------------------------------------------------------------
    // Observer frame
    // ------------------------------------------------------------------

    /** Apparent sidereal time at Greenwich, degrees (Meeus 12.4 + eq. of equinoxes). */
    static double gast(double jdUT, Nutation nut) {
        double d = jdUT - 2451545.0;
        double T = d / 36525.0;
        double gmst = 280.46061837 + 360.98564736629 * d + 0.000387933 * T * T
                - T * T * T / 38710000.0;
        return norm360(gmst + nut.dPsiDeg * Math.cos(nut.eps));
    }

    /**
     * Convert equatorial to horizontal coordinates.
     *
     * @return {altitude deg (geometric, geocentric), azimuth deg from true north}
     */
    static void horizontal(Equatorial q, double lstDeg, double latDeg, double[] out) {
        double H = (lstDeg * D2R) - q.ra;
        double phi = latDeg * D2R;
        double sinAlt = Math.sin(phi) * Math.sin(q.dec)
                + Math.cos(phi) * Math.cos(q.dec) * Math.cos(H);
        sinAlt = Math.max(-1, Math.min(1, sinAlt));
        double alt = Math.asin(sinAlt);
        double az = Math.atan2(-Math.cos(q.dec) * Math.sin(H),
                Math.sin(q.dec) * Math.cos(phi)
                        - Math.cos(q.dec) * Math.sin(phi) * Math.cos(H));
        out[0] = alt * R2D;
        out[1] = norm360(az * R2D);
    }

    /** Sun's geometric altitude in degrees (no refraction). Fast path for event search. */
    public static double sunAltitude(long utcMillis, double latDeg, double lonDeg) {
        double jd = julianDay(utcMillis);
        double T = (jd + deltaTSeconds(jd) / 86400.0 - 2451545.0) / 36525.0;
        Nutation nut = nutation(T);
        Equatorial s = sun(T, nut);
        double[] hz = new double[2];
        horizontal(s, gast(jd, nut) + lonDeg, latDeg, hz);
        return hz[0];
    }

    /**
     * Full Sun + Moon state for one instant at one place.
     */
    public static SkyState compute(long utcMillis, double latDeg, double lonDeg) {
        double jd = julianDay(utcMillis);
        double T = (jd + deltaTSeconds(jd) / 86400.0 - 2451545.0) / 36525.0;
        Nutation nut = nutation(T);
        Equatorial s = sun(T, nut);
        Equatorial m = moon(T, nut);
        double lst = gast(jd, nut) + lonDeg;

        double[] hz = new double[2];
        SkyState st = new SkyState();
        st.utcMillis = utcMillis;

        horizontal(s, lst, latDeg, hz);
        st.sunAltDeg = hz[0];
        st.sunAzDeg = hz[1];

        horizontal(m, lst, latDeg, hz);
        double parallaxDeg = Math.asin(EARTH_RADIUS_KM / m.distKm) * R2D;
        // topocentric altitude: geocentric minus parallax in altitude
        st.moonAltDeg = hz[0] - parallaxDeg * Math.cos(hz[0] * D2R);
        st.moonAzDeg = hz[1];
        st.moonParallaxDeg = parallaxDeg;
        st.moonDistKm = m.distKm;

        // elongation (geocentric) and phase angle (Meeus ch. 48)
        double cosPsi = Math.sin(s.dec) * Math.sin(m.dec)
                + Math.cos(s.dec) * Math.cos(m.dec) * Math.cos(s.ra - m.ra);
        cosPsi = Math.max(-1, Math.min(1, cosPsi));
        double psi = Math.acos(cosPsi);
        double i = Math.atan2(s.distKm * Math.sin(psi), m.distKm - s.distKm * Math.cos(psi));
        st.elongationDeg = psi * R2D;
        st.phaseAngleDeg = i * R2D;
        st.illumFraction = (1 + Math.cos(i)) / 2.0;
        st.waxing = norm360(m.lonDeg - s.lonDeg) < 180.0;
        return st;
    }
}
