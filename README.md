# The Gangs Modpack
Welcome to The Gangs Modded Adventure
A hand-picked Fabric 1.20.1 pack built for friends who want unforgettable journeys, shared progression, and nonstop moments worth laughing about later. This pack blends exploration, combat growth, world variety, quality-of-life upgrades, and immersive atmosphere into one multiplayer-focused experience that feels alive from the very first spawn.

Expect expanded structures, dangerous encounters, richer dimensions, and rewarding loot that keep every trip exciting. Base building feels meaningful, travel feels purposeful, and survival stays engaging as your group pushes farther from home. Whether your crew loves boss fights, treasure hunts, building cozy towns, or chaotic “this was a bad idea” expeditions, there is always a new objective waiting.

The heart of this pack is cooperative storytelling. Claim land, gear up together, rescue each other from near-disasters, and turn random detours into legendary side quests. Early game is welcoming for casual sessions, while later progression gives experienced players deeper challenges to overcome as a team.

Every feature is chosen to support group play: smoother team coordination, better adventure flow, and more opportunities for each player to shine in their own way. The result is a world where every login can become a shared memory, from quiet base nights to all-out runs into unknown territory.

This modpack is built around friendship, discovery, and those “you had to be there” moments, The Gangs Modded Adventure is a Tribute to the Best people anyone could ask for! It's time to write our own story one wild session at a time!

## Pack requirements and resource packs

Version 0.1.48 requires Minecraft 1.20.1, Java 17 or newer, and Fabric Loader
0.19.3 or newer.

The pack includes AttributeFix, CorgiLib, Data Anchor, Enhanced Celestials,
Cinematic Respawn (client only), and Marium's Soulslike Weaponry 1.4.9.
RangedWeaponAPI remains at 1.1.4 to preserve compatibility with Archers 1.3.0.
Do not install the supplied RangedWeaponAPI 2.3.4 or Soulslike Weaponry 1.4.10:
their API changes are incompatible with the existing combat mods. The new mods
and Immersive Interfaces are delivered from official Modrinth downloads, pinned
to the supplied versions except for this compatibility downgrade, and verified
by hash.

Immersive Interfaces installs into `resourcepacks/`. Global Packs is configured
in `config/global_packs.toml` to automatically enable and require every resource
pack in that folder, including packs added there in future. These packs cannot
be disabled through the resource-pack selection screen. The existing
`global_packs/required_resources/` location remains required as well.
Mod-provided built-in packs and server-sent packs are not affected by this
folder setting. Shader packs are also separate and are not forced.

The previously published 0.1.46 ZIP archives are historical snapshots and do
not contain these changes; use the 0.1.48 all-in-one ZIP or current packwiz manifest.

## Gameplay update and server checks

Boots of Swiftness now have one 1.25% chance per filled chest container.
On the server, plushie and plush-box recipes are removed except for the player
plushie. Existing items, loot, shops and kit rewards remain available.
PVP deaths retain inventory and XP, skip Universal Graves capture, and respawn
at the hub. Death rules in other dimensions are unchanged.

RSW has a visible border centered at 0,0 with edges at +/-5000 and a 50-block
warning distance. It resets daily at 05:00 fixed EST (10:00 UTC, without a DST
adjustment), with warnings at 15, 10, 5, 4, 3, 2 and 1 minutes. RSW closes during
replacement, evacuates players, archives the unloaded save and creates a new
seed. A missed deadline is handled once after startup. The reset-duration
message is a 15-minute estimate, not a measured guarantee. RSW access remains
admin-only, unchanged by this update.

Reset state is saved in `config/goldclaim/rsw-reset.json`. Recoverable old saves
are kept under `<level-name>/dimensions/multiworld/rsw-reset-backups/`; monitor
disk space and retain or remove old backups according to your backup policy.
Do not delete the reset journal or move an active dimension while the server
is running. If a reset fails, RSW stays closed and the server logs the cause.

Kit progression remains Week 1 on day 0, Week 2 on day 7, Week 3 on day 14 and
Monthly on day 21, with all kits resetting every 30 days. Offline elapsed time
counts. Eligibility and menu permissions update automatically; complete
rewards are prepared before delivery, and insufficient inventory space does
not consume the claim. The full monthly bundle and its optional-drop chances
are preserved.

`/claim trust all`, `/claim trust interact all` and `/claim trust manager all`
apply to present and future visitors in the current claim. Matching
`/claim untrust ... all` commands disable public access without deleting named
trust. Public access takes precedence over individual untrust. Only the claim
owner can enable or revoke public manager trust; it lets everyone manage or
remove the claim.

With the server stopped, back up the world and configs before updating jars
and configs. Start the server yourself after installation; Paxi config changes
require a full restart, not `/reload`. Verify a PVP death with test items/XP,
a normal-world death, player-plushie crafting, `/kits` and one monthly claim,
the RSW border, and public trust using a second player. Builds and focused
policy tests are checked before release; live death/reward behavior and actual
RSW reset duration still need these in-game checks.
