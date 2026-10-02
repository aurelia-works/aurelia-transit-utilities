# ATU 1.0 feasibility audit (MTR 4.0.5, Minecraft 1.20.1, Fabric)

Date: 2026-10-02. Source: `minecraft-transit-railway-FABRIC-4.0.5+1.20.1.jar`, read with `javap -p` / `javap -c -p` (846 classes under `org.mtr.core` and `org.mtr.mod`). No code written.

## Verdicts

| # | Item | Verdict | ATU 1.0 candidate |
|---|---|---|---|
| U1 | Platform belongs to which station when zones overlap | **Needs a mixin** (owner decision) for the real feature. No-mixin honest subset: overlap detector + resolver that edits station corners | Subset yes; full only with mixin |
| U2 | Save/load preset trains for sidings | **Doable without mixins** | Yes |
| U3 | Drag-and-drop stop reordering | **Doable without mixins as an ATU screen**; inside MTR's own dashboard list needs a mixin | Yes (ATU screen) |
| U4 | Per-route speed, per-platform dwell in a dashboard | Dwell: **doable without mixins**. Per-route speed: **needs an MTR change** (no field, no simulation hook) | Dwell only |
| U5 | Node orientation beyond 0 / 22.5 / 45 | **Needs an MTR change** (angle is a 16-value enum stored in rail data) | No |
| U6 | Trains use any reachable platform | **Needs an MTR change** (paths are fixed per route platform at depot generation) | No |

Recommended ATU 1.0: **U2, U3 (ATU screen), U4 dwell part, U1 overlap resolver**. U1 full, U4 speed, U5, U6 go to the owner as "mixin or upstream" decisions.

## How an addon talks to MTR 4.0.5 (applies to every item)

- MTR core (`org.mtr.core.Main`) runs in the server JVM, but `Init.main` (private static) and `Main.simulators` (private) are not reachable without reflection. The server-side `Simulator` is therefore off limits under the "public API only" rule.
- The public write path is the one MTR's own screens use. `SidingScreen.onClose2()` does exactly:
  `InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(new UpdateDataRequest(MinecraftClientData.getDashboardInstance()).addSiding(siding)))`.
  `UpdateDataRequest` has public `addStation / addPlatform / addSiding / addRoute / addDepot / addLift / addRail`. On the core side `UpdateDataRequest.update()` applies the objects and then calls `Data.sync()`.
- The public read path on the client is `MinecraftClientData.getInstance()` / `getDashboardInstance()` (both extend `org.mtr.core.data.Data`: public `stations`, `platforms`, `sidings`, `routes`, `depots` sets and `*IdMap`s), plus `InitClient.findStation(BlockPos)`, `findClosePlatform(...)`, `findDepot(BlockPos)`.
- **Permission:** `PacketRequestResponseBase.runServerOutbound` forwards the request to core with no permission check. MTR gates editing on the client with `MinecraftClientData.hasPermission()`. ATU must apply the same gate before every write, and must not offer a wider write surface than MTR's own screens.
- Screens: MTR screens extend `org.mtr.mapping.mapper.ScreenExtension` (a vanilla `Screen`), so Fabric API `ScreenEvents.AFTER_INIT` can add an ATU button to an MTR screen without a mixin. It cannot read the screen's state: fields such as `DashboardScreen.editingRoute` (private) and `SavedRailScreenBase.savedRailBase` (protected) are not accessible. ATU screens must therefore find their target themselves (player position / list pick), not from the MTR screen.

## U1: platform station membership (flagship)

**How MTR decides.** `Data.sync()` calls private static `Data.mapAreasAndSavedRails(platforms, stations)`. Per platform, `lambda$mapAreasAndSavedRails$15` sets `SavedRailBase.area = null`, takes `getMidPosition()`, then iterates `Data.stations` (an `ObjectArraySet`, insertion order) and assigns the **first** station where `AreaBase.isTransportMode(platform) && AreaBase.inArea(mid)` is true, adding the platform to `AreaBase.savedRails`. This runs on every `sync()`, on both sides: server `Simulator` (via `UpdateDataRequest.update()`) and client `ClientData.sync()` → `Data.sync()`. There is no stored per-platform station id; `PlatformSchema` only has `dwellTime`, `SavedRailBaseSchema` only `position1/position2`.

Consequence: any station choice ATU stored itself would be overwritten on the next sync, and everything downstream (`Platform.getStationName()`, `Station.getInterchange*`, OBA stops, PIDS, announcements, route station lists) reads `platform.area`.

**Options.**
1. **Mixin (owner decision).** Redirect the assignment in `Data.mapAreasAndSavedRails` (the synthetic lambda; fragile target) or inject at `Data.sync()` TAIL to re-map overridden platforms from an ATU override table (platform id → station id). Must run on server and client, and ATU must persist and sync the override table itself. This is the only way to deliver U1 as written.
2. **Upstream MTR change.** Add an optional `stationId` override to `PlatformSchema` and honour it in `mapAreasAndSavedRails`. Cleanest; out of ATU's hands.
3. **No-mixin honest subset (recommended for 1.0).** An "overlap resolver": list every platform whose midpoint lies in 2+ station areas of its transport mode, show which station MTR picked (first in set order) and which it could be, and offer to shrink/move the losing station's corners (`AreaBase.setCorners` + `addStation`) so the midpoint falls in exactly one zone, with a preview of other platforms that would change membership. Stations are axis-aligned boxes, so some layouts (stacked or interleaved platforms) cannot be separated; the tool must say so instead of pretending.
4. Rejected: deleting and re-adding a station to change its position in the `ObjectArraySet`. It relies on undocumented insertion order, only sets a global priority per station pair, and is lost or reshuffled by save/load.

## U2: preset trains in the siding editor

- `Siding.getVehicleCars()` / `Siding.setVehicleCars(ObjectArrayList<VehicleCar>)` are public. `VehicleCar` has a public constructor `(vehicleId, length, width, bogie1Position, bogie2Position, couplingPadding1, couplingPadding2)` and `VehicleCar(ReaderBase)` / `serializeData(WriterBase)` for round-tripping with `org.mtr.core.serializer.JsonReader/JsonWriter`.
- Save: copy the cars of the target siding to a client-side preset file (`config/aurelia_transit_utilities/presets.json`). Optionally also acceleration/deceleration, max trains, manual settings (all have public setters on `Siding`).
- Load: `setVehicleCars(...)` on the client copy, send `PacketUpdateData(...addSiding(siding))` exactly as `SidingScreen.onClose2()` does.
- Validation (fail visibly): vehicle ids missing from the current resource packs, transport mode mismatch (`Siding.getTransportModeOrdinal()`), total length over `Siding.getRailLength()` (`Siding.getTotalVehicleLength(...)`).
- UI: MTR's `SidingScreen` / `VehicleSelectorScreen` cannot be extended in place without a mixin. ATU adds a "Presets" button to `SidingScreen` via `ScreenEvents.AFTER_INIT`, which opens an ATU screen targeting the siding the player is standing at / looking at (found from `MinecraftClientData.sidings` + `SavedRailBase.containsPos/closeTo`), with a list fallback. Caveat: if `SidingScreen` is closed after ATU applied a preset, its `onClose2()` re-sends its own copy; ATU must close the MTR screen first (or apply on the instance MTR then sends). To verify live.

## U3: drag-and-drop stop reordering

- `Route.getRoutePlatforms()` returns the live `ObjectArrayList<RoutePlatformData>`; reorder it and send `addRoute(route)`. `RoutePlatformData` holds `platformId` + `customDestination`, so a reorder keeps per-stop custom destinations.
- MTR's own list (`DashboardList`, private `onUp/onDown`, `buttonUp/buttonDown`, state in `DashboardScreen.editingRoute` / `editingRoutePlatformIndex`, all private) cannot get drag-and-drop without a mixin.
- No-mixin version: an ATU "Route stops" screen (route picker → draggable stop list → Save). Button injected into `DashboardScreen` via `ScreenEvents.AFTER_INIT`.
- After saving, depots serving the route must be regenerated for trains to follow the new order (same as MTR's own editor); ATU should say so and offer the existing MTR regenerate action rather than silently triggering it.

## U4: per-route speed and per-platform dwell

**Dwell: doable.** `Platform.getDwellTime()` / `setDwellTime(long)` are public; `PlatformScreen.onClose2()` writes them. `SavedRailScreenBase.MAX_DWELL_TIME = 1200` bounds MTR's own UI; ATU uses the same bound. A dashboard-style ATU screen can list all platforms of a station or route and bulk-edit dwell, sending one `UpdateDataRequest` with several `addPlatform`. Dwell is copied into `PathData.dwellTime` at path generation, so depots must be regenerated (same behaviour as MTR's own platform screen).

**Per-route speed: needs an MTR change.** `RouteSchema` has no speed field (`routeType, routeNumber, hidden, circularState, routePlatformData`). Speed comes from rails: `RailSchema.speedLimit1/speedLimit2` (final), copied into `PathDataSchema.speedLimit`, used by `Siding.getUpcomingSlowerSpeed(...)` and `Vehicle.simulateMoving(...)`. Per-siding knobs that do exist: `acceleration`, `deceleration`, `delayedVehicleSpeedIncreasePercentage`, `maxManualSpeed` (manual only). Nothing caps an automatic train per route. Faking it by rewriting rail speed limits along a route would change every route sharing those rails, so it is not offered as "per-route speed".

## U5: node orientation beyond 22.5 degrees

`org.mtr.core.tool.Angle` is an enum of 16 values (`E, SEE, SE, … NEE`, `ANGLE_INCREMENT` 22.5°), with `fromAngle(float)` snapping to the nearest. `RailSchema.angle1/angle2` are `Angle`, serialized into saved rail data and used by `RailMath`, `PathData`, `SidingPathFinder.PositionAndAngle`. Finer orientation needs new angle values in core data and the save format. Not possible as an addon; a mixin cannot sensibly add enum constants. **Upstream only** (#1435).

## U6: trains use any reachable platform

Paths are fixed at generation: `Depot.generateMainRoute(...)` builds the path from each route's `RoutePlatformData` (fixed `platformId`), `SidingPathFinder(Data, startSavedRail, endSavedRail, stopIndex)` takes one fixed end platform per stop, `Siding.generateRoute(Platform, Platform, int, long)` likewise. `Vehicle` follows the precomputed `PathData` list; there is no runtime platform choice and no platform-group concept in the data model. Needs MTR simulation and data-model changes. **Upstream only** (or a large mixin into `Depot`/`Siding`/`Vehicle`, not recommended).

## Decision (2026-10-02)

Owner delegated the choice; ATU is a quality-of-life release, no blocks. ATU 1.0 = **U2 presets, U3 route stop order screen, U4 bulk dwell, U1 overlap resolver**, all without mixins, client-side only (`environment: client`, writes go through MTR's own `PacketUpdateData`). U1 full assignment, U4 per-route speed, U5 and U6 stay out of 1.0 and are left for upstream MTR.

## Owner decisions that were open

1. ATU 1.0 subset. Proposal: U2, U3 (ATU screen), U4 dwell, U1 overlap resolver.
2. U1: accept the honest subset for 1.0, or authorise a mixin on `Data.sync()` / `mapAreasAndSavedRails` (and the ATU-side override storage and sync that comes with it).
3. U4 speed, U5, U6: file or upvote upstream (#1376, #1435, S24) and keep them out of ATU 1.0?
