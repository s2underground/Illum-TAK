
#import "@preview/polylux:0.4.0": *
#import "formatting.typ": *

#show: userguide.with(
   plugin-name: "Illum",
   plugin-version: "1.0",
   platform: "ATAK",
   platform-version: "5.6.0",
)


#tak-slide[
  = Overview
#toolbox.side-by-side(columns: (.75fr, 9fr))[
#image("plugin_icon.png", width: 70%)
][
Illum gives night light data for any point on the map: sunset, EENT, BMNT, sunrise, moonrise and moonset (with azimuth), moon illumination, and a green/amber/red stoplight that says whether the night is High Light Level (HLL) or Low Light Level (LLL).

Everything is computed on the device from astronomical formulas. Illum needs *no network and no data downloads*, so the 30-night plan (up to 90) works offline indefinitely.
]
= Getting started
- Tap the *Illum* toolbar button. The pane opens on tonight at your position.
- *My Pos* uses your GPS position. *Map Ctr* uses the center of the map. *Pick* lets you tap the map or a marker; press it again to cancel.
- Step through nights with the arrows, tap the date to choose any date, or press *Tonight* to return.
- Tap any row in the plan to open that night.
]

#tak-slide[
  = Reading the night card
- *Banner:* the stoplight color, the light level (HLL, MIXED or LLL), the share of the rated window that is HLL, the window times, the standard and the mission type.
- *Times:* Sunset, EENT, BMNT, Sunrise (optionally civil and astronomical twilight). The local time is shown with its zone letter (L = device local, Z = Zulu, or the AO nautical zone letter), with Zulu underneath.
- *Moonrise / Moonset:* time and true azimuth. "Up all night" or "down all night" when there is no event.
- *Illum:* moon illumination at the middle of the window, with an arrow for waxing (↑) or waning (↓).
- *Moon max:* highest moon elevation inside the window. *Moonlight:* brightest moonlight inside the window.
- *Notes:* the HLL periods on a MIXED night (twilight-only periods are marked), low moon cautions, and polar day/night messages.

= Timeline
Background shading shows day, civil, nautical and astronomical twilight, and night. The yellow strip at the top shows when the moon is up. The white curve is the estimated illuminance (0.1 millilux to 1 lux, log scale), and the dashed line is the HLL threshold. The bottom strip shows the stoplight color moment by moment. The cyan brackets mark the rated window.
]

#tak-slide[
  = Stoplight logic
Each night's rated window (default EENT to BMNT) is checked every 5 minutes:
- *HLL* when at least 90% of the window is HLL, *LLL* when at most 10% is, otherwise *MIXED*. The 10% tolerance is adjustable.
- *NVG / aviation* mission: HLL = green, MIXED = amber, LLL = red.
- *Ground concealment* mission: the colors are reversed (LLL = green).

= Standards
- *USAF* (AFI 11-214): high illumination is at least 2.2 millilux, roughly a 20% moon at 30° or higher.
- *USN/USMC* (MCO 3500.14): HLL is 0.0022 lux or above. This is the same threshold as the USAF; Navy/USMC guidance also warns that planning tools ignore low moon angle, terrain and shadows.
- *Army*: at least 23% illumination with the moon at least 30° up. This is a rule of thumb; verify it against your unit SOP.
- *Custom*: your own lux threshold, or your own minimum illumination and elevation.
]

#tak-slide[
  = Settings
Open them from the pane's *Settings* button, or from Settings › Tool Preferences › Specific Tool Preferences › Illum Preferences.
- Service standard, mission type, custom standard values.
- Rated window: EENT–BMNT, sunset–sunrise, or custom clock times (for example 2200–0400; a start before noon means the following morning).
- Sky condition: clear, thin clouds (÷2), average clouds (÷3), dark stratus (÷10), from USNO Circular 171.
- Include starlight/airglow (0.5 millilux). This is off by default, which is more conservative for NVG planning.
- Time zone: device local, Zulu, AO nautical zone (by longitude, without daylight saving), or a manual offset. The night always runs from local noon to local noon.
- Number of planning nights: 7 to 90.

= Accuracy and limits
Sun and moon positions follow Meeus, _Astronomical Algorithms_. Against an independent ephemeris, rise, set and twilight times agree within about 15 seconds at 8 test sites from the equator to 70°N. Illuminance uses the USNO Circular 171 model for a *clear sky*. Clouds, terrain masking, smoke, snow cover and city lights change real light levels. Illum supports planning and does not replace official SLAP or weather products.
]
