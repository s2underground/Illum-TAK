using System;
using System.Globalization;
using NodaTime;

namespace Illum.Core
{
    public abstract class DisplayZone
    {
        public abstract long offsetMillis(long utcMillis);
        public virtual string suffix(long utcMillis) => letterFor(offsetMillis(utcMillis));
        public abstract string describe(long utcMillis);
        public virtual bool isUtc() => false;
        internal virtual long localToUtc(long day,int minutes)
        {
            long local=day*86400000L+minutes*60000L;
            long guess=local-offsetMillis(local);
            return local-offsetMillis(guess);
        }
        private static long ResolveLocal(DateTimeZone zone,long day,int minutes)
        {
            int[] date=CivilDate.toCivil(day);
            // Repeated times use the earlier occurrence; skipped times move forward by the gap.
            return zone.AtLeniently(new LocalDate(date[0],date[1],date[2]).AtMidnight().PlusMinutes(minutes)).ToInstant().ToUnixTimeMilliseconds();
        }
        public static readonly DisplayZone ZULU = new Fixed(0, "Zulu (UTC)");
        public static DisplayZone device() => new WindowsZone(TimeZoneInfo.Local);
        public static DisplayZone of(string ianaId) => new IanaZone(DateTimeZoneProviders.Tzdb[ianaId]);
        public static DisplayZone localAtPoint(string ianaId) => of(ianaId);
        public static DisplayZone atPoint(TzMap map, double lat, double lon)
        {
            string id = map?.zoneAt(lat, lon);
            var zone=id==null || id.StartsWith("Etc/",StringComparison.Ordinal)?null:DateTimeZoneProviders.Tzdb.GetZoneOrNull(id);
            return zone==null?nautical(lon):new IanaZone(zone);
        }
        public static DisplayZone fixedMinutes(int minutes) => new Fixed(minutes * 60000L, "Manual time zone");
        public static DisplayZone nautical(double longitude) => new Fixed((long)Math.Max(-12,Math.Min(12,JavaMath.round(longitude/15)))*3600000L,"Military / nautical time at this longitude (no daylight saving)");
        public long offsetChange(long from, long to)
        {
            if (to <= from || offsetMillis(from) == offsetMillis(to)) return NightReport.NONE;
            long before = offsetMillis(from), lo = from, hi = to;
            while (hi - lo > 1) { long mid = lo + (hi - lo) / 2; if (offsetMillis(mid) == before) lo = mid; else hi = mid; }
            return hi;
        }
        public static string letterFor(long offset)
        {
            long minutes = offset / 60000; int rawHours = (int)(minutes / 60);
            int hours = Math.Max(-12, Math.Min(12, rawHours));
            string letter = hours == 0 ? "Z" : hours < 0 ? ((char)('N' - hours - 1)).ToString()
                : ((char)(hours <= 9 ? 'A' + hours - 1 : 'K' + hours - 10)).ToString();
            return minutes % 60 == 0 && rawHours>=-12 && rawHours<=12 ? letter : letter + "*";
        }
        public static string formatOffset(long offset)
        {
            long minutes = Math.Abs(offset / 60000);
            return "UTC" + (offset < 0 ? "−" : "+") + (minutes / 60).ToString(CultureInfo.InvariantCulture)
                + (minutes % 60 == 0 ? "" : ":" + (minutes % 60).ToString("00", CultureInfo.InvariantCulture));
        }
        private sealed class Fixed : DisplayZone
        {
            private readonly long offset; private readonly string label;
            internal Fixed(long offset, string label) { this.offset = offset; this.label = label; }
            public override long offsetMillis(long t) => offset;
            public override string describe(long t) => suffix(t) + " = " + formatOffset(offset) + ", " + label;
            public override bool isUtc() => offset == 0;
        }
        private sealed class IanaZone : DisplayZone
        {
            private readonly DateTimeZone zone;
            internal IanaZone(DateTimeZone zone) { this.zone = zone; }
            internal override long localToUtc(long day,int minutes) => ResolveLocal(zone,day,minutes);
            public override long offsetMillis(long t) => (long)zone.GetUtcOffset(Instant.FromUnixTimeMilliseconds(t)).ToTimeSpan().TotalMilliseconds;
            public override string describe(long t)
            {
                var interval = zone.GetZoneInterval(Instant.FromUnixTimeMilliseconds(t));
                return suffix(t) + " = " + formatOffset(offsetMillis(t)) + ", " + zone.Id + " (" + interval.Name + ")";
            }
        }
        private sealed class WindowsZone : DisplayZone
        {
            private readonly TimeZoneInfo zone;
            internal WindowsZone(TimeZoneInfo zone) { this.zone = zone; }
            internal override long localToUtc(long day,int minutes) => ResolveLocal(NodaTime.TimeZones.BclDateTimeZone.FromTimeZoneInfo(zone),day,minutes);
            public override long offsetMillis(long t) => (long)zone.GetUtcOffset(DateTimeOffset.FromUnixTimeMilliseconds(t)).TotalMilliseconds;
            public override string describe(long t) => suffix(t) + " = " + formatOffset(offsetMillis(t)) + ", device time: " + zone.Id;
        }
    }
}
