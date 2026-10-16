# Changelog

## 1.4.0 (not released)
- New **Performance** page in the ATU hub with fixes for MTR 4.0.5's renderer. Each is a switch; off runs MTR's own code. Saved in `config/aurelia_transit_utilities_performance.properties`. Only active on MTR 4.0.5; on any other version they stay off.
- Merge train parts: each MTR model is uploaded as one buffer per material instead of one per OBJ group per material. About half the draw calls for OBJ trains (2,280 → 1,099 on 56 test models), same geometry. The parts both door states use are uploaded once and shared by the doors-open and doors-closed models (MTR uploaded them twice): 88 of 144 vertex arrays reused for a 2-car NS Civity.
- Shader pack fast path: with Iris, MTR re-bound the whole shader for every draw call; now only the part's matrix uniforms are set inside a batch.
- Train LOD: cars beyond a distance (default 5 chunks) are drawn without interior parts; beyond another (default 10 chunks) as a plain box in the vehicle's colour, without loading the model. The ridden train is always full.
- Rail LOD: 3D rail pieces beyond a distance (default 3 chunks) are drawn as MTR's flat rail strips, which MTR batches; beyond another (default 8 chunks) one strip covers 4 blocks. Rail models taller than half a block (catenary, walls) keep their 3D model and are only thinned far away.
- Cheaper shadows with shader packs: in the shadow pass MTR skips translucent parts, cars always use their exterior parts, and rails use the far tier.
- Faster model loading: ATU's own OBJ reader returns exactly the meshes MTR's javagl reader does (checked vertex by vertex on 50 OBJ files from 8 packs and 36 edge cases), 3.4-3.7x faster; unusual files still go to javagl. Train models are merged in primitive arrays instead of one Java object per triangle corner (2.8x faster, byte-identical buffers), and MTR no longer recompiles its shaders for every model it loads. Render-thread time for a 2-car train after a reload: about 1.1 s → 0.45 s.
- Load trains in the background (new switch): a train's OBJ files are parsed on worker threads, all files of a car at once, and so are the merged meshes of its four models (doors open, doors closed, two bogies); the train is built when they are ready instead of freezing frames. Materials, textures and GPU uploads stay on the render thread; forced builds and resource reloads stay as they were. A heavy car coming back into view: longest single stall 68 → 32 ms (2-car NS Civity, profiled; most of what is left is MTR placing parts and garbage collection).
- Faster door checks (new switch): every frame, for every doorway of every stopped train, MTR looked at every block around the doorway for a platform or platform screen door (in a profile of a parked 2-car train, about 40% of MTR's render-thread time). ATU remembers what each doorway's box holds until any client block changes, and still reads the door blocks themselves every frame, so platform screen doors open as before. Every 256th check also runs MTR's own and compares; on any difference ATU's is switched off for the session (250 compared, 0 different). MTR's per-frame CPU in the test view: 19.8 → 5.5 ms.
- MTR's shadow-pass check no longer walks the whole thread stack each time it is asked (several times per frame, with or without a shader pack): without Iris or OptiFine the answer is always "no"; with them it is worked out once per MTR render pass and checked against MTR's way every 128th call.
- Frees unused models: MTR's model cache stops holding every model forever, and the GPU buffers of a dropped model are deleted (MTR left them allocated).
- F3 lines: MTR draw calls and render time per frame (and all of MTR's per-frame time), door checks, model builds, freed and shared GPU arrays, OBJ reading and background loads.
- This is the first ATU version with mixins into MTR (client-side, rendering only).

## 1.3.0
- Return route: builds the opposite direction of a route (stops reversed, other platform of each station, ends keep theirs); every stop can be switched before saving; optionally added to the same depots.
- Speed check: read-only list of slow spots and too-fast curves in the loaded rails, with Jump.
- Train inspector: loaded trains with route, next stop, depot, siding, speed vs limit and predicted delay.
- Platform calls: approaching / now arriving / delayed announcements while you stand on a platform, from MTR's arrival data. Own on/off button in the hub.
- Train presets can carry siding settings (acceleration, deceleration, max trains, manual mode and delay options) and apply to a whole depot. Preset file version 2; version 1 files still load.
- Depot health: new checks for depots with no departures, sidings not linked to track, and platforms outside any station.

## 1.2.0
- Vehicle melodies: new on/off button in the ATU hub, a preference for Joban Client Mod vehicle scripts. Saved in `config/aurelia_transit_utilities/handshake.properties`.
- Handshake protocol 2: when Joban Client Mod is installed, ATU publishes its version, `handshake` (protocol), `announcer`, `announcer_lang`, `melodies` and `caps` keys through JCM's `registerAddonVersion`, and republishes them when you change a setting. Scripts read them with `Resources.getAddonVersion`; scripts that do not are unaffected. Keys: `docs/HANDSHAKE.md`.

## 1.1.0
- Announcer: on MTR trains you ride, announces the next station, arrivals, "change here for" other routes, route continuation and the last stop. English only; on/off in the ATU hub.
- The announcement lines go to your local chat and replace MTR's own chat announcement while you ride. Station clips (sound events `mtr:aureliaex_ann_<key>`) play when a resource pack provides them, otherwise text only.
- Tells Joban Client Mod vehicle scripts whether the Announcer is on.

## 1.0.1-hotfix
- The ATU button sits under MAGIC's button and is clickable over MTR's map.

## 1.0.1
- The ATU dashboard button moved beside MAGIC's menu button.

## 1.0.0
- Train presets, route stop order, platform dwell times, departure times, station overlaps, zone heights, station codes, depot health and Jump to.
