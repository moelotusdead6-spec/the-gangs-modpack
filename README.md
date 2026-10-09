# The Gangs Modpack
Welcome to The Gangs Modded Adventure
A hand-picked Fabric 1.20.1 pack built for friends who want unforgettable journeys, shared progression, and nonstop moments worth laughing about later. This pack blends exploration, combat growth, world variety, quality-of-life upgrades, and immersive atmosphere into one multiplayer-focused experience that feels alive from the very first spawn.

Expect expanded structures, dangerous encounters, richer dimensions, and rewarding loot that keep every trip exciting. Base building feels meaningful, travel feels purposeful, and survival stays engaging as your group pushes farther from home. Whether your crew loves boss fights, treasure hunts, building cozy towns, or chaotic “this was a bad idea” expeditions, there is always a new objective waiting.

The heart of this pack is cooperative storytelling. Claim land, gear up together, rescue each other from near-disasters, and turn random detours into legendary side quests. Early game is welcoming for casual sessions, while later progression gives experienced players deeper challenges to overcome as a team.

Every feature is chosen to support group play: smoother team coordination, better adventure flow, and more opportunities for each player to shine in their own way. The result is a world where every login can become a shared memory, from quiet base nights to all-out runs into unknown territory.

This modpack is built around friendship, discovery, and those “you had to be there” moments, The Gangs Modded Adventure is a Tribute to the Best people anyone could ask for! It's time to write our own story one wild session at a time!

## Pack requirements and resource packs

Version 0.1.53 requires Minecraft 1.20.1, Java 17 or newer, and Fabric Loader
0.19.3 or newer.

The pack includes AttributeFix, CorgiLib, Data Anchor, Enhanced Celestials,
Cinematic Respawn (client only), and Marium's Soulslike Weaponry 1.4.9.
RangedWeaponAPI remains at 1.1.4 to preserve compatibility with Archers 1.3.0.
Do not install the supplied RangedWeaponAPI 2.3.4 or Soulslike Weaponry 1.4.10:
their API changes are incompatible with the existing combat mods. The new mods
and Immersive Interfaces are delivered from official Modrinth downloads, pinned
to the supplied versions except for this compatibility downgrade, and verified
by hash.

Immersive Interfaces installs into `resourcepacks/`. Global Packs no longer
requires resource packs, so players can disable them through Options > Resource
Packs. The resource-pack defaults update enables installed file/folder packs
once; subsequent launches remember each player's choices rather than forcing
them back on. It copies legacy `global_packs/required_resources/` packs into
the normal `resourcepacks/` folder so they remain available without being locked.
Datapack requirements are unchanged. Mod-provided built-in packs retain their
existing selection; shader packs and server-sent packs are separate.

For an existing client, close Minecraft and run the update package's
`Install-ResourcePackDefaults.ps1 -Target Client -GameDirectory <minecraft-folder>`.
This preserves unrelated options and seeds the actual game-root `options.txt`,
not just the supplied `config/options.txt` template. Do not overwrite an existing
game-root `options.txt` with the template. The installer records completion so
running it again does not re-enable packs a player has disabled.

For the server, run the same installer with `-Target Server` during your later
scheduled stop, then restart normally. It unlocks only the Global Packs resource
selection and clears `require-resource-pack` if present in `server.properties`;
it preserves datapack settings and unrelated server properties. Applying this
update does not require changing mods, worlds, player data or the reset journal.
A server restart alone cannot update players' local resource-pack selection;
each client must also apply the client update and relaunch Minecraft.

Previously published ZIP archives are historical snapshots. The original 0.1.50
all-in-one ZIP still locks resource packs. Version 0.1.51 includes unlocked
resource packs, actual game-root options seeded once for new instances, and
client/server update installers. Older clients should close Minecraft and run
`client-update/Install-ClientUpdate.ps1 -GameDirectory <minecraft-folder>`.

The v0.1.53 all-in-one release includes Soulslike Backpacks Compatibility in
the client mods and server update folders. The server JAR may be staged in the
server's `mods/` folder while it is running and loads on the next normal
restart. Clients must also install the update and relaunch because the reported
crashes occurred in the client inventory tick.

### Version 0.1.53

The observed trigger was Xander's replacement
`sophisticatedbackpacks:netherite_backpack`; Xander's other backpacks continue
to work. All Sophisticated Backpack tiers share the same item class, so
Soulslike Backpacks Compatibility guards that shared class against a null
ability list without changing backpack contents, upgrades or NBT.

### Version 0.1.52

Soulslike Backpacks Compatibility prevents a client or server crash when a
Sophisticated Backpack is equipped, opened or carried in player inventory.
Marium's Soulslike Weaponry 1.4.9 remains pinned for compatibility with the
pack's combat mods; the compatibility mod supplies the empty ability list that
its inventory hook expects from backpack items.

### Version 0.1.51

Nature's Compass and Explorer's Compass are removed from both client and server.
Existing installations must remove their jars; importing the new Prism ZIP
creates an instance without them. Compass2Map and vanilla compasses remain.

GoldClaim 1.0.27 bans `sophisticatedbackpacks:inception_upgrade`, not backpacks
or other upgrades. Its recipe is disabled in Sophisticated Core and the supplied
datapack; server guards also reject commands and empty newly created, refilled,
saved and nested banned stacks, including loot and creative acquisition.
Installed Inception upgrades become empty when their upgrade inventory loads,
so they cannot activate nesting. Backpack inventory items, stored child
backpacks and UUID references are preserved. Remove any nested child backpacks
before updating if you want immediate access to them after nesting is disabled.
Offline player files and unloaded backpack storage are not rewritten.

Saro's actual player plushie (`sarosplayerplushiemod:plushie`) is craftable again.
The old exception used an incorrect item ID. All other plushie and plush-box
recipes remain removed; non-crafting availability of other plushies is unchanged.

RSW behavior is unchanged and verified: first entry after each reset needs safe
ground; same-generation remembered returns do not need a safe-block check.
Focused tests cover location persistence, resets and nearest-origin searching.

The server policy defaults retain `view-distance=6` and `simulation-distance=6`.
With the server stopped, run the supplied installer:
`powershell.exe -NoProfile -ExecutionPolicy Bypass -File server-update/Install-ServerUpdate.ps1 -GameDirectory <server-folder>`.
It backs up affected files, removes both compass
jars and old GoldClaim jars, installs 1.0.27, updates GoldClaim filename pins in
`start.bat`, merges only the Inception/config/resource-pack/distance policy
settings, and preserves worlds, player data, claims, homes and the RSW journal.
It does not start the server. Keep the backup until in-game checks pass.

The source installers are maintained in `scripts/`. The isolated runtime smoke
test uses synthetic restricted items and the real Saro mod to verify mixed-in
item creation, refills, NBT loads, nested storage, command rejection and recipe
filtering without starting the live server:
`gradlew.bat runPolicySmoke -PsmokePlushieJar=<path-to-Saro-jar>` from
`sources/goldclaim`. First copy your already accepted Minecraft `eula.txt` into
`tmp/goldclaim-policy-smoke/`. The task configures a loopback-only temporary
server with an ephemeral port and stops itself. A successful run writes
`tmp/goldclaim-policy-smoke/policy-smoke-passed.txt`; an old result is cleared
before each run.

### Version 0.1.50

This release includes GoldClaim 1.0.25 for exact remembered RSW positions and
Gang Shop 1.0.8 for catalog filtering and food categorization. Farmer's Delight
crates, bales and feast foods, plus Nether's Delight raw stuffed hoglin, are
categorized as food unless denied by the price config. Ancient debris,
Chipped ancient-debris variants, selected Hybrid Aquatic utility/pearl items,
and the Alex's Caves and Alloy Forgery namespaces are excluded from the shop.
Both updated mods are server-only and bundled in the ZIP's `server-update`
folder, not the Prism client mods folder.

## Gameplay update and server checks

Boots of Swiftness have one independent 2.5% chance per player's first personal
Lootr chest loot generation. Reopening saved personal loot does not reroll.
Shared or automated chest fills do not roll. Already-generated loot is unchanged.
On the server, plushie and plush-box recipes are removed except for the player
plushie. Existing items, loot, shops and kit rewards remain available.
The Let's Do Furniture bin (`furniture:bin`) is separately banned server-wide:
its recipes and drops are removed, item commands are rejected, and bin stacks
cannot be acquired or refilled, including through creative inventory packets.
Existing bins disappear when inventory/container data or chunks load, including
nested container items; placed bins are removed without drops. Other furniture
is unchanged. This is server-only enforcement: clients may still display the
bin in creative menus or recipe viewers, but cannot use it on the server.
Offline player files and unloaded regions are not rewritten.
GoldClaim 1.0.26 fixes a watchdog crash during player joins: placed-bin cleanup
is deferred until the end of a world tick, processes at most four chunks per
world per tick, and does not propagate block updates into neighboring chunks.
Unloaded chunks are removed from the cleanup queue. The bin ban remains enforced.

Gobber's unlimited Dragon armor flight is disabled. End armor gliding,
other armor perks, creative/spectator flight, and fueled Gang Boots flight
are unchanged. `config/gobber2/general.json5` sets `enableDragonFlying` false
and retains `enableGlidingEndArmor`; GoldClaim also disables the armor's
flight grant even if an existing server config still enables it. Existing
Gangs Boots enforcement clears stale survival flight without bypassing fuel.
PVP deaths retain inventory and XP, skip Universal Graves capture, and respawn
at the hub. Death rules in other dimensions are unchanged. PVP's world clock
always reports noon (6000 ticks), independently of later stored-time writes.
Its daylight cycle is disabled without repeating time commands or time resets;
other dimensions keep their normal clocks.
Existing outbound time updates to PVP players are normalized to frozen noon,
without adding time broadcasts. PVP weather is set clear once and its weather
cycle is disabled, so rain and thunderstorms cannot darken or flash the arena
sky. These settings affect only PVP.

In PVP, only operators can break blocks, while all players can place blocks and
use combat items. Explosions, including TNT, respawn anchors and end crystals,
still damage entities and apply knockback but do not destroy blocks or create
blast fires. Explosive items still consume themselves normally. These rules
also protect player-placed blocks, so operators must clean up arena building.
Other dimensions retain their existing claim and explosion rules.

RSW has a visible border centered at 0,0 with edges at +/-5000 and a 50-block
warning distance. It resets daily at 05:00 fixed EST (10:00 UTC, without a DST
adjustment), with warnings at 15, 10, 5, 4, 3, 2 and 1 minutes. RSW closes during
replacement, evacuates players, archives the unloaded save and creates a new
seed. A missed deadline is handled once after startup. The reset-duration
message is a 15-minute estimate, not a measured guarantee. `/rsw` is public.
Operators (permission level 2+) and the console can run `/rsw reset` to start
the same 15-minute warning countdown and normal reset/recovery flow. Manual
countdowns survive restarts. A duplicate request, active reset/recovery, or
already-earlier daily reset is rejected without postponing the current reset.
After completion the normal 05:00 EST daily schedule resumes.

`/rsw` returns to your exact last position in the current RSW, including its
orientation. Locations are captured when leaving, disconnecting, respawning,
or stopping the server. Running `/rsw` inside RSW keeps your current position.
Every successful automatic or manual reset clears all return positions,
including offline players' saved positions. Evacuated players remain in the
hub; their next `/rsw` tries safe ground at 0,0. If that column is unsafe,
it uses the nearest safe surface by horizontal distance without modifying
terrain. Remembered positions do not use the random-teleport ground/air check:
leaving while airborne or on partial blocks still returns to that exact position.
If terrain has changed, the saved position is still used; positions outside the
current world border or build limits report an error instead of redirecting to 0,0.
The search is bounded by `rswEntrySearchRadius` in `config/goldclaim.json`
(default 256 blocks, supported range 0-512); if no safe landing is found,
the command reports an error rather than sending the player into danger.
Unloaded terrain is prepared asynchronously, with a bounded number of
columns checked per tick. Pending entry is cancelled on reset, death, logout,
or changing worlds rather than teleporting into an obsolete generation.
Using `/rtp` in RSW stays in RSW, at least 250 blocks inside its actual border;
using it in the wild stays in the configured wild world. RSW RTP is unavailable
during a reset rather than redirecting players to another world.

Reset state, manual deadlines and current-generation return positions are saved
atomically in `config/goldclaim/rsw-reset.json`. Recoverable old saves
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

### Installing and checking this server policy update

GoldClaim 1.0.27 and the current packwiz manifest contain these policies;
existing ZIP releases do not receive changes retroactively. With the server
stopped, back up worlds/player data and configs, replace the old GoldClaim jar
with `artifacts/goldclaim-1.0.27.jar` (do not leave two GoldClaim versions
installed), and apply the updated configs/datapack. Keep any unrelated custom
Gobber settings when applying its two flight/gliding settings. No client mod
update is required for the server guards.

Focused Gradle tests and an isolated Minecraft/Fabric fixture verify the
runtime item/block/recipe guards, loaded-chunk cleanup, Gobber armor flight
suppression, retained gliding and Gang Boots flight/fuel, public `/rsw`,
operator-only reset, remembered positions, a complete accelerated reset,
and post-reset entry. The live server is not started or modified by these tests.
On the full server, check:

- A non-operator cannot use `/rsw reset`; an operator starts the 15-minute
  countdown, with warnings at the normal thresholds.
- Restart during that countdown and verify its original deadline survives.
- Leave RSW through a command/portal, reconnect, and return to the exact same
  location, including airborne or partial-block positions. After a reset,
  both online and previously offline players start
  at the origin or its nearest safe surface, not old coordinates.
- Existing bins in player/ender inventories, nested storage, item entities,
  and loaded chunks disappear. Crafting, `/give`, `/item`, `/setblock`,
  creative packets, structures and loot cannot create usable bins.
- Full Gobber Dragon armor provides no creative flight. End gliding and
  Gang Boots still work, and flying with Gang Boots consumes durability.
