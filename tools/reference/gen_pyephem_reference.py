"""Reference night events from PyEphem for Illum unit tests.
Conventions match USNO: sun/moon rise-set = upper limb at -34' refraction
(pressure 0, horizon -0:34); twilights = center at -6/-12/-18.

Regenerate:  pip install ephem
             python3 gen_pyephem_reference.py > ../../app/src/test/resources/pyephem_reference.csv
"""
import ephem, math, datetime, sys
locs = [("DC",38.89,-77.04),("FtLiberty",35.14,-79.00),("Kabul",34.53,69.17),
        ("Sydney",-33.87,151.21),("Nairobi",-1.29,36.82),("Fairbanks",64.84,-147.72),
        ("Tromso",69.65,18.96),("Honolulu",21.31,-157.86)]
# epoch days (days since 1970-01-01)
d0 = datetime.date(1970,1,1)
dates = [datetime.date(2026,10,29), datetime.date(2026,12,21), datetime.date(2027,3,20),
         datetime.date(2027,6,21), datetime.date(2027,8,5), datetime.date(2026,11,24),
         datetime.date(2028,2,10), datetime.date(2030,9,15)]
def ms(d): return int(round((ephem.Date(d).datetime()-datetime.datetime(1970,1,1)).total_seconds()*1000))
def obs(lat,lon,h,t):
    o=ephem.Observer(); o.lat=str(lat); o.lon=str(lon); o.elevation=0; o.pressure=0
    o.horizon=h; o.date=t; return o
def events(body, lat, lon, h, center, t0, t1, rising):
    out=[]; t=t0
    while True:
        o=obs(lat,lon,h,t)
        try:
            e = (o.next_rising if rising else o.next_setting)(body, use_center=center)
        except (ephem.AlwaysUpError, ephem.NeverUpError):
            return out
        if e > t1: return out
        out.append(e); t = ephem.Date(e + ephem.minute)
print("# name,lat,lon,epochDay,kind,utcMillis")
for name,lat,lon in locs:
    off = round(lon/15.0); off = max(-12,min(12,off))
    for d in dates:
        ed = (d-d0).days
        span0 = ephem.Date(datetime.datetime(d.year,d.month,d.day,12) - datetime.timedelta(hours=off))
        span1 = ephem.Date(span0 + 1.0)
        def first(lst): return lst[0] if lst else None
        rows=[]
        sunset = first(events(ephem.Sun(),lat,lon,'-0:34',False,span0,span1,False))
        eent = first(events(ephem.Sun(),lat,lon,'-12',True,span0,span1,False))
        bmnt = first(events(ephem.Sun(),lat,lon,'-12',True, eent if eent else span0,span1,True))
        sunrise = first(events(ephem.Sun(),lat,lon,'-0:34',False, sunset if sunset else span0,span1,True))
        for k,v in (("sunset",sunset),("eent",eent),("bmnt",bmnt),("sunrise",sunrise)):
            print(f"{name},{lat},{lon},{ed},{k},{ms(v) if v else 'NONE'}")
        for e in events(ephem.Moon(),lat,lon,'-0:34',False,span0,span1,True):
            print(f"{name},{lat},{lon},{ed},moonrise,{ms(e)}")
        for e in events(ephem.Moon(),lat,lon,'-0:34',False,span0,span1,False):
            print(f"{name},{lat},{lon},{ed},moonset,{ms(e)}")
