# MTR OBJ train loading: findings, benchmark and plan

Date: 2026-10-16. Target: MTR 4.0.5, Minecraft 1.20.1 Fabric. Machine: MacBook M1, 8 GB, Apple GL 4.1 over Metal.
Status (updated 2026-10-16, evening): built in ATU 1.4.0. M1, M2, M3, M5 and M6 are in the mod (section 7); M4 and M7 are not built. Sections 1-6 are the original research.

## 1. Why OBJ trains load slowly (MTR 4.0.5, from the jar's bytecode)

How a train model reaches the screen:

1. `RenderVehicles` asks `VehicleResource.getCachedVehicleResource(..)` every frame.
2. That is a chain of `CachedResource`s. `CachedResource.getData` builds **at most one resource per client tick across the whole game** (a static `canFetchCache` flag). The chain needs 1 + (number of model files) + 1 + 4 builds, so one vehicle type takes 7+ ticks to appear, and different trains queue behind each other.
3. Each build runs on the render thread: `VehicleModel.createModel` → `ObjModelLoader.loadModel` (javagl: read, split by group, split by material, `convertToRenderable`, one `Vertex` plus two `Vector3f` per vertex) → `ModelPropertiesPart.writeCache` (copies every vertex for every placement) → `VehicleResource.writeToOptimizedModels` → `OptimizedModel.fromObjModels` (`generateNormals` turns every triangle corner into a new vertex, `distinct`, then `upload` runs `distinct` again and packs).
4. The data expires 60 s after the train was last drawn (`VehicleModel.MODEL_LIFESPAN`). When the train comes back, all of the above runs again.

Problems found, with evidence:

| # | Problem | Where | Effect |
|---|---|---|---|
| P1 | Slow CPU path: object per vertex, three geometry copies in javagl, normals expand every corner, `distinct` twice with boxed `Objects.hash` | `ObjModelLoader`, `RawMesh.generateNormals/distinct/upload`, `Vertex.hashCode` | Hundreds of ms per heavy car on the render thread (table 2) |
| P2 | Every non-door part is added to two maps (doors open and doors closed); `fromObjModels` uploads the same `ObjModel` once per list entry | `ModelPropertiesPart.writeCache` / `addObjModelPosition`, `OptimizedModel.fromObjModels` | 2.0x GPU memory on all 56 models measured. A part placed at N positions is uploaded N times with N copies each (N² geometry, N redundant draws) |
| P3 | Every model load recompiles MTR's 3 shader programs | `OptimizedRenderer.beginReload` → `ShaderManager.reloadShaders`, called from `VehicleModel.createModel` and 4x from `writeToOptimizedModels` | 9–17 ms each (measured), (model files + 4) times per vehicle type. JCM 2.2.5 already cancels this (`OptimizedRendererMixin`) |
| P4 | `VehicleModel` builds its cache before `shouldPreload` is set, so even preloaded models expire after 60 s and the OBJ is parsed again on the render thread when the train returns | `VehicleModel` constructors, `VehicleResource` constructor | Preloading (`preloadResourcePattern` in `mtr.json`) does not stop re-parsing |
| P5 | `CachedResource` registers every instance in a static list that is never pruned; each 60 s expiry/rebuild adds new entries whose suppliers keep the old geometry alive | `CachedResource.CACHED_RESOURCES` | Heap grows over a session. Matches MTR issue #1272 (heap dump: CachedResource 6.88 GB) |
| P6 | GPU buffers are never freed: expiry only nulls the Java object, nobody calls `OptimizedModel.close()`, and `VertexArray.close()` deletes only the VAO (the VBO handle is dropped) | `CachedResource.tick`, `VertexArray` | VRAM leak every time a train leaves view for 60 s and returns, and on every resource reload |
| P7 | Legacy (MTR 3 format) OBJ trains build new `VehicleModel`s per car number and train length, so the same OBJ is parsed once per car | `LegacyVehicleResource` (`extraModelsSupplier`) | Not benchmarked (no legacy pack on this Mac) |

Related reports in MTR's tracker (subagent research, see section 6): #1272 (CachedResource heap), #1063 and #870 (minutes-long loading with OBJ packs), #1407 (freeze on reload while riding), #1240 (train stutter).

## 2. Benchmark on this Mac (headless, Minecraft not started)

Tool: `tools/objbench/` (run with `tools/objbench/run.sh cpu|gl <unzipped packs>`). It calls MTR 4.0.5's own classes from the real jar for everything up to and including the GL upload (headless CGL context, no window), and compares them with **FastObj**, a prototype of a faster path (`tools/objbench/src/FastObj.java`).

Packs: SEPTA M4, SEPTA Xcelsior, Manchester M5000 (from your instances), NS trains and BR Class 373 (Modrinth, from the enviromtr pack list). 56 OBJ vehicle models, about 110 MB of OBJ text.

Correctness: for **all 56 models FastObj produces byte-identical GPU data** (every triangle's 72 bytes of position, UV and packed normal compared after sorting). Raw output: `tools/objbench/results-*.txt`.

| Measurement | MTR 4.0.5 | FastObj | |
|---|---|---|---|
| Parse + place + build + GL upload, all 56 models (real GL, one warm-up run) | 9.7 s | 1.6 s | 6.1x |
| Same, CPU only, median of 3 warm runs, single thread | 15.1 s* | 1.42 s | 10.6x |
| CPU only, FastObj on 4 worker threads | 15.0 s serial | 0.73 s | 20.8x |
| Heaviest car (`tgv/gner last_carriage`, 4.3 MB OBJ), real GL | 282–336 ms | 36–39 ms | ~8x |
| First model in a cold JVM (SEPTA M4 body) | 426–553 ms | 42 ms | |
| GPU vertex+index data uploaded | 2.0x the unique geometry (all 56) | 1.0x | P2 |
| Java heap kept alive after loading all 56 | 1147 MB | 192 MB packed (droppable after upload) | P5 |
| One `reloadShaders()` | 9.2–10.3 ms (first 15–22 ms) | 0 | P3 |

\* the CPU-only MTR column includes my emulation of the byte packing, which allocates more than MTR does; the real-GL row is the number to quote.

What a player feels: each heavy car is a single 0.1–0.3 s render-thread stall (plus 9+ ms per shader reload without JCM), one model per tick, repeated whenever a train has been out of view for a minute.

Caveats: warm-JIT medians except where marked; the in-game JVM is shared with Minecraft and other mods; GPU draw cost (FPS) was not measured; P5–P7 are code-verified, not measured live.

## 3. What can be done

### Without mixins (allowed by the current ATU rules)

- **A. Pack doctor** (new ATU tool, read-only): per vehicle, OBJ size, triangles, groups parsed but never placed, parts with several positions (P2 multiplier), estimated load time from this benchmark's ms-per-MB, legacy format warning (P7). Helps pack authors and users pick packs. Small and safe.
- **B. Preload switch**: MTR's `preloadResourcePattern` moves loading to the resource-reload screen. It has no setter (private field, only in `mtr.json`), and because of P4 the model is parsed again after 60 s anyway, so the gain is partial. Not recommended on its own.

Nothing in P1–P7 can be fixed from public API alone.

### With client mixins into MTR (needs your OK: KICKOFF says no mixins unless you decide per item)

Precedent: JCM 2.2.5, which you run in Noriega and Metropolis, already ships mixins into MTR's `RawMesh` (faster `distinct`, skip the second one) and `OptimizedRenderer.beginReload` (no shader recompiles). ATU must not touch those two targets, and must still work when JCM is absent.

Ranked by gain against risk:

| Step | Fix | Targets (MTR classes) | Gain (measured where possible) | Risk |
|---|---|---|---|---|
| M1 | Upload each `ObjModel` once and share it between the doors-open and doors-closed models and between list entries | `OptimizedModel.fromObjModels` (identity cache per build) | GPU memory for OBJ trains halved (2.0x measured); build CPU about halved; N² → N for multi-position parts | Low. Translucent parts placed at several positions stop being drawn N times, so they may look lighter (that is the author's intent) |
| M2 | No shader recompile per model load, only on real resource reloads; skipped when JCM is loaded (it does the same) | `OptimizedRenderer.beginReload` | (files + 4) × 9–17 ms per vehicle type | Low |
| M3 | FastObj path: bytes → primitive arrays → final buffers, same output | `OptimizedModel.ObjModel.loadModel`, `addTransformation`, `fromObjModels` | 6x (real GL), 10x CPU on 56 models; heap 1147 → 192 MB | Medium. JCM's `ObjModelAccessor`, `RawMeshAccessor` and scripting read MTR's `ObjModel`/`RawMesh`, so ObjModel must stay a valid MTR object (keep `rawMeshes` filled lazily, or fall back to MTR when JCM scripts ask) |
| M4 | Parsed-geometry cache keyed by resource id: an expired model is rebuilt from the cache, not from OBJ text; fix the preload lifespan | `VehicleModel` (constructor lifespan), `ModelResourceLoader.loadModel` | Train re-entering view: ~280 ms → a few ms per heavy car | Low–medium (bounded LRU, cleared on resource reload) |
| M5 | Stop the leaks: registry holds weak references and is cleared on reload; close `OptimizedModel`s when their cache entry expires (deferred to after the frame) and keep VBO handles so they can be deleted | `CachedResource`, `VertexArray`, `CustomResourceLoader.reload` | Ends the unbounded heap (#1272) and VRAM growth | Medium (closing a model that is still queued for this frame must be avoided) |
| M6 | Parse off the render thread (FastObj is thread-safe), upload on the render thread with a time budget per frame instead of one model per tick | `CachedResource.getData`, `VehicleModel.createModel` | No stalls; trains appear as soon as the worker finishes (0.73 s for all 56 on 4 threads) | Higher; do last |
| M7 | Legacy packs: share one parsed model per OBJ across car numbers | `LegacyVehicleResource` supplier | P7 | Medium; needs a legacy test pack |

All behind one ATU setting ("Faster MTR model loading", default on, off = pure MTR) and a mixin config plugin that disables each step if its target method is missing (MTR updates) or if JCM already patches it.

## 4. Implementation plan (if you approve mixins)

Agent-time estimates.

1. **Scaffold (0.5 h).** `aurelia_transit_utilities.mixins.json` (client, `remap = false` for MTR targets), mixin plugin with the JCM / MTR-version checks, the setting in the ATU hub.
2. **M1 + M2 (2 h).** Unit test: a model with a part at 3 positions gives 3 copies once, not 3×3. Self-test: count VAOs for a test vehicle with and without the setting.
3. **FastObj into `src/main` (4–6 h).** Move the prototype into `com.aureliatransit.utilities.perf`, unit tests on small OBJ fixtures (g/o rules, n-gons, missing vt/vn, `#flipv`, MTL colours, negative indices). Keep the equivalence check as a test against MTR's own loader on the fixtures and, optionally, on any packs found in `run/selftest/resourcepacks`. Then M3 mixins.
4. **M4 (2 h)**, **M5 (3 h)**, then measure in the dev client with the NS / TGV packs: F3 memory, time to first draw, a 10-minute ride with trains leaving and returning.
5. **M6 (4–6 h)** only after 1–4 are proven live.
6. Live check in Noriega with JCM 2.2.5 and the AureliaEX pack (scripts, displays, doors) before any release. Version 1.4.0.

Total for M1–M5: about 12–14 agent hours. M6: 4–6 more.

## 5. Decisions for you

Update 2026-10-16: LOD for 3D rails and trains, and the proposal to put all of this in a separate unofficial add-on instead of ATU, are in `docs/MTR_PERFORMANCE_ADDON_PLAN.md`. Its decisions replace question 1 below.

1. Allow client mixins into MTR for this feature (exception to the KICKOFF rule)? Without them, only the pack doctor (A) is possible.
2. If yes: start with M1 + M2 + M4 (low risk, quick), or go straight to M3 (largest gain)?
3. Report P2, P4, P5 and P6 upstream to MTR? Decided 2026-10-16: no, nothing is posted to MTR; ATU only works around them.

## 6. Sources

- MTR 4.0.5 jar decompiled with jadx (classes named above). JCM 2.2.5 mixins from `JCM-fabric-2.2.5+1.20.1.jar` in the Noriega instance.
- Subagent research (Haiku): MTR issue tracker, MTR master/4.0.6/4.1 source, NTE, JCM, `mtr-optimizer`. Notable: NTE uploads each model once and places it with a model-matrix per instance; MTR 4.1 (MC 1.21) rewrites this path (`NewOptimizedModel`), so these fixes are specific to 4.0.x.

## 7. What 1.4.0 ships (2026-10-16)

| Step | In 1.4.0 | Where |
|---|---|---|
| M1 | Yes. The doors-open and doors-closed lists of each part condition are linked when MTR creates them; the parts both use are uploaded once (merged per material) and both models draw those buffers; the closed model adds the door parts. | `perf/DoorShare`, `VehicleResourceBuildMixin` (lambda 17), `ObjModelShareMixin`, `OptimizedModelMixin` |
| M2 | Yes (outside resource reloads; left to JCM when it is installed). | `OptimizedRendererMixin.beginReload` |
| M3 | Yes, in two parts. The mesh build (normals, de-duplication, packing) is `FastMeshBuild` (byte-identical buffers). The OBJ read is `FastObjParse` + `FastObjLoader`, hooked at `ObjModelLoader.loadModel(String, ...)`: it returns exactly the `RawMesh`es javagl gives MTR (vertex order, UVs, normals, faces, materials), so `ObjModel` stays a normal MTR object for JCM and scripts. Files outside the plain subset (line continuations, tabs, mixed corner forms, bad indices or numbers) go to javagl. | `perf/FastObjParse`, `perf/FastObjLoader`, `ObjModelLoaderMixin` |
| M4 | No. With M3 and M6 a returning train is parsed again, but off the render thread and about 4x faster; a parsed-geometry cache would cost memory for little left to gain. | |
| M5 | Yes (weak cache registry, GPU buffers freed when unreachable). | `CachedResourceMixin`, `client/perf/GlGarbage` |
| M6 | Yes. The OBJ text is read on the render thread through MTR's provider (its resource cache is not thread-safe); parsing and the vertex and face lists run on 1-3 worker threads; MTL files, materials, textures and all GPU uploads stay on the render thread. Until the parse is done `createModel` returns null, which MTR's caches already treat as "not ready", and the one-build-per-tick slot is handed back. All model files of a car are started at once. Forced builds and builds during a resource reload stay synchronous. | `perf/AsyncModels`, `VehicleModelMixin`, `VehicleResourceBuildMixin` (lambda 18), `CachedResourceMixin` |
| M7 | No (no legacy test pack). | |

Checks: `tools/objbench/run.sh parse <packs>` compares ATU's reader with MTR's javagl reader on 36 hand-written edge cases (both split modes) and on every OBJ file of the packs; on 2026-10-16 all 50 distinct files of the 8 local packs were identical, 2.27 s → 0.61 s (3.7x; a repeat run 3.4x). Unit tests: `FastObjParseTest`. In game: `tools/perfbench/`.
