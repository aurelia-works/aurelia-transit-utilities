# MTR performance in ATU: draw calls, LOD for 3D rails and OBJ trains, loading

Date: 2026-10-16. Target: MTR 4.0.5, Minecraft 1.20.1 Fabric. Status: plan only, nothing built.
Builds on `docs/MTR_OBJ_PERFORMANCE.md` (loading fixes M1–M7 and the benchmark).

## 1. Where it lives

Owner decision (2026-10-16): **in ATU**, as client-only performance mixins into MTR (KICKOFF exception recorded). Same model as Joban Client Mod, which already ships mixins into MTR. The rest of ATU stays mixin-free. The "not stepping on toes" rules in section 4 apply to ATU.

## 1b. Why 3 trains can cost a 3070 most of its frame rate (per-frame cost, from the jar)

Load stalls (the earlier benchmark) are one problem; the frame rate after loading is another. Hypotheses, with evidence:

1. **One draw call per part, per material, per car, every frame.** `BatchManager.RenderCall.draw` binds a VAO, sets ~7 vertex attributes (allocating a new `ByteBuffer` for the matrix each time) and calls `glDrawElements` once per uploaded part. Measured part counts per car from the benchmark (doors-closed set, i.e. what is drawn each frame): SEPTA M4 body ~50, NS Civity cab ~87, NS ICM ~57, M5000 ~10. Three 6-car NS trains are therefore ~1,500 draw calls a frame before bogies, rails and displays. The OBJs have 18–40 `usemtl` materials each, and MTR keeps every group × material separate even though all of them share the car's one matrix.
2. **With a shader pack it is far worse.** When a shader pack is on, `Utilities.canUseCustomShader()` is false, and `VertexAttributeState.apply` then calls `ShaderProgram.bind()` (`method_34586`) **on every draw call** to set the model matrix. Under Iris 1.7.6 that is `ExtendedShader.bind()`: two matrix inversions, three texture binds, all Iris sampler/uniform/image updates and a **framebuffer bind**, per draw. Iris also runs the shadow pass, so everything is drawn twice. Vanilla's own entities do not do this per draw. This is the most likely reason for "20 FPS with 3 trains" on a strong GPU, if that player runs shaders.
3. **CPU work per car per frame** in `RenderVehicles.render`: stream/collector allocations, nested `CachedResource` lookups (`getCachedVehicleResource` is called twice per car), doorway and floor boxes for every car at any distance.

To confirm in game (needs a dev client): an F3 line with MTR draw calls per frame and the time spent in `OptimizedRenderer.render`, at 3 NS trains, shaders on vs off. If (2) is right, frame time falls sharply with shaders off; if (1) dominates, it scales with draw count either way.

Fixes, best first:

| Step | Fix | Gain (expected, to be measured) | Risk |
|---|---|---|---|
| D1 | **Merge parts per car**: one buffer per material (MTR's own `MaterialProperties`: shader, texture, colour, light) per part condition, instead of one per OBJ group × material. **Built.** Measured on the 56 models: 2,280 → 1,099 draws (2.1x), GPU data bitwise identical (`tools/objbench/results-draws-2026-10-16.txt`). Moving colour into the vertex data would only reach 1,003, because the NS packs use ~48 textures per car; not worth it. (MTR also has an unused `INSTANCE_BUFFER` attribute source that could draw all pieces of a 3D rail in one instanced call.) | 2.1x fewer train draws | Low: same geometry and materials |
| D2 | **Shader-pack path**: bind the shader once per batch, and per draw upload only the model-matrix uniforms (`ModelViewMat` and Iris's `iris_ModelViewMatInverse` / `iris_NormalMat`, computed as Iris 1.7.6 does) directly. **Built**, not yet run in game | removes thousands of full Iris binds per frame | Medium: must be checked against Iris and a couple of shader packs |
| D3 | Shadow pass draws the cheapest LOD tier, no interiors, no translucent parts | roughly halves the shader-pack cost | Low |
| D4 | No per-draw `ByteBuffer` allocation; reuse one buffer | GC pressure | Low |

Status 2026-10-16 (first agent hour, no Minecraft): D1, D2, the F3 counters, the settings page and train LOD tiers T1 (no interior) and T2 (box, no load) are built and pass the build and unit tests; D1 is verified bitwise on 56 models headless. Nothing has run in game yet. D3/D4 and rail LOD are not built.

## 2. What MTR 4.0.5 does today (from the jar)

### 3D rails (`RenderRails`)
- Every frame, for every rail the client knows about, `renderWithinRenderDistance` walks the whole curve (`railMath.render`) at the style's `repeatInterval` and keeps every piece within render distance × 16 blocks that is in front of the camera (no other distance cut).
- For a 3D style, each kept piece calls `railResource.render(..)`: one model instance per piece, with two light lookups, `atan2` maths, a new matrix and lambdas.
- MTR's built-in `default_3d` rail is `mtr:models/rail/rail.obj`: **48 triangles, repeated every 0.5 blocks**. That is 2 instances per block of track per material, and `BatchManager.drawBatch` issues **one `glDrawElements` per instance** (`VertexArray.draw`). The triangle count is tiny; the cost is draw calls and per-piece CPU. Custom 3D rail styles in packs often have hundreds or thousands of triangles per piece.
- Rough count (arithmetic, not measured): at render distance 12 (192 blocks), one double-track line in view is about 800–1,500 draw calls per frame for rails alone, depending on whether it runs away from you or across your view. A station throat or yard is several times that.
- With Iris shaders, the rail pass also runs in the shadow pass (`renderingShadows()` only gates the culling job), so the cost roughly doubles.

### OBJ trains (`RenderVehicles`, `VehicleResource.queue`)
- No distance-based detail at all. Every car that passes occlusion culling queues its full model (every part, interior included) and asks `getCachedVehicleResource`, which **starts the full OBJ load** (the 0.1–0.3 s per heavy car measured on this Mac) for any train that comes into render distance, however far away.
- The car's size is known without loading anything: `VehicleCar.getLength()/getWidth()` and the resource `color`.
- Parts have a `RenderStage` (exterior, interior, interior translucent, lights...), so interiors can be told apart.

## 3. The LOD design

Distances are in chunks and set by the player. Defaults below are starting points to tune in game. Every tier falls back to MTR's own rendering if anything fails.

### Rails

| Tier | Default range | What is drawn | Gain |
|---|---|---|---|
| R0 full | 0–3 chunks | MTR's own 3D model, unchanged | none (this is where you look) |
| R1 simple | 3–8 chunks | Flat rail texture strips (MTR's own `default` look) through MTR's normal textured path, which batches into a few draws | thousands of draw calls → a handful |
| R2 far | 8 chunks to render distance | Same flat strips with a longer interval (4 blocks per piece on straights instead of 0.5) | ~8x fewer quads again |
| Shadows | any | Shadow pass always uses R2 | halves the Iris cost |

- Tall rail models (catenary masts, walls or slab track built into the rail model) would vanish in R1. The mod measures each rail model's height once at load: anything taller than ~0.5 blocks keeps its 3D model in R1 and is only thinned (every 2nd/4th piece) in R2. This is checked per style, so packs work without config.
- Later (phase 4): "baked" rails like Aurelia LOD's merged tiles: build one mesh per rail per chunk once, upload it once, redraw it with one call; rebuild only when the rail changes, and keep the old mesh until the new one is ready (no holes). Same look as R0 at a fraction of the cost, but it needs our own shader path and an Iris check, so it comes after the simple tiers.

### Trains

| Tier | Default range | What is drawn | Loading |
|---|---|---|---|
| T0 full | 0–4 chunks | MTR's full model | loaded as today (with the M1–M5 fixes) |
| T1 simple | 4–10 chunks | Full model minus interior parts (seats, poles, interior lights) | needs the model loaded |
| T2 proxy | beyond 10 chunks, and any car whose model is not loaded yet | A box per car at the car's real length and width, in the vehicle's colour, with bogies omitted | **nothing loaded**: the OBJ is not read until the train comes within T1 range |

- T2 is the big one for your "heavy trains shouldn't load in full at 14 chunks" point: a far train never triggers the OBJ parse, and a train that is still loading shows a box instead of popping in from nothing.
- Later (phase 3): a real simplified mesh for T1/T2 (vertex-clustering decimation to ~5–10% of the triangles), built in the background with the FastObj parser and **cached on disk** by the OBJ's hash, so it costs nothing on the second visit. Optionally a side-view texture baked from the full model the first time it loads, for nicer far boxes.
- Riding: the car you ride, and its train, is always T0. Floors and doorways (boarding boxes) are only worked out for cars within T0 range.

### Interaction with the loading fixes

LOD decides when a model is needed; the M-steps make loading it cheap. Together: far trains cost nothing, mid trains load in the background (M6) with FastObj (M3), and returning trains hit the parsed-geometry cache (M4).

## 4. Not stepping on MTR's toes

1. **Honest page.** ATU's own name, no MTR logo or art. Description line: "Unofficial add-on for Minecraft Transit Railway. Not affiliated with or endorsed by MTR or its developers." MTR listed as a required dependency on Modrinth; add the Optimization category.
2. **License.** MTR is MIT (from its `fabric.mod.json`), so mixins into it, and even reused code with the notice kept, are allowed. We still write our own code (FastObj is ours) and ship none of MTR's code or assets.
3. **Client-only and stateless.** No blocks, items, packets, world data or server install. Removing the mod returns pure MTR; it cannot damage worlds or servers.
4. **Fails soft.** Version gate: features only switch on for MTR versions they were tested on (4.0.5, 4.0.6 after a check); unknown versions start with everything off and a log line. Each mixin is optional (`require = 0`) and checked by a mixin plugin; if a feature throws, it turns itself off for the session and MTR's own path runs.
5. **Plays well with others.** Skips targets JCM 2.2.5 already patches (`RawMesh` distinct, `OptimizedRenderer.beginReload`). Checks for Iris and uses the shadow-pass tier. Each feature has its own switch so users can bisect.
6. **Bug reports come to us.** README and crash-report line: "if a problem goes away without this mod, report it to us, not to MTR." This keeps noise off MTR's tracker.
7. **Work around MTR's bugs, do not report them.** Owner decision (2026-10-16): nothing is posted to MTR (no issues, no PRs). ATU works around P2 (double upload), P4 (preload lifespan), P5 (CachedResource registry) and P6 (VBO leak). If a later MTR version fixes one, the version gate keeps our workaround off for versions it was not tested on.
8. **4.0.x only.** MTR 4.1 (MC 1.21) rewrites this renderer (`NewOptimizedModel`), so this mod targets MTR 4.0.x on 1.20.1 and says so.

## 5. Build plan (agent hours)

| Phase | Work | Hours |
|---|---|---|
| 0 | ATU mixin config + plugin with version/JCM/Iris checks, "Performance" page in the ATU hub, F3 debug line (MTR draws per frame, time in MTR's renderer, cars per tier) | 2 |
| 0b | Measure in a dev client: 3 NS trains, shaders on/off, to confirm 1b hypotheses before fixing | 1 |
| 1 | Draw-call fixes D1–D4 | 8–10 |
| 1b | Rail tiers R1/R2 + tall-model detection + shadow tier; train T2 proxy boxes + no load beyond T1; T1 interior skip | 8–10 |
| 2 | Loading fixes M1–M5 from `MTR_OBJ_PERFORMANCE.md` | 12–14 |
| 3 | Decimated T1/T2 meshes with disk cache; background parse and per-frame upload budget (M6) | 10–14 |
| 4 | Baked rail meshes with own shader path, Iris check | 8–12 |
| later | Rails beyond render distance drawn as thin lines next to Aurelia LOD terrain (needs a check that the client receives rail data that far out) | research first |

Phase 1 is the visible win and the smallest. Measurement for it: the F3 counters plus FPS in a fixed test world (a long double-track line and a yard) with the setting on and off, at render distance 8 and 12, with and without shaders. That needs a dev client, so it waits until the terrain-gen job is done and you say go.

## 6. Decisions

Decided 2026-10-16: lives in ATU (not a separate mod). Everything in this plan ships together as one 1.4.0 update (draw-call fixes, shadow tier, train and rail LOD, loading fixes, background loading), about 25–30 agent hours. Wait for the owner's go before continuing (Minecraft is in use by another agent).

Decided 2026-10-16 (later): nothing is posted to MTR; ATU only works around P2, P4, P5, P6. Order: measure (0b), then draw-call fixes (phase 1), then LOD (1b), then loading fixes (2). Owner gave the go to launch a dev client.
