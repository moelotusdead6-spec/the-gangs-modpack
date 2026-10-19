# Updating The Gangs Modpack

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
Minecraft or Fabric Loader version changes may require a separate launcher
update; Packwiz's pre-launch hook manages pack files, not Prism components.

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
