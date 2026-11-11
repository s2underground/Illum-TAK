# Illum for WinTAK 1.0.0

I ported Illum to WinTAK so I could use its offline night light planning on
Windows. I kept the Java core's formulas, units, field names and sampling rules
where possible, which makes it easier to compare the two implementations.

I build and test this port with **WinTAK-CIV 5.8.0.149**, using the developer SDK
on Windows x64 with .NET Framework 4.8. The WPK is unsigned. I haven't verified
other host versions or production signing requirements.

The Windows projects live under `wintak/`. Android builds still use `app/`.
For installation and controls, see [INSTALL.md](INSTALL.md). I describe this
release in [RELEASE-NOTES.md](RELEASE-NOTES.md), compare the two platforms in
[PARITY-AUDIT.md](PARITY-AUDIT.md), and keep the testing record in
[RELEASE-CHECKLIST.md](RELEASE-CHECKLIST.md).

## What is included

- A ribbon button and dockable WPF pane.
- GPS, map-center, ground and marker picking, plus decimal-degree coordinates.
- Sunset, sunrise, civil/nautical/astronomical twilight, lunar rise/set and
  azimuths, phase and illumination, and HLL/LLL/mixed ratings.
- USAF, Navy/USMC, Army and custom standards, with NVG/aviation and ground
  concealment missions, sky settings, night-sky light and custom rated windows.
- Offline local-at-point time and DST, using the existing timezone grid and
  Noda Time's IANA rules. Zulu, device, nautical and manual-offset modes are
  also available, with notes when a night crosses a clock change.
- A 7-90-night grid, previous/next/tonight navigation, key-time cards, HLL
  intervals and shadow outlook. Moon graphics show waxing/waning and mirror
  for the southern hemisphere.
- A timeline with twilight shading, visible and terrain-hidden moon intervals,
  event labels, illuminance and moon elevation, the lux threshold, mission
  ratings and rated-window brackets.
- Optional bare-ground terrain analysis through WinTAK's `IElevationManager`.
  The report shows missing coverage and keeps the flat-terrain comparison.
- Keyboard navigation, shortcuts, inline input errors and a four-column grid
  that fits the tested pane widths without a horizontal scrollbar.

I gave the controls explicit dark styles so their text stays readable when
WinTAK supplies different inherited colors. Long report sections have bounded
heights, and changing nights preserves the grid position. Calculations run in
the background; starting a new plan cancels the previous one. Preferences are
saved atomically under `%APPDATA%/WinTAK/Illum/`.

## Build and run

You'll need Windows, a .NET SDK, the .NET Framework 4.8 targeting pack and the
matching WinTAK developer SDK. Visual Studio's .NET desktop development tools
provide the desktop build tools. NuGet restores the pinned Noda Time dependency
on the first build. Illum itself makes no network requests.

From the repository root:

```powershell
.\wintak\build.ps1
# If the SDK is installed elsewhere:
.\wintak\build.ps1 -WinTakDir 'D:\SDKs\WinTAK'
```

I use this script to run the core tests, build the plugin, check the WPF pane
against the installed SDK, and package the result with `WpkBuilder.exe`. The
script also renders `artifacts/Illum-preview.png`, checks the package metadata,
contents and DLL hashes, adds the dependency notices, and writes a SHA-256
checksum. WinTAK's own assemblies stay out of the package.

Outputs:

```text
wintak/artifacts/Illum-1.0.0.0-5.8.0.149.wpk
wintak/artifacts/Illum-1.0.0.0-5.8.0.149.wpk.sha256
wintak/Illum.WinTAK/bin/Release/net48/
```

Import the WPK through Plug-in Manager, or drop it onto WinTAK, then enable
**Illum**. Restart WinTAK after an upgrade. The install guide also explains how
to add the button if your saved toolbar layout doesn't show it.

For a local development copy, close WinTAK first, then run:

```powershell
.\wintak\build.ps1 -Install
```

This copies the plugin, Noda Time, debug symbols and notices into
`%APPDATA%/WinTAK/Plugins/Illum/`. Enable Illum in Plug-in Manager if needed.
WinTAK supplies `System.Runtime.CompilerServices.Unsafe`; I don't bundle it.

Open `wintak/Illum.sln` to work on the port. To debug it, configure the plugin
project to launch the installed `WinTAK.exe` with its installation directory
as the working directory. Close WinTAK before replacing loaded DLLs.

## How I tested it

For the 1.0.0 release, I ran **4,632 core assertions**, including **364 independent
reference nights** from the existing Android CSV datasets:

- **300 JPL DE421/Skyfield nights**, checking event times, illumination and
  whether events occur.
- **64 PyEphem nights**, including polar night and midnight sun.
- The Android tests' event tolerances: 60 seconds for the sun, 180 seconds for
  grazing JPL twilight, 120 seconds for the moon, and 0.5 percentage points for
  illumination.

The largest event errors were 58 s for JPL sun events and 7.406 s for JPL moon
events; PyEphem errors were 15.905 s and 13.918 s respectively. The builds
finished with zero warnings or errors.

I also tested calendar round trips, timezone lookup, DST, mission ratings,
threshold boundaries, custom windows, 90-night plans, terrain coverage and
ridge masking. Custom-window tests cover clock changes in New York, Zagreb
and Sydney.

For the UI checks, I use real WPF controls and installed SDK types with stubbed
GPS, map and elevation services. These tests run without a visible WinTAK
window. They cover registration names, bindings, contrast, moon graphics,
timeline settings, picking cleanup, stale-result handling, cancellation and
preference recovery. Layout checks use 300, 380, 520 and 760 pixel panes,
including 90-night terrain and polar plans. Routed input tests cover grid
navigation, sorting, shortcuts, date selection and validation.

In WinTAK itself, I tested upgrades, restart and preference persistence,
ground and marker picking, cancellation, closing during a 90-night terrain
calculation, keyboard controls, narrow-pane layout, terrain fallback and
plugin unload/reload. The [release checklist](RELEASE-CHECKLIST.md) records
which checks passed, the package scope, and what I haven't verified, including
live GPS hardware, every elevation coverage case and other display scaling.

## Project layout

| Path | Purpose |
| --- | --- |
| `Illum.Core/` | Platform-independent calculations and timezone handling. |
| `Illum.WinTAK/` | Plugin registration, WPF pane, drawing and WinTAK adapters. |
| `Illum.Tests/` | Core validation on .NET Framework 4.8; exits nonzero on a failed assertion. |
| `Illum.SdkCheck/` | WPF/SDK checks and preview rendering with isolated preferences and stub services. |
| `build.ps1` | Build, validation, packaging and optional local deployment. |

I list the sources and dependency notices in
[THIRD-PARTY-NOTICES.txt](THIRD-PARTY-NOTICES.txt) and `licenses/`. The timezone
grid keeps its ODbL license and rebuild instructions in the repository's
`tools/tzmap/` directory.
