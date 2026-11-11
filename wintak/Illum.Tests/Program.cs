using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Linq;
using Illum.Core;

namespace Illum.Tests
{
    // Runs on the same .NET Framework as WinTAK, with no host or test runner required.
    internal static class Program
    {
        private static int assertions;
        private static readonly CultureInfo Invariant = CultureInfo.InvariantCulture;
        private static int Main()
        {
            try
            {
                References("jpl_de421_reference.csv", 300, true);
                References("pyephem_reference.csv", 64, false);
                Positions(); CalendarAndZones(); RatingsAndWindows(); CustomWindowDst(); TerrainAndShadows();
                Console.WriteLine("PASS: " + assertions + " assertions; 364 independent reference nights; calendar, DST, terrain and mission rules.");
                return 0;
            }
            catch (Exception e) { Console.Error.WriteLine("FAIL: " + e); return 1; }
        }
        private static void Check(bool ok, string what) { assertions++; if (!ok) throw new Exception(what); }
        private static void Near(double expected, double actual, double tolerance, string what)
            => Check(!double.IsNaN(actual) && Math.Abs(expected - actual) <= tolerance, what + ": expected " + expected + ", actual " + actual);
        private sealed class Row
        {
            internal string Name, Kind; internal double Lat, Lon, Value; internal long Day, Time;
        }
        private static void References(string filename, int count, bool jpl)
        {
            var rows = File.ReadLines(Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "References", filename))
                .Where(x => !string.IsNullOrWhiteSpace(x) && !x.StartsWith("#"))
                .Select(x => x.Split(','))
                .Select(p => new Row { Name = p[0], Lat = double.Parse(p[1], Invariant), Lon = double.Parse(p[2], Invariant),
                    Day = long.Parse(p[3], Invariant), Kind = p[4], Time = p[5] == "NONE" ? NightReport.NONE : long.Parse(p[5], Invariant),
                    Value = p.Length < 7 || p[6] == "" ? double.NaN : double.Parse(p[6], Invariant) })
                .GroupBy(x => x.Name + "/" + x.Day).ToList();
            Check(rows.Count == count, filename + " night count");
            long worstSun = 0, worstMoon = 0; double worstIllum = 0;
            foreach (var night in rows)
            {
                var first = night.First();
                var n = NightCalculator.compute(first.Lat, first.Lon, first.Day, DisplayZone.nautical(first.Lon), PlanSettings.defaults());
                long sunTolerance = jpl && n.eent != NightReport.NONE && n.bmnt != NightReport.NONE && n.bmnt - n.eent < 10800000 ? 180000 : 60000;
                foreach (var r in night)
                {
                    long actual;
                    switch (r.Kind)
                    {
                        case "sunset": actual = n.sunset; break;
                        case "eent": actual = n.eent; break;
                        case "bmnt": actual = n.bmnt; break;
                        case "sunrise": actual = n.sunrise; break;
                        case "moonrise": case "moonset": continue;
                        case "illum":
                            double error = Math.Abs(Ephemeris.compute(r.Time, r.Lat, r.Lon).illumFraction - r.Value);
                            Check(error <= 0.005, night.Key + " illumination"); worstIllum = Math.Max(worstIllum, error); continue;
                        default: throw new Exception("Unknown reference kind " + r.Kind);
                    }
                    Check((actual == NightReport.NONE) == (r.Time == NightReport.NONE), night.Key + " " + r.Kind + " presence");
                    if (r.Time != NightReport.NONE)
                    { long error = Math.Abs(actual - r.Time); Check(error <= sunTolerance, night.Key + " " + r.Kind + " error " + error); worstSun = Math.Max(worstSun, error); }
                }
                foreach (bool rise in new[] { true, false })
                {
                    var expected = night.Where(x => x.Kind == (rise ? "moonrise" : "moonset")).Select(x => x.Time).ToArray();
                    var actual = n.moonEvents.Where(x => x.rise == rise).Select(x => x.time).ToArray();
                    foreach (long e in expected)
                    {
                        if (actual.Length == 0 && NearEdge(e, n)) continue;
                        long error = actual.Length == 0 ? long.MaxValue : actual.Min(a => Math.Abs(a - e));
                        Check(error <= 120000, night.Key + " missing moon event: " + error); worstMoon = Math.Max(worstMoon, error);
                    }
                    foreach (long a in actual) Check(NearEdge(a, n) || expected.Any(e => Math.Abs(a - e) <= 120000), night.Key + " extra moon event");
                }
            }
            Console.WriteLine(filename + ": " + count + " nights; worst sun " + worstSun / 1000.0 + " s, moon " + worstMoon / 1000.0 + " s, illumination " + worstIllum * 100 + " percentage points.");
        }
        private static bool NearEdge(long t, NightReport n) => Math.Abs(t - n.spanStart) < 120000 || Math.Abs(t - n.spanEnd) < 120000;
        private static void Positions()
        {
            var a = Ephemeris.compute(1793318400000L, 35, 69);
            Near(-21.5681, a.sunAltDeg, .02, "Sun altitude"); Near(91.9707, a.sunAzDeg, .02, "Sun azimuth");
            Near(72.1419, a.moonAltDeg, .02, "Moon altitude"); Near(250.7233, a.moonAzDeg, .05, "Moon azimuth");
            long[] times = { 1793318400000, 1800000000000, 1700000000000, 1767225600000 };
            double[] fractions = { .8078, .4467, .0280, .9154 };
            for (int i = 0; i < times.Length; i++) Near(fractions[i], Ephemeris.compute(times[i], 38.9, -77).illumFraction, .005, "Moon phase");
        }
        private static void CalendarAndZones()
        {
            foreach (int year in new[] { 1900, 1969, 1970, 2000, 2026, 2036, 2100 })
                for (int month = 1; month <= 12; month++)
                {
                    int[] c = CivilDate.toCivil(CivilDate.fromCivil(year, month, 15));
                    Check(c[0] == year && c[1] == month && c[2] == 15, "Calendar round trip");
                }
            Check(CivilDate.fromCivil(1970, 1, 1) == 0 && CivilDate.label(0) == "Thu 01 JAN 70", "Epoch day");
            var map = TzMap.LoadEmbedded(); Check(map.zoneCount() > 400, "World time zone grid");
            var places = new[] { new { Lat=45.815, Lon=15.982, Id="Europe/Zagreb" }, new {Lat=38.89,Lon=-77.04,Id="America/New_York"},
                new {Lat=33.45,Lon=-112.07,Id="America/Phoenix"}, new {Lat=-33.87,Lon=151.21,Id="Australia/Sydney"},
                new {Lat=35.69,Lon=51.39,Id="Asia/Tehran"}, new {Lat=34.53,Lon=69.17,Id="Asia/Kabul"} };
            foreach (var p in places) { Check(map.zoneAt(p.Lat, p.Lon) == p.Id, "Time zone " + p.Id); DisplayZone.of(p.Id); }
            long jan = new DateTimeOffset(2026,1,15,0,0,0,TimeSpan.Zero).ToUnixTimeMilliseconds();
            long jul = new DateTimeOffset(2026,7,15,0,0,0,TimeSpan.Zero).ToUnixTimeMilliseconds();
            var zagreb = DisplayZone.of("Europe/Zagreb"); Near(3600000, zagreb.offsetMillis(jan), 0, "Zagreb winter"); Near(7200000, zagreb.offsetMillis(jul), 0, "Zagreb summer");
            var ny = DisplayZone.of("America/New_York");
            foreach (var date in new[] { new DateTime(2026,3,7), new DateTime(2026,10,31) })
            {
                var n = NightCalculator.compute(38.89,-77.04,CivilDate.fromCivil(date.Year,date.Month,date.Day),ny,PlanSettings.defaults());
                Check(Formats.clockChange(n,ny) != null, "DST change note " + date);
            }
            Check(DisplayZone.letterFor(0) == "Z" && DisplayZone.letterFor(19800000) == "E*", "Military zone letters");
            Check(DisplayZone.letterFor(-14*3600000L)=="Y*" && DisplayZone.letterFor(14*3600000L)=="M*", "Out-of-band manual offsets retain an explicit nonstandard suffix");
            Check(map.zoneAt(double.NaN, 0) == null, "Invalid timezone input");
        }
        private static void RatingsAndWindows()
        {
            var zone = DisplayZone.of("America/New_York"); var ps = PlanSettings.defaults();
            var full = NightCalculator.compute(38.89,-77.04,CivilDate.fromCivil(2026,11,24),zone,ps);
            var dark = NightCalculator.compute(38.89,-77.04,CivilDate.fromCivil(2026,11,9),zone,ps);
            Check(full.illumPercent > 95 && full.level == NightReport.LightLevel.HLL && full.rating == NightReport.Rating.GREEN, "Full moon rating");
            Check(dark.illumPercent < 5 && dark.level == NightReport.LightLevel.LLL && dark.rating == NightReport.Rating.RED, "New moon rating");
            var conceal = ps.toBuilder().mission(PlanSettings.Mission.CONCEAL).build();
            Check(NightCalculator.compute(38.89,-77.04,dark.epochDay,zone,conceal).rating == NightReport.Rating.GREEN, "Concealment flips rating");
            Check(!LightStandard.ARMY.isHighLight(1,0,25,90) && !LightStandard.ARMY.isHighLight(1,0,60,20) && LightStandard.ARMY.isHighLight(0,0,35,30), "Army AND rule");
            Check(LightStandard.USAF.isHighLight(.0022,0,0,0) && !LightStandard.USN.isHighLight(.0021,0,90,100), "Lux threshold boundary");
            var custom = ps.toBuilder().window(PlanSettings.WindowMode.CUSTOM).customWindow(1320,240).build();
            var n = NightCalculator.compute(38.89,-77.04,CivilDate.fromCivil(2026,10,29),zone,custom);
            Check(n.windowEnd-n.windowStart == 21600000, "Overnight custom window");
            var local = NightCalculator.compute(35,69,n.epochDay,DisplayZone.nautical(69),ps);
            var remote = NightCalculator.compute(35,69,n.epochDay,DisplayZone.of("Pacific/Honolulu"),ps);
            Check(local.sunset == remote.sunset && local.sunrise == remote.sunrise && local.spanStart == remote.spanStart, "Device zone cannot cut a night short");
            n = NightCalculator.compute(69.65,18.96,CivilDate.fromCivil(2027,6,21),DisplayZone.nautical(18.96),ps);
            Check(n.sunUpAllSpan && !n.hasWindow() && n.rating == NightReport.Rating.NONE, "Midnight sun");
            var watch=Stopwatch.StartNew(); var plan=NightCalculator.range(38.89,-77.04,dark.epochDay,90,zone,ps); watch.Stop();
            Check(plan.Count == 90 && plan.Any(x=>x.rating==NightReport.Rating.GREEN) && plan.Any(x=>x.rating==NightReport.Rating.RED) && plan.Any(x=>x.rating==NightReport.Rating.AMBER), "90-night plan");
            Console.WriteLine("90-night plan: " + watch.ElapsedMilliseconds + " ms");
        }
        private static void CustomWindowDst()
        {
            var cases=new[] {
                new {Zone="America/New_York",Lat=38.89,Lon=-77.04,Y=2026,M=3,D=7,Hours=5},
                new {Zone="America/New_York",Lat=38.89,Lon=-77.04,Y=2026,M=10,D=31,Hours=7},
                new {Zone="Europe/Zagreb",Lat=45.815,Lon=15.982,Y=2026,M=3,D=28,Hours=5},
                new {Zone="Europe/Zagreb",Lat=45.815,Lon=15.982,Y=2026,M=10,D=24,Hours=7},
                new {Zone="Australia/Sydney",Lat=-33.87,Lon=151.21,Y=2026,M=4,D=4,Hours=7},
                new {Zone="Australia/Sydney",Lat=-33.87,Lon=151.21,Y=2026,M=10,D=3,Hours=5} };
            var ps=PlanSettings.defaults().toBuilder().window(PlanSettings.WindowMode.CUSTOM).customWindow(22*60,4*60).build();
            foreach(var c in cases)
            {
                var zone=DisplayZone.of(c.Zone); long day=CivilDate.fromCivil(c.Y,c.M,c.D);
                var n=NightCalculator.compute(c.Lat,c.Lon,day,zone,ps);
                Check(Formats.hhmm(n.windowStart,zone)=="2200" && Formats.hhmm(n.windowEnd,zone)=="0400",c.Zone+" custom wall-clock endpoints survive DST");
                Check(n.windowEnd-n.windowStart==c.Hours*3600000L,c.Zone+" custom DST duration");
            }
            var ny=DisplayZone.of("America/New_York");
            long repeated=CivilDate.localToUtc(CivilDate.fromCivil(2026,11,1),90,ny);
            Check(repeated==new DateTimeOffset(2026,11,1,5,30,0,TimeSpan.Zero).ToUnixTimeMilliseconds(),"Repeated 0130 uses the earlier occurrence");
            long skipped=CivilDate.localToUtc(CivilDate.fromCivil(2026,3,8),150,ny);
            Check(Formats.hhmm(skipped,ny)=="0330","Skipped 0230 moves forward by the DST gap");
            Check(DisplayZone.atPoint(null,45,15).describe(repeated).Contains("nautical"),"Missing timezone grid falls back with an accurate label");
        }
        private sealed class Ridge : HorizonProfile.ElevationSampler
        {
            private readonly double height;
            internal Ridge(double height) { this.height=height; }
            public void sample(double[] lat,double[] lon,double[] output)
            { for(int i=0;i<lat.Length;i++) { double east=(lon[i]+77.04)*111320*Math.Cos(38.89*Math.PI/180); output[i]=100+(Math.Abs(east-5000)<1000?height:0); } }
        }
        private sealed class Missing : HorizonProfile.ElevationSampler { public void sample(double[] lat,double[] lon,double[] output) { } }
        private static void TerrainAndShadows()
        {
            Check(HorizonProfile.build(38.89,-77.04,new Missing(),20000)==null,"Missing terrain does not imply a measured skyline");
            var flat=HorizonProfile.build(38.89,-77.04,new Ridge(0),20000);
            var ridge=HorizonProfile.build(38.89,-77.04,new Ridge(900),20000);
            Near(1,flat.coverage,1e-9,"Terrain coverage");
            Check(Math.Abs(flat.altitudeAt(90))<.1 && ridge.altitudeAt(90)>10 && ridge.altitudeAt(90)<14 && Math.Abs(ridge.altitudeAt(270))<.1,"East ridge skyline");
            long day=CivilDate.fromCivil(2026,10,29); var zone=DisplayZone.of("America/New_York"); var ps=PlanSettings.defaults();
            var a=NightCalculator.compute(38.89,-77.04,day,zone,ps); var b=NightCalculator.compute(38.89,-77.04,day,zone,ps,ridge);
            Check(b.terrainApplied && b.hllFraction<a.hllFraction && a.moonEvents[0].time==b.moonEvents[0].time,"Terrain masks light without changing astronomical moonrise");
            Near(a.hllFraction,b.doctrineHllFraction,1e-12,"Doctrine comparison");
            long terrainRise=b.terrainMoonEvents.First(x=>x.rise).time;
            Check(terrainRise-a.moonEvents[0].time>3600000,"Ridge delays effective moonrise");
            Check(b.shadows.terrainFraction>0 && b.shadows.lines(zone).Count>0,"Shadow outlook");
        }
    }
}
