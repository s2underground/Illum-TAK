# Illum for WinTAK 1.0.0

Release date: 10 November 2026. Assembly/package version: **1.0.0.0**.
Target: **WinTAK-CIV 5.8.0.149**, x64, .NET Framework 4.8.

This is the first versioned Windows release of Illum, developed alongside the
ATAK sources. Its Windows version and Git tag are independent of ATAK releases.
The package produced by this repository is unsigned and targets the developer
host. It does not carry TAK.gov approval or a production signing claim.

## Included

- Offline solar, twilight, lunar phase/rise/set, illuminance and shadow planning.
- USAF, Navy/USMC, Army and custom standards; NVG/aviation and concealment missions.
- Sky factors, optional night-sky light, rated windows and mixed-light tolerance.
- Local-at-point IANA time/DST, Zulu, device, nautical and manual-offset modes.
- GPS, map-center, ground/marker picking and decimal-degree inputs.
- Optional 20 km terrain skyline and flat-terrain doctrine comparison.
- A 7–90-night grid, moon graphics, event cards and a detailed night timeline.
- WPF controls with explicit contrast, keyboard navigation, field validation,
  map-pick cancellation and saved preferences.

## Verification

`build.ps1` runs **4,632 core assertions**, including **364 independent reference
nights**, plus installed-SDK WPF and adapter checks. It packages only the plugin,
calculation library, pinned Noda Time dependency and notices, validates package
identity/version/SDK/variant, and writes a SHA-256 sidecar for the WPK.

Automated GPS, map and elevation services are stubbed. The user's development-host
sessions verify loading, reports, terrain display and interactive use of the
preceding development build. Final lifecycle, installation and hardware checks
are tracked separately in [RELEASE-CHECKLIST.md](RELEASE-CHECKLIST.md); automated
success is not a claim that every host/hardware scenario has been exercised.

## Distribution and source

Use Git tag **`wintak-v1.0.0`** to identify the Windows source baseline. Android
sources and versions are unchanged. See [INSTALL.md](INSTALL.md) for installation
and upgrades, [PARITY-AUDIT.md](PARITY-AUDIT.md) for platform differences, and
`THIRD-PARTY-NOTICES.txt` / `licenses/` for dependency notices.

Before distribution to a different host build or signing channel, validate that
host's package acceptance and complete the applicable signing/review process.
