# Windows 1.0.0 release checks

## Automated release command

Run `.\wintak\build.ps1` with the matching WinTAK-CIV 5.8.0.149 SDK. A successful
run requires the core assertions, WPF/adapter suite and package validation to pass.
The build writes the WPK and its SHA-256 sidecar into `wintak/artifacts/`.

| Check | Evidence |
| --- | --- |
| Astronomy, timezones/DST, terrain and mission rules | 4,632 assertions; 364 JPL/PyEphem reference nights. |
| WPF registrations, bindings, contrast and moon graphics | Installed-SDK windowless harness. |
| Plans, dates, validation, superseded work and preferences | Windowless pane and isolated preferences. |
| GPS/map/elevation adapters and pick lifecycle | Stub host services, actual SDK marker types and routed input tests. |
| Narrow grid and variable report content | 300–760 px renders; 90-night terrain/polar plans and viewport measurements. |
| Package identity/version/SDK/Civilian variant and dependency contents | ZIP manifest/entry assertions in `build.ps1`. |

## Interactive host checks

These require the real host. Record outcomes in the release artifact's validation
record. Do not infer them from the stub service checks.

- Clean package installation; upgrade from 0.3.1; removal/reinstallation.
- Loading/enabling, toolbar placement and opening the Illum pane.
- GPS without a fix and, when hardware is available, with a fix.
- Map-center, ground/marker picks, cancel/Escape and ordinary map input afterwards.
- Closing/reopening during a 90-night terrain calculation; unload/reload.
- Preferences after a host restart.
- Loaded elevation data, a real ridge, partial coverage and no elevation data.
- Keyboard focus, dropdown/calendar interactions and the intended display scaling.

Prior user sessions demonstrate the preceding development build loading and
rendering real terrain reports in WinTAK. They are not a record of fresh-install
or final-package lifecycle tests. Unavailable hardware and host checks should be
identified explicitly in the final validation record.

## Release baseline and distribution

- Keep diagnostics, traces, test preferences, build output and SDK files out of Git.
- Commit Windows sources and documentation; retain the independent ATAK history.
- Build the final package from the recorded commit and create `wintak-v1.0.0`.
- Keep the WPK, SHA-256 sidecar, source commit/tag and validation record together.
- Identify this artifact as unsigned; verify the intended public host/signing
  channel before claiming production-host acceptance or TAK.gov signing.
