# Illum for WinTAK 1.0.0

Released 10 November 2026. Assembly/package version: **1.0.0.0**.

This is my first Windows release of Illum. I ported the offline planning tools
to WinTAK while keeping the Android sources and build separate. The Windows
version and `wintak-v1.0.0` tag are independent of the ATAK release versions.

I build and test against **WinTAK-CIV 5.8.0.149**, using the developer host on
Windows x64 with .NET Framework 4.8. This WPK is unsigned. I haven't verified
other host versions or production signing requirements.

## What is included

- Offline solar, twilight, lunar phase/rise/set, illuminance and shadow planning.
- USAF, Navy/USMC, Army and custom standards for NVG/aviation and ground
  concealment missions.
- Sky factors, optional night-sky light, rated windows and mixed-light tolerance.
- Local-at-point IANA time and DST, plus Zulu, device, nautical and manual offsets.
- GPS, map-center, ground and marker picking, and decimal-degree coordinates.
- Optional 20 km terrain skyline analysis with a flat-terrain comparison.
- A 7-90-night grid, moon graphics, event cards and a detailed night timeline.
- Readable dark controls, keyboard navigation, inline input errors, picking
  cancellation and saved preferences.

I also corrected custom clock windows across DST changes in the Windows port.
Each endpoint is resolved in local time, so a 22:00-04:00 window keeps those
clock times when the offset changes overnight. The Android source is unchanged.

## Testing

I ran **4,632 core assertions**, including **364 independent reference nights**,
and the installed-SDK WPF checks. The builds finished with zero warnings or
errors. The WPF checks use real controls and SDK types with stubbed GPS, map
and elevation services.

In WinTAK, I tested upgrades, restart and preference persistence, map picking
and cancellation, closing during a 90-night terrain calculation, keyboard
navigation, narrow-pane layout, terrain fallback and plugin unload/reload.
I keep the scope of those checks and the remaining hardware checks in
[RELEASE-CHECKLIST.md](RELEASE-CHECKLIST.md).

The build script checks the WPK's identity, version, SDK, Civilian variant,
contents and DLL hashes. It includes the dependency notices and writes a
SHA-256 sidecar.

## Install and source

Drop `Illum-1.0.0.0-5.8.0.149.wpk` onto WinTAK, or import it through Plug-in
Manager. Restart WinTAK after upgrading. I tested an in-place upgrade and kept
my existing preferences.

See [INSTALL.md](INSTALL.md) for the full instructions,
[README.md](README.md) for building the port, and
[PARITY-AUDIT.md](PARITY-AUDIT.md) for the differences from ATAK. Sources and
dependency notices are in `THIRD-PARTY-NOTICES.txt` and `licenses/`.
