using System;

namespace Illum.Core
{
    public sealed class PlanSettings
    {
        public enum Mission { NVG, CONCEAL }
        public enum WindowMode { NAUTICAL_DARK, SUNSET_SUNRISE, CUSTOM }
        public sealed class Sky
        {
            public static readonly Sky CLEAR = new Sky(1, "Clear"), THIN_CLOUDS = new Sky(2, "Thin clouds"),
                AVERAGE_CLOUDS = new Sky(3, "Average clouds"), DARK_STRATUS = new Sky(10, "Dark stratus");
            public static readonly Sky[] All = { CLEAR, THIN_CLOUDS, AVERAGE_CLOUDS, DARK_STRATUS };
            public readonly double divisor;
            public readonly string label;
            private Sky(double divisor, string label) { this.divisor = divisor; this.label = label; }
            public override string ToString() => label;
        }
        public readonly LightStandard standard;
        public readonly Mission mission;
        public readonly WindowMode windowMode;
        public readonly int customStartMin, customEndMin, sampleMinutes;
        public readonly double tolerance;
        public readonly Sky sky;
        public readonly bool includeNightSky;
        private PlanSettings(Builder b)
        {
            standard = b.Standard; mission = b.MissionValue; windowMode = b.Window;
            customStartMin = b.Start; customEndMin = b.End; sampleMinutes = b.Sample;
            tolerance = b.Tolerance; sky = b.SkyValue; includeNightSky = b.NightSky;
        }
        public static PlanSettings defaults() => new Builder().build();
        public Builder toBuilder() => new Builder { Standard = standard, MissionValue = mission, Window = windowMode,
            Start = customStartMin, End = customEndMin, Sample = sampleMinutes, Tolerance = tolerance, SkyValue = sky, NightSky = includeNightSky };
        public sealed class Builder
        {
            internal LightStandard Standard = LightStandard.USAF;
            internal Mission MissionValue = Mission.NVG;
            internal WindowMode Window = WindowMode.NAUTICAL_DARK;
            internal int Start = 1320, End = 240, Sample = 5;
            internal double Tolerance = 0.10;
            internal Sky SkyValue = Sky.CLEAR;
            internal bool NightSky;
            public Builder standard(LightStandard s) { Standard = s ?? LightStandard.USAF; return this; }
            public Builder mission(Mission m) { MissionValue = m; return this; }
            public Builder window(WindowMode w) { Window = w; return this; }
            public Builder customWindow(int start, int end) { Start = (start % 1440 + 1440) % 1440; End = (end % 1440 + 1440) % 1440; return this; }
            public Builder tolerance(double t) { Tolerance = Math.Max(0, Math.Min(0.49, t)); return this; }
            public Builder sky(Sky s) { SkyValue = s ?? Sky.CLEAR; return this; }
            public Builder includeNightSky(bool value) { NightSky = value; return this; }
            public Builder sampleMinutes(int value) { Sample = Math.Max(1, Math.Min(30, value)); return this; }
            public PlanSettings build() => new PlanSettings(this);
        }
    }
}
