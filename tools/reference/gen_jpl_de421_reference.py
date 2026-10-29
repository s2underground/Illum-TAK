"""JPL DE421 (via Skyfield) reference events for Illum unit tests.
USNO conventions: rise/set = upper limb at -34' refraction (Skyfield
find_risings/find_settings defaults); twilights = sun centre at -12 deg.

Regenerate:  pip install skyfield skyfield-data
             python3 gen_jpl_de421_reference.py > ../../app/src/test/resources/jpl_de421_reference.csv
"""
import datetime as dt
from skyfield import almanac
from skyfield.api import load, wgs84
import os, skyfield_data
ts = load.timescale(builtin=True)
eph = load(os.path.join(skyfield_data.get_skyfield_data_path(), 'de421.bsp'))
sun, moon, earth = eph['sun'], eph['moon'], eph['earth']
sites = [("DC",38.89,-77.04),("FtLiberty",35.14,-79.00),("Kabul",34.53,69.17),
         ("Sydney",-33.87,151.21),("Nairobi",-1.29,36.82),("Fairbanks",64.84,-147.72),
         ("Tromso",69.65,18.96),("Honolulu",21.31,-157.86),("Ushuaia",-54.80,-68.30),
         ("Reykjavik",64.15,-21.94)]
d0 = dt.date(1970,1,1)
dates = [dt.date(2026,11,3) + dt.timedelta(days=127*i) for i in range(30)]
def ms(t): return int(round((t.utc_datetime() - dt.datetime(1970,1,1,tzinfo=dt.timezone.utc)).total_seconds()*1000))
print("# name,lat,lon,epochDay,kind,utcMillis,value")
for name, lat, lon in sites:
    off = max(-12, min(12, round(lon/15.0)))
    topo = wgs84.latlon(lat, lon)
    obs = earth + topo
    for d in dates:
        ed = (d - d0).days
        s0 = dt.datetime(d.year,d.month,d.day,12,tzinfo=dt.timezone.utc) - dt.timedelta(hours=off)
        t0 = ts.from_datetime(s0); t1 = ts.from_datetime(s0 + dt.timedelta(days=1))
        def first(times, flags, after=None):
            for t, f in zip(times, flags):
                if f and (after is None or t.tt > after.tt): return t
            return None
        st, sf = almanac.find_settings(obs, sun, t0, t1)
        rt, rf = almanac.find_risings(obs, sun, t0, t1)
        sunset = first(st, sf); sunrise = first(rt, rf, sunset)
        nt, nf = almanac.find_settings(obs, sun, t0, t1, horizon_degrees=-12.0)
        nr, nrf = almanac.find_risings(obs, sun, t0, t1, horizon_degrees=-12.0)
        eent = first(nt, nf); bmnt = first(nr, nrf, eent)
        for k, v in (("sunset",sunset),("eent",eent),("bmnt",bmnt),("sunrise",sunrise)):
            print(f"{name},{lat},{lon},{ed},{k},{ms(v) if v is not None else 'NONE'},")
        mr, mrf = almanac.find_risings(obs, moon, t0, t1)
        ms_, msf = almanac.find_settings(obs, moon, t0, t1)
        for t, f in zip(mr, mrf):
            if f: print(f"{name},{lat},{lon},{ed},moonrise,{ms(t)},")
        for t, f in zip(ms_, msf):
            if f: print(f"{name},{lat},{lon},{ed},moonset,{ms(t)},")
        tm = ts.from_datetime(s0 + dt.timedelta(hours=12))
        frac = almanac.fraction_illuminated(eph, 'moon', tm)
        print(f"{name},{lat},{lon},{ed},illum,{ms(tm)},{frac:.5f}")
