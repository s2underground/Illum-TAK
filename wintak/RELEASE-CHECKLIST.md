# My WinTAK 1.0.0 release checks

I use this checklist to keep the automated results separate from my checks in
WinTAK. The results below are from the 1.0.0 release work; this documentation
update doesn't add a new test run.

## Automated checks

I ran `.\wintak\build.ps1` with the WinTAK-CIV 5.8.0.149 developer SDK. All
checks passed, and the builds finished with zero warnings or errors. The script
writes the WPK and its SHA-256 sidecar to `wintak/artifacts/`.

| Area | What I checked |
| --- | --- |
| Astronomy, time zones, DST, terrain and mission rules | 4,632 assertions, including 364 JPL/Skyfield and PyEphem reference nights. |
| WPF registration, bindings, contrast and moon graphics | Real WPF controls and installed SDK types in the windowless test harness. |
| Plans, dates, validation, cancellation and preferences | Pane tests with isolated preferences, including recovery from a damaged primary file. |
| GPS, map and elevation adapters | Stub services, actual SDK marker types, picking cleanup and routed input events. |
| Narrow layouts and long reports | Renders at 300, 380, 520 and 760 pixels, with 90-night terrain/polar plans and grid-position measurements. |
| WPK identity, dependencies and integrity | Manifest and entry checks, packed DLL hashes compared with build output, and a SHA-256 sidecar. |

The WPF tests run without a visible WinTAK window. I use stub GPS, map and
elevation services there, so I check the real host separately.

## Checks I completed in WinTAK

I tested these on the WinTAK-CIV 5.8.0.149 developer host:

- [x] Upgrade the existing development installation to 1.0.0.0 in place.
- [x] Restart WinTAK, confirm the version and keep saved preferences.
- [x] Open Illum from its toolbar button.
- [x] Pick ground and a marker, cancel with the button and Escape, and return
  to normal map interaction.
- [x] Close and reopen Illum during a 90-night terrain calculation.
- [x] Use Tab/Shift+Tab, dropdowns, the calendar and grid keyboard navigation.
- [x] Narrow the pane and keep all four grid columns accessible without a
  horizontal scrollbar.
- [x] Compare terrain enabled/disabled and try a location without elevation data.
- [x] Unload/reload the plugin and open its pane again.

I completed these manual checks before rebuilding the WPK to record the updated
Git commit. I reran the automated checks after that rebuild. The runtime code
was unchanged, and I didn't repeat the manual checks on the repackaged WPK.

## What I haven't verified

- A separate clean-install and full remove/reinstall cycle.
- Live GPS hardware, with and without a fix.
- Every real ridge and partial elevation coverage case. I have checked terrain
  display and the no-data fallback, with broader coverage cases in the stub tests.
- Other display scaling settings, WinTAK versions or production signing channels.

I keep these limits explicit so a successful automated run doesn't stand in
for a hardware or deployment test I haven't done.

## Preparing a release

- I commit the Windows sources and documentation, leaving the ATAK versions
  independent. I use `wintak-v1.0.0` for the Windows 1.0.0 source baseline.
- I build the package from the recorded source commit and keep the WPK,
  SHA-256 sidecar and validation record together.
- I publish the WPK and checksum as release assets, with installation
  instructions and release notes.
- I keep build output, SDK files and local test files out of Git.
- I identify this WPK as unsigned. Before supporting a different host or
  signing channel, I'll check its package requirements and test it there.
