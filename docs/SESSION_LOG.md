# ATU session log

## 2026-10-02 — audit, decision, scaffold

- Feasibility audit of U1-U6 against the MTR 4.0.5 jar: `docs/FEASIBILITY.md`.
- Owner delegated scope. ATU 1.0 = U2, U3 (ATU screen), U4 dwell, U1 overlap resolver. No mixins. Client-only mod.
- Scaffolded the Fabric project (mod id `aurelia_transit_utilities`, package `com.aureliatransit.utilities`), toolchain copied from ATA: Loom 1.10.5, yarn 1.20.1+build.10, loader 0.19.5, Fabric API 0.92.12+1.20.1, Gradle 8.14, Java 17. MTR is `modCompileOnly` + `modLocalRuntime`.
- `./gradlew build` passes. `./gradlew runClient` boots: Fabric loads 61 mods including `mtr 4.0.5` and `aurelia_transit_utilities 1.0.0`, log shows `[ATU] Client initialised`, no errors, reaches "Sound engine started".
