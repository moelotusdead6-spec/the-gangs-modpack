# Updating The Gangs Modpack

## Keep one Prism instance

Keep the same Prism instance and press Play for normal client pack updates.
New v1.1.3 imports have automatic client-only Packwiz updates enabled.
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

GoldClaim 1.0.32 coordinates Resetting World (RSW) rotation across three
synchronized dimensions (`multiworld:rsw`, `multiworld:rsw_nether`, and
`multiworld:rsw_the_end`) with matching seeds and 10,000-block diameter
(5,000-block radius) borders centered at (0, 0). The Nether dimension uses a
1:1 coordinate scale to match the Overworld, ensuring portal exits stay
within the 5,000-block world border. Nether and End portals in the wild
Overworld may be lit but cannot be entered. Blood Moons are isolated
exclusively to RSW. `/rtp` is restricted to Wild Overworld and RSW Overworld.
GoldClaim also disables `doFireTick` across RSW dimensions.
RSW reset timing, first-entry safety and saved return positions are unchanged.
