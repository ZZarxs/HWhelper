# krsHubSwap protocol reference

Reference supplied by the user: `krsHubSwap-1.21.4 (1).jar`, mod ID `krshubswap`, version 1.0, Minecraft 1.21.4.
Original author: **krosov_ok**. Contact recorded in its metadata: https://t.me/krsCODE.
Original metadata license: **All-Rights-Reserved**.
Reference JAR SHA-256: `62492ab9c5832b29cee2f00b3c81c1b9b177e2e5620bd8387b4a8dd092a13f16`.

HWhelper contains a new native 1.21.11 implementation of the observed selector protocol and compatible configuration readers. The reference binary, assets and decompiled class sources are not redistributed or relicensed.

Observed protocol: send `hub`; await a new client world or the server's already-connected message; send `lite`; click the unique category and server items in the server container. `minecraft:custom_data.PublicBukkitValues` contains `advancedserverselecter:server-type` (`solo`, `duo`, `trio`, `clans`) and `advancedserverselecter:server` (`lanarchy` for 1, `lanarchyN` otherwise). Default ranges are 1–15, 16–31, 32–47 and 48–66. Original aliases are `lt` and `an`; original configuration key is F6. The two original preference filenames are supported by a one-time importer.

HWhelper adds unambiguous matching, exclusion of player inventory slots, click snapshot validation, manual cancellation, request throttling, finite stage deadlines and scoreboard confirmation.
