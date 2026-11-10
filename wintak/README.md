# Illum for WinTAK 1.0.0

Targets the **WinTAK-CIV 5.8.0.149 developer SDK**, x64, .NET Framework 4.8.
The WPK built here is unsigned. It is not a TAK.gov-signed release. The developer
host is the validated target; signed distribution and loading in other host builds
must be verified separately for the intended distribution channel.

See [INSTALL.md](INSTALL.md) for installation, upgrades and keyboard controls,
[RELEASE-NOTES.md](RELEASE-NOTES.md) for the v1.0.0 scope, and
[RELEASE-CHECKLIST.md](RELEASE-CHECKLIST.md) for validation and distribution checks.

The Windows projects live here alongside the Android sources. The numerical
port retains the Java core's formulas, units, field names and sampling rules
to make comparison practical. Android builds continue to use `app/`.

## Included

- Ribbon button and WPF dockable pane.
- Location from GPS, map center, clicking/long-pressing a map point or marker, or decimal degrees.
- Sunset/sunrise, civil/nautical/astronomical twilight, lunar rise/set and azimuth,
  illumination/phase, HLL/LLL/mixed ratings and mission-dependent colors.
- USAF, Navy/USMC, Army and custom standards; sky conditions, night-sky light,
  custom clock windows and mixed-light tolerance.
- Offline local civil time and DST at the selected point using the existing
  timezone grid plus Noda Time's bundled IANA rules. Zulu, device, nautical and
  manual-offset display options; clock-change notes.
- 7–90-night grid with four columns that fit the pane, selectable night details, previous/next/
  tonight navigation, key-time cards, HLL intervals and
  shadow outlook. Moon-phase disks appear in the banner and grid, including
  waxing/waning and southern-hemisphere mirroring.
- Timeline with twilight shading, moon-visible/terrain-hidden strip, event labels,
  illuminance and moon-altitude curves, lux threshold, mission-color strip and
  rated-window brackets. The chart focuses on sunset through sunrise where available.
- Explicit dark control styles for buttons, dropdowns, editable fields, date
  picker and planning grid, independent of inherited WinTAK control colors.
- One outer scrolling surface, keyboard grid navigation, Enter/double-click
  report access, field-level validation/focus, pane shortcuts and a fixed map-picking
  indicator. The selected location includes a picked marker's name where available.
- Optional terrain skyline sampling through WinTAK `IElevationManager`, restricted
  to bare-terrain model data. Missing data is reported; the flat-terrain doctrine
  rating remains visible alongside terrain-adjusted results.
- Background calculations, cancellation of superseded plans, map-event cleanup,
  and atomically saved preferences under `%APPDATA%/WinTAK/Illum/`.

## Build and validate

Requirements: Windows, Visual Studio's .NET desktop development tools (or MSBuild
and the .NET Framework 4.8 targeting pack), a current .NET SDK, and the matching
WinTAK developer SDK. NuGet restores the pinned NodaTime dependency on first build;
the plugin itself makes no network requests. WinTAK assemblies are referenced
from the installation and excluded from the plugin package.

From the repository root:

```powershell
.\wintak\build.ps1
# For a non-default SDK directory:
.\wintak\build.ps1 -WinTakDir 'D:\SDKs\WinTAK'
```

The script builds and runs the core reference validator, compiles the WPF plugin,
validates the pane with stub host services and renders `artifacts/Illum-preview.png`,
packages it using the installed `WpkBuilder.exe`, verifies the package's SDK and
Civilian variant metadata, and includes third-party license notices. Outputs:

```text
wintak/artifacts/Illum-1.0.0.0-5.8.0.149.wpk
wintak/artifacts/Illum-1.0.0.0-5.8.0.149.wpk.sha256
wintak/Illum.WinTAK/bin/Release/net48/
```

Install the WPK with WinTAK's Plug-in Manager and enable **Illum**. The SDK's
developer host is the intended target for this unsigned build. For direct local
deployment while WinTAK is closed:

```powershell
.\wintak\build.ps1 -Install
```

This copies only Illum and Noda Time into `%APPDATA%/WinTAK/Plugins/Illum/`.
Depending on the host's plugin discovery settings, enable it in Plug-in Manager;
copying DLLs by itself does not prove the plugin has been loaded. The host supplies
`System.Runtime.CompilerServices.Unsafe`; it is not included in the WPK.

Open `Illum.sln` for editing/debugging. Configure the plugin project's debugger to
launch the installed `WinTAK.exe`, with its installation directory as the working
directory. Close WinTAK before replacing loaded assemblies.

## Validation and remaining host checks

The core validator uses the unchanged Android CSV reference data:

- JPL DE421/Skyfield: **300 nights**, including illumination and event presence.
- PyEphem: **64 nights**, including polar night and midnight sun.
- Same event tolerances as the Android tests: sun 60 seconds (180 seconds for
  grazing JPL twilight), moon 120 seconds, illumination 0.5 percentage points.
- Calendar round trips, IANA lookup, winter/summer offsets and DST-change notes,
  mission inversion, threshold boundaries, remote-display-zone independence,
  custom windows, 90-night planning, terrain coverage and ridge masking.

Current validation: **4,632 assertions pass**. Worst event errors: JPL sun 58 s,
moon 7.406 s; PyEphem sun 15.905 s, moon 13.918 s. Build: zero warnings/errors.

The SDK-backed, windowless pane validator also checks actual WPF XAML creation,
valid WPF names for ribbon/pane registration IDs, bindings and rendering,
manual-coordinate plans, changing the selected night,
invalid input, Zagreb's DST transition, cancellation of superseded plans and
preference persistence. GPS, map and elevation services are stubbed in this check.
It checks control text contrast under conflicting inherited host colors, moon
illuminated area at new/crescent/quarter/gibbous/full phases, waxing/waning and
hemisphere orientation, and timeline bindings to the selected light standard.
Rendered previews include the main pane, planning grid and dropdown/calendar controls.
The usability checks cover 300, 380, 520 and 760 pixel panes, unclipped grid text,
90-night plans, sorting, routed arrow/Home/End/Page/Enter/Escape events, wheel
scrolling, inline validation, invalid date handling and saved section expansion.
No visible host window is created by these checks.

The user's earlier WinTAK screenshot confirms Illum loads and opens. This build
still needs interactive host testing; the SDK harness uses stub GPS, map and
elevation services. See [PARITY-AUDIT.md](PARITY-AUDIT.md) for the feature comparison,
test evidence, retained differences and outstanding host checks.

### Development history: 0.1.1 loading fix

WinTAK assigns registered tool IDs to WPF `FrameworkElement.Name`. The dotted
button ID in 0.1.0 (`S2U.Illum.Button`) caused `CommandManager.OnImportsSatisfied`
to fail, preventing plugin composition. Both ribbon and pane IDs now use
underscores. The validation harness reproduces the old exception and exercises
WPF's actual name validator against both registration attributes.

Install the current WPK, then restart WinTAK to rebuild its command catalog after
the failed composition. Other plugins can report errors through the same shared
command manager while it is in the failed state.

### 0.2.0 contrast and visual parity

The initial Windows port omitted ATAK's phase disks and simplified the timeline.
This version restores the phase graphic in the report banner and each planning
row, twilight backgrounds, visible/hidden moon strip, SS/EENT/BMNT/SR/MR/MS labels,
lux threshold and mission-dependent rating strip. It also adds a compact key-time
summary and an abbreviations key. Detailed twilight times and HLL intervals are
under a report expander.

The Windows layout still differs from Android: settings are inline in the pane,
and report information is arranged as cards and expandable details. The numerical
models and offline calculation behavior were unchanged by the visual update.

### 0.2.1 parity and correctness review

Restores date navigation, row rise/set times, peak/maximum and terrain event cards,
and missing report explanations. Fixes marker anchor selection, stale ratings on
invalid input, timezone fallback, preference backup recovery and custom-window
endpoints across DST. Custom input defaults and limits now match Android. The
expanded SDK harness exercises these paths with isolated test preferences and
stub services; the full comparison is in [PARITY-AUDIT.md](PARITY-AUDIT.md).

### 0.3.0 mouse and keyboard usability

The planning grid uses four proportional columns: Evening, Light, HLL % and Moon.
The Moon column contains a phase disk and illumination percentage; full phase
text is available in its tooltip and the selected report. EENT/BMNT, moonrise/set
and azimuths remain in the report cards and full twilight details. The grid has
no horizontal overflow at the tested widths. One outer scrollbar handles the
entire plan, including the wheel while the pointer is over grid rows.

Up/Down, Home/End and Page Up/Down select nights without jumping to the top.
Enter, double-click or **Back to selected report** opens the report above.
Tab leaves the grid. Numeric column sorting follows values rather than formatted
text, and keyboard navigation follows the displayed sort order.

While focus is inside Illum, Enter in numeric fields, Ctrl+Enter or Alt+C calculates. Enter in a dropdown/date
editor retains its normal behavior. Alt+P uses GPS, Alt+M uses map center, Alt+K
toggles picking, and Alt+R opens the selected report. Escape is intercepted only
while Illum owns map picking, including when the map has focus. Closing/unloading
the pane releases that hook. The picking indicator stays above the scrolling area.

Errors appear beside the offending field, clear the previous result, open a
collapsed custom-options section when necessary and request focus on that field.
Expanded options, twilight and help sections survive restart. WinTAK continues
to manage the dock pane's position and size.

### 0.3.1 stable report layout

Shadow outlook has a fixed-height area with the summary above scrollable intervals
and notes. Terrain/doctrine explanations and the standard reference share that
area. Long text remains available without moving the planning grid.

The banner and key-time cards also have stable heights for the pane width; long
content scrolls within them. Expanded twilight details have a fixed-height
viewport. Detail scroll positions reset when switching nights, and the wheel
returns to the outer pane at a detail area's top/bottom boundary.

Night selection preserves a visible grid row's screen position after layout,
including rapid changes and residual height changes elsewhere above the grid.
Already visible rows ignore native focus scrolling. Arrow/Home/End/Page navigation
only scrolls when necessary to reveal its destination. Enter/double-click report
access and deliberate scrolling take precedence over a pending viewport restore.

Regression checks cover every night in a 90-night terrain plan at 300/380/520 px,
polar plans spanning rated/unrated nights, changing card counts, long/short shadow
text, visible and offscreen keyboard selection, rapid changes and wheel handoff.
They also introduce an extra height change above the grid and measure whether
the same row remains at the same screen position.

Before release, check:

1. WPK installs/enables; Illum appears on the Plugins ribbon and opens its pane.
2. Map-center and manual-coordinate plans, date/row selection and timeline layout.
3. Pick/cancel, picking a marker, closing while picking, closing/reopening during
   a calculation, plugin unload/reload. Normal map interaction must be restored.
4. GPS absent/present, custom standards/windows, all time modes and DST nights.
5. Terrain with a real DTED/SRTM ridge, missing/partial data, and doctrine comparison.
6. Preferences survive restart; invalid input never produces a new rating.
7. Actual keyboard focus/Tab order, shortcut interactions with host ribbon commands,
   calendar/dropdown behavior, double-click report access and high-DPI pane sizing.

## Structure

`Illum.Core/`: platform-independent C# calculations and timezone handling.

`Illum.WinTAK/`: MEF registration, WPF pane, drawing and WinTAK adapters.

`Illum.Tests/`: executable validation on .NET Framework 4.8; no device or WinTAK
process needed. It exits nonzero on any failed assertion.

`Illum.SdkCheck/`: windowless WPF/SDK validation and preview rendering, using
isolated test preferences and stub host services.

`build.ps1`: validation, packaging and optional local deployment.

See `THIRD-PARTY-NOTICES.txt` and `licenses/`. The timezone grid retains its ODbL
license and rebuild instructions in the repository's `tools/tzmap/` directory.
