package com.atakmap.android.illum.plugin.core;

/** Everything (besides place and date) that shapes a night's report. Immutable. */
public final class PlanSettings {

    /** What the user wants from the light. */
    public enum Mission {
        /** NVG, aviation, driving: light is good (HLL = green). */
        NVG,
        /** Ground infiltration, concealment: darkness is good (LLL = green). */
        CONCEAL
    }

    /** Which part of the night is rated. */
    public enum WindowMode {
        /** EENT to BMNT (sun 12 deg below the horizon). */
        NAUTICAL_DARK,
        /** Sunset to sunrise. */
        SUNSET_SUNRISE,
        /** User-set clock times in the display zone. */
        CUSTOM
    }

    /** Sky condition divisors from USNO Circular 171. */
    public enum Sky {
        CLEAR(1, "Clear"),
        THIN_CLOUDS(2, "Thin clouds"),
        AVERAGE_CLOUDS(3, "Average clouds"),
        DARK_STRATUS(10, "Dark stratus");

        public final double divisor;
        public final String label;

        Sky(double divisor, String label) {
            this.divisor = divisor;
            this.label = label;
        }
    }

    public final LightStandard standard;
    public final Mission mission;
    public final WindowMode windowMode;
    /** Custom window start/end, minutes after local midnight in the display zone. */
    public final int customStartMin;
    public final int customEndMin;
    /**
     * A window is rated solidly HLL when at least (1 - tolerance) of it is HLL,
     * solidly LLL when at most tolerance of it is HLL, otherwise MIXED.
     */
    public final double tolerance;
    public final Sky sky;
    /** Add the USNO 0.5 millilux night-sky (starlight + airglow) term. */
    public final boolean includeNightSky;
    public final int sampleMinutes;

    private PlanSettings(Builder b) {
        standard = b.standard;
        mission = b.mission;
        windowMode = b.windowMode;
        customStartMin = b.customStartMin;
        customEndMin = b.customEndMin;
        tolerance = b.tolerance;
        sky = b.sky;
        includeNightSky = b.includeNightSky;
        sampleMinutes = b.sampleMinutes;
    }

    public static PlanSettings defaults() {
        return new Builder().build();
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.standard = standard;
        b.mission = mission;
        b.windowMode = windowMode;
        b.customStartMin = customStartMin;
        b.customEndMin = customEndMin;
        b.tolerance = tolerance;
        b.sky = sky;
        b.includeNightSky = includeNightSky;
        b.sampleMinutes = sampleMinutes;
        return b;
    }

    public static final class Builder {
        LightStandard standard = LightStandard.USAF;
        Mission mission = Mission.NVG;
        WindowMode windowMode = WindowMode.NAUTICAL_DARK;
        int customStartMin = 22 * 60;
        int customEndMin = 4 * 60;
        double tolerance = 0.10;
        Sky sky = Sky.CLEAR;
        boolean includeNightSky = false;
        int sampleMinutes = 5;

        public Builder standard(LightStandard s) {
            standard = s == null ? LightStandard.USAF : s;
            return this;
        }

        public Builder mission(Mission m) {
            mission = m == null ? Mission.NVG : m;
            return this;
        }

        public Builder window(WindowMode w) {
            windowMode = w == null ? WindowMode.NAUTICAL_DARK : w;
            return this;
        }

        public Builder customWindow(int startMin, int endMin) {
            customStartMin = clampMinutes(startMin);
            customEndMin = clampMinutes(endMin);
            return this;
        }

        public Builder tolerance(double t) {
            tolerance = Math.max(0.0, Math.min(0.49, t));
            return this;
        }

        public Builder sky(Sky s) {
            sky = s == null ? Sky.CLEAR : s;
            return this;
        }

        public Builder includeNightSky(boolean b) {
            includeNightSky = b;
            return this;
        }

        public Builder sampleMinutes(int m) {
            sampleMinutes = Math.max(1, Math.min(30, m));
            return this;
        }

        public PlanSettings build() {
            return new PlanSettings(this);
        }

        private static int clampMinutes(int m) {
            return ((m % 1440) + 1440) % 1440;
        }
    }
}
