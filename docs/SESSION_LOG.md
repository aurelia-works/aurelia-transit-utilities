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
