# ATU session log

## 2026-10-02 — audit, decision, scaffold

- Feasibility audit of U1-U6 against the MTR 4.0.5 jar: `docs/FEASIBILITY.md`.
- Owner delegated scope. ATU 1.0 = U2, U3 (ATU screen), U4 dwell, U1 overlap resolver. No mixins. Client-only mod.
- Scaffolded the Fabric project (mod id `aurelia_transit_utilities`, package `com.aureliatransit.utilities`), toolchain copied from ATA: Loom 1.10.5, yarn 1.20.1+build.10, loader 0.19.5, Fabric API 0.92.12+1.20.1, Gradle 8.14, Java 17. MTR is `modCompileOnly` + `modLocalRuntime`.
- `./gradlew build` passes. `./gradlew runClient` boots: Fabric loads 61 mods including `mtr 4.0.5` and `aurelia_transit_utilities 1.0.0`, log shows `[ATU] Client initialised`, no errors, reaches "Sound engine started".

## 2026-10-02 — U2 train presets

- Pure logic (`preset/`): `PresetCar`, `TrainPreset` (total length = MTR's `Siding.getTotalVehicleLength` rule, checked against the bytecode), `PresetNames`, `PresetLibrary` (max 256 presets, 128 cars, unique names ignoring case), `PresetCheck` (wrong mode, missing vehicle ids, too long for the rail), `PresetJson` (versioned file, bad entries skipped and counted, garbage rejected without wiping). 8 unit tests pass.
- MTR glue (`client/MtrBridge`): reads `MinecraftClientData.getDashboardInstance()`, writes with `PacketUpdateData(UpdateDataRequest.addSiding)` exactly like MTR's `VehicleSelectorScreen`; gated on `MinecraftClientData.hasPermission()`. Coupling paddings are recovered through `VehicleCar.getTotalLength(isFirst, isLast)` because their getters are package-private.
- UI: ATU button on MTR's dashboard via Fabric `ScreenEvents.AFTER_INIT` (no mixin), tools hub, train presets screen. First placement covered MTR's cursor-coordinate readout (seen on screenshot); moved to the map's top-left (`IGui.PANEL_WIDTH + 4`).
- Live: new dev-only self-test (`./gradlew runSelftest`, `docs/LIVE_TESTING.md`). Run result: 11/11 PASS, including "applied preset persisted in MTR" (preset applied, dashboard re-fetched from core, siding has the new 1-car train) and "original train restored in MTR" (back to 2 cars). Screenshots checked by eye.
- Not yet checked live: clicking Apply in the screen itself (the self-test calls the same `MtrBridge.applyPreset`), multiplayer server with ATU only on the client.

## 2026-10-02 — U3 route stop order

- Pure logic `route/StopOrder`: permutation of original indices (repeated platforms and custom destinations stay with their stop), `move(from, insertBefore)`, reverse, reset, `apply` refuses a route whose size changed, `insertionIndex` for the drag drop point. 6 unit tests (14 total) pass.
- UI: `DragListWidget` (drag with ghost row + insertion line, auto-scroll, Move up/down, Shift+arrows) and `RouteStopsScreen`. Save reorders the live `Route.getRoutePlatforms()` list and sends `addRoute`; "Regenerate depots" sends `PacketDepotGenerate(DepotOperationByIds)` like MTR's depot screen, only on click.
- Live (self-test, 16/16 PASS): real mouseClicked/mouseDragged/mouseReleased on the list moved stop 1 to the end; saved; re-fetched from MTR core: order `[B, C, A]`; restored with the Move up button and Save; re-fetched: original order. Screenshot showed the "Regenerate depots" label clipped at 90 px; buttons widened to 110 px and checked again.
- Not checked live: Regenerate depots actually rebuilding paths (button sends MTR's own packet; not exercised by the self-test).

## 2026-10-02 — U4 platform dwell (per-route speed stays out, see FEASIBILITY)

- MTR rule read from bytecode: `PlatformScreen.onClose2` stores `(minutes*60 + halfSecondSlider/2) * 1000` ms; sliders allow 0-10 min and 0-59.5 s, `tick2` forces at least 0.5 s and nothing past 10:00.
- Pure logic `dwell/DwellTime`: parse (`30`, `12.5`, `1:30`, `1m30s`, `2m`), half-second rounding, MTR range, clamp/adjust, format. 5 unit tests (19 total) pass.
- `PlatformDwellScreen`: scopes all / station (`AreaBase.savedRails`) / route (route order, each platform once), tick list, Set / ±5 s, one `UpdateDataRequest` with every changed platform; Regenerate depots = union of `platform.routes[].depots`. Cable-car platforms excluded.
- Live (self-test, 22/22 PASS): unticked one of 3 route platforms, "0" kept Set disabled, Set 42.5 then +5 s; re-fetched from MTR core: `10000(unticked) 47500 47500`; restored to 10000 and verified. Screenshot showed the dwell value cut off behind long station names; label reordered to dwell-first and rechecked.

## 2026-10-02 — U1 station overlap resolver (no-mixin version)

- MTR rule from bytecode: `AreaBase.inArea` → `Utilities.isBetween` inclusive on X, Y and Z (padding 0), only with valid corners; `SavedRailBase.getMidPosition` = (p1 + p2) / 2 with long division (rounds toward zero); first station in `Data.stations` order wins.
- Pure logic `overlap/`: `Box`, `Point.midpoint`, `Zones` (assignment, conflicts, diff), `OverlapResolver` (cut every other zone containing the midpoint on one face; rank cuts by platforms moved, then map cut before height cut, then volume lost; report side effects; null when a zone would vanish). 10 unit tests (29 total) pass. The first ranking (volume only) chose a 1-block height cut on full-height zones; a test caught it and map cuts are now preferred unless a height cut moves fewer platforms (stacked stations).
- `StationOverlapScreen`: conflicts per transport mode, station choice, plan preview with side effects, Apply sends `addStation` for each cut. Re-reads zones only on open/resize/Apply. Shows a warning if ATU's computed assignment ever disagrees with MTR's `platform.area`.
- Live (self-test, 29/29 PASS): stretched a second station's zone over a platform; 0 mismatches between ATU's prediction and MTR's `platform.area` for all platforms; conflict listed with MTR's pick first; chose the other station; plan 1 cut, 0 side effects; after Apply and a fresh fetch MTR itself assigns the platform to the chosen station; both zones restored and verified.

## 2026-10-02 — zone heights (#815), overflow fix, self-test comfort

- Found while reading `DashboardScreen.onDrawCorners`: MTR draws every zone with Y `Long.MIN_VALUE`..`Long.MAX_VALUE`. `Box.volume()` overflowed on that (long arithmetic); now computed in double, regression test added.
- `overlap/HeightRange` (blank = no limit, typed values bounded, bottom ≤ top, describe/apply). 5 unit tests (35 total) pass.
- `ZoneHeightScreen` for stations and depots; preview diff of platform/siding assignment on every field edit (not per frame); `MtrBridge.applyHeight` sends `addStation`/`addDepot`. Zone reading generalised to depots + sidings.
- Self-test 35/35 PASS: range excluding the station's platform is warned about ("Platform 1: … → (none)"), bad input keeps Save off, range keeping it says "No platform changes station", saved → MTR core has Y −63..−54 and still assigns the platform → restored to unbounded.
- Self-test now opens on the non-primary monitor and never captures the mouse (dev-only Minecraft mixin in `src/selftest`; release jar checked: no mixins).
