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
