# My ATAK / WinTAK comparison for Illum 1.0.0

I compared the Windows port with the Android implementation, including
`IllumPane.java`, `IllumPrefs.java`, `TimelineView.java`, `MoonPhaseView.java`,
the preference resources and the numerical core. I kept the calculations close
to the Java version and adapted the controls and layout for a desktop pane.

## Features I checked

| Area | Windows result |
| --- | --- |
| Sun, twilight, lunar events and azimuths | I ported the calculations and checked them against the existing JPL/Skyfield and PyEphem CSVs. |
| Moon phase graphics | The banner and grid show illuminated disks. I checked waxing/waning and hemisphere orientation with rendered-pixel tests. |
| Standards, missions, sky factor and night-sky light | The Android choices are present, with matching custom defaults and limits. |
| Rated windows and tolerance | Nautical, sunset/sunrise and custom windows are available. Each custom endpoint is resolved independently across DST. |
| Time modes | Point-local IANA, Zulu, nautical and manual offsets are present. I also added an explicit Windows device-time choice. |
| Timezone fallback | If the timezone grid is unavailable, the report labels the nautical fallback. Other time modes don't need the grid. |
| Date selection | Previous night, Tonight and Next night are available. Recalculation keeps the selected row; location changes keep a manually chosen evening. |
| Planning grid | I support Android's 7, 14, 30, 45, 60 and 90 nights, plus intermediate lengths. Four columns show date, rating, HLL and moon phase/illumination. Rise/set and EENT/BMNT remain in the selected-night details. |
| Key events | Cards show solar/lunar times and azimuths, moon maximum, peak moonlight and distinct terrain clearance events. |
| Timeline | Twilight shading, visible/hidden moon intervals, lux curve and threshold, event labels, mission strip and rated-window brackets are present. I also plot moon elevation. |
| Shadow outlook and explanations | The report includes shadow outlook, polar notes, low-moon caution, HLL intervals, the Army/custom moon rule and clock-change notes. |
| Terrain | I kept the 20 km, 2-degree, 48-sample skyline model, flat-terrain comparison and partial-coverage behavior. The report includes skyline, coverage and query-failure explanations. |
| Location | GPS, map center, manual coordinates, ground clicks and long presses are supported. Marker picking uses the anchor and remembers the marker name. |
| Cancellation and lifecycle | An older calculation can't replace a newer plan. I checked that canceling a pick and unloading the pane release the map handlers. |
| Invalid input | The previous report, chart, grid and rating clear when validation fails. Errors identify the field that needs attention. |
| Preferences | I kept atomic saves, added recovery from the backup if the primary file is damaged, and use defaults for invalid saved dropdown indices. |
| Contrast | I gave the controls scoped dark styles and checked them against conflicting inherited host colors. |
| Mouse and keyboard | The four-column grid fits the tested pane widths. Arrow/Home/End/Page keys navigate the grid; Enter or a double-click opens the report. The pane has scoped shortcuts and a fixed picking indicator. |
| Report layout | Long shadow details, event cards, the banner and expanded twilight text have bounded heights. Changing nights preserves a visible grid row's position; navigation reveals an offscreen destination when needed. |

## Differences I kept

- I put settings inside the Windows pane and apply them through **Calculate
  plan**. Android recalculates after a short delay when preferences change.
  Full twilight details are an expander in Windows.
- I accept decimal-degree coordinates and keep a picked marker's name. Android
  uses ATAK's coordinate-format preference. I haven't integrated that host
  coordinate-format setting on Windows.
- A manually selected first evening anchors the Windows grid. Android keeps
  the grid anchored to tonight and can calculate a separate night outside it.
  Windows navigation shifts the grid when necessary.
- On a fresh installation, Windows needs a location button or coordinates.
  Android tries self position, then map center automatically. In Windows,
  **My position** takes a snapshot when pressed.
- When civil timezone data is unavailable, I use an explicit nautical fallback
  on Windows. Android can prefer device time near the self marker. Windows
  device time remains available as a separate choice.
- I refresh terrain on each calculation. Android caches the skyline at the
  same point. Selecting an existing Windows report makes no new terrain query.
- I use minutes for manual UTC offsets in Windows; Android uses decimal hours.
  Both allow UTC-14 through UTC+14. Nonstandard military suffixes have an asterisk.

## Custom windows across DST

I found that adding a fixed duration to a custom window's start could move its
end away from the requested clock time. For example, 22:00-04:00 could end at
05:00 or 03:00 across a clock change.

In the Windows port, I resolve both local endpoints independently. The window
then lasts five hours on a spring transition and seven on an autumn transition
while keeping the requested clock times. I added tests for New York, Zagreb and
Sydney. Repeated times use the earlier occurrence; skipped times move forward
by the gap, following Noda Time's lenient resolution. I haven't changed the
Android implementation.

## How I checked the comparison

I ran **4,632 core assertions**, including **364 independent reference nights**,
plus calendar, rating, terrain, custom DST-window and timezone-label checks.

For the UI, I used real WPF controls and installed SDK types with stub services.
I checked bindings, contrast, moon graphics, navigation, selected-row retention,
marker anchors, long press, picking cleanup, stale-result clearing,
cancellation, timezone fallback, missing/partial elevation and preference
recovery.

The layout checks render 300, 380, 520 and 760 pixel panes and check grid text
for clipping. They include 90-night terrain and polar plans, long and short
reports, rapid selection, sorting, keyboard events, input errors, saved section
expansion and report navigation. These checks run without a visible host window.

I also tested the plugin in WinTAK, including upgrades, restart and preferences,
ground and marker picking, cancellation, closing during calculation, keyboard
controls, the narrow grid, terrain fallback and unload/reload. I keep the
completed checks, package scope and remaining hardware/deployment checks in
[RELEASE-CHECKLIST.md](RELEASE-CHECKLIST.md).
