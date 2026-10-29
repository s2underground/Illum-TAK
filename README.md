Illum


_________________________________________________________________
PURPOSE AND CAPABILITIES

Illum is an ATAK plugin for night light planning. For any location (your
position, the map center, or a point or marker you tap) it shows:

- Sunset, EENT, BMNT and sunrise (plus civil and astronomical twilight)
- Moonrise and moonset with azimuth, and moon illumination and phase
- Estimated natural illuminance through the night (USNO Circular 171 model)
- A green/amber/red stoplight saying whether the night is High Light Level
  (HLL), Low Light Level (LLL) or mixed, under a selectable standard:
  USAF (2.2 millilux), USN/USMC (0.0022 lux), Army (23% moon, 30 deg up)
  or custom values, for an NVG/aviation or a ground-concealment mission
- A planning grid of 7 to 90 nights

All data is computed on the device from astronomical formulas (Meeus,
"Astronomical Algorithms"). There are no downloads and no network use, so
Illum works offline indefinitely.

Settings: Settings > Tool Preferences > Specific Tool Preferences >
Illum Preferences, or the Settings button in the pane.

_________________________________________________________________
STATUS

1.0 released Oct 29, 2026. Signed by the TAK.gov Third Party Pipeline
(built against ATAK-CIV 5.6.0.26; Fortify scan: 0 issues).

_________________________________________________________________
POINT OF CONTACTS

S2Underground - s2underground@protonmail.com

_________________________________________________________________
PORTS REQUIRED

None. Illum makes no network connections.

_________________________________________________________________
EQUIPMENT REQUIRED

None.

_________________________________________________________________
EQUIPMENT SUPPORTED

Any device running ATAK-CIV 5.6.0 or newer.

_________________________________________________________________
COMPILATION

Standard ATAK plugin build. `./gradlew assembleCivRelease` for release.
For the 5.5.1 developer build of ATAK, build with
`./gradlew assembleCivDebug -PatakVersion=5.5.0`.

Unit tests (`./gradlew testCivDebugUnitTest`) check sunset, EENT, BMNT,
sunrise, moonrise, moonset and moon illumination against NASA JPL's DE421
ephemeris (via Skyfield) for 300 nights at 10 sites from 55S to 70N
(2026-2036), and against PyEphem for 64 more nights, including midnight sun
and polar night. Typical agreement is a few seconds; the worst cases (about
a minute) are high-latitude nights when the sun barely dips below 12 deg.
The scripts that generate the reference tables are in tools/reference/.

_________________________________________________________________
DEVELOPER NOTES

- core/ is pure Java (no Android): Ephemeris (sun and moon positions),
  Illuminance (USNO Circular 171), NightCalculator (events, window,
  stoplight), LightStandard, PlanSettings, DisplayZone, IllumPrefs.
- ui/ holds the pane (IllumPane), the TimelineView graphic and the Tool
  Preferences fragment.
- Accuracy: see COMPILATION above. The illuminance is a clear-sky estimate
  scaled by the Sky setting.

_________________________________________________________________
ACKNOWLEDGMENTS

- Sun and moon position algorithms: Jean Meeus, "Astronomical Algorithms",
  2nd ed. (Willmann-Bell, 1998), chapters 12, 22, 25, 47 and 48.
- Illuminance model: P. M. Janiczek and J. A. DeYoung, "Computer Programs
  for Sun and Moon Illuminance", U.S. Naval Observatory Circular 171 (1987),
  public domain.
- No third-party code or libraries are bundled in the plugin.
