# Review of the owner's MTR idea list (MTR IDEADS)

Date: 2026-10-02. Source: the note "MTR IDEADS" (Discord suggestions). Rules applied: ATU is client-only, no new blocks, no mixins, no per-tick work, no faking MTR data, and writes only through MTR's own packets. Evidence comes from the earlier three docs plus a fresh `javap -p` check of the MTR 4.0.5 jar (classes named in the tables).

45 distinct ideas. Duplicates in the note (for example the two "muffle sounds inside a train" entries, and "station codes" / "JR East station numbering") are counted once.

| Bucket | Count |
|---|---|
| Already in ATU | 4 |
| ATU can add (no mixin) | 3 |
| ATA material | 7 |
| Needs MTR change / mixin | 27 |
| Unclear | 4 |
| Total | 45 |

## Build next (ranked)

1. **Departure-time editor and timetable generator (M, needs check).** Type exact departure times, or give a first train and a gap (e.g. 06:30 to 22:30 every 5 minutes). Covers idea 33 (rest hours), 34 (fixed interval) and, as an approximation, 22 (leave the first station N minutes after depot departure). Matches #1289 and #1409.
2. **Depot health screen (finish it).** Not from your note, but it is already in progress and has the most upstream demand (#918 and many "path will not generate" reports). Finish it before starting anything new.
3. **Jump-to list for stations, depots and platforms (S).** Not in your note; it stays on the earlier list (#1112). Cheap and useful next to the departure editor.
4. **First-station time helper (part of item 1, S on top of it).** Say "train should leave the first station at 17:00" and ATU works out the depot departure from the generated path time. Needs check: `Depot.getPath()` carries usable times.
5. **Resolve the Unclear question on station codes (idea 20).** If a codes-in-the-name bulk tool is acceptable, it is an S/M tool and a popular request (JR-style numbering).
6. **Resolve the Unclear question on tunnel/bridge/wall builders (ideas 9, 10, 45).** Biggest community appetite in your note, but it may not fit a client-only mod. Decide before any work.
7. **Network backup and restore (M-L).** Not in your note; carried over from the issue review (data-loss reports). Owner decision still open.

Nothing else in the note can be built under the current rules. Most of the rest needs MTR itself to change.

## Already in ATU

| # | Idea (owner's words, short) | ATU tool | Notes |
|---|---|---|---|
| 6 | Choose which station a platform belongs to when in two station zones | Station-overlap resolver | Partly. The resolver trims zones so only one station owns the platform. Picking the owner freely, with zones left overlapping, needs a mixin (MTR assigns the first station, `Data.mapAreasAndSavedRails`). #975, #1372 |
| 27 | Save and load preset trains in the siding editor | Train presets | Done. #864 (partly) |
| 42 | Differentiate stations on the Y axis (subway below overground) | Station/depot zone height editor (#815) plus the overlap resolver | Zones are 3D boxes (`AreaBase.getMinY/getMaxY`, `inArea`). #815 |
| 43 | Drag and drop to reorder stations in a route | Drag-and-drop route stop order | Done. Opens from an ATU button, not inside MTR's own list |

## ATU can add (no mixin)

| # | Idea | Approach | Size | Demand |
|---|---|---|---|---|
| 22 | Leave the first station N minutes after depot departure | Helper in the departure-time editor: you give the wanted time at the first station, ATU subtracts the travel time from the generated path and writes the depot departure. Approximate, because MTR has no "offset after departure" rule. Used: `Depot.getRealTimeDepartures()`, `getPath()`, `setUseRealTime`. Needs check | M (on top of the editor) | #1289 |
| 33 | Loop line rests overnight (e.g. 22:30 to 06:30) | Generate departures only inside the active window with the departure-time editor, and bulk-set per-hour frequencies (`Depot.setFrequency(hour, n)`). Needs check: for "repeat infinitely" depots (`setRepeatInfinitely`) it is unknown whether the time list or hourly frequencies are used at all. If not, this moves to MTR | M, needs check | #1409 |
| 34 | Trains leave at an even gap (e.g. every 5 min), computed for you | Same editor: enter the gap, ATU writes either 12-per-hour frequencies or an exact time list. Same "needs check" for loop depots | S on top of the editor | #1289, #1409 |

Ideas 22, 33 and 34 share one piece of work (the departure-time editor), so they are really one build. Questions about them are at the bottom of the Unclear section.

## ATA material (blocks, visuals, signage, sounds)

| # | Idea | Why ATA | Notes |
|---|---|---|---|
| 5 | Route name instead of platform number on route signs | Signage | New sign type that reads route data |
| 7 | Link PIDS so one custom message shows network-wide | PIDS | ATA A6. Needs a shared message source; ATA's data provider already reads MTR |
| 11 | Real curved platforms | Platform pieces | ATA A13 for decorative curved pieces; true curved platform rails also need MTR |
| 21 | Languages side by side on entrance signs | Signage | ATA A4 |
| 24 | Custom announcements instead of TTS | Announcements | Already done in ATA 1.1 (voice packs); refine in 1.2 |
| 32 | "Calling at" list with times on station displays | PIDS | ATA A14. Times come from MTR arrival data |
| 35 | Each Exit entry on a sign configured on its own | Signage | ATA A3. #1263 |

## Needs MTR change / mixin

| # | Idea | Why it cannot be done as an add-on |
|---|---|---|
| 1 | Transit agencies with ownership and sharing | Data model: MTR data has no owner field on stations, routes or depots, and permission is one global check (`MinecraftClientData.hasPermission`). #1007 |
| 2 | Per-carriage settings (disable floor, lock door, disable motor) | `VehicleCar` holds only id, size, bogies and coupling padding. No per-car flags exist. Belongs with JCM scripting |
| 3 | Muffle sound inside a train (default 30%, configurable) | Vehicle sound playback is inside MTR's renderer and sound classes; changing it means a mixin. (Listed twice in the note) |
| 4 | Escalator speed | Escalator behaviour is in MTR's block entity. #1357 |
| 8 | Optimise rail rendering (20-30 fps cost) and badly optimised Blender train models | MTR rail renderer; train model cost is the train pack author's |
| 12 | Elevator speed and door motion profiles | `Lift` has `speed` and `stoppingCoolDown` as live state, no configurable settings. #1474, #702 |
| 13 | Elevator "out of service" schedule | No such state in `Lift`. #1474 |
| 14 | Elevator car buttons (open, close, emergency) | Lift logic and screens in MTR. #1474 |
| 15 | Lift state API for other add-ons | There is nothing to expose; MTR would have to add the states first. #1474, #1478 |
| 16 | Signalling: signal boxes, signals, switches | Signal blocks are part of MTR simulation (`Rail.signalColors`, server side); control logic is server only. Boxes would be blocks (ATA) but the logic is MTR's |
| 17 | Advanced dispatching | Server-side `Simulator`; not reachable from an add-on |
| 18 | Lifts and floors locked with driver keys | Lift access logic in MTR |
| 19 | Trains belonging to several depots | A siding belongs to the depot whose zone it sits in (`Depot` area); no multi-depot link in data |
| 23 | PIDS detects trains that pass without stopping | MTR exposes arrivals for stopping trains only (ATA A16 already marked blocked) |
| 25 | LOD compatibility | Rendering compatibility; belongs to MTR and Aurelia LOD |
| 26 | Day, weekly and monthly passes | Fare system is MTR core. #1392, #1475 |
| 28 | Trains go to any free platform | Paths are fixed at depot generation (`Depot.generateMainRoute`, `SidingPathFinder`). #537, #997 |
| 29 | Suspend only a section of a route for maintenance | A route is one stop list and one generated path; no per-section closed state. Editing stops and regenerating changes the whole route, so it is not a safe stand-in |
| 30 | Faster maximum speed for planes | Speed comes from the rail (`RailSchema.speedLimit1/2`, final, set when the rail is built). #373, #996 |
| 31 | Name coaches (letter or number) for destination binds and displays | `VehicleCar` has no name field; displays depend on train metadata |
| 36 | Door side per platform; crew-only stations | `Platform` stores only dwell time and positions; door side lives in train logic |
| 37 | Couple, uncouple and split trains during operation | Vehicle simulation. #1395 (couplers) |
| 38 | Vehicles (planes especially) keep moving in unloaded chunks | Server simulation and chunk loading |
| 39 | Advanced ticketing: tickets for a specific trip | Fare system is MTR core. #359, #1392 |
| 40 | "Public transport only" in Find Directions | The route finder is in MTR core and its web map. (An in-game map is possible for ATU later, but the route finder itself is not ours) |
| 41 | Manual door release per platform with auto-close | Door control is train and platform logic. #1405 |
| 44 | Lift stops at a floor and keeps doors open like a lobby | Lift logic |

## Unclear

| # | Idea | Question for the owner |
|---|---|---|
| 9 | Advanced tunnel builder (more shapes and sizes, optional Create-mod blocks) | ATU is client-only and cannot place blocks by itself. Is a tool that only works for operators or creative players, sending ordinary commands such as /fill, acceptable? Or should this be an ATA item? |
| 10 | Bridge creator | Same question as idea 9. |
| 45 | Semi-underground "wall creator" (replaces only solid blocks, no roof) | Same question as idea 9. It is the easiest of the three to prototype. |
| 20 | Station codes / JR-East style station numbering | OK to store the code inside the station name text (for example "A01"), knowing signs will show it? Or should ATA own a separate code field, shown only on its signs? |
| 33, 34 (follow-up) | Loop line depots | Do your loop lines use "repeat infinitely", or timed departures? This decides whether ideas 33 and 34 work without MTR. |
| 22 (follow-up) | Offset after depot departure | Is a "target time at the first station" helper close enough, given that MTR has no true offset rule? |

## Counting note

Each idea sits in exactly one bucket:

- Already in ATU (4): 6, 27, 42, 43
- ATU can add (3): 22, 33, 34 (all one departure-time editor; 33 and 34 are "needs check" for loop depots)
- ATA material (7): 5, 7, 11, 21, 24, 32, 35
- Needs MTR change / mixin (27): 1, 2, 3, 4, 8, 12, 13, 14, 15, 16, 17, 18, 19, 23, 25, 26, 28, 29, 30, 31, 36, 37, 38, 39, 40, 41, 44
- Unclear (4): 9, 10, 20, 45

Total 45. The two "follow-up" rows in the Unclear table are extra questions about ideas that already have a bucket; they are not counted again.
