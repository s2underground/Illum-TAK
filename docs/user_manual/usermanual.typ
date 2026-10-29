
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
Illum gives night light data for any point on the map: sunset, End of Evening Nautical Twilight (EENT), Beginning of Morning Nautical Twilight (BMNT), sunrise, moonrise and moonset (with azimuth), moon illumination with a picture of the phase, and a green/amber/red stoplight that says whether the night is High Light Level (HLL) or Low Light Level (LLL) for Night Vision Goggle (NVG) or ground operations. All abbreviations are listed on the last page, and under *Abbreviations and terms* at the bottom of the pane.

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
- *Banner:* the stoplight color, the light level (HLL, MIXED or LLL), the share of the rated window that is HLL, the window times, the standard and the mission type. The picture on the right shows the moon's phase as you see it (mirrored in the southern hemisphere). The plan rows show a small version.
- *Times:* Sunset, EENT, BMNT, Sunrise. Civil twilight (EECT/BMCT) and astronomical twilight (EEAT/BMAT) can be turned on in Settings. Each time is followed by its zone letter: L = device local time, Z = Zulu (UTC), or the military time zone letter for the Area of Operations (AO). Zulu is shown underneath.
- *Moonrise / Moonset:* time and true azimuth. "Up all night" or "down all night" when there is no event.
- *Illum:* moon illumination at the middle of the window, with an arrow for waxing (↑) or waning (↓).
- *Moon max:* highest moon elevation inside the window. *Moonlight:* brightest moonlight inside the window, in lux or millilux (mlux).
- *Notes:* the HLL periods on a MIXED night (twilight-only periods are marked), low moon cautions, and polar day/night messages.

= Timeline
Labels along the top: SS sunset, EENT, MR moonrise, MS moonset, BMNT, SR sunrise. Background shading shows day, civil, nautical and astronomical twilight, and night. The yellow strip at the top shows when the moon is up. The white curve is the estimated illuminance (0.1 millilux to 1 lux, log scale), and the dashed line is the HLL threshold. The bottom strip shows the stoplight color moment by moment. The cyan brackets mark the rated window.
]

#tak-slide[
  = Stoplight logic
Each night's rated window (default EENT to BMNT) is checked every 5 minutes:
- *HLL* when at least 90% of the window is HLL, *LLL* when at most 10% is, otherwise *MIXED*. The 10% tolerance is adjustable.
- *NVG / aviation* mission: HLL = green, MIXED = amber, LLL = red.
- *Ground concealment* mission: the colors are reversed (LLL = green).

= Standards
- *USAF* (Air Force Instruction 11-214): high illumination is at least 2.2 millilux, roughly a 20% moon at 30° or higher.
- *USN/USMC* (Marine Corps Order 3500.14): HLL is 0.0022 lux or above. This is the same threshold as the USAF; Navy/USMC guidance also warns that planning tools ignore low moon angle, terrain and shadows.
- *Army*: at least 23% illumination with the moon at least 30° up. This is a rule of thumb; verify it against your unit Standing Operating Procedure (SOP).
- *Custom*: your own lux threshold, or your own minimum illumination and elevation.
]

#tak-slide[
  = Settings
Open them from the pane's *Settings* button, or from Settings › Tool Preferences › Specific Tool Preferences › Illum Preferences.
- Service standard, mission type, custom standard values.
- Rated window: EENT–BMNT, sunset–sunrise, or custom clock times (for example 2200–0400; a start before noon means the following morning).
- Sky condition: clear, thin clouds (÷2), average clouds (÷3), dark stratus (÷10), from U.S. Naval Observatory (USNO) Circular 171.
- Include starlight/airglow (0.5 millilux). This is off by default, which is more conservative for NVG planning.
- Time zone: device local, Zulu, AO nautical zone (by longitude, without daylight saving), or a manual offset. The night always runs from local noon to local noon.
- Number of planning nights: 7 to 90.

= Accuracy and limits
Sun and moon positions follow Meeus, _Astronomical Algorithms_. Against an independent ephemeris, rise, set and twilight times agree within about 15 seconds at 8 test sites from the equator to 70°N. Illuminance uses the USNO Circular 171 model for a *clear sky*. Clouds, terrain masking, smoke, snow cover and city lights change real light levels. Illum supports planning and does not replace official SLAP (military solar/lunar almanac) data or weather products.
]

#tak-slide[
  = Abbreviations and terms
#set text(size: 0.85em)
#table(
  columns: (auto, 1fr, auto, 1fr),
  stroke: 0.4pt + luma(180),
  inset: 4pt,
  [*SS / SR*], [Sunset / Sunrise: the top edge of the sun on the horizon],
  [*MR / MS*], [Moonrise / Moonset; "\@ 071°" is the true azimuth],
  [*EECT / BMCT*], [End of Evening / Beginning of Morning Civil Twilight: sun 6° below the horizon],
  [*EENT / BMNT*], [End of Evening / Beginning of Morning Nautical Twilight: sun 12° below. The default rated window runs EENT to BMNT],
  [*EEAT / BMAT*], [End of Evening / Beginning of Morning Astronomical Twilight: sun 18° below, full darkness],
  [*Illum*], [Percent of the moon's disk that is lit; ↑ waxing (growing), ↓ waning (shrinking)],
  [*HLL / LLL*], [High / Low Light Level: at or above / below the selected standard's threshold],
  [*MIXED*], [Part of the window is HLL and part is LLL],
  [*lux, mlux*], [Units of illuminance; 1 millilux = 0.001 lux. A full moon high in the sky gives about 0.25 lux; the HLL threshold is 2.2 mlux],
  [*NVG*], [Night Vision Goggles. _Degain_: the goggles dim when bright light, such as a low moon, is in view],
  [*L / Z / letter*], [After a time: device Local time / Zulu (UTC) / military time zone letter (e.g. R = UTC−5)],
  [*AO*], [Area of Operations],
  [*AFI / MCO*], [Air Force Instruction / Marine Corps Order],
  [*SOP*], [Standing Operating Procedure],
  [*SLAP*], [Official military solar/lunar almanac data used in NVG briefs],
  [*USNO*], [U.S. Naval Observatory, source of the illuminance model],
  [*USAF / USN / USMC*], [U.S. Air Force / Navy / Marine Corps],
  [*Azimuth*], [Compass direction from true north, in degrees],
)
]
