using System.Globalization;

namespace Illum.Core
{
    public sealed class LightStandard
    {
        public enum Method { LUX, MOON_RULE }
        public const double HLL_LUX = 0.0022;
        public static readonly LightStandard USAF = new LightStandard("usaf", "USAF", Method.LUX, HLL_LUX, 20, 30,
            "AFI 11-214: high illumination is at least 2.2 millilux, roughly a 20% moon disk at 30° or higher.");
        public static readonly LightStandard USN = new LightStandard("usn", "USN/USMC", Method.LUX, HLL_LUX, 20, 30,
            "Navy/USMC (MCO 3500.14): HLL is 0.0022 lux or above. Planning tools do not account for low moon angle, terrain or shadows.");
        public static readonly LightStandard ARMY = new LightStandard("army", "Army", Method.MOON_RULE, HLL_LUX, 23, 30,
            "Army aviation rule of thumb: at least 23% moon illumination and moon elevation of at least 30°. Verify against your unit SOP.");
        public readonly string id, label, reference;
        public readonly Method method;
        public readonly double luxThreshold, minIllumPercent, minMoonAltDeg;
        public LightStandard(string id, string label, Method method, double lux, double percent, double altitude, string reference)
        { this.id = id; this.label = label; this.method = method; luxThreshold = lux; minIllumPercent = percent; minMoonAltDeg = altitude; this.reference = reference; }
        public static LightStandard custom(Method method, double lux, double percent, double altitude) =>
            new LightStandard("custom", "Custom", method, lux, percent, altitude, method == Method.LUX
                ? string.Format(CultureInfo.InvariantCulture, "Custom: HLL at or above {0:0.00} millilux.", lux * 1000)
                : string.Format(CultureInfo.InvariantCulture, "Custom: HLL when moon illumination is at least {0:0}% and elevation at least {1:0}°.", percent, altitude));
        public static LightStandard byId(string id) => id == "army" ? ARMY : id == "usn" ? USN : USAF;
        public bool isHighLight(double totalLux, double sunLux, double moonAltDeg, double illumPercent) =>
            method == Method.LUX ? totalLux >= luxThreshold : sunLux >= luxThreshold || moonAltDeg >= minMoonAltDeg && illumPercent >= minIllumPercent;
        public override string ToString() => label;
    }
}
