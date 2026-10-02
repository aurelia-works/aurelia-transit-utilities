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

- `docs/FEASIBILITY.md`: what an add-on can and cannot reach in MTR 4.0.5, with evidence.
- `docs/LIVE_TESTING.md`: in-game checklist and the automated self-test.
- `docs/SESSION_LOG.md`: what was built and how each piece was verified.
- `docs/MTR_ISSUES_REVIEW.md`, `docs/OWNER_IDEAS_REVIEW.md`: where the feature list comes from.

License: MIT.
