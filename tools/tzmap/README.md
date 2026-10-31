# Time zone map

`app/src/main/assets/tzmap.bin` maps latitude/longitude to IANA time zone ids
on a 0.025° grid (about 3 km). Illum reads it with `core/TzMap` and takes
offsets and daylight saving rules from the device's own time zone database.

Source: [timezone-boundary-builder](https://github.com/evansiroky/timezone-boundary-builder)
release 2026e, `timezones-with-oceans.geojson.zip`. Data (c) OpenStreetMap
contributors, Open Database License (ODbL) 1.0. The derived `tzmap.bin` is
made available under the ODbL 1.0.

Rebuild (needs Python 3, numpy, pillow):

    python3 build_tzmap.py combined-with-oceans.json 0.025 ../../app/src/main/assets/tzmap.bin

Checked against the source polygons at 40,000 random points: the zone differs
only within about one grid cell of a border or coast, plus the disputed
Kashmir area. Where Asia/Urumqi overlaps Asia/Shanghai, Shanghai (China's
official time) is used.
