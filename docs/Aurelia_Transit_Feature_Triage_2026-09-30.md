# Aurelia Transit Feature Triage - 2026-09-30

Status: working triage document. Every classification below is a proposal, not a commitment.
Prepared for: Aurelia Metropolis (Minecraft Transit Railway addon family)
Sources: the Discord suggestion dump supplied in-thread, the ATA repository at `Aurelia Transit Architecture`, the Minecraft Transit Railway GitHub issue tracker for 2025-12-01 through 2026-09-30, and the Philadelphia / SEPTA research from this thread.

---

## 1. The three layers

The whole point of this document is to stop Aurelia turning into one giant fragile MTR fork. Three layers, three jobs:

| Layer | Job | What it owns |
|---|---|---|
| **MTR** | Runs the railway | Rails, nodes, signals, routes, depots, schedules, trains, fares, lifts, core simulation |
| **ATA - Aurelia Transit Architecture** | Makes stations feel real | Architecture, signage, wayfinding, PIDS/CIS, announcements, furniture, platform equipment, bus infrastructure |
| **ATU - Aurelia Transit Utilities** | Makes the railway easier and deeper to operate | Operational and quality-of-life features people keep asking MTR for: overlap handling, presets, configuration tools, dispatch, building workflows |

Both ATA and ATU stay MTR addons. Neither replaces MTR internals. Nothing in this document justifies a mixin into MTR core without a separate, explicit decision.

---

## 2. Classification scheme

- **ATA** - belongs in Aurelia Transit Architecture.
- **ATU** - belongs in Aurelia Transit Utilities.
- **Later** - a good idea, but it should not be started until the current release line is stable.
- **Keep elsewhere** - real and valuable, but the right home is MTR core, JCM/MAGIC MTR, a train pack, Aurelia LOD/Shaders, or another addon.
- **Not for Aurelia** - do not build this, at all, in either mod.

Filter to apply before promoting anything: **votes are evidence of demand, not instructions.** A suggestion with a lot of support that has sat unimplemented is worth investigating, not automatically worth copying. Four questions, every time:

1. Is it still missing today?
2. Does another addon already solve it well?
3. Can the MTR API support it safely, without core changes?
4. Can we do it lightweight enough for the performance contract in section 12?

---

## 3. Where ATA actually stands on 2026-09-30

ATA is past the "concept" stage. Current repository state:

- **V1 (1.0.0)** - 45 blocks across Platforms (7), Signage (6), Furniture (6), Architecture (15), Catenary (4), Bus (7). Editable joined signs, canopy composition, decorative-only, no mixins, no MTR modification.
- **1.1 workstream A - live transit systems** - MTR data provider (`MtrStationDataProvider`), PIDS, CIS, hanging variants, concourse departure boards, announcement engine, wall and ceiling speakers, voice packs. Documented in `docs/MTR_INTEGRATION.md` and `docs/LIVE_TESTING.md`.
- **1.1 workstream B - interactive station content** - seating, editable information blocks, station sign v2, clocks, bus polish.

Counted in the checkout: 113 Java files, live and interactive test suites present, client-only data access, no block-entity ticking.

That matters for triage: the live-data plumbing already exists, so most of the SEPTA direction in section 4 is "render more things from data we already have", not "build a new system".

---

## 4. ATA - Aurelia Transit Architecture

### 4.1 Philadelphia / SEPTA-inspired requirements

This is the part that came from the hometown research, and it is the direction to keep. SEPTA is worth stealing *design logic* from, not branding. No real operator identity, no logos, no station reproductions.

**Wayfinding as a system, not individual signs.** SEPTA's Metro redesign uses letters, numbers, colours, symbols, direction and terminal names together: the Broad Street Line keeps orange and becomes the **B**, the Market-Frankford Line is the **L** (leaning into "the El"). ATA should render the same idea generically:

```text
[B]  Southbound
     Aurelia Airport
     Platforms 3-4

<- Exit / Buses
-> Regional Rail
```

Line badge + colour + direction + destination + transfers. Never hardcoded SEPTA values - pulled from MTR route data where available, configured otherwise.

**Station entrances and exit identity.** SEPTA's newer wayfinding includes illuminated entrance pylons, clearer entrance/exit signs, neighbourhood maps and nearby-connection signs. ATA equivalents:

- illuminated station entrance pylons
- configurable "Exit A / Exit B" signs and stair-number signs
- street-name signs
- "To buses / railway / metro" connection signs
- wall-mounted neighbourhood/map frames
- configurable landmark arrows
- entrance totems that display the station name and the lines serving it

**Local / express / limited service patterns.** The Broad Street Line distinguishes B1 Local, B2 Express and B3 Spur. PIDS and platform signs should understand service patterns, not just destinations:

```text
B2 EXPRESS
Aurelia Airport
Stops: Central - University - Airport

B1 LOCAL
Aurelia Airport
All stations
```

If MTR exposes enough route and stopping information, LOCAL / EXPRESS / LIMITED / custom labels can be derived rather than hand-entered.

**The El: an elevated-transit architecture family.** The Market-Frankford Line mixes elevated and underground infrastructure, and the Frankford elevated structure historically used concrete in support beams to reduce vibration and noise. ATA's architecture family should eventually cover:

- steel and concrete viaduct supports
- under-platform lighting
- elevated station stairs, enclosed stair towers
- platform wind screens
- elevated station canopies
- utility conduits and cable trays
- trackside fencing and noise-barrier pieces
- under-El columns

Most Minecraft railway mods handle tracks and trains far better than this kind of infrastructural detail, and this is exactly what makes urban rail read as real.

**Accessibility detail.** SEPTA station projects include lifts, wider ramps and corridors, wider stairs, railings, accessible signage, upgraded audio-visual PA and Braille / raised-letter signage. ATA equivalents:

- lift indicator panels
- accessible route signs
- tactile directional paving and platform-edge tactile strips
- wheelchair boarding-position markers
- help points
- accessible entrance badges
- Braille-style decorative plaques
- lift status displays

**Solar / e-paper real-time bus displays.** SEPTA began piloting real-time electronic arrival displays at bus stops in 2026, starting at Broad & Oregon. This maps directly onto the Bus Town side of ATA:

```text
AURELIA CENTRAAL
12  University        2 min
37  Harbourview       8 min
52  Airport          14 min
```

No animation except an occasional refresh. Extremely cheap to render. This is the single best fit for the potato-PC rule in section 11.

**Smaller Philly pieces worth having:** fare-gate banks, fare-card readers, cashier/booth windows, emergency intercom and help-point boxes, public-address speakers, platform CCTV housings, electronic message boards, transfer signs, mezzanine directional signage, "trains this side" indicators, platform markers, track-number signs, and service-change boards.

**Beyond Philly, keep it one-idea-per-culture and generic:** Japanese boarding-position markers and departure melodies, German/Swiss train-composition boards, Dutch platform-sector letters, UK "next fastest train" displays, Hong Kong exit lettering and dense interchange wayfinding, Singapore-style platform screen-door information panels, New York entrance globes and service-change signage.

The philosophy to hold onto: **take one excellent idea from each transit culture and make it generic and configurable.** Philadelphia supplies the gritty urban infrastructure, local/express logic, elevated stations, strong line identity, transfer-heavy signage and bus integration. Keep some Philadelphia DNA visible - the L is where this project started.

### 4.2 ATA candidates

| # | Item | Source | Phase |
|---|---|---|---|
| A1 | Modular wayfinding: line badge, colour, direction, destination, transfers | SEPTA | 1.2 |
| A2 | Station codes / station numbering (JR-East style) | S16, S37 | 1.2 |
| A3 | Independent per-Exit configuration on multi-exit signs | S29 | 1.2 |
| A4 | Side-by-side multi-language layout on entrance signs | S17 | 1.2 |
| A5 | Station suffix settings (station / airport / port / terminal) with per-context display | #1391 | 1.2 |
| A6 | Network-wide PIDS message broadcast (link displays for disruption notices) | S7 | 1.2 |
| A7 | Entrance pylons, Exit A/B signs, street-name signs, landmark arrows, connection signs | SEPTA | 1.2 |
| A8 | Accessibility family: tactile paving, boarding markers, help points, accessible route signs, lift status panel | SEPTA | 1.2 / 1.3 |
| A9 | E-paper bus stop arrival display (static, occasional refresh) | SEPTA | 1.2 |
| A10 | Fare-gate banks, fare-card readers, booth windows, PA speakers, CCTV housings, message boards | SEPTA | 1.3 |
| A11 | Service-change boards and mezzanine / "trains this side" / track-number signage | SEPTA | 1.3 |
| A12 | Elevated-transit architecture family (viaduct supports, stair towers, wind screens, noise barriers, under-El columns) | SEPTA | 1.3 |
| A13 | Curved platform pieces | S10 | 1.3, may need MTR cooperation |
| A14 | Timestamps for calling points on station displays | S27 | 1.3 |
| A15 | Custom announcements / voice packs instead of TTS | S20 | done in 1.1, refine in 1.2 |
| A16 | Through-train / non-stopping service warnings ("stand back") | S19 | blocked - MTR does not expose through trains |
| A17 | Custom platform screen-door text | #1450 | 1.3, needs MTR-side support |
| A18 | Station display from coach naming / composition | S27 | Later, depends on MTR train metadata |

### 4.3 Known ATA issues today

Both of these came from the in-thread issue list and should be treated as open defects, not backlog ideas:

1. **Route number glitches / flickering on PIDS and display boards.** Live-render defect. Relevant MTR-side context: #1504 (vehicle display sizes rounded), #1482 (non-Latin script scaling), #1366 (route sign branch overlap), #1377 (map diagonal glitches).
2. **Platform edge blocks do not open the train door.** ATA platform edge pieces are full-height decorative blocks aligned to MTR platform height. MTR door opening is driven by MTR's own platform/rail logic, so a decorative block at the platform face can prevent the interaction. Relevant upstream context: #1374 (new platform screen-door alignment), #1500 (platform gates stretch on large maps), #1422 / #1477 / #1451 (ticket barriers not working). This needs a documented placement rule and possibly a thinner collision profile before it is called solved.

---

## 5. ATU - Aurelia Transit Utilities

ATU is not "more ATA". It is the operational half: the friction MTR users keep reporting, packaged as an addon instead of a core change. The name is still provisional.

| # | Item | Source | Phase |
|---|---|---|---|
| U1 | Choose which station a platform belongs to when it sits inside two station zones | S6 | 1.0 - flagship |
| U2 | Save/load preset trains in the siding train editor | S23 | 1.0 |
| U3 | Drag-and-drop station reordering in route editing instead of arrow buttons | S35 | 1.0 |
| U4 | Per-route train speed and per-platform dwell time, set in the dashboard | S18 part, #1376 | 1.0 |
| U5 | Node orientation beyond 0 / 22.5 / 45 degrees via brush | #1435 | 1.0 |
| U6 | Trains able to use any platform they can reach, not a fixed one | S24 | 1.0 |
| U7 | Loop-line rest periods: return to sidings overnight, depart on a fixed interval | S28 | 1.2 |
| U8 | Partial route suspension for maintenance (Station A-B closed, C-D running) | S25 | 1.2 |
| U9 | Advanced scheduling: "leave the first station N minutes after depot departure" | S18 part | 1.2 |
| U10 | Signalling / dispatch tooling: signal boxes, control signals, switches, advanced dispatching | S12, S13 | 1.2+, large |
| U11 | Station differentiation on the Y axis (subway below overground counted separately) | S34 | 1.2 |
| U12 | Manual door release per platform with auto-close timer | S33 | Later, may need MTR door hooks |
| U13 | Advanced tunnel / bridge / semi-underground wall creator | S9, S39 | Later, building workflow |
| U14 | Per-player transit agencies with sharing (own stations, lines, depots) | S1 | Later, multiplayer data model |
| U15 | "Public transport only" route calculation in map / PIDS | S32 part | Later |

Same filter as ATA applies. U10 in particular is a project on its own and should not be bundled with anything else.

---

## 6. Later

Real, wanted, but explicitly not now:

- Coupling and uncoupling trains mid-route, including splitting an 8-car set so each half serves a different branch (S31, S38). Deep MTR train-model work.
- Advanced ticketing: single tickets, day/weekly/monthly passes, top-up machines, fare zones, station-to-station pricing (S22, S32 part, #1392, #1475, #1479, #1458).
- Coach naming (letter/number) feeding destination binds and displays (S27 part).
- Curved platform support beyond decorative pieces (S10).

---

## 7. Keep elsewhere

Valuable, but the right owner is not either Aurelia mod:

| Item | Source | Right home |
|---|---|---|
| Per-carriage vehicle settings (disable floor, lock door, disable motor) | S2 | MTR core / JCM scripting |
| Interior sound muffling for vehicles, configurable percentage | S3, S5 | MTR core audio |
| Escalator speed, lift motion internals, lift lobby behaviour, driver-key floor locks | S4 part, S14, S36 | MTR core lifts/escalators |
| Configurable lift speed, out-of-service schedules, car buttons, lift addon API | S11 | MTR core - already filed upstream as #1474 and #1478 |
| Trains belonging to multiple depots | S15 | MTR core depot model |
| Platform-level door-side selection and crew-only stations | S30 | MTR core platform logic |
| Plane maximum speed and chunk loading while flying | S26 | MTR core vehicles |
| Vehicles continuing to move in unrendered chunks | S32 part | MTR core simulation |
| Core rail rendering performance (20-30 fps cost, culling, shader compatibility) | S8 | MTR core |
| LOD / distant-terrain compatibility | S21 | Aurelia LOD and MTR rendering |
| Signal-block, node and routing correctness bugs | #1398, #1419, #1427, #1442, #1486 | MTR core |

**Note on the elevator cluster.** The single largest theme in the issue window is lifts: 14 of 114 issues. None of that belongs in ATA. But it does mean station builds that rely on MTR lifts are currently risky, and ATA's accessibility family should not assume a lift will render correctly for every player.

---

## 8. Not for Aurelia

Explicitly out of scope for both mods:

- Renderer rewrites (Vulkan migration) - #1408, #1473
- Minecraft version ports and loader matrix expansion - #1352, #1362, #1463, #1488
- MTR3 backports - #1489
- Third-party launcher memory support - #1499
- Wii U and other non-target platform versions - #1417
- Platform-as-support-channel requests - #1413
- Anything that only makes sense as SEPTA branding rather than generic design logic

The instruction that produced this bucket still stands: if it is not necessary for the mod, and it is not ours to fix, it does not go in.

---

## 9. MTR GitHub issue findings, 2025-12-01 to 2026-09-30

Verified against the live tracker index on 2026-09-30. Issues only; pull requests excluded.

### 9.1 Window statistics

| Metric | Value |
|---|---|
| Issues created in window | 114 |
| Open | 101 |
| Closed | 13 |
| Labelled `bug` | 70 |
| Labelled `enhancement` | 34 |
| Labelled `from-discord` | 14 |
| Labelled `confirmed` | 9 |
| Unlabelled | 10 |

By month: Dec 7, Jan 7, Feb 12, Mar 6, Apr 8, May 10, Jun 21, Jul 18, Aug 15, Sep 10.

Two things follow immediately. First, **101 open against 13 closed over ten months** - the upstream backlog is not clearing, so any Aurelia plan that depends on MTR fixing something should be treated as blocked rather than scheduled. Second, the volume roughly doubles from June onward, which is worth watching: a lot of that cluster is 4.1 beta fallout.

### 9.2 Rendering and loader compatibility

The largest technical theme. Shaders, Vulkan requests, and mod-interop rendering breakage dominate.

| Issue | Date | State | Title |
|---|---|---|---|
| #1373 | 2026-02-06 | closed | 3D rails doesn't render |
| #1394 | 2026-04-08 | open, confirmed | 4.0.3 + Oculus incompatibility |
| #1397 | 2026-04-14 | closed | Incompatible with Shader Packs |
| #1400 | 2026-04-28 | open, confirmed | Texture brightness at train connection goes abnormally dark |
| #1402 | 2026-05-01 | open, confirmed | Rails fail to cull |
| #1408 | 2026-05-14 | open | Moving to Vulkan |
| #1436 | 2026-06-20 | open | Incompatible with Mellow Shader |
| #1455 | 2026-07-05 | open | Improve rails and trains display with shaders |
| #1462 | 2026-07-14 | open | Rails still render beyond view distance in 4.1.0 beta 2 |
| #1464 | 2026-07-16 | open | Car lights do not render |
| #1472 | 2026-07-27 | open | OBJ trains and tracks missing in 4.1.0-beta2 |
| #1473 | 2026-07-29 | open | Render using Vulkan in the future |
| #1476 | 2026-08-03 | open | NeoForge 4.1.0: black textures, transparency, glowing models |
| #1480 | 2026-08-16 | open | Eye Candy rendering with Voxy |
| #1481 | 2026-08-16 | open | Trains and tracks do not render in Replay playback |
| #1485 | 2026-08-26 | open | Eye Candy stays a transparent placeholder |
| #1504 | 2026-09-19 | open | Vehicle display sizes are rounded |

Practical consequence for ATA: ATA renders its own display surfaces. Anything MTR gets wrong about culling, shader compatibility or unrendered-chunk behaviour will show up in ATA's displays too. Do not promise "works with shaders" until ATA has been tested with Iris plus at least one of Sodium/Iris-adjacent setups, on its own hardware.

### 9.3 Signage, PIDS, maps and wayfinding

Directly on ATA's turf, and unusually rich.

| Issue | Date | State | Title |
|---|---|---|---|
| #1355 | 2025-12-10 | open | Minecraft skin not reflected on System Map |
| #1365 | 2026-01-25 | open, confirmed | Hidden routes return no "calling at" information |
| #1366 | 2026-01-27 | open | Route sign branch station names overlap |
| #1377 | 2026-02-21 | open | Map diagonal lines glitch in some browsers |
| #1381 | 2026-02-26 | open, confirmed | Announcer route filters miss lines and behave inconsistently outside station areas |
| #1390 | 2026-04-03 | open | Double signs (stacked variants requested) |
| #1391 | 2026-04-04 | open | Station suffix settings |
| #1414 | 2026-05-27 | closed | Station colour blocks not persisting |
| #1433 | 2026-06-18 | open | Driver's key colour wrong |
| #1450 | 2026-06-28 | open | Custom platform screen-door text |
| #1467 | 2026-07-23 | open | Transit map only plans down-direction routes |
| #1482 | 2026-08-16 | open | Non-Latin script scaling |
| #1492 | 2026-09-01 | open | Station exits duplicated, signs non-functional |
| #1500 | 2026-09-13 | open | Platform gates stretch on 10+ block maps |
| #1505 | 2026-09-20 | open | The non-CJK switch doesn't work |

This cluster is the strongest single argument for ATA existing and for the wayfinding work in section 4.1. It is also a warning: **#1482 and #1505 both concern text rendering for non-Latin scripts.** ATA's signs and displays must be tested with CJK, Cyrillic and Arabic-script station names before a 1.2 release is called done.

### 9.4 Lifts, escalators and vertical circulation

The largest single cluster by count, and almost entirely upstream's problem.

| Issue | Date | State | Title |
|---|---|---|---|
| #1357 | 2025-12-27 | open | Elevator and escalator suggestions (speed slider, door buttons, materials, Q-Train) |
| #1358 | 2025-12-31 | open | Both lift doors open at once when floors are close together |
| #1375 | 2026-02-12 | open | Dwell-time option appears when editing a cable-car platform |
| #1380 | 2026-02-23 | open, confirmed | Lift position wrong after adding a floor below the lowest one |
| #1383 | 2026-02-28 | closed, confirmed | Lift rendered at 0,0,0 with only one floor |
| #1429 | 2026-06-14 | open | Lift and train models invisible to non-creators on a hybrid server |
| #1430 | 2026-06-16 | open | I can't see the elevator |
| #1449 | 2026-06-27 | open | Elevator not spawning |
| #1454 | 2026-07-05 | open | Lift door opening problem |
| #1474 | 2026-07-30 | open | Configurable motion, out-of-service controls, car buttons, API support |
| #1478 | 2026-08-09 | closed | Adjustable lift ascent and descent speed |
| #1483 | 2026-08-17 | open | Lift floor-selection menu does not appear |
| #1490 | 2026-08-27 | open | Lift bug (unspecified) |
| #1491 | 2026-09-01 | open | Ramp escalators requested |

ATA takeaway: build accessibility features (section 4.1) so they still make sense when the lift is misbehaving, and prefer ground-level or ramp-based accessible routes in example builds.

### 9.5 Signalling, routing and train behaviour

| Issue | Date | State | Title |
|---|---|---|---|
| #1371 | 2026-01-30 | open | Loop lines don't run in loops |
| #1382 | 2026-02-28 | open | Path cannot refresh; cannot get off normally |
| #1387 | 2026-03-19 | open | Rails flip the wrong way in certain circumstances |
| #1398 | 2026-04-17 | open | Nodes gone after loading world |
| #1411 | 2026-05-19 | open | Train glitches on intersection |
| #1418 | 2026-06-06 | open, confirmed | Trains overshoot then snap back to the stop position |
| #1419 | 2026-06-06 | open | Trains refuse to enter signal blocks in some cases |
| #1427 | 2026-06-13 | open | Train turns around when a turn-off shares the platform rail end node |
| #1442 | 2026-06-23 | open | Ghost stop (9 comments) |
| #1446 | 2026-06-26 | open | NTE bridge creator calculation not carried over into MTR4 |
| #1457 | 2026-07-08 | open | Schedule sensor activates twice |
| #1459 | 2026-07-10 | open | Signalling priority issues |
| #1469 | 2026-07-24 | open | Signal does not face certain directions |
| #1486 | 2026-08-26 | open | Train permanently stuck at a station, doors open, never departs |
| #1502 | 2026-09-17 | open | Rail placing preview not appearing |
| #1503 | 2026-09-19 | closed | Redstone-controlled block occupancy controller |
| #1506 | 2026-09-27 | open | Rail flip option has no effect in 4.1 |

ATU takeaway: this is the demand evidence for U1-U12. It also explains why ATU must stay narrow. #1446 in particular shows what happens when a creator tool is rebuilt without the original calculation method.

### 9.6 Performance, freezes and server health

| Issue | Date | State | Title |
|---|---|---|---|
| #1384 | 2026-03-01 | open | Game freezes when in train |
| #1402 | 2026-05-01 | open, confirmed | Rails fail to cull |
| #1407 | 2026-05-14 | open | Freeze when riding a vehicle and reloading resource packs |
| #1456 | 2026-07-07 | open | `Utilities.circularClamp()` significantly affects server TPS |
| #1484 | 2026-08-20 | open | Frequent GC stutter on 4.0.5 servers |
| #1496 | 2026-09-05 | open | Server repeatedly GC-ing on newer MTR |

This is the same complaint set that produced the Discord dump's "optimize the mod, rails eat 20-30 fps even in 2D" entry. ATA cannot fix MTR, but ATA must not add to it. Section 11 is the contract that follows from this.

### 9.7 Fares, barriers and ticketing

| Issue | Date | State | Title |
|---|---|---|---|
| #1392 | 2026-04-06 | open | Fare zone and ticketing system rework |
| #1422 | 2026-06-09 | closed | Ticket barrier not working |
| #1451 | 2026-07-02 | open | Barrier glitch |
| #1458 | 2026-07-10 | open | Request for a physical Octopus-style card entity |
| #1475 | 2026-07-30 | open | Configure a currency item |
| #1477 | 2026-08-04 | open | Ticket barriers unusable |
| #1479 | 2026-08-09 | open | Config option to pass barriers at no cost |

ATA takeaway: fare gates, readers and booth windows are safe decorative props (section 4.2, A10). Any actual fare logic stays upstream.

### 9.8 Demand signals that are not ours to act on

#1352 (1.20.4 tilting-track build), #1362 (1.21.1), #1417 (Wii U), #1420 and #1425 (Kotlin for Forge conflict on 4.1), #1421 (missing channel), #1463 (version 26.1), #1488 (1.21.11), #1489 (MTR3 backport), #1499 (third-party launchers). Real demand, wrong team. Listed so the record is complete, then closed as "Not for Aurelia".

### 9.9 Discord dump items that are now formally filed upstream

Worth noting, because it changes the priority of a few entries in the dump. The elevator feature bundle from Discord (S11) exists upstream as **#1474** and **#1478**. The signage items map to **#1390** (double signs), **#1391** (station suffix), **#1414** (colour persistence), **#1450** (platform door text), **#1492** (duplicated exits). Operational items map to **#1376** (per-route speed / dwell), **#1435** (node orientation), **#1459** (signalling priority), **#1503** (occupancy controller), **#1506** (rail flip). Ticketing maps to **#1392**, **#1475**, **#1479**, **#1458**.

So roughly a third of the Discord dump is not speculative at all - it is already tracked, still open, and still unfixed. That is the strongest available evidence that an addon has room here.

### 9.10 What the window means for the two mods

1. Upstream is not going to absorb this work on any useful timescale. 101 open issues, 13 closed, ten months.
2. The station-facing half of the problem (signage, PIDS, wayfinding, text rendering, accessibility props) is under-served and is exactly ATA's remit.
3. The operations half (overlap, presets, scheduling, dispatch, building workflows) is under-served and is exactly ATU's remit.
4. Rendering and performance are the shared risk. ATA/ATU must be measurably cheap, because the player base is already fighting frames and TPS.
5. Non-Latin text and shader compatibility are ATA-specific release gates, not nice-to-haves.

---

## 10. Proposed release sequence

### ATA 1.1 - sealed first (current line)
Live displays, announcements, speakers, voice packs, interactive station content. Finish and test the two open defects in section 4.3 before adding scope.

### ATA 1.2 - wayfinding and accessibility
A1 wayfinding system, A2 station codes, A3 independent exits, A4 side-by-side languages, A5 station suffixes, A6 network PIDS messages, A7 entrance pylon / exit / street signage, A8 first accessibility pieces, A9 e-paper bus arrival display. Text-rendering and shader gates apply.

### ATA 1.3 - urban infrastructure
A10 fare gates and station equipment, A11 service-change and mezzanine signage, A12 elevated-transit architecture family, A13 curved platform pieces, A14 calling-point timestamps, A17 platform door text once MTR supports it.

### ATU 1.0 - the overlap and preset release
U1, U2, U3, U4, U5, U6. Small surface, high demand, no new rendering. This is the smallest possible proof that ATU should exist.

### ATU 1.2+ - operations
U7, U8, U9, U11 first; U10 signalling and dispatch only when it can be scoped as its own project; U12-U15 later.

Keep the two release lines independent. ATA slipping must not block ATU, and ATU 1.0 must not depend on an ATA rendering change.

---

## 11. The potato-PC performance contract

This is a standing requirement, not an aspiration. The player base is already reporting rails costing 20-30 fps, broken culling, TPS loss in `circularClamp()`, and servers stuck in GC.

Rules for anything added to either mod:

1. **No per-tick block entity work.** Displays draw only when visible and in range. Speakers resolve only while loaded and only for players inside the radius.
2. **Bounded caches with eviction.** The existing pattern - at most 512 / 512 / 128 entries, 30 s sweep, 2 s / 5 s refresh windows - is the standard. New systems copy it.
3. **Static-first displays.** The e-paper bus arrival board is the model: a still image that changes occasionally. Animated, high-refresh displays are opt-in, never default.
4. **No new polling of the server.** Everything client-side from MTR's already-synced data.
5. **Measure before claiming.** Any new display or speaker family gets the stress scenario in `docs/LIVE_TESTING.md` (about 50 displays, 20 speakers) run with counters on, and the before/after numbers recorded.
6. **Version-appropriate testing.** Test on 1.20.1 Fabric with MTR 4.x plus Iris, since shader compatibility is where upstream is currently weakest.
7. **Fail visibly, not silently.** Missing station, missing route, missing arrival data - show the idle message rather than inventing data. Nothing gets fabricated to make a screenshot look better.

---

## 12. Summary of the classification

| Bucket | Count of dump items | Notes |
|---|---|---|
| ATA | 14 | signage, wayfinding, PIDS, accessibility, bus |
| ATU | 15 | overlap, presets, scheduling, dispatch, building tools |
| Later | 4 | coupling, ticketing, coach naming, curved platforms |
| Keep elsewhere | 12 | MTR core, JCM, train packs, Aurelia LOD |
| Not for Aurelia | 5 groups | renderer rewrite, version ports, launchers, branding |

Plus, from the issue window: one ATA-blocked item (#1450, awaiting MTR), one ATA release gate (non-Latin text, #1482 / #1505), and a standing performance requirement driven by section 9.6.

---

## 13. Method and provenance

- **Verified in this pass:** the MTR issue index for 2025-12-01 through 2026-09-30 (114 issues, counts, labels, states, monthly distribution, and the titles referenced above) was read from the live tracker on 2026-09-30.
- **Verified from disk:** ATA's current block families, 1.1 live systems, MTR integration contract, and the 50-display performance scenario, read from the `Aurelia Transit Architecture` checkout at `~/Downloads/Aurelia Transit Architecture`.
- **Carried from this thread:** the Philadelphia / SEPTA research, the Discord suggestion dump (39 items, referenced as S1-S39), and the two current ATA defects.
- **Judgement, not evidence:** every ATA / ATU / Later / Keep elsewhere / Not for Aurelia assignment in this document is a proposal from this triage. Nothing here is committed to a roadmap until it is separately agreed.

This is a living document. Re-run the issue pull before acting on section 9, since the counts will drift.
