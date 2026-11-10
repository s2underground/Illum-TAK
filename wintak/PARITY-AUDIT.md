# ATAK / WinTAK parity review: 1.0.0

Reviewed 10 November 2026 against the Android implementation in this checkout,
especially `IllumPane.java`, `IllumPrefs.java`, `TimelineView.java`,
`MoonPhaseView.java`, the preference resources and the numerical core.

## Features checked

| Area | Windows result |
| --- | --- |
| Sun, twilight, lunar events and azimuths | Ported; checked against the Android JPL and PyEphem reference CSVs. |
| Phase and illuminated disk | Banner and grid disks; waxing/waning and hemisphere orientation checked by rendered pixels. |
| Standards, missions, sky factor, night-sky light | All Android choices present. Custom defaults/limits now match Android. |
| Rated windows and tolerance | Nautical, sunset/sunrise and custom windows present; custom endpoints now respect DST independently. |
| Time modes | Point-local IANA time, Zulu, nautical and manual offsets present. Explicit Windows device time is an additional choice. |
| Timezone failure | Map failures no longer stop a plan; a labelled nautical fallback is shown. Other time modes do not load the grid. |
| Date selection | Previous night, Tonight and Next night restored. Recalculation retains the selected row; location changes retain a manually chosen evening. |
| Planning grid | All Android UI ranges (7, 14, 30, 45, 60, 90) supported, plus intermediate lengths. Four columns show date, rating, HLL and phase/illumination; rise/set and EENT/BMNT remain in selected-night details. |
| Key events | Solar/lunar event times, azimuths, moon maximum and peak moonlight shown. Distinct terrain clearance events now have cards. |
| Timeline | Twilight shading, visible/hidden moon strip, lux curve/threshold, event labels, mission strip and window brackets present. Windows also plots moon elevation. |
| Shadows and explanations | Shadow outlook retained. Restored polar notes, low-moon caution, HLL intervals, Army/custom moon-rule explanation and visible clock-change notes. |
| Terrain | Same 20 km / 2° / 48-sample skyline model, flat-terrain doctrine comparison and partial-coverage behavior. Added skyline/coverage and query-failure explanations. |
| Position selection | GPS, map center and manual coordinates present. Marker picks now use the marker anchor; ground click and long press supported. |
| Cancellation and lifecycle | Superseded calculations cannot replace the current plan. Cancel pick and unload restore event subscriptions/stack in adapter checks. |
| Invalid input | Old report, chart, grid and rating are cleared when validation fails. |
| Preferences | Atomic save retained; corrupted primary preferences now recover the backup, and invalid saved dropdown indices use defaults. |
| Control contrast | Explicit scoped WPF styles; checked against conflicting inherited host colors. |
| Mouse and keyboard | One outer scrollbar; proportional four-column grid without horizontal overflow at tested widths. Arrow/Home/End/Page navigation stays in the grid; Enter/double-click opens the report. Input errors have field messages and focus requests. Scoped shortcuts and a fixed map-picking banner are present. |
| Stable report layout | Shadow intervals/notes, event cards, banner and expanded twilight details have bounded heights. Visible grid rows retain their screen position when nights change; keyboard navigation reveals an offscreen destination only when necessary. Long details remain scrollable. |

## Differences retained

- Settings live inside the Windows pane and apply through **Calculate plan**.
  Android preferences trigger a debounced recalculation. Full twilight details
  are an expander instead of Android's preference-controlled display.
- Windows accepts decimal-degree coordinates and remembers the picked marker's
  name. Android uses ATAK's coordinate-format preference. Host coordinate-format
  integration remains unported.
- A manually selected first evening anchors the Windows grid. Android's grid
  stays anchored to tonight and can calculate a separate selected night outside it.
  Windows navigation shifts the grid when necessary.
- Windows requires a location button or coordinates on a fresh installation.
  Android tries self position and then map center automatically. GPS is a snapshot
  when **My position** is pressed.
- If civil timezone data is unavailable, Windows uses explicit nautical time.
  Android can prefer device time close to the self marker. Windows device time
  remains available as an explicit choice.
- Windows refreshes terrain on each calculation; Android caches the skyline at
  the same point. Row selection uses existing reports and makes no terrain query.
- Manual UTC offset input uses minutes on Windows, decimal hours on Android.
  Both allow UTC−14 through UTC+14. Nonstandard military suffixes have an asterisk.

## Correctness change beyond the initial port

Both the Android source and initial C# port created a custom window by adding a
fixed duration to its start. Across a clock change, **22:00–04:00** could end at
05:00 or 03:00. Windows now converts each local endpoint independently: that
window lasts five hours on a spring transition and seven on an autumn transition.
Tests cover New York, Zagreb and Sydney. Repeated clock times use the earlier
occurrence; skipped times move forward by the gap, using Noda Time's lenient
resolution. The Android source has not been changed by this Windows review.

## Evidence and outstanding checks

- Core validator: **4,632 assertions**, including **364 independent reference
  nights**, calendar, ratings, terrain, custom DST windows and timezone labels.
- SDK harness: real WPF XAML/rendering and installed SDK types, with stub host
  services. Checks contrast, phase pixels, navigation, settings/row retention,
  GPS adapter, actual `MapMarker` anchor selection, long press, pick cleanup,
  stale-result clearing, cancellation, IANA/DST/fallback behavior, partial/missing
  elevation and preference recovery.
- Usability checks render 300/380/520/760 pixel panes, measure all grid text in a
  90-night plan for clipping, and assert there is no hidden horizontal overflow.
  They exercise WPF routed keyboard events, sorted navigation, outer wheel
  scrolling, explicit report access, inline validation, collapsed-section error
  handling, invalid dates, picking Escape through WPF's input manager, visual
  unload cleanup and remembered expanded sections. These run without a visible
  window; real focus and interactions with WinTAK ribbon shortcuts still need host testing.
- The user's earlier WinTAK screenshot confirms the plugin can load and open.
  The 0.3.0 screenshot also exposed layout movement from changing shadow text.
  Layout regressions now compare 90 terrain nights at three pane widths and polar
  reports, check row screen coordinates, rapid selection, focus scrolling,
  deliberate report navigation and internal/outer mouse-wheel handoff.
  Automated rendering is windowless; it does not prove this build's interactive
  behavior inside the running host.

Still test in WinTAK: WPK update/restart and toolbar placement; actual map center
and GPS with/without a fix; map gestures and normal interaction after closing or
unloading the pane; a real DTED/SRTM ridge and partial/no elevation coverage;
dropdown/calendar interaction, narrow-pane scrolling, and unload/reload while a
calculation is running.
