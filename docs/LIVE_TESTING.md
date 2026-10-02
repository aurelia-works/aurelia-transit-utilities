# Live testing

## Automated self-test (dev only)

`./gradlew runSelftest` starts a dev client with the extra `atu_selftest` mod (`src/selftest`, never in the release jar), loads `run/selftest/saves/ATU Test` directly, opens MTR's dashboard the way the server does (`PacketOpenDashboardScreen.sendDirectlyToServer`), drives the ATU screens and checks the results against data fetched back from MTR core. It writes `run/selftest/atu_selftest.txt` (PASS/FAIL lines, last line `RESULT PASS|FAIL`) and screenshots `run/selftest/screenshots/atu_*.png`, then quits. `-Datu.selftest.stay=true` keeps the game open. The window moves to the first non-primary monitor at start; `-Datu.selftest.monitor=N` picks another (GLFW order). The self-test never captures the mouse (dev-only Minecraft mixin on `Mouse.lockCursor`, in `src/selftest` only) and keeps running when unfocused.

Test world: `run/` is git-ignored. `ATU Test` is a copy of ATA's `run/client/saves/ATA Release Check` (3 stations, 4 platforms, 2 routes, 1 depot, 1 siding with a 2-car train). Copy it there before the first run. Errors about `ata_test:*` functions and `mtr:train_cargo_*` loot tables in the log come from that world's datapack and from MTR, not ATU.

## Manual checklist

### Train presets (U2)
1. Open MTR's dashboard. An **ATU** button sits in the top-left corner of the map. Click it, then **Train presets**.
2. Pick a siding (the closest one within 32 blocks is pre-selected). Type a name, **Save this siding's train**. The preset appears on the right with car count and length.
3. Pick another siding of the same transport mode, pick the preset, **Apply to siding**. Reopen the siding in MTR: its train matches. Regenerate the depot if running trains do not change.
4. A preset that is longer than the siding rail is greyed and refused with the lengths. A preset using vehicles from a resource pack that is no longer loaded is refused with the missing ids. Presets of other transport modes are not listed.
5. Presets live in `config/aurelia_transit_utilities/train_presets.json`. A corrupt file is moved to `train_presets.json.bad` and the screen says so.
6. In adventure or spectator mode the Apply button stays disabled (MTR's own permission rule).

### Route stop order (U3)
1. Dashboard → **ATU** → **Route stop order**. Pick a route; its stops show in order with station, platform and custom destination.
2. Drag a stop: a green line shows where it lands. Or select a stop and use **Move up / Move down** (or Shift+Up/Down). **Reverse** flips the whole route, **Revert** drops the edits.
3. **Save order** writes the route. Reopen it in MTR's own route editor: same order, custom destinations still on the same stops.
4. **Regenerate depots** sends MTR's depot Generate for every depot that runs the route. Trains only follow a new order after that (same as editing in MTR).
5. Switching to another route with unsaved edits drops them and says so. If the route's stop count changed elsewhere in the meantime, Save refuses and reloads it.

### Platform dwell times (U4, dwell part)
1. Dashboard → **ATU** → **Platform dwell times**. Pick **All platforms**, a station (■) or a route (→). Its platforms are listed with their current dwell, all ticked. Click a row to untick or tick it; **All / None**.
2. Type a time (`30`, `12.5`, `1:30`, `2m`) and **Set dwell**, or use **-5 s / +5 s**. Limits are MTR's own: 0.5 s to 10:00 in half seconds; anything else keeps Set disabled.
3. Reopen a changed platform in MTR's platform screen: same dwell. **Regenerate depots** rebuilds the depots of every route through the changed platforms (needed for running trains to use the new times, same as MTR).
4. Cable-car platforms are not listed (MTR ignores dwell for continuous movement, #1375).

### Station overlaps (U1)
1. Dashboard → **ATU** → **Station overlaps**. Every platform inside two or more station zones is listed as "Platform @ current station (+ others)". "Current" is the station MTR picked (the first one in MTR's list).
2. Pick a platform, then the station it should belong to. The right side shows which zone edges get cut back (for example "east edge x 277 → 275") and whether any other platform would change station (orange list). Nothing is sent yet.
3. **Apply** cuts the zones. Reopen MTR's dashboard: the zone is smaller on the map, and the platform now shows under the chosen station (PIDS, announcements and route station lists follow).
4. Stacked stations (subway under an overground station) are separated by height instead of on the map, because a map cut would move the other platform.
5. If a zone would shrink to nothing, it says so and leaves the zones alone. If the red "differ from ATU's reading" warning ever shows, ATU's copy of MTR's rule is out of date: report it.

### Zone heights (MTR #815)
1. Dashboard → **ATU** → **Zone heights**. Stations (■) and depots (⌂) are listed with their height ("any height" is how MTR draws zones).
2. Pick one; type **Bottom Y** / **Top Y** (blank = no limit) or press **My Y** to use your feet. The preview lists every platform (or siding, for depots) that would change station (or depot), before anything is saved.
3. **Save**. Typical use: give the subway station "up to Y 40" and the street station "Y 41 and up" so both can share the same map area.

### Depot health (#918 and the "path won't generate" reports)
1. Dashboard → **ATU** → **Depot health**. Every depot, worst first: ✖ red = errors, ! orange = warnings, ✔ green = fine.
2. Pick one: MTR's last generation result in words ("No path found between platform 2 (Central) and platform 1 (Harbour)…") plus setup mistakes (no routes, no sidings, sidings without a train, routes with under two stops or deleted platforms).
3. **Regenerate this depot** / **Regenerate all with ✖** send MTR's own Generate; press **Refresh** after a moment for the new result. The screen never polls.

### Station codes (owner idea 20, JR East style)
1. Dashboard → **ATU** → **Station codes**. Pick a route; set **Prefix** (1-4 letters), **Start**, **Step** (negative counts down), **Digits** (padding). The preview shows each station's new name in route order; a loop's return visit is numbered once.
2. **Apply codes** adds the code as the last part of the name (`新宿|Shinjuku|JY17`), so MTR's own signs, maps and the dashboard show it ("Alpha AT03"). Applying again with the same prefix replaces the code; another prefix (an interchange line) is kept next to it.
3. **Remove this prefix** takes only that line's codes back off.

### Departure times (MTR #1289, #1409; owner ideas 22, 33, 34)
Dashboard → **ATU** → **Departure times**, pick a depot. It opens on the tab that matches how the depot runs now. Nothing is sent until a Save button.
- **Timed**: From/to/every builds an even service (**Add** merges, **Replace** starts over; past midnight works). **Depot → 1st stop** (minutes): the times you type are when trains should be at the first station; the depot departs that much earlier. **Rest** + **No trains** removes a time window. **Shift** moves everything later/earlier. The summary shows count, first, last and longest wait. **Save as timed departures** switches the depot to timed mode.
- **Trains per hour**: give a gap to a range of hours, or **No trains those hours** (rest hours). MTR allows at most 5 trains an hour (gap ≥ 12 min); the grid shows what MTR will run. **Save as trains per hour**.
- **Loop**: for depots set to "repeat infinitely", each train circles forever, so rest hours are impossible there (MTR repeats every departure each round trip). Enter the round-trip time and the number of trains; **Space evenly** gives one departure per train, evenly apart.
- Times are local clock time, exactly like MTR's own depot screen. Regenerate the depot if trains don't follow.
- Travel time to the first stop has to be typed: the client receives no depot path data from MTR (checked live: 0 path segments).

### Jump to (MTR #1112)
Dashboard → **ATU** → **Jump to…**. Search, pick a station (■), platform, depot (⌂) or siding, **Jump**. It sends a normal `/tp`, so the server decides: operators or cheats on. Stations and depots put you on their first platform or siding (zones have no usable height); a zone with no rails keeps your current height.
