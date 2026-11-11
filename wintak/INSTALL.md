# Illum for WinTAK 1.0.0

I tested this release with **WinTAK-CIV 5.8.0.149**, using the developer host
on Windows x64 with .NET Framework 4.8. The package is unsigned. I haven't
verified other WinTAK versions or production host signing requirements.

## Install and open

1. Download `Illum-1.0.0.0-5.8.0.149.wpk` and drop it onto WinTAK, or import it
   through Plug-in Manager.
2. Enable/load **Illum** and restart WinTAK if prompted.
3. Open the pane with the **Illum** toolbar button.

If the button is missing, open Toolbar Manager and add **Illum** from Removed
Items to a visible toolbar. A saved toolbar layout can leave out a new tool.

## Upgrade

I tested upgrading an existing development installation to 1.0.0.0 without
removing it first. My saved preferences survived the upgrade and restart.

1. Close the Illum pane and import the newer WPK.
2. Restart WinTAK so it loads the replacement assemblies.
3. Check that Plug-in Manager shows **1.0.0.0**, then open Illum again.

If you previously copied the DLLs manually, close WinTAK before replacing them.
The build script's `-Install` option uses `%APPDATA%\WinTAK\Plugins\Illum`.
Keep one copy of the plugin in one installation location; duplicate copies of
`S2U.Illum.WinTAK.dll` can make it unclear which one WinTAK is loading.

I store preferences separately in `%APPDATA%\WinTAK\Illum`.

## Remove

Unload/disable and remove Illum through Plug-in Manager, then restart WinTAK if
prompted. Keep `%APPDATA%\WinTAK\Illum` if you want to reuse your preferences.
For a manually copied installation, close WinTAK before removing only the
plugin's own `Plugins\Illum` directory.

These are the removal steps; I haven't separately verified a full
remove/reinstall cycle for this release.

## Make a plan

Choose **My position**, **Map center**, **Pick on map**, or enter decimal-degree
coordinates using a decimal point. Picking a marker uses its anchor and name.
**My position** takes a snapshot of the available position and needs a fix.

Choose an evening, 7-90 nights, a light standard, mission, sky and rated window,
then select **Calculate plan**. Custom clock windows use the selected display
time zone. Enter manual UTC offsets in minutes. Calculations run offline, and
terrain analysis uses elevation data already loaded in WinTAK.

The grid shows Evening, Light, HLL percentage and Moon. Select a row to see its
report. Event cards and the full twilight report include rise/set times and
azimuths. Green favors the selected mission: light for NVG/aviation, darkness
for ground concealment. With terrain enabled, the report also keeps the
flat-terrain comparison.

## Keyboard and mouse

I added these shortcuts for working in the pane:

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

Alt shortcuts and Ctrl+Enter work while focus is within Illum. Escape also
cancels picking when the map has focus. Enter keeps its normal selection
behavior in dropdowns and date editors. Tab leaves the planning grid in one
step. Long report sections have their own vertical scroll areas; at the top
or bottom, the wheel returns to the main pane. Expanded sections are remembered
after a restart.

## Reading time and terrain results

I reuse the offline timezone boundary grid with Noda Time's IANA rules for
local-at-point time. Zulu, device, nautical and manual-offset modes are also
available. The report identifies the zone and offset, and adds a note when a
night crosses a DST change. If the timezone grid is unavailable, the report
clearly labels the nautical-time fallback.

The terrain model looks at bare ground within 20 km. It treats missing
directions as flat and reports incomplete coverage. Trees, buildings and actual
weather are outside that model. The sky setting scales the astronomical light
estimate.
