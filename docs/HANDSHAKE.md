# ATU <-> JCM vehicle script handshake (protocol 2)

ATU publishes settings to Joban Client Mod (JCM, mod id `jsblock`) vehicle scripts, so a train script can adapt. Scripts read a key with `Resources.getAddonVersion(key)`; it returns a string, or `null` when nothing is registered.

## Keys (all values are strings)

| Key | Value | Meaning |
|---|---|---|
| `aurelia_transit_utilities` | ATU's friendly version string, e.g. `1.2.0+mc1.20.1-fabric` (not just `1.2.0`; compare by prefix if needed) | ATU is installed (unchanged since 1.1.0) |
| `aurelia_transit_utilities:handshake` | `2` | protocol version; bumped only when a key changes meaning |
| `aurelia_transit_utilities:announcer` | `on` / `off` | the Announcer speaks station info on rides; scripts should then skip their own next-station / last-stop announcements |
| `aurelia_transit_utilities:announcer_lang` | `en` | language the Announcer speaks. It only supports English (text and the `next_<slug>` clips), so `en` is the only value; there is no auto/ja/zh |
| `aurelia_transit_utilities:melodies` | `on` / `off` | player preference: may vehicle scripts play their departure melodies. Stored by ATU, set in the ATU hub ("Vehicle melodies") |
| `aurelia_transit_utilities:caps` | comma list, now `announcer,announcer_lang,melodies` | features this ATU publishes; check it before relying on a key |

## Behaviour when a key is missing

- `aurelia_transit_utilities` null: ATU is not installed (or JCM loaded before ATU registered): script uses its own defaults.
- `:handshake` null but base key present: ATU 1.1.x, only `:announcer` exists.
- `:melodies` null: treat as `on` (the vehicle's normal behaviour). Anything other than `off` also means on.
- `:announcer` null: treat as `off` (script announces itself).
- Unknown extra values in `:caps`: ignore. Unknown key values: ignore.

## When values change

ATU registers at client start and again whenever the Announcer or Melodies toggle changes (all keys each time). The melodies preference persists in `config/aurelia_transit_utilities/handshake.properties` (`melodies=on|off`, missing file = on); the Announcer state in `announcer.properties`. Without JCM nothing is registered (the publish is only recorded for the self-test).

## Verified in JCM 2.2.5+1.20.1 bytecode (`javap -c`)

- `public static void MTRScriptingAPI.registerAddonVersion(String, String)`: throws `IllegalArgumentException` on a null id or version, then `getstatic addonVersionMap; invokeinterface java/util/Map.put`. A plain put, so an existing key is overwritten: values can change after registration.
- `public static String MTRScriptingAPI.getAddonVersion(String)`: `getstatic addonVersionMap; invokeinterface Map.get; checkcast String`. `ScriptResourceUtil.getAddonVersion` (the script global `Resources`) only delegates to it. No caching on either side, so scripts see a new value on their next call; a script that reads once at load, or caches, will not.
- `private static final Map<String,String> addonVersionMap` is initialised in `<clinit>` as `new java.util.HashMap()`: not synchronized. ATU only calls the API from the client thread, and after the first registration only replaces values of existing keys (no resize), so a concurrent read from a script thread is benign in practice; this is not a guarantee JCM gives.
- Nothing ever removes keys.

## Not verifiable without JCM

Delivery into JCM's map and the scripts reading it are checked only in Noriega (e.g. a line in JCM's Script Debug Overlay). The dev self-test checks the published map, persistence and republishing.
