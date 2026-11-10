using System;
using System.Globalization;

namespace Illum.Core
{
    public static class Formats
    {
        public const string NO_TIME = "----";
        public static string hhmm(long utc, DisplayZone zone)
        {
            if (utc == NightReport.NONE) return NO_TIME;
            long m = CivilDate.floorMod(CivilDate.floorDiv(utc + zone.offsetMillis(utc) + 30000, 60000), 1440);
            return (m / 60).ToString("00", CultureInfo.InvariantCulture) + (m % 60).ToString("00", CultureInfo.InvariantCulture);
        }
        public static string time(long utc, DisplayZone zone) => utc == NightReport.NONE ? NO_TIME : hhmm(utc, zone) + zone.suffix(utc);
        public static string timeWithZulu(long utc, DisplayZone zone, bool zulu) => utc == NightReport.NONE ? NO_TIME : time(utc, zone) + (zulu && !zone.isUtc() ? " / " + time(utc, DisplayZone.ZULU) : "");
        public static string lux(double x) => double.IsNaN(x) ? "--" : x < 0.0001 ? "<0.1 mlux" : x < 0.1 ? (x * 1000).ToString("0.0", CultureInfo.InvariantCulture) + " mlux" : x.ToString(x < 10 ? "0.00" : "0", CultureInfo.InvariantCulture) + " lux";
        public static string percent(double x) => double.IsNaN(x) ? "--" : (x * 100).ToString("0", CultureInfo.InvariantCulture) + "%";
        public static string degrees(double x) => double.IsNaN(x) ? "--" : (JavaMath.round(Ephemeris.norm360(x)) % 360).ToString("000", CultureInfo.InvariantCulture) + "°";
        public static string altitude(double x) => double.IsNaN(x) ? "--" : x.ToString("0", CultureInfo.InvariantCulture) + "°";
        public static string moonEvent(NightReport.MoonEvent e, DisplayZone zone) => time(e.time, zone) + " @ " + degrees(e.azimuthDeg);
        public static string clockChange(NightReport n, DisplayZone z)
        {
            long t = z.offsetChange(n.spanStart, n.spanEnd); if (t == NightReport.NONE) return null;
            double minutes = (z.offsetMillis(t) - z.offsetMillis(t - 1)) / 60000.0;
            return "Clocks go " + (minutes < 0 ? "back " : "forward ") + Math.Abs(minutes).ToString("0", CultureInfo.InvariantCulture)
                + " min at " + time(t, DisplayZone.ZULU) + ". Times after that use " + z.describe(t) + ".";
        }
    }
}
