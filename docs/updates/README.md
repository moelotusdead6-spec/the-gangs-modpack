# Updating The Gangs Modpack

## FancyMenu Halloween decorations removed (2026-11-09)

The title-screen orange string lights and falling leaves are disabled, and
FancyMenu uses its standard Dark interface theme again. The custom wallpaper,
buttons, and music configuration are unchanged by this appearance update.
After publication, close Minecraft and press Play in the existing auto-updating
Prism instance to receive it. No server restart or new instance is needed.

## Effective ambience crash mitigation (2026-11-08)

Effective is downgraded and pinned to **2.2.2.1+1.20.1** to remove the
2.3.2 biome-ambience handler responsible for the confirmed
`tickingSound is null` client crash. Waterfall/splash visuals and waterfall
audio remain; the newer Effective biome ambience is removed. Existing
AmbientSounds 6.3.11 and Sound Physics Remastered 1.5.1 are unchanged.

After publication, close Minecraft and press Play in the existing
auto-updating Prism instance. The old Effective jar is replaced with the
standard inert retirement placeholder, not loaded alongside the downgrade.
This is client-only: no server restart, world change or `/reload` is needed.
The release jars were checksum-verified: the downgrade contains no
`org/ladysnake/effective/ambience` classes or ambience entrypoint/mixin,
but still contains its waterfall sound class. This removes the identified
crash path; it does not certify every other feature or mod interaction.

Upstream: [waterfall crash report](https://github.com/Ladysnake/Effective/issues/325)
and [ambience null-sound report](https://github.com/Ladysnake/Effective/issues/297).
Do not unpin or restore 2.3.2 without a verified fix for that handler.

## Client auto-update checksum repair

The pack index contained a checksum for `ARTIFACTS.md` that did not match the
LF-normalized file published by GitHub Pages. Prism's Packwiz pre-launch
update rejects that download and stops before Minecraft starts.
The corrected index has been tested with a real Packwiz update and retry.

After the corrected index is published, close Minecraft and press Play in the
same auto-updating Prism instance. No new import or server restart is needed.
If the launch still fails, share the full Prism launch log.

After `packwiz refresh`, maintainers should run
`powershell -NoProfile -ExecutionPolicy Bypass -File scripts\Test-PackwizIndex.ps1`
before publishing.
It checks the index hashes and rejects files Git would transform, including
mixed or CRLF line endings in files that Git stores with LF.

## Prepared locally: v1.1.21 Simple Voice Chat

Adds Simple Voice Chat 2.6.25 to clients and server with 48-block proximity
and optional private groups. Voice uses UDP on the game port (`port=-1`), so
no host changes are needed. The server installs it at a restart through
`gangs-voicechat-restart-1.0.66.jar` once that launcher is armed. See
[voice chat setup](next-restart.md).

## Prepared: Neon Wall Wonders 11.0.0

The local update includes all 87 version 11 signs in the existing neon kit
pool and excludes them from Gang Shop. It has not been installed or published.
Before a future coordinated server/client update, read the
[version 9 compatibility warning and deployment notes](next-restart.md).

GoldClaim 1.0.64 is also prepared locally with separate interaction and
container permissions, `/claim trustlist`, and `/claimblocks`. It has not been
installed or activated. See the [claim update notes](next-restart.md).

## v1.1.19 client log and model fixes

Stops Item Borders rewriting its config every second, removes FoxFriend's
per-tick log spam, and fixes the broken Alex's Caves Grottoceratops egg model.
Close Minecraft and press Play in the existing auto-updating Prism instance.
No live server restart, reload or installation is part of this publication.
[Details and rollback](v1.1.19.md).

## v1.1.18 installed server and supporting fixes

Publishes the installed GoldClaim 1.0.62, GangSales 1.0.3, Ledger repair,
Biolith concurrency fix, Command Limits 1.0.1 and Rank Badges 1.0.3.
Close Minecraft and press Play in the existing auto-updating Prism instance.
Only Biolith and Rank Badges are new client mod replacements in this release;
Neon Wall Wonders and the v1.1.16 End libraries remain included.
No live server restart, reload or installation is part of this publication.
[Details and rollback](v1.1.18.md).

## v1.1.17 Neon Wall Wonders

Adds **Neon Wall Wonders 8.0.0** to both server and clients. The server jar
is staged for the next normal restart; no restart or reload is needed to
stage it. Close Minecraft and press Play in the same auto-updating Prism
instance before rejoining after restart. No replacement instance is needed.
See [installation, attribution and rollback](v1.1.17.md).

## v1.1.16 End library updates

BCLib updates to **3.0.16** and Better End to **4.0.12** for Fabric 1.20.1.
Close Minecraft and press Play in the same auto-updating Prism instance.
Packwiz downloads the new jars and replaces the previously managed versions;
no new instance or manual ZIP installation is needed.
Instances without automatic updates must enable them using the setup below.
The matching server update is prepared for the owner's next normal restart.

## v1.1.15 Halloween client appearance

This release originally used FancyMenu's built-in **Spooky Season** interface theme. The title
screen adds orange string lights and a light autumn-leaves overlay; the
custom wallpaper, menu buttons, music and multiplayer layouts are unchanged.
This is a client-pack change, not a server setting. Players receive it by
closing Minecraft and pressing Play in the same auto-updating Prism instance.
Older instances must enable automatic updates using the setup below.
No server restart or replacement instance is needed.

## Prepared next-restart changes

The **2026-10-24 server-only patch** prepares GoldClaim **1.0.52**:
15-minute ground cleanup, claimed-block protection against Supplementaries
bombs/cannonballs, all lunar events in RSW Overworld, and Blue/Harvest moons
preserved in Wild without Blood Moons. Persisted forecasts are migrated once.
The checksum-verified next-start launcher installs before normal Fabric startup;
staging does not restart the running server or require client updates.
See [current patch, tests and rollback](next-restart.md).

After the subsequent Biolith concurrent-modification crash, a targeted
**End-fix concurrency hotfix** was tested and installed while offline.
The prior jar is retained for rollback; no world reset or server start was
sent. Teleport Optimizer, movement checks and flight speed are unchanged.
See [crash fix, tests and restart status](next-restart.md).

The gameplay bundle below has now been installed. GangSales **1.0.2** was
subsequently installed while stopped to fix a LuckPerms exception interrupting
login command-tree construction. Normal Fabric startup was restored; do not
reuse the completed installer that pins GangSales 1.0.1.
See [login recovery and validation](next-restart.md).

GoldClaim 1.0.49 / GangSales 1.0.1 block short-range hub teleportation,
restore occasional roaming RSW bosses, update cleanup messages and correct
seller item names. Shop exclusions cover the 56 items inspected in Oux_y's
three shulkers plus the disabled furniture bin.
The same next-start installer keeps Teleport Optimizer enabled and
temporarily pauses Spawn Animations while keeping its mod/settings and saved-mob
recovery. Flight speed and movement protections are unchanged.
See [next-restart details and validation](next-restart.md).
Hosted next-start staging installs the update only when the server is next
started; it does not change the running game. See the DedicatedMC section in
the notes before changing startup settings.

## v1.1.14 hosted server address

The server has moved to DedicatedMC at `tgma.dedimc.io`. Close Minecraft and
press Play in the same auto-updating Prism instance. GoldClaim Map 1.0.2 also
maintains the pack's public server entry: it replaces the old Playit address
(including explicit default port), or adds the new address if absent.
Existing names, icons, resource-pack choices and unrelated servers are preserved.
Before changing an existing list it creates a `servers.dat.before-tgma-*.bak`
backup in the game directory. Subsequent launches do not rewrite an already
updated list. This client-only update needs no server restart or world reset.
Clients without automatic updates must enable them using the setup below or
manually add `tgma.dedimc.io`.

## v1.1.13 restart bundle and JEI cleanup

The v1.1.13 bundle includes the pending GoldClaim 1.0.43 hotfix. Normal
auto-updating Prism clients keep their instance and press Play; Packwiz removes
previously managed JEI. For leftover unmanaged JEI, close Minecraft and Prism, then
run `client-update/Remove-ClientJei.ps1 -GameDirectory '<existing game directory>'`
from the extracted bundle, or use the full client installer. REI and personal
settings are preserved. A server restart alone cannot remove client JEI.
OpenBlocks Elevator already centers both directions and does not expose the
suggested boolean; the reported in-game issue is not yet reproduced.
See [v1.1.13 details](v1.1.13.md).

## Keep one Prism instance

Keep the same Prism instance and press Play for normal client pack updates.
New v1.1.11 imports and later have automatic client-only Packwiz updates enabled.
The public ZIP is for new installations, not a replacement instance for
every release.

Existing players must close Minecraft and Prism Launcher, then run the included
`client-update/Install-ClientUpdate.ps1` against their existing Prism game
directory once. Follow the ZIP's `client-update/README.txt` for the command.
After setup, future normal client pack updates need no ZIP reimport or
manual installer.

The v1.1.2 download includes corrected Qt INI escaping. For an earlier
test import, edit its Settings > Custom Commands and set
the pre-launch command to:

```text
"$INST_JAVA" -jar "$INST_DIR/packwiz-installer-bootstrap.jar" -g -s client https://moelotusdead6-spec.github.io/the-gangs-modpack/pack.toml
```

Enter that text in Prism's UI, not directly into `instance.cfg`: Prism handles
the required INI escaping when it saves. Alternatively, close Prism and run
the included `client-update/Repair-PrismAutoUpdate.ps1` against the existing
game directory. Neither method changes player settings.

Internet access is required for update checks. Failed updates block launch
and appear in Prism's log rather than silently launching a mismatched client.
The headless Packwiz pre-launch hook also updates Prism's saved Minecraft and
loader component versions when the pack pins change. A launcher restart may
still be needed before Prism uses updated component metadata.

## Personal settings

Personal `options.txt`, keybindings, resource-pack selections and
`servers.dat` are not replaced by Packwiz updates. The v1.1.14 client migration
only updates/adds the pack's public address as described above. Personal mod config files that
are not part of the pack remain untouched. Pack-supplied mod configs are
managed by Packwiz and may change when a release changes those configs.

Default Options 18.0.6 supplies starting options/keybindings and the public
server `tgma.dedimc.io` only when the corresponding saved files
are missing. Existing settings take precedence.

Default Options is not cloud backup and cannot transfer settings between
separate instances. If you intentionally create a new instance, copy your
old `options.txt`, `servers.dat` and personal mod configs before its first
launch. Keep the old instance until you have verified the result.

Do not copy the full config folder over an existing instance or use
`/defaultoptions saveall` as a personal backup command.

## Server update

The all-in-one ZIP includes a separate `server-update` payload with its own
instructions and stopped-server installer. Apply it only after stopping the
server and making a world backup, then start the server yourself.
Publishing the pack does not install or restart the live server.

GoldClaim 1.0.35 coordinates Resetting World (RSW) rotation across three
synchronized dimensions (`multiworld:rsw`, `multiworld:rsw_nether`, and
`multiworld:rsw_the_end`) with matching seeds and 10,000-block diameter
(5,000-block radius) borders centered at (0, 0). The Nether dimension uses a
1:1 coordinate scale to match the Overworld, ensuring portal exits stay
within the 5,000-block world border. Nether and End portals in the wild
Overworld may be lit but cannot be entered. Blood Moons are isolated
exclusively to RSW. `/rtp` is restricted to Wild Overworld and RSW Overworld.
GoldClaim also disables `doFireTick` across RSW dimensions.
RSW reset timing, first-entry safety and saved return positions are unchanged.

v1.1.4 removes YUNG's Better End Island, retaining True Ending: Ender Dragon
Overhaul (by limesplatus). Good Ending is a separate Overworld mod and remains.
The RSW End uses Minecraft's dragon fight lifecycle with True Ending's attacks;
dimension compatibility overrides route its functions and predicates to RSW End.
See [v1.1.4 details](v1.1.4.md), including the optional backed-up End regeneration.

v1.1.5 adds the October 13 mod downloads, BetterEnd and BCLib. Macaw's items
share one shop category. Waystones placement/use and travel are disabled
throughout RSW. On the next startup, a one-time cleanup removes existing Wild
waystones never activated by any player, including offline players; future
Wild placements are not automatically removed. Cleanup backs up touched region
files and the waystone registry separately before removal. No additional End
reset is needed for the already-prepared server. See [v1.1.5 details](v1.1.5.md).

v1.1.6 retains no completed RSW backups and caps other timestamped server
backup categories at two generations each. It adds BetterEnd Lootr conversion
tags and targeted server-only Lithium/C2ME compatibility settings. The pending
PacketEvents custom-particle parsing issue is documented separately.
See [v1.1.6 details](v1.1.6.md).

v1.1.7 removes GrimAC and its bundled PacketEvents after the newest official
Fabric build still rejected BetterEnd's custom particle. GrimAC cheat checks
are disabled; BetterEnd, Lootr, GoldClaim protections and permissions remain.
It also purges saved homes/warps/return locations and Waystones targeting the
original `minecraft:the_nether` and `minecraft:the_end`, including offline
players' activated lists. GoldClaim blocks entry there through teleport APIs
and commands without an operator bypass; stranded players are sent to the hub.
Wild Overworld and all RSW worlds remain available. No world reset or
Sharestone-specific ban is introduced. See [v1.1.7 details](v1.1.7.md).

v1.1.8 (GoldClaim 1.0.36) fixes RSW End exit portals, including the
post-dragon pedestal and portals in modded structures, which dropped players
into or under the Overworld bedrock. They now go to the player's RSW spawn
point or to safe RSW ground near 0,0. Every world runs on HARD, and
`/difficulty` applies to all worlds and is saved to `server.properties`.
The stopped-server installer also restores Kunoisback's lost items once.
The client update is metadata-only. See [v1.1.8 details](v1.1.8.md).

v1.1.9 (GoldClaim 1.0.40) repairs kit rewards, restores original Enderman
spawning, and makes unopened BetterEnd city chests use Lootr on first opening.
Future RSW resets target 87.5% BetterEnd outer-island biome selection and
generate/save at least seven reachable cities before reopening. The existing
End and reset schedule are unchanged. Apply server files only while stopped
at the next scheduled restart; publishing does not deploy them.
See [v1.1.9 details and validation](v1.1.9.md).

v1.1.10 (GoldClaim 1.0.41) fixes RSW End city chests that could not be opened.
They now open as personal Lootr chests even when a duplicate legacy data pack
hides the Lootr conversion tag. Clients receive no file changes. Stage the
server jar for the next normal restart; publishing does not deploy it.
See [v1.1.10 details and validation](v1.1.10.md).

v1.1.11 (GoldClaim 1.0.42 + GoldClaim Map 1.0.0) draws every claim as a soft
gold outline on JourneyMap's full-screen map, never the minimap. Hovering
shows the owner's name. Clients receive one new client-only mod automatically
when they press Play. Stage the server jar for the next normal restart;
publishing does not deploy it.
See [v1.1.11 details and validation](v1.1.11.md).

v1.1.12 updates only GoldClaim Map to 1.0.1. It prevents an incompatible legacy
JourneyMap plugin from blocking discovery of every valid map plugin. Restart
Minecraft and press Play to receive it; no server restart is required.
See [v1.1.12 details and validation](v1.1.12.md).
