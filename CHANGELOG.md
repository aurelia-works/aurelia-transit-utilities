# Changelog

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
