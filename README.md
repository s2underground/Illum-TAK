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

1.0 in development (Oct 2026).

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

Unit tests (`./gradlew testCivDebugUnitTest`) check rise, set and twilight
times against an independent ephemeris (PyEphem) at 8 sites from the
equator to 70N, including midnight sun and polar night.

_________________________________________________________________
DEVELOPER NOTES

- core/ is pure Java (no Android): Ephemeris (sun and moon positions),
  Illuminance (USNO Circular 171), NightCalculator (events, window,
  stoplight), LightStandard, PlanSettings, DisplayZone, IllumPrefs.
- ui/ holds the pane (IllumPane), the TimelineView graphic and the Tool
  Preferences fragment.
- Accuracy: sun/moon events within about 15 seconds of the reference. The
  illuminance is a clear-sky estimate scaled by the Sky setting.
