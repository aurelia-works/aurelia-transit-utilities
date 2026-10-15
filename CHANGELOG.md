# Changelog

## 1.3.0
- Return route: builds the opposite direction of a route (stops reversed, other platform of each station, ends keep theirs); every stop can be switched before saving; optionally added to the same depots.
- Speed check: read-only list of slow spots and too-fast curves in the loaded rails, with Jump.
- Train inspector: loaded trains with route, next stop, depot, siding, speed vs limit and predicted delay.
- Platform calls: approaching / now arriving / delayed announcements while you stand on a platform, from MTR's arrival data. Own on/off button in the hub.
- Train presets can carry siding settings (acceleration, deceleration, max trains, manual mode and delay options) and apply to a whole depot. Preset file version 2; version 1 files still load.
- Depot health: new checks for depots with no departures, sidings not linked to track, and platforms outside any station.

## 1.2.0
- Vehicle melodies: new on/off button in the ATU hub, a preference for Joban Client Mod vehicle scripts. Saved in `config/aurelia_transit_utilities/handshake.properties`.
- Handshake protocol 2: when Joban Client Mod is installed, ATU publishes its version, `handshake` (protocol), `announcer`, `announcer_lang`, `melodies` and `caps` keys through JCM's `registerAddonVersion`, and republishes them when you change a setting. Scripts read them with `Resources.getAddonVersion`; scripts that do not are unaffected. Keys: `docs/HANDSHAKE.md`.

## 1.1.0
- Announcer: on MTR trains you ride, announces the next station, arrivals, "change here for" other routes, route continuation and the last stop. English only; on/off in the ATU hub.
- The announcement lines go to your local chat and replace MTR's own chat announcement while you ride. Station clips (sound events `mtr:aureliaex_ann_<key>`) play when a resource pack provides them, otherwise text only.
- Tells Joban Client Mod vehicle scripts whether the Announcer is on.

## 1.0.1-hotfix
- The ATU button sits under MAGIC's button and is clickable over MTR's map.

## 1.0.1
- The ATU dashboard button moved beside MAGIC's menu button.

## 1.0.0
- Train presets, route stop order, platform dwell times, departure times, station overlaps, zone heights, station codes, depot health and Jump to.
