# Aurelia Transit Utilities (ATU)

Quality-of-life tools for [Minecraft Transit Railway](https://github.com/jonafanho/Minecraft-Transit-Railway) 4.0.5 on Minecraft 1.20.1 (Fabric). No new blocks; blocks live in the sister mod Aurelia Transit Architecture (ATA).

ATU is **client-side only**: install it on your client, the server does not need it. It adds an **ATU** button to MTR's dashboard (top-left of the map) that opens these tools:

| Tool | What it does |
|---|---|
| Train presets | Save a siding's train under a name and put it on any other siding. |
| Route stop order | Drag a route's stops into a new order (or Move up / down, Reverse). |
| Platform dwell times | Set the dwell time of many platforms at once: a station, a route or all. |
| Departure times | Build timed departures (even service, rest hours, shift, "times at the first station"), set trains per hour for a range of hours, or space trains evenly on a loop. |
| Station overlaps | A platform inside two station zones: choose which station it belongs to. |
| Zone heights | Give station and depot zones a height range, so stacked stations can share a map area (MTR #815). |
| Station codes | Number a route's stations JR East style (`Shinjuku JY17`). |
| Depot health | Every depot's last generation result and setup problems, worst first, with Regenerate. |
| Jump to | Teleport to any station, platform, depot or siding (uses `/tp`). |

## Announcer (1.1.0)

While you ride an MTR train, ATU announces the next station, arrivals, "change here for" other routes, route continuation and the last stop. The lines go to your local chat (nothing is sent to the server) and replace MTR's own chat announcement for the ride. If a resource pack provides the sound events `mtr:aureliaex_ann_<key>`, ATU plays them too; without them it is text only. On/off button in the ATU hub; English only.

## Vehicle scripts (1.2.0)

When Joban Client Mod is installed, ATU publishes its settings to JCM vehicle scripts through JCM's `registerAddonVersion`: its version, whether the Announcer is on, and a **Vehicle melodies** on/off preference (ATU hub) that scripts may read to mute departure melodies. Scripts that do not read it are unaffected. Keys and behaviour: `docs/HANDSHAKE.md`.

## How it works

ATU uses only MTR's public classes. It reads the data MTR already sends to the client when its dashboard opens and saves with the same update packets MTR's own screens use. It has no mixins and changes nothing inside MTR. Editing follows MTR's own permission rule (creative or survival mode); Jump to needs `/tp` rights.

What ATU deliberately does not do, because MTR itself would have to change: per-route train speed, rail angles finer than 22.5°, trains choosing any free platform, rest hours on "repeat infinitely" loops. See `docs/FEASIBILITY.md`.

## Building

```
./gradlew build          # jar in build/libs, runs the unit tests
./gradlew runClient      # dev client with MTR
./gradlew runSelftest    # dev-only in-game self-test, see docs/LIVE_TESTING.md
```

Java 17, Fabric Loader ≥ 0.15, Fabric API ≥ 0.92, MTR 4.0.5.

## Docs

- `docs/HANDSHAKE.md`: keys ATU publishes to JCM vehicle scripts.
- `CHANGELOG.md`: release history.
- `docs/FEASIBILITY.md`: what an add-on can and cannot reach in MTR 4.0.5, with evidence.
- `docs/LIVE_TESTING.md`: in-game checklist and the automated self-test.
- `docs/SESSION_LOG.md`: what was built and how each piece was verified.
- `docs/MTR_ISSUES_REVIEW.md`, `docs/OWNER_IDEAS_REVIEW.md`: where the feature list comes from.

License: MIT.
