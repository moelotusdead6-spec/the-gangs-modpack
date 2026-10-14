# Updating The Gangs Modpack

## Keep one Prism instance

Keep the same Prism instance and press Play for normal client pack updates.
New v1.1.8 imports have automatic client-only Packwiz updates enabled.
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
`servers.dat` are not replaced by updates. Personal mod config files that
are not part of the pack remain untouched. Pack-supplied mod configs are
managed by Packwiz and may change when a release changes those configs.

Default Options 18.0.6 supplies starting options/keybindings and the public
server `gangs-adventure.playit.plus` only when the corresponding saved files
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
