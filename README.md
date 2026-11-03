# The Gangs Modpack
Welcome to The Gangs Modded Adventure
A hand-picked Fabric 1.20.1 pack built for friends who want unforgettable journeys, shared progression, and nonstop moments worth laughing about later. This pack blends exploration, combat growth, world variety, quality-of-life upgrades, and immersive atmosphere into one multiplayer-focused experience that feels alive from the very first spawn.

Expect expanded structures, dangerous encounters, richer dimensions, and rewarding loot that keep every trip exciting. Base building feels meaningful, travel feels purposeful, and survival stays engaging as your group pushes farther from home. Whether your crew loves boss fights, treasure hunts, building cozy towns, or chaotic “this was a bad idea” expeditions, there is always a new objective waiting.

The heart of this pack is cooperative storytelling. Claim land, gear up together, rescue each other from near-disasters, and turn random detours into legendary side quests. Early game is welcoming for casual sessions, while later progression gives experienced players deeper challenges to overcome as a team.

Every feature is chosen to support group play: smoother team coordination, better adventure flow, and more opportunities for each player to shine in their own way. The result is a world where every login can become a shared memory, from quiet base nights to all-out runs into unknown territory.

This modpack is built around friendship, discovery, and those “you had to be there” moments, The Gangs Modded Adventure is a Tribute to the Best people anyone could ask for! It's time to write our own story one wild session at a time!

## Getting started

- **Minimum software requirements:** Minecraft 1.20.1, Java 17+, and Fabric Loader 0.19.3+.
- **Download:** [Latest release](https://github.com/moelotusdead6-spec/the-gangs-modpack/releases/latest). Import the all-in-one ZIP into Prism Launcher.
- **Updating:** See the [update guide](docs/updates/README.md).
- **Resource packs:** Enable or disable them in Options > Resource Packs.

## Simple Voice Chat

Pack v1.1.21 adds Simple Voice Chat **2.6.25** for Fabric 1.20.1, with
48-block proximity voice and optional private groups (press `V`). Players
without the mod can still join. The server reuses the game port for UDP
voice, so no host changes are needed; a restart launcher installs it. See
[voice chat setup](docs/updates/next-restart.md).

## Neon Wall Wonders

Neon Wall Wonders **11.0.0** is in the client pack and installs on the server
at the next restart. Its 87 sign models include all 65 from the previous 8.0.0
jar. They remain excluded from the Gang Shop and are available through the
existing neon kit pool. See [update notes](docs/updates/next-restart.md).

Created by oux_y using [CreativeMode.net](https://creativemode.net).
The original, unmodified `neon-wall-wonders-gwbzt3mk.jar` is provided
for free with its embedded notices preserved.
[Official mod page](https://creativemode.net/mod/neon-wall-wonders-gwbzt3mk)
and [CreativeMode Mods License](https://creativemode.net/mod-license).

## Claim permissions

GoldClaim 1.0.64 is staged on the server and becomes active at the next
restart. Use `/claim trust interact <player>` to let a
player use controls such as levers, buttons, pressure plates and elevators
without granting access to protected containers and machines. Use `/claim trust interact all`
to grant that control access to everyone, or `/claim untrust interact all` to
remove it.

- `/claim trustlist` shows the permission levels for the claim you stand in.
- `/claimblocks` shows your used and remaining claim blocks in this dimension.

## Server protection

- **Published v1.1.19:** Client fixes stop Item Borders config rewrites and
  FoxFriend log spam and repair the Alex's Caves Grottoceratops egg model.
  See [details and rollback](docs/updates/v1.1.19.md).
- **Published v1.1.18:** GoldClaim 1.0.62, GangSales 1.0.3,
  Command Limits 1.0.1, Ledger repair, Biolith concurrency fix and Rank Badges
  1.0.3 now have published source and checksum-pinned pack metadata.
  Close Minecraft and press Play in the same auto-updating Prism instance
  for the matching Biolith/Rank Badges client update.
  No live power action was sent; local-only login-damage protection and
  restart installers remain unpublished. See [details](docs/updates/v1.1.18.md).

- **Earlier gameplay update:** GoldClaim 1.0.62 added missing player-name completion, doubled sponge absorption budgets, and stopped Alex's Mobs shattered dimensional portals inside the current world border. BCLib 3.0.16 and Better End 4.0.12 require matching client updates. Oux_y and Rootamiss receive UUID-scoped Fiw verification exemptions only; other protections remain enforced. See [activation and rollback](docs/updates/next-restart.md).
- **Staged for next restart:** GoldClaim 1.0.59 removes the Withered Wabbajack, restores staff-only `/locate`, and protects future beach terrain from Expanded Ecosphere's sea-level air cut. Additive tags repair Alex's recipes, including Straddleboard; six cave configs enable Alex's Caves in newly generated RSW Overworld chunks. Existing terrain and other Soulslike weapons remain unchanged. No live restart or reload; see [activation and rollback](docs/updates/next-restart.md).
- **Resource-world travel:** GoldClaim 1.0.57 prepares new RSW arrival terrain asynchronously instead of waiting for generation in `/rsw` on the main server thread. Preparation has a two-minute timeout and releases its chunk tickets when finished or cancelled.
- **Audit logging:** The server uses Ledger `1.2.8+gangs.compat.1` to repair missing-world registration during logging and compatibility with the installed Kotlin runtime. Existing audit records and database IDs are preserved; previously dropped actions cannot be reconstructed. The jar and LGPL corresponding source bundle are listed in [ARTIFACTS.md](ARTIFACTS.md).
- **X-ray:** AntiXray obfuscates vanilla and modded ores on the server. It is configured for the Overworld, Nether, and RSW worlds; clients do not need the AntiXray mod.
- **Cheat-mod monitoring:** [Fiw AntiCheat by Fi3w0](https://modrinth.com/mod/fiw-anticheat) reports known x-ray and autoclicker mod signatures and audits reported resource packs. Monitoring is enabled; detections alert online staff and are logged, but do not automatically kick or ban. Install the same modpack update on clients before updating the server; clients without Fiw are kicked when verification times out, even in monitor-only mode. Fully close Minecraft and relaunch after updating. GoldClaim 1.0.56 waits until Fiw releases the player before starting the automatic hub-transfer delay. This prevents Fiw's saved login coordinates from overriding the hub spawn after a dimension change. Server position corrections remain allowed; item-triggered short-range teleports in the hub remain blocked.
- **Large Fiw reports:** Server-side Packet Fixer 3.3.2 raises the client-report payload allowance from 32 KiB to a bounded 1 MiB. Keep `config/packetfixer.properties` with `allSizesUnlimited=false`; the unlimited upstream default is not used. No additional client mod is needed for this incoming-packet fix.
- **Join verification allowance:** Fiw allows 120 seconds for the client report (`config/fiw-mods-api/config.json`, `timeout_seconds`). Earlier 10- and 60-second limits caused verification-timeout kicks, including players who passed on retry. Verification remains required; this does not exempt missing or incompatible clients.
- **Reviewing reports:** Use `/fiwmods profile <player>` to inspect a player's reported mods and resource packs. A mod report is not proof that a player used a cheat; custom clients can also evade reporting.
- **Manual ban reason:** Staff can use `/ban <player> Banned for cheating! Visit Discord to apeal.` after reviewing evidence.
- **Fire spread:** Disabled in all loaded worlds, including `/pvp`, and applied again when a world loads.
  This requires the updated GoldClaim on the server. On older builds, stand in each
  Multiworld world and use `/mw gamerule doFireTick false`, then query
  `/mw gamerule doFireTick`. Vanilla `/gamerule` uses the server's main rules even
  under `execute in`, so it does not verify or change PvP's separate rules.
  The rule stops ordinary fire propagation regardless of what ignited the flame,
  and lava ignition, but does not stop items, spells, or explosions from placing
  new fire directly.
