# Live testing

## Automated self-test (dev only)

`./gradlew runSelftest` starts a dev client with the extra `atu_selftest` mod (`src/selftest`, never in the release jar), loads `run/selftest/saves/ATU Test` directly, opens MTR's dashboard the way the server does (`PacketOpenDashboardScreen.sendDirectlyToServer`), drives the ATU screens and checks the results against data fetched back from MTR core. It writes `run/selftest/atu_selftest.txt` (PASS/FAIL lines, last line `RESULT PASS|FAIL`) and screenshots `run/selftest/screenshots/atu_*.png`, then quits. `-Datu.selftest.stay=true` keeps the game open.

Test world: `run/` is git-ignored. `ATU Test` is a copy of ATA's `run/client/saves/ATA Release Check` (3 stations, 4 platforms, 2 routes, 1 depot, 1 siding with a 2-car train). Copy it there before the first run. Errors about `ata_test:*` functions and `mtr:train_cargo_*` loot tables in the log come from that world's datapack and from MTR, not ATU.

## Manual checklist

### Train presets (U2)
1. Open MTR's dashboard. An **ATU** button sits in the top-left corner of the map. Click it, then **Train presets**.
2. Pick a siding (the closest one within 32 blocks is pre-selected). Type a name, **Save this siding's train**. The preset appears on the right with car count and length.
3. Pick another siding of the same transport mode, pick the preset, **Apply to siding**. Reopen the siding in MTR: its train matches. Regenerate the depot if running trains do not change.
4. A preset that is longer than the siding rail is greyed and refused with the lengths. A preset using vehicles from a resource pack that is no longer loaded is refused with the missing ids. Presets of other transport modes are not listed.
5. Presets live in `config/aurelia_transit_utilities/train_presets.json`. A corrupt file is moved to `train_presets.json.bad` and the screen says so.
6. In adventure or spectator mode the Apply button stays disabled (MTR's own permission rule).

### Route stop order (U3)
1. Dashboard → **ATU** → **Route stop order**. Pick a route; its stops show in order with station, platform and custom destination.
2. Drag a stop: a green line shows where it lands. Or select a stop and use **Move up / Move down** (or Shift+Up/Down). **Reverse** flips the whole route, **Revert** drops the edits.
3. **Save order** writes the route. Reopen it in MTR's own route editor: same order, custom destinations still on the same stops.
4. **Regenerate depots** sends MTR's depot Generate for every depot that runs the route. Trains only follow a new order after that (same as editing in MTR).
5. Switching to another route with unsaved edits drops them and says so. If the route's stop count changed elsewhere in the meantime, Save refuses and reloads it.

### Platform dwell times (U4, dwell part)
1. Dashboard → **ATU** → **Platform dwell times**. Pick **All platforms**, a station (■) or a route (→). Its platforms are listed with their current dwell, all ticked. Click a row to untick or tick it; **All / None**.
2. Type a time (`30`, `12.5`, `1:30`, `2m`) and **Set dwell**, or use **-5 s / +5 s**. Limits are MTR's own: 0.5 s to 10:00 in half seconds; anything else keeps Set disabled.
3. Reopen a changed platform in MTR's platform screen: same dwell. **Regenerate depots** rebuilds the depots of every route through the changed platforms (needed for running trains to use the new times, same as MTR).
4. Cable-car platforms are not listed (MTR ignores dwell for continuous movement, #1375).
