# MTR 4.x add-on competitor review for ATU

Date: 2026-10-14. Scope: functional (non-decoration) add-ons for MTR 4.x, plus MTR 4.1.0-beta.2 itself. Companion to `MTR_ISSUES_REVIEW.md`, `OWNER_IDEAS_REVIEW.md` and `FEASIBILITY.md`. Research only; no ATU code changed.

## Method

- 24 add-on jars pulled from Modrinth (newest 1.20.1 Fabric build where one exists) plus the four already in the Aurelia Metropolis instance. REA 6.3.0 `inspect-artifact` gives a safe inventory of a jar, but REA has no JVM decompiler provider, so the code was read with jadx (`--no-res`). Modrinth descriptions were read for every jar.
- MTR3-only add-ons were dropped (owner: target is MTR 4.x+): NTE, ANTE, Manual-Enchance, Yomi Utility Mod. They matter only as proof that MTR4 still lacks their features.
- MTR `FABRIC-4.1.0-beta.2+1.21.1` was diffed against 4.0.5 (class list, `javap`, `en_us.json`).
- Recent MTR issues (since 2026-09-25) were added on top of the 2026-10-02 issue review.

## MTR 4.x competitors, what they do and how

| Add-on | Side | Mixins | Features relevant to ATU |
|---|---|---|---|
| **MAGIC 1.3.5** (`org.justnoone.jme`) | client + server | 56 | Route folders in the dashboard list, saved dashboard-map views ("areas", centre on player), rail overlay and rail export on the dashboard map, BlueMap layers, a web dashboard server, cron schedules per depot, depot cancellations, alternative platforms, waypoints, train stop position on platform (start/middle/end), max speed per siding, custom rail speed/tilt/rotation/colours, train detector block |
| **S1MTR 1.0.9.1** (`top.s1metro.s1mtr`) | client + server | 8 | In-game rail network map screen, station list with teleport, recommended speed from curve radius and gradient, per-route/global speed override, door-open and departure delays, node copier, fast track builder, composite (bridge/tunnel) builder with layer presets |
| **TransitManager 2.0.0-beta.2** (1.20.4 only) | server | 9 | `/mtr generatePath`, `/mtr clearTrains`, `/mtrpath status/abort`, `/platform` and `/train` info on the nearest one, `/train deploy`, `/train skipDwell`, `/warpsta`, `/warpdepot` |
| **Tianjin Metro 1.1.2** | both | 30 | "Duplicate route" button in the route editor, "duplicate siding settings" in the siding editor (both mixins) |
| **GURIGURI Station Companion 1.5.1** (1.20.4) | client (+ server feed) | 9 | Platform-side announcements (approaching, arrival, departure, delay) from MTR's `ArrivalsCacheClient` / `PacketFetchArrivals`, VOICEVOX speech, replaceable chimes and melodies, cancellation feed for PIDS |
| **MTR Rail Works 0.2.3** | both | 8 | Rail Worker item: bridge / tunnel / walls between two nodes, bridge wall creator |
| **MTR Fare Pro 0.2** | both | 4 | Distance, path (Dijkstra) and zone-path fare modes via a `TicketSystem` mixin |
| **MTR Breakdowns 1.0.0** | both | 5 | Random traction/brake/door failures, `/breakdown` commands, HUD |
| **MTR Surveyor 1.0.0** | server | 4 | Stations and depots as Surveyor map landmarks |
| **FangSu 1.1.2** | both | 26 | Multi-direction node block (free node angle), hybrid builder, OBJ gates, PSD central control |
| **JCM 2.3.0-beta.1** | both | yes | PIDS presets, vehicle scripting, fare blocks; no operations tools. ATU already talks to it (`HANDSHAKE.md`) |
| London Underground, Russian Metro, Yunzhu, Japan Ticket, Traffic, BTE | both | 0-368 | Blocks, lifts, tickets, road traffic. No overlap with ATU's tool set |

Pattern: every competitor that touches operations uses mixins and usually needs the server. ATU is the only client-only, mixin-free operations add-on. That is its selling point (drop into any server, no MTR breakage on updates) and also its limit.

## Pain points seen across the field

1. **Depot path generation fails or lies** (#1509, 2026-10-03: "successfully generated main path" while sidings cannot connect; #918 and the cluster in `MTR_ISSUES_REVIEW.md`). TransitManager exists mainly to work around this with commands.
2. **Building the return direction of a route is manual.** Tianjin added "duplicate route" for this.
3. **Why is my train slow?** Speed comes from rails; nobody can see which section limits a route (#1002, #1280, #1410). S1MTR and MAGIC both added speed tools.
4. **No in-game map with trains** (#1052, #1116, #1309). S1MTR, MAGIC (dashboard overlay, BlueMap) and Surveyor each solve a slice.
5. **Timetables** (#1289, #1409, #1510 "individual schedules for each train in one depot"). MAGIC added cron schedules.
6. **Announcements at the platform, not only on board.** GURIGURI built a whole mod around this.
7. **Fragmentation.** TransitManager is 1.20.4 only; four popular tool mods are still MTR3 only; MTR 4.1 moves to 1.21.x.

## Decision and status (2026-10-14)

Owner: ATU stays on **MTR 4.0.x / Minecraft 1.20.1**; MTR 4.1 is not released, so C1 is dropped for now. C2-C7 were built in 1.3.0 on branch `feat/competitor-tools` (Speed check, Return route, Platform calls, Depot health additions, Train inspector, preset siding settings). C4 owner call: platform calls read MTR's shared arrivals cache only while the player stands on a platform. C8 and C9 remain later ideas. Decompiling was done with jadx; REA 6.3.0 only lists JVM jar contents.

## MTR 4.1 (beta) and what it means for ATU

- MTR 4.1.0-beta.2 ships for **1.21.1 and 1.21.4 only** (Fabric and NeoForge). There is no 1.20.1 build.
- **Core data API is unchanged**: all 16 `org.mtr.core.*` classes ATU imports exist, and the `Depot` and `Siding` methods ATU uses (`setUseRealTime`, `setFrequency`, `getRealTimeDepartures`, `getLastGeneratedStatus`, `getFailedPlatformIds`, ...) keep their signatures.
- **The mod layer moved**: the 11 `org.mtr.mod.*` classes ATU imports now live under `org.mtr.*` (`org.mtr.client.MinecraftClientData`, `org.mtr.packet.PacketUpdateData`, `org.mtr.screen.DashboardScreen`, ...). `org.mtr.mod.InitClient` is gone (`org.mtr.MTRClient` instead). `MinecraftClientData.getDashboardInstance()` and `hasPermission()` still exist.
- **4.1 adds features ATU provides today**: "Y Bounds" on zones (overlaps Zone heights), a realtime departure expression `07:10:00 + 10 * 00:03:00` (overlaps part of Departure times), rail speed and rail cant editing, "Platforms in this station", station exits, homes and landmarks.
- ATU's `fabric.mod.json` pins `mtr >=4.0.5 <4.1.0`, so ATU will not load on 4.1 at all.

## What ATU can add (client-only, no mixins)

Ranked by demand and fit. Sizes: S small, M medium, L large.

| # | Idea | Inspired by | Approach | Size |
|---|---|---|---|---|
| C1 | **MTR 4.1 / 1.21.x build** | owner ("4.x+"), MTR 4.1 | Branch `mc1.21.1`. Core logic unchanged; swap the 11 moved imports behind a thin `MtrCompat` class, port Minecraft 1.21 screen/text APIs. On 4.1, hide Zone heights (native Y Bounds) and pitch Departure times as the bulk/preview layer over MTR's new expression | M |
| C2 | **Route speed and curve audit** (read-only) | S1MTR `RailSpeedHelper` (safe speed = √(0.7·r) m/s, gradient cap 90·√(0.003/g), max 320 km/h) | For a route, walk its rails from the client copy and list sections whose set limit exceeds the safe curve speed and sections that make the train crawl. Jump-to the worst one. Changes nothing. `Data.rails`, `Rail.railMath` and `Rail.getSpeedLimitKilometersPerHour` are public in 4.0.5 | M |
| C3 | **Return route in one click** | Tianjin "duplicate route" | ATU screen: duplicate a route, optionally reversed, mapping each stop to the other platform of the same station (preview, user can fix any stop). Writes one `addRoute` | S |
| C4 | **Platform announcements and "next trains" panel** | GURIGURI | Extend the Announcer to the platform: when the player stands in a station zone, read `ArrivalsCacheClient` (what MTR's PIDS already use) and announce "approaching / arriving / departing / delayed". Same sound-event hook as today. Needs an owner call on the "no new server polling" rule: it only fetches while in a station, at PIDS rate | M |
| C5 | **Depot doctor** (extend Depot health) | #1509, TransitManager | Add checks for the false-success case: sidings with no rail link to the main path, platforms on a route not reachable from any siding, depots with zero departures. Keep the Regenerate button; add "regenerate all failed" | S-M |
| C6 | **Train inspector** | TransitManager `/train`, `/platform` | Read-only panel for the nearest or ridden train: depot, siding, route, next stop, delay. Uses `VehicleExtension` data ATU already reads for the Announcer. Deploy and skip-dwell need the server, so out | S |
| C7 | **Siding settings presets** | Tianjin "duplicate siding settings" | Extend Train presets to carry acceleration, deceleration, max trains and delay-handling options, plus "apply to all sidings in this depot" | S |
| C8 | **In-game network map with live trains** | S1MTR map, MAGIC overlay | Same as A4 in the issue review. Biggest job here; worth it only after C1 decides the version line | L |
| C9 | **Network backup / restore** | MAGIC rail export | Same as A7. Save stations, routes, depots, sidings to a file; restore through `UpdateDataRequest` | M-L |

## What competitors do that ATU should leave alone

Needs a mixin or the server under ATU's rules, and MAGIC already ships it: alternative platforms, waypoints, stop position on platform, per-train max speed, depot cron schedules and cancellations, rail tilt/rotation. Also server-only: TransitManager's deploy, skip dwell and clear trains; Breakdowns; Fare Pro. Builder items (Rail Works, S1MTR composite builder, FangSu) are ATA territory per the 2026-10-02 decision. Revisit only if the owner relaxes "no mixins".

## Recommendation

1. ~~Decide the version line first (C1).~~ Decided: 4.0.x only.
2. Then C3 and C5 (small, high-pain), C2 (unique; nobody shows *why* a route is slow), C4 (extends the Announcer that already exists).
