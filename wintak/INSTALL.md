# Illum for WinTAK 1.0.0

## Supported host

WinTAK-CIV **5.8.0.149**, x64, with .NET Framework 4.8. This package is unsigned;
the developer host is its validated target. Other WinTAK versions and production
host signature policies have not been validated by the automated checks.

## Install and open

1. Open WinTAK's Plug-in Manager and import `Illum-1.0.0.0-5.8.0.149.wpk`.
2. Enable/load **Illum** and restart WinTAK if requested.
3. Use the **Illum** toolbar button to open the dock pane. If the button is absent,
   open Toolbar Manager and add **Illum** from Removed Items to a visible toolbar.
   Saved toolbar layouts can omit newly installed tools.

## Update from a development build

1. Close the Illum pane and use Plug-in Manager to update the installed package.
2. Restart WinTAK so the replacement assemblies are loaded.
3. Confirm Plug-in Manager shows **1.0.0.0** and open Illum again.

For a previous manual DLL deployment, close WinTAK before replacing assemblies.
The build script's `-Install` option copies the plugin to
`%APPDATA%\WinTAK\Plugins\Illum`. Use one deployment method for a given copy.
Avoid retaining multiple copies of `S2U.Illum.WinTAK.dll` in different plugin
directories. Preferences are stored separately in `%APPDATA%\WinTAK\Illum`.

## Remove

Unload/disable and remove Illum through Plug-in Manager, then restart WinTAK if
requested. Preferences remain in `%APPDATA%\WinTAK\Illum`; preserve that directory
if you intend to reinstall. A manually deployed copy must be removed while
WinTAK is closed, using only its own `Plugins\Illum` directory.

## Make a plan

Choose **My position**, **Map center**, **Pick on map**, or enter decimal-degree
coordinates using a decimal point. Picking a marker uses its anchor and name.
**My position** takes a position snapshot. GPS requires an available fix.

Choose an evening, 7–90 nights, a light standard, mission, sky and rated window,
then select **Calculate plan**. Custom clock windows use the selected display
time zone. Manual UTC offsets are entered in minutes. All calculations run
offline. Terrain uses elevation data already loaded in WinTAK.

The four grid columns show Evening, Light, HLL percentage and Moon. Select a row
to update its report. Event cards and the full twilight report contain rise/set
times and azimuths. Green favors the selected mission: light for NVG/aviation,
darkness for ground concealment. Terrain results retain a flat-terrain doctrine
comparison.

## Keyboard and mouse

| Action | Control |
| --- | --- |
| Move between controls | Tab / Shift+Tab |
| Select nights in the grid | Up / Down, Home / End, Page Up / Down |
| Open the selected report | Enter in the grid, double-click a row, or Back to selected report |
| Calculate | Enter in a numeric field, Ctrl+Enter, or Alt+C |
| My position / map center / pick | Alt+P / Alt+M / Alt+K |
| Previous night / Tonight / next night | Alt+V / Alt+T / Alt+X |
| Return to the selected report | Alt+R |
| Focus latitude / longitude / evening / night count | Alt+L / Alt+O / Alt+E / Alt+I |
| Cancel picking | Escape or Cancel pick |

Alt shortcuts and Ctrl+Enter apply while focus is within Illum. Escape also
cancels active picking when the map has focus. Enter retains its normal selection
behavior in dropdowns and date editors. Tab leaves the planning grid in one step.
Long report sections have their own vertical scroll areas; at their boundaries,
the wheel returns to the main pane. Expanded sections are remembered after restart.

## Time and terrain interpretation

Local-at-point time uses an offline boundary grid and Noda Time's IANA rules.
Zulu, device, nautical and manual-offset modes are also available. Timezone
descriptions identify the selected zone and offset; clock-change notes identify
DST transitions. A missing timezone grid falls back to labelled nautical time.

Terrain considers bare ground within 20 km at the selected point. Missing
directions are treated as flat, and incomplete coverage is reported. Trees,
buildings and actual weather are outside the skyline model. The astronomical
estimate is scaled by the selected sky setting.
