# ATU kickoff: Aurelia Transit Utilities

Start a new Claude Code session in this folder and say: "Read KICKOFF.md and start."

## What ATU is

The operational half of the Aurelia transit mods (see `docs/Aurelia_Transit_Feature_Triage_2026-09-30.md`, sections 1, 5, 10, 11). ATA (`../Aurelia Transit Architecture`, repo `aurelia-works/aurelia-transit-architecture`) makes stations look real; ATU makes MTR easier and deeper to operate. Separate mod, separate release line: ATU must never depend on ATA.

Target: Minecraft 1.20.1, Fabric, MTR 4.0.5 (same toolchain as ATA: copy its `build.gradle`, `gradle.properties`, Loom and yarn versions).

## ATU 1.0 scope (from the triage)

| # | Item |
|---|---|
| U1 | Choose which station a platform belongs to when it sits inside two station zones (flagship) |
| U2 | Save/load preset trains in the siding train editor |
| U3 | Drag-and-drop station reordering in route editing |
| U4 | Per-route train speed and per-platform dwell time in the dashboard |
| U5 | Node orientation beyond 0 / 22.5 / 45 degrees |
| U6 | Trains may use any platform they can reach |

## Rules carried over from ATA

- MTR addon only. **No mixins into MTR and no MTR core changes** unless the owner decides so explicitly for a specific item. Public MTR classes and data only (ATA calls `TicketSystem` and implements `PlatformHelper` this way).
- Potato-PC contract (triage section 11): no per-tick work, bounded caches, no new server polling, fail visibly.
- Never fake MTR data. Where MTR lacks an API, build the honest subset and document the gap.
- Unit-test every pure-logic class. Do not claim something works until it ran (dev client or the Noriega Prism instance).
- Commit locally after each step; push to the private repo only when the owner asks.

## First steps

1. **Feasibility audit, no code yet.** For U1-U6, read the MTR 4.0.5 jar (`~/.gradle/caches/modules-2/files-2.1/maven.modrinth/minecraft-transit-railway/`, `javap`) and decide per item: doable without mixins / needs an MTR change / needs a mixin (owner decision). Several items live inside MTR's own screens and simulation (MTR core runs in a separate process-like "Main" with its own data), so expect some to be blocked. Write `docs/FEASIBILITY.md` with the evidence (class + method names).
2. Owner picks the ATU 1.0 subset from that audit.
3. Scaffold the Fabric project (mod id `aurelia_transit_utilities`), build, dev client boots with MTR.
4. Build the chosen items one at a time, each with tests, a live check and a session log (`docs/SESSION_LOG.md`).
5. Create the private repo `aurelia-works/aurelia-transit-utilities` when there is a first working build.
