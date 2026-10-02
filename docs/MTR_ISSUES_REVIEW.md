# MTR GitHub issues review for ATU

Date: 2026-10-02. Source: `gh issue list` on jonafanho/Minecraft-Transit-Railway (read-only, nothing written to GitHub). Companion to `FEASIBILITY.md` and section 9 of `Aurelia_Transit_Feature_Triage_2026-09-30.md`.

## How to read this

- **Scope.** 326 issues reviewed: all 301 open issues (oldest from 2021), plus 25 issues closed since 2025-10-01. The tracker holds 1,042 issues in total; the other 716 are older closed ones and were not read.
- **Reactions are rare** on this tracker (most issues have none). "Demand" below means duplicates, comment count and how many different issues ask for the same thing.
- **Rules applied.** No new blocks in ATU, no mixins, no MTR changes, client-side only, no per-tick work.
- **Open feasibility checks** are marked "needs check". They need a short live test before anyone commits to building them.

## Top candidates for ATU next

Ranked by demand and fit. IDs A1 to A7 refer to the table "ATU can add".

1. **Station zone height editor (A1, size S).** #815. The MTR owner said Y-range is already in MTR core but not in the dashboards. It also unlocks stacked stations, which makes U1 much stronger (#975, #1372).
2. **Depot health screen (A2, M).** #918, plus a large cluster of "path will not generate / train stuck" reports (#952, #1301, #1342, #1382, #1386, #1293). One screen showing every depot's last result and the platform that failed. Needs check: that the client copy carries the status.
3. **In-game network map and train list (A4, L).** #1309, #1052, #1116. Useful for servers that cannot open the web map. Biggest job on the list, needs check.
4. **Departure-time editor (A3, M).** #1289 (also the workaround for #1409). Type exact departure times or generate a timetable. Needs check that the time list can be written.
5. **Jump-to list (A5, S).** #1112. Pick a station, depot or platform from a list and teleport (for players allowed to).
6. **Route speed profile (A6, M).** #1002, #1280, #1410, #1376. Read-only: shows which rail sections slow a route down. It does not change speeds. Needs check.
7. **Network backup and restore (A7, M-L).** Not asked for directly. Built from the data-loss reports (#912, #1228, #1351, #1398, #935): save stations, routes, depots and rails to a file and send them back through MTR's own packets. Owner decision whether this is wanted.

Not in the list on purpose: waypoints for train paths (#665, 9 comments, plus #1336, #1432), speed per siding or route (#373, #996, #1286, #1111, #1376), and "any free platform" (#537, #997, #1288, #886). All are ruled out as needing MTR changes.

## Summary

| Bucket | Issues |
|---|---|
| Done in ATU | 2 |
| ATU can add (no mixin) | 9 |
| ATA material | 20 |
| Needs MTR change / upstream only | 88 |
| Not for us | 207 |
| **Total** | **326** |

## Things that contradict or sharpen FEASIBILITY.md

1. **Stacked stations CAN be separated.** FEASIBILITY (U1, option 3) says stacked or interleaved layouts cannot be separated because stations are "axis-aligned boxes". Verified in the jar: `AreaBase.inArea` checks X, Y and Z (`Utilities.isBetween` with seven numbers), and `AreaBase` has `getMinY/getMaxY`. So a station zone is a full 3D box. Two stations stacked above each other can be separated by height. The U1 resolver should shrink Y as well as X/Z, and A1 above becomes the natural partner. Only interleaved layouts on the same level remain impossible.
2. **Y-range is not an MTR change.** #815 confirms MTR core already supports it; only the dashboard UI is missing. ATU can add that UI.
3. **Depot status is public.** `Depot.getLastGeneratedStatus()` and `getFailedPlatformIds(...)` are public (jar check). FEASIBILITY does not mention them. They make A2 possible.
4. **U4 note.** #1375 reports MTR showing a dwell field on cable-car platforms. U4's bulk dwell screen should skip cable-car platforms.
5. No contradiction found on U2, U3, speed (#1376), node angles (#1435) or free platform choice.

## Done in ATU

| # | Title | State | Date | Note |
|---|---|---|---|---|
| #864 | Vehicle Selection Improvements (Tags/Filters) | open | 2024-06-22 | U2 (partial). Presets save and reload whole consists. Auto-building head+car+tail from one click is not part of U2. |
| #975 | 連接車站優化 | open | 2024-10-04 | U1 (partial). Overlapping or stacked stations: the resolver picks the owner and trims the other zone. A tool to pin a facility to a station is not possible (needs MTR). |

## ATU can add (no mixin)

Sizes: S small, M medium, L large. A-numbers are used in the top list.

| # | Title | State | Date | Note |
|---|---|---|---|---|
| #815 | 建议添加车站区域Y轴范围 Suggest to Add Y Axis for station area | open | 2024-02-26 | A1. Y-range editor for station zones. The MTR owner replied that Y-range exists in MTR core but not in the dashboards. I confirmed zones are 3D boxes (S). Also makes U1 stronger. |
| #918 | Debug Features? (better error-log) | open | 2024-07-27 | A2. Depot health screen: last generation result (no sidings / two platforms needed / path not found) and which platform failed, for every depot at once (M, needs check that the client copy carries it). |
| #1002 | Speed limits in-between | open | 2024-11-10 | A6. Read-only route speed profile: show the slowest rail sections along a route so builders see why trains crawl (M, needs check). Setting speeds stays with MTR. |
| #1052 | Train on map | open | 2025-02-21 | A4. In-game map with live trains (the web map already shows trains of a selected route). Same screen as #1309 (L, needs check). |
| #1112 | Teleport to depot / station icon on depot / station list dashboard | open | 2025-05-18 | A5. 'Jump to' list of stations/depots/platforms; sends the normal teleport command for players allowed to use it (S). |
| #1116 | Viewable train positions on systemmap | open | 2025-05-20 | A4. Live train positions and a 'where are my trains' list (L, needs check). Load per train is not available to us. |
| #1280 | Speed restrictions not applied to train in some cases | open | 2025-07-30 | A6. Same read-only speed profile helps find the section behind this complaint. The tail-of-train speed bug itself is MTR's. |
| #1289 | Add a new train scheduling way based on the pricise MineCraft time | open | 2025-08-03 | A3. Departure-time editor for real-time depots: type exact times such as 06:38, or generate a timetable from a first train and gap (M, needs check that the list can be written from outside). |
| #1309 | Add Transport System Map Display the game | open | 2025-09-06 | A4. In-game network map screen drawn from the client copy of stations, routes and rails, for servers that cannot open the web map (L, needs check). |

## ATA material

Blocks, signage, furniture, PIDS and announcements.

| # | Title | State | Date | Note |
|---|---|---|---|---|
| #146 | Option to Show "Station" in the Station Name (Entrance) Sign | open | 2021-09-03 | Station-name sign with optional 'Station' word. ATA signage. |
| #196 | Customisable PSD/APG Styles | open | 2021-10-28 | Custom styles for platform screen doors and gates. ATA platform pieces. |
| #369 | Passenger Seats | open | 2022-05-27 | Seats players can sit on. ATA furniture (sitting needs a small entity trick, no MTR change). |
| #397 | New Route overview route sign | open | 2022-06-15 | New route overview sign. ATA signage. |
| #423 | Diagonal PSD/APG/Platform Blocks 如果月台是斜的，無法安裝幕門或閘門 | open | 2022-07-03 | Diagonal platform doors and gates. ATA platform pieces. |
| #466 | Vertical Exit Signs 关于添加竖形出口指示牌显示个别建筑的建议 | open | 2022-08-18 | Vertical exit signs. ATA signage. |
| #563 | 1-wide Railway Sign | open | 2022-12-02 | 1-wide railway sign. ATA signage. |
| #931 | 站台门路线图改进 | open | 2024-08-11 | Platform-door route map text squashed. ATA can ship its own map panel. |
| #955 | Add placeholder tags to Train Announcer and the Passenger Information System | open | 2024-09-12 | Placeholder tags in announcer and PIDS text. ATA announcements. |
| #1085 | Multi-way Ticket Barrier | open | 2025-04-18 | Two-way ticket barrier model. ATA visual; fare logic is MTR's. |
| #1094 | Sitting | open | 2025-05-01 | Sitting. ATA furniture, same as #369. |
| #1105 | Easy playback of music files | open | 2025-05-08 | Play station-name audio files from a block. ATA announcements. |
| #1201 | Add level-crossings | open | 2025-06-16 | Level crossing props. ATA visual only; train detection stays with MTR. |
| #1263 | Certain railway sign tiles cannot have different values throughout the same sign... | open | 2025-07-16 | Signs whose tiles hold independent values. ATA sign design. |
| #1275 | 多项建议 | open | 2025-07-25 | Diagonal platforms and a big station screen (train name, origin, status). ATA PIDS and platform pieces. |
| #1307 | Next station announcements with pipe characters | open | 2025-08-30 | Pipe character in destination names read out in announcements. ATA announcements can parse it its own way. |
| #1315 | Platform number sign without direction | open | 2025-10-04 | Platform number sign without direction arrow. ATA signage. |
| #1378 | New block | open | 2026-02-21 | Block that changes platform display when a train passes. ATA PIDS idea. |
| #1390 | Double signs | open | 2026-04-03 | Double signs. ATA signage (stacked variants). |
| #1450 | 自定义幕门文字/Custom Platform doors texts | open | 2026-06-28 | Custom platform-door text. ATA platform pieces. |

## Needs MTR change / upstream only

| # | Title | State | Date | Note |
|---|---|---|---|---|
| #45 | Disallow players to place block when they are inside a train | open | 2021-05-08 | Block placing inside trains. Train/collision behaviour. |
| #59 | Better Pole Merging | open | 2021-06-07 | Pole merging in sign blocks. MTR block model. |
| #170 | Last stop option for platforms. | open | 2021-10-02 | Last-stop flag on platforms. New platform field. |
| #181 | Allow comments in platform/siding names | open | 2021-10-15 | Notes in platform and siding names. Needs display support in MTR. |
| #279 | Multiple trains stop at one platform at the same time | open | 2022-01-28 | Several trains at one platform. Core simulation. |
| #307 | AI Passengers | open | 2022-02-21 | AI passengers. Core (NPC system is in progress upstream per #1403). |
| #326 | Train emergency brake button | open | 2022-03-23 | Emergency brake. Manual driving system. |
| #332 | Please add a free-angle rail node! More angles of the lines are in need! | open | 2022-04-07 | Free-angle rail nodes. Same as ruled out #1435. |
| #337 | [Suggestion] Don't make rain pass through trains | open | 2022-04-19 | Rain through trains. Rendering. |
| #359 | Ticket System Enhancements/Rework | open | 2022-05-21 | Ticket and irregular fare-zone rework. Core fare logic. |
| #373 | The train's speed can't be a custom number. | open | 2022-05-29 | Per-siding custom max speed. No field for automatic trains (ruled out like #1376). |
| #394 | Add sounds for minecart and add placeholder rails | open | 2022-06-15 | Minecart sounds and placeholder rails. |
| #489 | Changeable or at least consistent order of lines (railway sign route tile, annou... | open | 2022-09-03 | Order of lines on signs and in announcements. MTR sorting. |
| #496 | Waterways on Railway system map and transport companies. | open | 2022-09-11 | Waterways on system map. Web map. |
| #537 | [Suggestion] Add the option for Route can be stoped in the different platfroms a... | open | 2022-11-06 | Choose several platforms per stop. Same as ruled out 'any free platform'. |
| #580 | Doppler Effect (Sounds) | open | 2022-12-18 | Doppler effect. Sound engine. |
| #586 | Cargo System Rework | open | 2022-12-21 | Cargo rework. Core. |
| #595 | The k train doors close after the platform doors close. | open | 2022-12-31 | Train doors close after platform doors. Door timing. |
| #598 | Make the elevators (lifts) have customizable walls and doors | open | 2023-01-02 | Customisable lift walls and doors. MTR lift renderer. |
| #627 | Cross-Dimension Support (Also Immersive Portals) | open | 2023-02-21 | Cross-dimension and Immersive Portals. Core. |
| #648 | Route Map Generation Enchancements/Rework | open | 2023-03-18 | Route map generation rework. Web map. |
| #663 | [Suggestion] 可選列車的停站位置，是否偏靠車頭、車尾或中間 | open | 2023-04-11 | Where the train stops along the platform. Core stopping logic. |
| #665 | Track Path Finding Waypoints | open | 2023-04-14 | Waypoints for path finding (9 comments). Would need new path data in depots; not possible from outside. |
| #702 | Speed Elevator | open | 2023-06-21 | Elevator speed. Lift data (the Yunzhu Transit Extension addon has it per #1478). |
| #875 | Show Rails on Dashboard Map | open | 2024-06-24 | Show rails on dashboard map. Web map. |
| #886 | 車務控制增強 | open | 2024-06-26 | Multi-platform and priority. Ruled out for platforms; announcer split by mode is also MTR. |
| #925 | Add economy to MTR | open | 2024-08-05 | Economy. Core fares. |
| #927 | 关于列车提前到站的问题 | open | 2024-08-10 | Trains arrive early. Timetable logic. |
| #940 | A function that allows the turnstile to be suspended | open | 2024-08-23 | Suspend a turnstile. Barrier logic. |
| #944 | Helicopters | open | 2024-09-03 | Helicopters. New vehicle type. |
| #996 | Vehicle maximum speed | open | 2024-11-02 | Vehicle max speed. Same as #373. |
| #997 | Arrive at the available platform | open | 2024-11-03 | Arrive at available platform. Ruled out (paths fixed at generation). |
| #1007 | Transit Agencies | open | 2024-11-25 | Transit agencies. Core data model. |
| #1014 | 能否加入电梯侦测器和红石信号呼叫电梯？ | open | 2024-12-07 | Lift detectors and redstone call. Lift logic. |
| #1047 | Seperate Entry and Exit sounds for Ticket Barriers and Processors | open | 2025-02-09 | Separate entry and exit sounds. Barrier logic. |
| #1079 | Fully Built-in MTR resourcepack create tool | open | 2025-04-09 | Built-in resource pack creator. MTR web tool. |
| #1086 | AI Passenger | open | 2025-04-19 | AI passenger duplicate of #307. |
| #1096 | Track type json option for Ends Only | open | 2025-05-02 | Track JSON option 'ends only'. Resource format. |
| #1097 | Align eyecandy with curve when placed near track | open | 2025-05-02 | Eye-candy alignment to curves. Block placement. |
| #1099 | Train Number | open | 2025-05-03 | Train numbers shown on the train. Vehicle data and rendering. |
| #1111 | Make max speed siding setting apply to AI trains too | open | 2025-05-18 | Siding max speed applies only to manual driving; fix is in the simulation. |
| #1113 | Turn off interior lights when waiting at a Siding | open | 2025-05-18 | Interior lights off in siding. Vehicle behaviour. |
| #1114 | Allow track type to be assigned BVE track audio file | open | 2025-05-18 | BVE track audio per track type. Resource format. |
| #1121 | 对于车站与收费系统的一点建议｜A few suggestions for Stations and the fare system | open | 2025-05-25 | Irregular zones, CSV fares, first class. Core fares. |
| #1122 | Improve elevator floor selection – make entire row clickable | open | 2025-05-25 | Whole row clickable in lift menu. MTR GUI; small fix upstream. |
| #1123 | Support Wheelchairs mod | open | 2025-05-25 | Wheelchairs mod support. Needs non-player entities on trains. |
| #1160 | Add funiculars | open | 2025-06-10 | Funiculars. New vehicle type. |
| #1241 | Signal Light Redstone Integration | open | 2025-06-27 | Redstone output from signals. Needs server-side signal state. |
| #1260 | More "hide route" options | open | 2025-07-13 | More 'hide route' options. New route flags. |
| #1264 | Opaque elevator variant (without glass textures) | open | 2025-07-16 | Opaque lift variant. MTR lift rendering. |
| #1265 | [Enhancement]對於列車運轉聲效的改進建議 | open | 2025-07-18 | Train sound improvements. Sound engine. |
| #1271 | Semáforos de tranvías | open | 2025-07-22 | Tram signals. Signal block. |
| #1278 | Door Alignment Issue | open | 2025-07-27 | Door alignment (5 comments). Train model/door positions. |
| #1286 | Add speed limit setting function for siding trains | open | 2025-08-01 | Per-siding speed limit. Same as #373. |
| #1288 | Un-specified platform | open | 2025-08-02 | Unspecified platform range. Ruled out, same reason as #997. |
| #1295 | Trains can kill people | open | 2025-08-13 | Trains hurting players. Core behaviour. |
| #1305 | 关于列车 | open | 2025-08-23 | Trains kill or carry any entity. Core behaviour. |
| #1306 | Configurable Signal Colours | open | 2025-08-25 | Configurable signal colours. MTR signal block. |
| #1312 | Please add Wayfinding rails OR Make platforms outside station areas not show up ... | open | 2025-09-22 | Wayfinding rails or hide platforms outside zones. New rail type or map filter. |
| #1320 | User should be warned when trying to add display to obj models in resource pack ... | open | 2025-10-10 | Resource pack creator warnings. MTR web tool. |
| #1325 | Resource Pack Creator: Replace file when adding a new one with the same name | open | 2025-10-15 | Resource pack creator replace file. MTR web tool. |
| #1327 | Ability to hide the player on system map | open | 2025-10-16 | Hide player on system map. Web map. |
| #1336 | Suggestion: Add a Manual Node-Based Pathfinding System for Trains | open | 2025-10-26 | Manual node-based paths. Same as #665. |
| #1349 | Minecraft Transit Railway建议提交 | closed | 2025-11-26 | Seven-part wishlist (separate rail/metro modes, multi-depot, tickets). Mostly core. |
| #1357 | 关于电梯等的一些建议 | open | 2025-12-27 | Lift and escalator suggestions. MTR lifts. |
| #1359 | 3D and Polygonal Station Area Needed | open | 2026-01-09 | Polygon or 3D station areas. Stations are boxes in the data format. U1 and #815 cover the overlap side only. |
| #1360 | "High Speed" Line Type UI Advice | open | 2026-01-09 | Leave high-speed lines out of announcements and maps. New filter. |
| #1372 | 车站绘制问题 | open | 2026-02-03 | Irregular station shapes. Not possible. U1 handles the overlap half of this request. |
| #1376 | 加入按照路线设置列车速度和停站时间的功能 | open | 2026-02-19 | Speed and dwell by route. Per-route speed is ruled out. U4 gives bulk dwell per platform but not per route. |
| #1379 | 建议车厂发车模式改为一个侧线跑一条线路 | open | 2026-02-23 | One siding per route in depots. Depot logic. |
| #1391 | 添加车站后缀设置 | open | 2026-04-04 | Station suffix setting. Name handling in MTR. |
| #1392 | 关于收费机制和票务系统的改良 | open | 2026-04-06 | Fare and ticketing rework. Core fares. |
| #1395 | 车厢优化 | open | 2026-04-12 | Car sway slider and couplers. Vehicle simulation and models. |
| #1403 | 希望看看能不能支持NPC还有假人上车/上电梯 | open | 2026-05-03 | NPC and fake players on trains and lifts. Upstream is building an NPC system. |
| #1405 | Change the platform screen door detection interval | open | 2026-05-10 | Platform-door detection distance. Door logic. |
| #1409 | 永久重复车场指令之后的强制间隔，以及另一种间隔设置方式 | open | 2026-05-18 | Forced headway for repeating depots. Simulation change (A3 can only write fixed times). |
| #1410 | 更真实的限速 | open | 2026-05-18 | Speed limit should use the train tail. Simulation change. |
| #1432 | 中文：功能建议：新增手动设置生成路径，参照信号铺设操作模式 英文：Feature Proposal: Add Manual Generation Path Co... | open | 2026-06-18 | Manual path set-up. Same as #665. |
| #1434 | Hide route | open | 2026-06-19 | Hide route only from signs, not from schedule block. New route flags. |
| #1435 | Node orientation | open | 2026-06-19 | Ruled out already: node angles (see #332). |
| #1458 | 建议加入实体八达通 | open | 2026-07-10 | Physical Octopus-style card. Items and fares. |
| #1470 | 股道与站台的区分，以及引导轨道的建议 | open | 2026-07-26 | Track numbers vs platform numbers and guide rails. New data fields. |
| #1471 | Rail Resource Pack options | open | 2026-07-27 | Rail resource pack model arrays. MTR resource format. |
| #1474 | [Feature Request] Add configurable speed/motion settings, "Out of Service" contr... | open | 2026-07-30 | Lift motion and out-of-service controls. MTR lifts. |
| #1475 | [FEATURE]: Set up currency item in configuration / 在配置文件中设置货币物品 | open | 2026-07-30 | Currency item in config. Config and fares. |
| #1479 | Please add an configuration that player can pass tickets barrier at no costs | open | 2026-08-09 | Free barrier passage option. Core fares. |
| #1491 | 添加坡道扶梯 | open | 2026-09-01 | Ramp escalators. MTR block. |
| #1503 | Feature Request: Redstone-Controlled Block Occupancy Controller | closed | 2026-09-19 | Redstone occupancy controller (closed). Needs server-side block state. |

## Not for us

Bugs in MTR, crashes, rendering and shader problems, other versions or loaders, other mods, train-pack content, support questions, duplicates. The default note "Bug in MTR itself." covers the bug-labelled reports not otherwise described. Rendering, lift, ticket-barrier, signal and path bugs from 2025-12 onward are also grouped in section 9 of the triage document.

| # | Title | State | Date | Note |
|---|---|---|---|---|
| #613 | Station Announcement Bugs | open | 2023-01-28 | Bug in MTR itself. |
| #617 | Actual R211 propulsion | open | 2023-02-01 | R211 propulsion. Train pack content. |
| #673 | Add Classical Chinese Translation | open | 2023-05-03 | Classical Chinese translation. Translation. |
| #699 | Elevator doors staying open(Note: does not crash) | open | 2023-06-15 | Bug in MTR itself. |
| #752 | Integration with Create's railway system | open | 2023-09-25 | Create railway integration. Other mod. |
| #789 | See transit lines on Dynmap | open | 2023-12-18 | Dynmap. Other mod. |
| #806 | Manual Driving Rework | open | 2024-02-08 | Bug in MTR itself. |
| #807 | Support for NeoForge modloader | open | 2024-02-10 | NeoForge support. Other loader. |
| #809 | High Speed Travel Alternative Implementation | open | 2024-02-17 | Bug in MTR itself. |
| #825 | Rail connection bug (4.0.0) | open | 2024-03-22 | Bug in MTR itself. |
| #860 | A single rail section splitting and misaligning | open | 2024-06-16 | Bug in MTR itself. |
| #861 | The train doors won’t close | open | 2024-06-16 | Door problem, labelled question. Support. |
| #872 | Northern Line 1938 Stock | open | 2024-06-24 | Northern Line stock. Train pack content. |
| #887 | Compatibility with Sodium under 1.19.4 | open | 2024-06-27 | Bug in MTR itself. |
| #891 | bug when open the client | open | 2024-06-29 | Bug in MTR itself. |
| #895 | 显示所有停靠车站的pids 不工作 | open | 2024-07-01 | Bug in MTR itself. |
| #899 | Invisible rail? For some reason... | open | 2024-07-03 | Bug in MTR itself. |
| #912 | MTR 3.2.2 Track, stations, elevator, fails, to save In server | open | 2024-07-22 | Data fails to save on server. Data-loss cluster (see backup idea A7). |
| #913 | 高月台门有概率不显示路线 | open | 2024-07-22 | Bug in MTR itself. |
| #914 | MTR服务器恶性BUG | open | 2024-07-23 | Bug in MTR itself. |
| #915 | Path finding problem in railway stations with passing loops | open | 2024-07-23 | Pathfinding at passing loops. MTR. |
| #916 | [Very malignant]Stuck in a point when take the train | open | 2024-07-23 | Stuck in a point. MTR. |
| #921 | create white screen | open | 2024-07-31 | Bug in MTR itself. |
| #923 | Incompatible with Shoulder Surfing Reloaded mod | open | 2024-08-03 | Bug in MTR itself. |
| #924 | Force removing rail nodes does not remove rails | open | 2024-08-03 | Bug in MTR itself. |
| #935 | 【MTR4.0.0beta8】重大错误！进行某些设置时无法正确保存 | open | 2024-08-18 | Settings not saved. Data-loss cluster (A7). |
| #937 | Server Crashing | open | 2024-08-19 | Bug in MTR itself. |
| #938 | Train operation problems | open | 2024-08-21 | Bug in MTR itself. |
| #943 | Train does not transit the player/drops off after some blocks | open | 2024-08-30 | Bug in MTR itself. |
| #952 | Path Cannot Generate | open | 2024-09-09 | Path generation fails. A2 would show why. |
| #961 | False running (all loads but nothing happend) | open | 2024-09-16 | Bug in MTR itself. |
| #968 | CC Tweaked (ComputerCraft) intergration | open | 2024-09-21 | CC Tweaked integration. Other mod, needs server code. |
| #998 | Can not mounted in VR (vivecraft) | open | 2024-11-05 | Bug in MTR itself. |
| #1006 | Text to Speech seems to be independent from game volume | open | 2024-11-22 | Bug in MTR itself. |
| #1010 | Destination Name not updated in PIDS until path refreshed | open | 2024-11-30 | Bug in MTR itself. |
| #1017 | Elevator doors (and in general more objects) bugging world when spawning | open | 2024-12-16 | Bug in MTR itself. |
| #1019 | Terrible Lag problems using the create mod | open | 2024-12-19 | Bug in MTR itself. |
| #1026 | Platform screen gate/door maps are quite buggy | open | 2025-01-04 | Bug in MTR itself. |
| #1032 | Airplane Render Error | open | 2025-01-17 | Bug in MTR itself. |
| #1038 | OneBusAway Not Work (1.20.1) | open | 2025-01-27 | OneBusAway not working. Support/bug. |
| #1042 | Memory leak | open | 2025-02-03 | Bug in MTR itself. |
| #1050 | texture bug | open | 2025-02-16 | Bug in MTR itself. |
| #1051 | Airplane when landing, teleports to random location | open | 2025-02-20 | Bug in MTR itself. |
| #1064 | Website does not state that WebGL is required | open | 2025-03-11 | Bug in MTR itself. |
| #1065 | When placing rails down for long periods of time, they stop rendering | open | 2025-03-15 | Bug in MTR itself. |
| #1067 | Random solid color for generated map | open | 2025-03-19 | Bug in MTR itself. |
| #1069 | 帧数较低时走出电梯或列车时会掉下去 | open | 2025-03-23 | Bug in MTR itself. |
| #1070 | Trains/Buses speed up with Shaders when Immersive Portals is present | open | 2025-03-23 | Bug in MTR itself. |
| #1074 | Attempting to change a line/station/depot color softlocks the game | open | 2025-03-29 | Bug in MTR itself. |
| #1083 | In-game Tutorials (Patchouli Integration) | open | 2025-04-16 | In-game tutorials via Patchouli. Docs project, not an add-on feature. |
| #1087 | A VVVF Simulator in C# | open | 2025-04-20 | VVVF simulator in C#. Separate project. |
| #1092 | Add in-car display to 1995 stock (Improved version in LU Addon | open | 2025-05-01 | In-car display for 1995 stock. Train pack. |
| #1100 | 经过测试，该mod与机械动力冲突 | open | 2025-05-03 | Bug in MTR itself. |
| #1104 | Minecraft transit railway 4.0.0 recource pack creator positions 0 0 0 is not cen... | open | 2025-05-07 | Bug in MTR itself. |
| #1109 | ServerWatchDog detect too long tick time | open | 2025-05-12 | Bug in MTR itself. |
| #1120 | Departure Schedule Bug: 50-100minute Wait After Refresh in mc departure schedule | open | 2025-05-25 | Bug in MTR itself. |
| #1130 | MTR 4 rendering issues in mobile launchers | open | 2025-05-28 | Bug in MTR itself. |
| #1146 | Displaying double the amount of sidings on refresh | open | 2025-06-01 | Sidings doubled on refresh. MTR. |
| #1147 | Las señales de colores no funcionan bien | open | 2025-06-02 | Bug in MTR itself. |
| #1148 | Trains are not following signals and enter occupied sections | open | 2025-06-02 | Bug in MTR itself. |
| #1159 | Decoration Object Migration Issues | open | 2025-06-08 | Bug in MTR itself. |
| #1195 | Displaying double the amount of sidings on refresh | open | 2025-06-11 | Sidings doubled on refresh. Duplicate of #1146. |
| #1198 | Rejecting UseItemOnPacket | open | 2025-06-12 | Bug in MTR itself. |
| #1228 | mtr major issue (Singleplayer) : Upon revisit world all elevator connection and ... | open | 2025-06-18 | Singleplayer data lost on revisit. Data-loss cluster (A7). |
| #1230 | some routes not showing up in announcer/sensors' GUI | open | 2025-06-20 | Bug in MTR itself. |
| #1232 | Bugs of system map | open | 2025-06-21 | Bug in MTR itself. |
| #1238 | Flights do not have a speed cap | open | 2025-06-26 | Bug in MTR itself. |
| #1239 | Announcements don't show after departing first stop when repeat instructions for... | open | 2025-06-26 | Bug in MTR itself. |
| #1240 | Trains and planes stuttering with specific GPUs | open | 2025-06-27 | Bug in MTR itself. |
| #1243 | Cable Cars Fail to Slow Down at Platform Sections | open | 2025-06-30 | Bug in MTR itself. |
| #1246 | A bug of system map | open | 2025-06-30 | System map bug. |
| #1251 | Outcome of Path generation sometimes not being reported (possibly client-side?) | open | 2025-07-04 | Bug in MTR itself. |
| #1255 | Placing separator character alone on a hide arrivals PID line breaks most MTR re... | open | 2025-07-04 | Bug in MTR itself. |
| #1258 | Duplicate interchanges on scrolling displays on UK trains (e. g.: 377, 802) | open | 2025-07-08 | Bug in MTR itself. |
| #1261 | A230 seat rendering issues | open | 2025-07-14 | Bug in MTR itself. |
| #1262 | 关于中国高铁追加包 | open | 2025-07-16 | China high-speed pack. Train pack. |
| #1266 | Screen element overlap if GUI scale is too large | open | 2025-07-18 | Bug in MTR itself. |
| #1267 | Third-person view: Displaying hitboxes causes a persistent obstructive line of s... | open | 2025-07-19 | Bug in MTR itself. |
| #1272 | Severe Memory Leak: CachedResource Occupies 50.84% Heap (6.88 GB) | open | 2025-07-23 | Bug in MTR itself. |
| #1273 | Scrambled Items on MTR Item Menus | open | 2025-07-23 | Bug in MTR itself. |
| #1279 | Small and mini cab variants of the christmas E44 yellow head render incorrectly | open | 2025-07-27 | Bug in MTR itself. |
| #1285 | Legacy train sounds do not play after reconnecting on a server/world | open | 2025-08-01 | Bug in MTR itself. |
| #1287 | Rails with shaders active cause lots of lag | open | 2025-08-01 | Bug in MTR itself. |
| #1290 | Preloaded Trains are missing door movements | open | 2025-08-04 | Bug in MTR itself. |
| #1291 | The train will rotate 180 degrees when it turns around | open | 2025-08-07 | Bug in MTR itself. |
| #1292 | Setting the Bogie position to a Positive Number flips the vehicle model. | open | 2025-08-08 | Bug in MTR itself. |
| #1293 | Sidings cannot connect to main path | open | 2025-08-09 | Sidings cannot connect to main path. A2 may help. |
| #1294 | Intermittent temporary freeze when using Create with MTR on Minecraft 1.20.1 (Fo... | open | 2025-08-10 | Bug in MTR itself. |
| #1296 | Black screen when selecting any colour (depot, station, route) on Radeon RX 9060... | open | 2025-08-17 | Bug in MTR itself. |
| #1301 | 在生成路径时卡住/Stuck while generating path | open | 2025-08-21 | Stuck generating path. A2 would show why. |
| #1303 | If a player stays in the ticket barrier, the next player will be judged as fare ... | open | 2025-08-22 | Bug in MTR itself. |
| #1310 | Cable Cars become fully dark when there's a block above the connection | open | 2025-09-12 | Bug in MTR itself. |
| #1313 | Station colored blocks sometimes appear gray when loading chunks | open | 2025-10-01 | Bug in MTR itself. |
| #1316 | Railway signs drop their item in creative mode when gamerule doTileDrops is true | open | 2025-10-04 | Bug in MTR itself. |
| #1317 | Use Sub module to manage github repositories | open | 2025-10-05 | Git submodules. Upstream housekeeping. |
| #1318 | 1.12.2 Support - The Holy Grail of Versions | closed | 2025-10-06 | 1.12.2 support. Other version. |
| #1321 | Resource pack creator corrupts (maybe not limited to) Japanese text even when op... | closed | 2025-10-11 | Bug in MTR itself. |
| #1322 | Announcer saving issue + only one can trigger per track regardless of filters | open | 2025-10-12 | Bug in MTR itself. |
| #1323 | ui element bug | closed | 2025-10-15 | Bug in MTR itself. |
| #1324 | Cool | closed | 2025-10-15 | Empty ('Cool'). Closed. |
| #1326 | Clicking 'Resource Pack Creator' in Railway Dashboard opens the System Map inste... | open | 2025-10-16 | Bug in MTR itself. |
| #1328 | Resource Pack Creator button on the dashboard and localhost:[port]/creator/ redi... | closed | 2025-10-16 | Bug in MTR itself. |
| #1329 | Unable to start the game properly. | closed | 2025-10-19 | Bug in MTR itself. |
| #1330 | CAF Airport Express Mini and Small versions are missing. | open | 2025-10-19 | Missing CAF mini variants. Train pack. |
| #1331 | If the player moves far from a signal that outputs redstone, the redstone stays ... | open | 2025-10-20 | Bug in MTR itself. |
| #1332 | Platform gate glitches out | open | 2025-10-22 | Bug in MTR itself. |
| #1334 | Maximum walking distance not working correctly | open | 2025-10-24 | Bug in MTR itself. |
| #1335 | BVE sounds & Legacy sounds issues | open | 2025-10-24 | Bug in MTR itself. |
| #1337 | real-time scheduling issue | open | 2025-10-27 | Real-time schedule cannot depart past 00:00. Bug in MTR. |
| #1339 | 4.0.2更新后，方块边缘出现渲染bug，4.0.1时没有这个问题，此外，mipmap因为某种原因被禁用 | open | 2025-10-31 | Bug in MTR itself. |
| #1340 | Signal Only Detect One Side Rail When It Placed Between Two Rail | open | 2025-11-05 | Bug in MTR itself. |
| #1341 | Support for 1.21.8 | closed | 2025-11-09 | 1.21.8 support. Other version. |
| #1342 | Train pathfinding problems | open | 2025-11-12 | Pathfinding problems. A2 may help. |
| #1343 | Tracks not rendering | closed | 2025-11-15 | Bug in MTR itself. |
| #1344 | 1.21.1 neoforge update pls | closed | 2025-11-16 | NeoForge 1.21.1. Other version/loader. |
| #1345 | Online system map: unable to generate a path in Find Directions function | open | 2025-11-22 | Bug in MTR itself. |
| #1346 | Interpolation of textures on obj and bb models (iris) | open | 2025-11-23 | Bug in MTR itself. |
| #1347 | [4.1.0] MTRClient.transformToFacePlayer renders incorrectly | closed | 2025-11-23 | Bug in MTR itself. |
| #1348 | Is there a 1.20.1 version? | closed | 2025-11-23 | Question about 1.20.1 version. Support question. |
| #1350 | PlayerTeleportationStateAccessor无法注入导致游戏崩溃 | open | 2025-11-29 | Bug in MTR itself. |
| #1351 | Routes clear themselves after some time in servers | open | 2025-12-01 | Routes clear themselves on servers. Data-loss cluster (A7). |
| #1352 | Please upload the version for 1.20.4 fabric with the tilting tracks Modrinth I a... | closed | 2025-12-03 | 1.20.4 build. Other version. |
| #1353 | Midnight controller mod - Exiting the dashboard causes a crash | open | 2025-12-09 | Bug in MTR itself. |
| #1355 | Minecraft Skin not reflected on System Map | open | 2025-12-10 | Bug in MTR itself. |
| #1356 | 是否考虑在模组里面添加直升机板块？ | open | 2025-12-21 | Helicopters (Chinese). Duplicate of #944. |
| #1358 | Lift doors open if the lift doors touch the lift, even in different levels | open | 2025-12-31 | Bug in MTR itself. |
| #1362 | 什么时候能有1.21.1 | closed | 2026-01-18 | 1.21.1 request. Other version. |
| #1363 | BVE sounds plays constantly even when a train is away from the player | closed | 2026-01-19 | Bug in MTR itself. |
| #1365 | If routes are hidden no "calling at" station information returned | open | 2026-01-25 | Hidden routes return no 'calling at'. MTR. |
| #1366 | 路线牌支线站点显示重叠 | open | 2026-01-27 | Route sign overlap. Bug. |
| #1371 | Loop lines don't run in loops | open | 2026-01-30 | Bug in MTR itself. |
| #1373 | 3D rails doesn't render | closed | 2026-02-06 | Bug in MTR itself. |
| #1374 | Feedback on the alignment issue of the new train platform screen doors | open | 2026-02-08 | Bug in MTR itself. |
| #1375 | 在铁路或渡轮或飞机仪表板中编辑缆车站台时出现“停留时间”选项 | open | 2026-02-12 | Dwell option shows for cable car platforms. MTR UI; U4 should hide dwell edit for cable cars. |
| #1377 | 在某些浏览器中交通路线图中的斜线会出现故障 | open | 2026-02-21 | Bug in MTR itself. |
| #1380 | 在垂直电梯最底层下添加楼层并刷新后电梯位置不正确 | open | 2026-02-23 | Bug in MTR itself. |
| #1381 | Route filtering screens of announcers do not display all lines and behave incons... | open | 2026-02-26 | Announcer filters miss lines. MTR. |
| #1382 | The path cannot refresh properly, and getting off normally is not possible. | open | 2026-02-28 | Path will not refresh. A2 may help. |
| #1383 | Lift rendered at 0, 0, 0 if there is only 1 floor | closed | 2026-02-28 | Bug in MTR itself. |
| #1384 | Game freezes when in train | open | 2026-03-01 | Bug in MTR itself. |
| #1385 | 在服务器中使用localhost交通路线图时无法导航 | open | 2026-03-07 | Bug in MTR itself. |
| #1386 | Unable to Find route for 2n Sidings | open | 2026-03-08 | Siding route not found. A2 may help. |
| #1387 | Rails flipping the wrong way in certain circumstances | open | 2026-03-19 | Bug in MTR itself. |
| #1388 | my chairlft floating above of the rope | closed | 2026-03-22 | Bug in MTR itself. |
| #1389 | Point sounds not playing for BVE sound system | open | 2026-03-31 | Bug in MTR itself. |
| #1394 | bug in MTR 4.0.3 at 1.20.1 forge while installed with oculus (all at latest vers... | open | 2026-04-08 | Bug in MTR itself. |
| #1397 | Imcompatible with Shader Packs | closed | 2026-04-14 | Bug in MTR itself. |
| #1398 | Nodes gone after loading world | open | 2026-04-17 | Nodes gone after loading. Data-loss cluster (A7). |
| #1400 | Under certain circumstances, the texture brightness at the connection of the tra... | open | 2026-04-28 | Bug in MTR itself. |
| #1402 | Rails fail to cull | open | 2026-05-01 | Bug in MTR itself. |
| #1407 | Game freeze when riding on vehicle and reloading resource packs | open | 2026-05-14 | Bug in MTR itself. |
| #1408 | Moving to Vulkan | open | 2026-05-14 | Vulkan. Rendering engine. |
| #1411 | Train glitches on intersection | open | 2026-05-19 | Bug in MTR itself. |
| #1413 | DISCORD SUPPORT | open | 2026-05-24 | Discord support. Support question. |
| #1414 | Station colour blocks not persisting their colours | closed | 2026-05-27 | Bug in MTR itself. |
| #1417 | Version compatible avec wiiu | closed | 2026-06-04 | Wii U version. Other version. |
| #1418 | Trains overshot then suddenly go back to the intended stopping position | open | 2026-06-06 | Bug in MTR itself. |
| #1419 | Trains refusing to enter signal blocks under some circumstances | open | 2026-06-06 | Bug in MTR itself. |
| #1420 | [4.1] Incompatible with Kotlin for Forge | open | 2026-06-07 | Bug in MTR itself. |
| #1421 | [4.1] Missing Channel | open | 2026-06-08 | Bug in MTR itself. |
| #1422 | Ticket barrier not working | closed | 2026-06-09 | Bug in MTR itself. |
| #1423 | [4.1] Exit the train bug | open | 2026-06-10 | Bug in MTR itself. |
| #1425 | 与Kotlin模组冲突 | closed | 2026-06-13 | Bug in MTR itself. |
| #1427 | Trains turns around in specific conditions (see thread) | open | 2026-06-13 | Bug in MTR itself. |
| #1429 | 混合端服务器中电梯和列车模型非创建者无法看见 | open | 2026-06-14 | Bug in MTR itself. |
| #1430 | I can't see the elevator | open | 2026-06-16 | Bug in MTR itself. |
| #1433 | 司机钥匙颜色错误/Driver's key color is wrong | open | 2026-06-18 | Bug in MTR itself. |
| #1436 | 不兼容Mellow Shader | open | 2026-06-20 | Bug in MTR itself. |
| #1439 | Crash report | open | 2026-06-22 | Crash report. Support. |
| #1442 | 幽灵停车 | open | 2026-06-23 | Bug in MTR itself. |
| #1446 | NTE bridge creator calculation method not brought over in MTR4 | open | 2026-06-26 | Bug in MTR itself. |
| #1449 | Elevator not spawning | open | 2026-06-27 | Bug in MTR itself. |
| #1451 | Barrier Glitch (Previous report was told to reopen) | open | 2026-07-02 | Bug in MTR itself. |
| #1454 | 关于电梯开门问题 | open | 2026-07-05 | Lift door problem. Bug. |
| #1455 | Improve the rails and trains display when open shaders | open | 2026-07-05 | Shader rendering. MTR. |
| #1456 | [4.1]Utilities.circularClamp() seems to have problem as it significantly affects... | open | 2026-07-07 | Bug in MTR itself. |
| #1457 | Schedule sensor activating twice | open | 2026-07-08 | Bug in MTR itself. |
| #1459 | Signalling Priority Issues | open | 2026-07-10 | Bug in MTR itself. |
| #1462 | 在4.1.0 beta 2 中轨道在视距外依旧渲染 | open | 2026-07-14 | Bug in MTR itself. |
| #1463 | Will it support version 26.1? | open | 2026-07-15 | Version 26.1. Other version. |
| #1464 | 车灯不渲染 | open | 2026-07-16 | Bug in MTR itself. |
| #1467 | The transit system map only plans routes in the down direction; routes in the up... | open | 2026-07-23 | Bug in MTR itself. |
| #1469 | Signal does not face certain directions | open | 2026-07-24 | Bug in MTR itself. |
| #1472 | obj trains and tracks is not showing in MTR 4.1.0-beta2 | open | 2026-07-27 | Bug in MTR itself. |
| #1473 | Render by Using Vulkan In the Futrue 在未来使用Vulkan进行渲染 | open | 2026-07-29 | Vulkan. Duplicate of #1408. |
| #1476 | Rendering Issues in NeoForge 4.1.0 (Black Textures, Transparency, and Glowing Mo... | open | 2026-08-03 | Bug in MTR itself. |
| #1477 | Ticket Barriers UNUSABLE | open | 2026-08-04 | Bug in MTR itself. |
| #1478 | I would like to be able to freely adjust the elevator's ascent and descent speed... | closed | 2026-08-09 | Closed. Lift speed already exists in the third-party Yunzhu Transit Extension. |
| #1480 | 装饰物件(Eye Candy)在含Voxy模组环境下的渲染问题 | open | 2026-08-16 | Bug in MTR itself. |
| #1481 | 列车与轨道无法在Replay模组的的回放状态下被正常渲染 | open | 2026-08-16 | Bug in MTR itself. |
| #1482 | Non-Latin script scaling | open | 2026-08-16 | Bug in MTR itself. |
| #1483 | Elevator floor selection menu does not appear | open | 2026-08-17 | Bug in MTR itself. |
| #1484 | 关于MTR 4.0.5 在我服时经常GC卡顿 | open | 2026-08-20 | GC stutter on 4.0.5 servers. MTR performance. |
| #1485 | Eyecandy (Decoration Object) does not render selected model, stays as default tr... | open | 2026-08-26 | Bug in MTR itself. |
| #1486 | Train gets permanently stuck at a station with doors open, never departs (no cra... | open | 2026-08-26 | Bug in MTR itself. |
| #1487 | Cannot leave the custom train after changing it's skin | open | 2026-08-26 | Bug in MTR itself. |
| #1488 | Update to 1.21.11 | open | 2026-08-27 | 1.21.11. Other version. |
| #1489 | 添加1.21.1-1.21.4的mtr3支持Add support for MTR3 mod in Minecraft 1.21.4 and 1.21.1 | open | 2026-08-27 | MTR3 on 1.21.x. Other version. |
| #1490 | There have a bug with lift | open | 2026-08-27 | Bug in MTR itself. |
| #1492 | Station exits are duplicated. Non-functional signs | open | 2026-09-01 | Bug in MTR itself. |
| #1496 | 新版本MTR让我的服务器一直在GC | open | 2026-09-05 | Bug in MTR itself. |
| #1499 | Support Fold Craft Launcher and ZalithLauncher 2 for proper memory allocation wi... | open | 2026-09-13 | Launcher support. Support question. |
| #1500 | [IMPORTANT] Platform Gates streches with 10+ blocks map | open | 2026-09-13 | Bug in MTR itself. |
| #1502 | Rail placing preview not appearing | open | 2026-09-17 | Bug in MTR itself. |
| #1504 | Vehicle display sizes are rounded | open | 2026-09-19 | Bug in MTR itself. |
| #1505 | The non-CJK switch doesn't work | open | 2026-09-20 | Bug in MTR itself. |
| #1506 | MTR4.1中轨道翻转选项不生效 | open | 2026-09-27 | Bug in MTR itself. |

