# Aurelia Transit Utilities (ATU)

Quality-of-life tools for [Minecraft Transit Railway](https://github.com/jonafanho/Minecraft-Transit-Railway) 4.0.5 on Minecraft 1.20.1 (Fabric). No new blocks; blocks live in the sister mod Aurelia Transit Architecture (ATA).

ATU is **client-side only**: install it on your client, the server does not need it. It adds an **ATU** button to MTR's dashboard (top-left of the map) that opens these tools:

| Tool | What it does |
|---|---|
| Train presets | Save a siding's train (optionally with its settings: acceleration, deceleration, max trains, manual mode and delay handling) under a name and put it on any siding, or on every siding of a depot at once. |
| Route stop order | Drag a route's stops into a new order (or Move up / down, Reverse). |
| Platform dwell times | Set the dwell time of many platforms at once: a station, a route or all. |
| Departure times | Build timed departures (even service, rest hours, shift, "times at the first station"), set trains per hour for a range of hours, or space trains evenly on a loop. |
| Station overlaps | A platform inside two station zones: choose which station it belongs to. |
| Zone heights | Give station and depot zones a height range, so stacked stations can share a map area (MTR #815). |
| Station codes | Number a route's stations JR East style (`Shinjuku JY17`). |
| Depot health | Every depot's last generation result and setup problems, worst first, with Regenerate. Also catches the "generated fine but nothing runs" cases: no departures, sidings not linked to any track, platforms outside every station. |
| Return route | Build the opposite direction of a route in one go: stops reversed, each moved to the other platform of its station, every stop adjustable before saving; optionally joins the same depots. |
| Speed check | Read-only check of the rails around you: slow spots between faster rails and curves set faster than is comfortable, with Jump. |
| Train inspector | Every train within render distance (the one you ride first): route, next stop, depot, siding, speed against the rail limit, and the delay MTR predicts. |
| Jump to | Teleport to any station, platform, depot or siding (uses `/tp`). |

## Announcer (1.1.0)

While you ride an MTR train, ATU announces the next station, arrivals, "change here for" other routes, route continuation and the last stop. The lines go to your local chat (nothing is sent to the server) and replace MTR's own chat announcement for the ride. If a resource pack provides the sound events `mtr:aureliaex_ann_<key>`, ATU plays them too; without them it is text only. On/off button in the ATU hub; English only.

### Platform calls (1.3.0)

Standing on a platform (within 4 blocks), ATU announces the next train from MTR's own arrival data, the same data its departure screens show: "approaching" about a minute out, "now arriving" in the last 15 seconds, and once "delayed" when MTR predicts it at least 2 minutes late. Only while you are at a platform; separate on/off button (**Platform calls**) in the ATU hub. Resource packs can provide `platform_approaching`, `platform_arriving` and `platform_delayed` clips; otherwise a chime.

## Vehicle scripts (1.2.0)

When Joban Client Mod is installed, ATU publishes its settings to JCM vehicle scripts through JCM's `registerAddonVersion`: its version, whether the Announcer is on, and a **Vehicle melodies** on/off preference (ATU hub) that scripts may read to mute departure melodies. Scripts that do not read it are unaffected. Keys and behaviour: `docs/HANDSHAKE.md`.

## Performance (1.4.0)

A **Performance** page in the ATU hub fixes slow spots in MTR 4.0.5's train and rail rendering and its model loading. Every fix is a switch, on by default; off runs MTR's own code. The fixes apply only on MTR 4.0.5 (on any other version they stay off) and are client-side.

- **Merge train parts**: a car is drawn with one call per material instead of one per OBJ group and material, and the parts both door states use are uploaded once instead of twice.
- **Shader pack fast path**: with Iris, MTR re-bound the whole shader for every draw call; ATU binds it once per batch.
- **Cheaper shadows** (shader packs): no translucent parts and no interiors in the shadow pass; rails use their far tier.
- **Train and rail LOD**: distant cars lose their interior, then become a box without loading the model; distant 3D rails become MTR's flat strips.
- **Faster model loading**: ATU's own OBJ reader and mesh builder (same output as MTR's, checked vertex by vertex on 50 OBJ files from 8 packs) and no shader recompile per model.
- **Load trains in the background**: OBJ files are read and the car's meshes are built on worker threads, so a train coming into view no longer freezes a frame.
- **Faster door checks**: MTR searched every block around every doorway of every stopped train each frame; ATU remembers the result until a block changes (and checks itself against MTR's search as it goes).
- **Free unused models**: models MTR drops give back their memory and GPU buffers.
- Always on (same result as MTR): MTR's shadow-pass check no longer walks the thread stack several times per frame.
- F3 shows ATU's counters (MTR draw calls, render time, all of MTR's frame time, door checks, model builds).

Measured numbers: `docs/SESSION_LOG.md` and `tools/perfbench/`.

## How it works

The tools use only MTR's public classes. They read the data MTR already sends to the client when its dashboard opens and save with the same update packets MTR's own screens use. Editing follows MTR's own permission rule (creative or survival mode); Jump to needs `/tp` rights.

The performance fixes (1.4.0) are the only part that changes MTR: client-side mixins into its rendering and model loading, checked against MTR 4.0.5 and switched off on any other version. They never touch MTR's data, saves or network traffic.

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
