# Published Artifacts

## GoldClaim Map 1.0.1 - pack v1.1.12

Client-only hotfix: isolate incompatible JourneyMap entrypoints so Waystones'
legacy API plugin cannot prevent GoldClaim Map from initializing. Packwiz
selects `artifacts/goldclaimmap-1.0.1.jar`; GoldClaim stays at 1.0.42.
Existing players restart Minecraft and press Play. No server restart or
deployment is required. See [release details](docs/updates/v1.1.12.md).

SHA256: `c88067141a4c7b731bf676b2dee8b31049552de72e95832f65b6efaf189f5afd`.

## GoldClaim 1.0.42 and GoldClaim Map 1.0.0 - pack v1.1.11

Packwiz's server-only GoldClaim entry and the v1.1.11 stopped-server payload
both select `artifacts/goldclaim-1.0.42.jar`. The new client-only Packwiz entry
`mods/goldclaimmap-direct.pw.toml` selects `artifacts/goldclaimmap-1.0.0.jar`.
See [release details](docs/updates/v1.1.11.md).

GoldClaim 1.0.42 keeps all 1.0.41 behavior. It also sends claim rectangles
and owner names to clients that run GoldClaim Map. GoldClaim Map draws them on
JourneyMap's full-screen map only. Sources: `sources/goldclaim` and
`sources/goldclaimmap`.

GoldClaim SHA256: `5cc35a49a08a95b5eeef6c3e8e2e06e7358bbad35c7162a10dce7f531d54db4a`.
GoldClaim Map SHA256: `850f80ccf18c6930ede144d05eb830f9516d5ea121cf836bd16bbf2324d1e4ae`.

Stage GoldClaim for the next normal restart:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\Stage-GameplayHotfix.ps1 -GameDirectory '<server directory>' -Version 1.0.42
```

## GoldClaim 1.0.41 - pack v1.1.10

Packwiz's server-only GoldClaim entry and the v1.1.10 stopped-server payload
both select this artifact. See [release details](docs/updates/v1.1.10.md).

`artifacts/goldclaim-1.0.41.jar` supersedes 1.0.40 and keeps all 1.0.40
behavior. When Lootr's tag-based lookup returns no replacement, it now
converts unopened plain BetterEnd chests directly to Lootr's chest. If
conversion fails, it opens the original chest instead of blocking it.

SHA256: `fb680c2fd5b807a8e72afe8b1e1967f5bf45a4d07b45a10dea1b02d2b4df9116`.

Stage for the next normal restart:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\Stage-GameplayHotfix.ps1 -GameDirectory '<server directory>' -Version 1.0.41
```

## GoldClaim 1.0.40 - pack v1.1.9

Packwiz's server-only GoldClaim entry and the v1.1.9 stopped-server payload
both select this artifact. Clients retain their existing automatic, client-only
Play workflow. See [release and update details](docs/updates/v1.1.9.md).

`artifacts/goldclaim-1.0.40.jar` supersedes 1.0.39 and retains its kit repairs,
Enderman rollback and seven-city reset gate.

SHA256: `3483ba91e7d89c0151c33e5cc1f31d19d11bc65b3d5cd411e9c956bdbb02b8ee`.

- Future **RSW End resets only** use the persisted Multiworld custom generator
  `gangs:rsw_end`. BetterEnd receives 87.5% of the outer-island biome selection
  weight; other eligible land biomes share the remaining 12.5%. Relative
  weights within each group are preserved. This is a target mix, not an exact
  percentage in every seed or local flight path.
- Nullscape also creates islands in vanilla void/barrens noise regions.
  Those outer regions use the same weighted land map, rather than leaving
  substantial real land vanilla-only. The central island and its existing
  1,024-block boundary, terrain/noise settings, border and other dimensions are
  unchanged. The custom biome source has a registered codec and is selected
  through the saved Multiworld configuration on subsequent starts.
- Installing this jar does **not** switch the current End's generator. Its
  existing `THE_END` configuration remains unchanged until the next reset
  after installation. No early reset or existing-chunk regeneration is needed.
- BetterEnd city chests have valid treasure markers and are already in the
  Lootr conversion tags. Lootr's background conversion waits for a full 5x5
  area of chunks; opening first can consume the deferred loot table and leave
  a shared chest permanently. A server-side chest-opening backstop now converts
  eligible, unopened BetterEnd chests in RSW End before loot is consumed and
  opens the replacement's normal Lootr menu.
- Existing unopened city chests are covered too. Original loot tables, seeds,
  facing, waterlogging and custom names are retained. Lootr's conversion switch,
  dimension/table blacklists and configured border restriction are respected.
  Player storage, nonempty/locked containers, existing Lootr identities and
  chests whose shared loot already generated are not reset or converted.
  Other dimensions retain their existing Lootr behavior.

Validation: 63 unit tests passed. With the installed BetterEnd, Nullscape,
Phantasm, BCLib and patched Biolith versions, outer-island surface-land samples
measured 89.49% BetterEnd (2,478/2,769, seed -6474732897241231737) and 87.92%
(2,410/2,741, seed -8789742013549837216). A full reset generated/saved seven
cities, verified actual purpur blocks in each, and reopened exactly once;
restarting reproduced the persisted biome policy and identical biome samples.
The current End and vanilla End exclusion checks passed.

The city loot regression reproduced shared chest opening on 1.0.39, then
passed all 17 lacugrove chests in the actual modded `ship`, `fat_tower_top` and
`third_floor_2` templates on 1.0.40. It verifies independent populated player
inventories, original tables/seeds, existing Lootr identity, stored/shared
inventory preservation, custom names, exclusions and disabled conversion.

Stage without stopping the live server:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\Stage-GameplayHotfix.ps1 -GameDirectory '<server directory>' -Version 1.0.40
```

The next normal start applies the checksum-verified pending files and backs up
1.0.39 and both reward configurations under `mod-backups/gameplay-1.0.40`.
The launcher backup is `start.bat.bak_before_goldclaim_1.0.40`; its Java command
and continuation retain their exact byte offsets. No client update is required.
Biome dominance begins at the next RSW reset **after that normal start**;
the chest-opening repair begins at that start without requiring a reset.

Live staging is complete and checksum-verified. The running server remained
listening on port 25565 with 1.0.39 active; both reward configurations, the
reset journal and the current End's Multiworld generator configuration were
unchanged. No live restart, reset or existing-chunk edit was performed.

Before a reset has activated the new generator, rollback with the server
stopped by restoring the launcher and backed-up 1.0.39 jar/configurations,
removing the 1.0.40 jar and moving its pending directory aside. After a reset
has saved `gangs:rsw_end`, do not blindly downgrade/remove the codec: retain
1.0.40 or arrange a compatible generator migration first.

## Previous local gameplay hotfix 1.0.39 (superseded)

`artifacts/goldclaim-1.0.39.jar` supersedes 1.0.38. Install it together with the
updated Monthly loot table and Random Collections configuration:

SHA256: `82fa07c65a96f5f2870a08d659f7f04e930c4efee6f8d6b68d86c7ff53f4bb80`.

- Fixes Monthly redemption: `doapi:standard` is not a registered item and caused
  Minecraft to reject the entire loot table, even though its bonus pool only
  rolls occasionally. Stale bow/wand/staff identifiers in the live bonus
  collections also rejected claims. Corrected identifiers retain their weights;
  the unavailable BetterNether ruby staff is replaced by the Netherite fire
  staff because BetterNether is not installed.
- Kit errors now go directly to the claiming player's chat, including when
  SkiesKits runs the command with a silent console source. Full-inventory and
  unavailable claims do not deliver partial rewards or consume the claim.
  Existing claim scores, unlock dates and 30-day cycles are not reset.
- Removes the 1.0.38 Enderman spawn balance, density overrides and natural-spawn
  rarity bypass. Enderman Overhaul's original spawning behavior is restored for
  new spawns; existing entities are not removed or converted.
- Future RSW resets verify and pre-generate **at least seven natural End cities**
  before reopening. The entire city, not just its start, must fit at least 16
  blocks inside the existing 10,000-block border, outside the central
  1,024-block radius and within the dimension's build height. Reserved cities
  are at least 512 blocks apart. Terrain, biome checks, normal loot and
  structure generation remain intact; extra natural cities are not capped, and
  seven ships are not promised. Generation is requested through nonblocking
  chunk futures, one at a time, and saved before the reset journal completes.
  An insufficient count is an explicit logged failure that keeps RSW closed,
  not a successful reset with inaccessible cities. Restarting during
  preparation safely rechecks the saved natural starts.
- Retains 1.0.38's gateway, BetterEnd deferred loot and city-frequency fixes.

The existing playable End is not scanned, replaced or edited by this policy.
The normal reset schedule and border size are unchanged. No client update is
required. This hotfix is not wired into the published 1.1.8 installer, which
still pins GoldClaim 1.0.36; do not run that older installer over this hotfix.

Stage on the live server without stopping it:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\Stage-GameplayHotfix.ps1 -GameDirectory '<server directory>'
```

The staging script leaves the active mod and configurations unchanged. On the
next normal start, the launcher invokes `Install-PendingGameplayHotfix.ps1`,
validates all staged checksums and checks for intervening configuration edits,
backs up 1.0.38 and both original configurations to
`mod-backups/gameplay-1.0.39`, then installs and verifies the three files.
It stops before Java on failure. The launcher's existing Java command and
everything after it retain their exact byte offsets so the currently running
batch file can finish normally. The original launcher is
`start.bat.bak_before_goldclaim_1.0.39`.

Rollback with the server stopped: restore that launcher and the three files in
`mod-backups/gameplay-1.0.39` to their original locations, remove the 1.0.39 jar,
and move the pending hotfix directory aside. Do not run two GoldClaim jars.

Validation uses 60 unit tests, production-remapped smoke fixtures with the
actual installed reward mods and End generation mods, and
`scripts/Test-GameplayHotfix.ps1` for staging, byte-offset preservation,
checksums, rollback backups, idempotence and post-stage configuration changes.
The optional smoke jar is built with `remapSmokeJar`; select one fixture using
`GOLDCLAIM_KIT_SMOKE=true` or `GOLDCLAIM_CITY_SMOKE=true` in an isolated Fabric
server with the test mod and accepted EULA. Never install the smoke jar on the
live server. A passing fixture stops itself and writes
`policy-smoke-passed.txt`.

Verified results: all 60 unit tests passed; all four kits passed real
SkiesKits console-dispatch tests with installed reward mods. The End fixture
verified actual purpur blocks in seven saved cities and confirmed the reset
stays closed until preparation completes, then reopens exactly once.
Live staging is complete: the existing server process, active 1.0.38 jar,
both configurations and reset journal were unchanged. The checksum-verified
hotfix activates on the next normal server start, not immediately.

## Previous local End hotfix 1.0.38 (superseded)

`artifacts/goldclaim-1.0.38.jar` is the server-only End hotfix, superseding the
gateway-only 1.0.37 build. It includes:

- Working unlinked gateways in `multiworld:rsw_the_end`. Vanilla normally creates
  a missing outer-island destination only in `minecraft:the_end`. The fix permits
  that check in RSW End while preserving landing searches, linked return gateways,
  saved destinations, exact teleports, owned Ender Pearls and cooldowns.
- More End City opportunities: BetterEnd's additional four-out-of-five rejection
  is removed. Normal biome eligibility, terrain checks and structure spacing
  remain; this is not a guarantee of a city at every candidate location.
- Deferred loot in naturally generated BetterEnd building chests. BetterEnd's
  temporary chest entity could lose its loot table when template NBT overwrote
  it, notably in foggy-mushroom tree houses. The replacement processor carries
  the table in template-placement NBT so Lootr can convert the final container.
  Explicit tables/seeds are preserved. Loot-bearing BetterEnd barrels retain
  their existing Lootr support; empty decorative barrels are not filled.
- A two-thirds Enderman Overhaul / one-third vanilla Enderman spawn-selection
  balance in eligible End biomes, including RSW End. Only the mod's existing
  End and End Islands variants participate, retaining their biome distribution.
  Competing monsters' relative spawn chances and the distribution of group sizes
  are preserved; no global mob cap is raised. The variants use vanilla Enderman
  density limits, and End Islands Endermen's separate four-out-of-five veto is
  skipped only for natural spawns in vanilla/RSW End. Normal spawn rules and
  Overhaul's disable switches still apply. Actual populations fluctuate with
  terrain, player movement, despawning and configuration; existing mobs are not
  forcibly converted. Non-End biomes are unchanged.

BetterEnd and Enderman Overhaul remain optional. The structure-loot processor
replacement is active only with Lootr installed.

SHA256: `c3b54e5ab6cb00f6f6b6034441ffcd2dea49f42f41da6325195b16ff9114c82e`.

This build was installed on the live server and is superseded by 1.0.39 above.
Its original 1.0.36 backup remains at
`mod-backups/goldclaim-1.0.36.jar.bak_before_1.0.38`.
For other servers, with the server stopped, replace the old GoldClaim jar in
`mods` with this build. Install only one GoldClaim version. Existing gateways without
a destination work on their next use, and the mob balance applies to new spawns
in already-generated chunks. New cities/structure loot require new generation;
the normal RSW reset will supply it. Already-generated containers with lost loot
tables or materialized inventories are not rewritten, and player storage is not
converted. No early reset or client update is needed. Reset timing is unchanged.
This build is not wired into the published 1.1.8 release. The existing
full-release installer still selects 1.0.36 and rewrites the launcher pin, so do
not run it on the live server after this hotfix.

Validation: all 57 unit tests and the build pass. An isolated Fabric server
with Multiworld/Fantasy, BetterEnd, BCLib, patched Biolith, Nullscape, Better
Cities and End's Phantasm verifies a fresh unlinked gateway, player travel to a safe outer-island
landing inside the border, its linked return gateway, cooldown rejection,
NBT-reloaded links, owned Ender Pearls, exact destinations and no automatic
destination generation in the Overworld. With Lootr and Enderman Overhaul added,
the natural-template fixture verifies 11 loot-bearing containers from three real
BetterEnd buildings, preserved tables/seeds, independent inventories for two
players and unchanged player storage. It also finds a viable End City position
under normal biome/terrain checks, verifies the 2:1 balance across 28 End biomes,
samples 30,000 weighted selections, and exercises the actual natural-spawn
predicates 1,000 times per Enderman type. Disabled mod spawn options and the
original non-natural End Islands rarity check are verified separately.

To repeat the gateway smoke test in `sources/goldclaim`, use your accepted
`eula.txt` in `tmp/goldclaim-policy-smoke` and run:

```powershell
$env:GOLDCLAIM_GATEWAY_SMOKE = 'true'
.\gradlew.bat runPolicySmoke "-PsmokePlushieJar=<Saro plushie jar>" "-PsmokeMultiworldJar=<Multiworld jar>" "-PsmokeFantasyJar=<extracted nested Fantasy jar>" "-PsmokeModsDir=<End mods and extracted nested dependencies>" --no-daemon --console=plain
Remove-Item Env:\GOLDCLAIM_GATEWAY_SMOKE
```

For the natural-loot, city and spawn-balance fixture, use
`GOLDCLAIM_END_LOOT_SMOKE=true` instead of `GOLDCLAIM_GATEWAY_SMOKE`. Include
Lootr, Enderman Overhaul, Resourceful Lib/Config, GeckoLib and their nested
dependencies in the smoke-mods directory. Set only one fixture variable at a time.

Each test uses only its isolated world, exits automatically and requires a fresh
passing result marker. It does not start or modify the live server.

## Published builds

`artifacts/goldclaim-1.0.32.jar` is the current server-only build. It rotates the Resetting World (RSW) across three synchronized dimensions (`multiworld:rsw`, `multiworld:rsw_nether`, and `multiworld:rsw_the_end`) with matching seeds, 10,000-block diameter (5,000-block radius) world borders centered at (0, 0), and a 1:1 coordinate scale in the Nether. It disables entering Nether and End portals in the wild Overworld while allowing them to be lit, prevents `/rtp` in the End and Nether, disables `doFireTick` in RSW, and manages player evacuation, unloading, and multi-folder backups during resets. The update is packaged for installation while the server is stopped, not applied to the live server.

`artifacts/goldclaim-1.0.31.jar` is the previous server-only build. It retains the 1.0.30 policies and disables `doFireTick` specifically in RSW when its dimension loads, after every reset, and during world maintenance. Other RSW gamerules, reset timing, saved positions and first-entry safety remain unchanged. The update is packaged for installation while the server is stopped, not applied to the live server.

`artifacts/goldclaim-1.0.30.jar` is the previous server-only build. It retains all 1.0.29 policies and allows untrusted visitors to attack hostile mobs in claims. The exception covers vanilla and modded entities implementing Minecraft's `Monster` interface or registered in `SpawnGroup.MONSTER`; tamed pets remain protected even if their mod uses that spawn group. Passive entities, villagers, decorations, entity interactions, inventories and block permissions remain unchanged.

`artifacts/goldclaim-1.0.29.jar` is the previous server-only build. It retains all 1.0.28 policies and implements public `/trash` as a native 27-slot disposable container. It no longer depends on Essential Commands' `/wastebin` registration order, command node, configuration or permissions.

`artifacts/goldclaim-1.0.28.jar` is the previous server-only build. Its `/trash` alias attempted to invoke Essential Commands' `/wastebin` command node and was not activated on the live server before the 1.1.1 update.

`artifacts/goldclaim-1.0.27.jar` is the previous server-only build. It retains the 1.0.26 chunk-cleanup hotfix, bans the exact `sophisticatedbackpacks:inception_upgrade` item through recipes, commands, item construction/refills and saved/nested item loading, and corrects the crafting exception to the actual `sarosplayerplushiemod:plushie` output. Existing installed Inception upgrades load as empty stacks and are not activated; backpack contents and UUID references remain intact. RSW first-entry safety and exact same-generation returns are unchanged.

`artifacts/goldclaim-1.0.26.jar` is the server-only watchdog hotfix. It retains the 1.0.25 policies below while deferring placed-bin cleanup out of chunk-load callbacks, limiting cleanup to four chunks per world tick, and suppressing neighbor propagation. Use this build instead of 1.0.25 on the server.

`artifacts/goldclaim-1.0.25.jar` is the server-only GoldClaim build used by this pack. It supplies hub protection, dimension routing, PVP-only inventory/XP preservation, a fixed noon clock and clear weather, recipient-specific time synchronization, operator-only PVP block breaking, public PVP placement and terrain-safe combat explosions, public RSW access with border-inset RTP, daily RSW rotation, operator-triggered persisted 15-minute resets, generation-bound exact RSW return positions (including airborne/partial-block positions) with nearest-safe-origin entry after resets, server-side furniture bin removal, Gobber Dragon armor creative-flight suppression, automatic kit eligibility and reward delivery, and public claim trust; clients do not download it.

`artifacts/gangsboots-1.0.7.jar` is a first-party Gangs Boots build with one 2.5% chance per player's first personal Lootr chest loot generation. Reopening saved personal loot does not reroll; shared or automated container fills do not roll. Its required CC-BY 4.0 attribution is in `artifacts/gangsboots-ATTRIBUTION.txt`.

`artifacts/gangscosmetics-2.0.2.jar` is the current Gangs Cosmetics build. It retains the server-authoritative MoreCosmetics wardrobe, bundled immutable models, persistent selections, tradeable keys/vouchers, and GoldClaim kit rewards from 2.0.0. It suppresses the custom nametag renderer for the local player so third-person view does not show a duplicate moving self-nameplate; other players' custom nametags remain unchanged. Weekly kit claims award one key and monthly claims award three. Players receive ownership rather than physical wearable items; duplicate key rolls create a voucher. Its source is maintained in the external `gangs-morecosmetics` project.

`artifacts/polymer-bundled-0.5.20+1.20.1.jar` is the user-supplied official Polymer bundle used on both client and server for server-driven resources.

`artifacts/rankbadges-1.0.2.jar` is a first-party Rank Badges build. Its source is maintained in `sources/rankbadges`.

`artifacts/soulsbackpackscompat-1.1.0.jar` is the current both-sides compatibility mod for Marium's Soulslike Weaponry 1.4.9 and Sophisticated Core 1.2.7.15.166. It retains the 1.0.1 null ability-list repair and adds a guard around Sophisticated Core's capped multiplication helper: zero or negative item max-stack values return zero instead of reaching division by zero. This covers Polymer's client-side `VirtualClientItem` reporting a zero max count while a backpack stack upgrade is active. Backpack contents, upgrades and NBT are not modified. Replace older compatibility jars on both client and server; do not install multiple versions. Its source is maintained in `sources/soulsbackpackscompat`.

Compatibility validation: run `gradle test build` in `sources/soulsbackpackscompat`. For the real-mod smoke test, run `gradle runAbilitySmoke -PsmokeMods=<directory>` with the pack's Soulslike Weaponry, Sophisticated Backpacks/Core and their runtime dependencies (including extracted nested dependency jars). The isolated server-side fixture exits before creating a world and requires a fresh success marker, checking the zero-count guard, mutable per-item recovery, inventory ticking, existing weapon abilities and unchanged backpack NBT.

`artifacts/gangshop-1.0.8.jar` is the server-only first-party Gang Shop build. It provides the shared Gang Bucks wallet and the server shop GUI, including the updated catalog exclusions and Farmer's Delight/Nether's Delight food categorization.

`artifacts/gangsales-1.0.0.jar` is the server-only first-party GangSales build. Its source is maintained in `sources/gangsales`; it requires Gang Shop and uses its Gang Bucks wallet for player-to-player sales.

`artifacts/commandlimits-1.0.0.jar` is a server-only first-party mod that caps expensive `locate` command search radii.

`artifacts/Multiworld-Fabric-1.20.1.jar` is the Multiworld build used by GoldClaim for managed PVP/RSW dimensions. Its portal wand handler is patched to ignore wooden axes so it does not conflict with WorldEdit's wand; Multiworld itself remains installed.

`artifacts/alexscaves-2.1.8.jar` is the custom Fabric build used by this pack. Its corresponding source archive is at `sources/alexscaves-fabric-2.1.8-source.zip`. The upstream Alex's Caves project is LGPL-3.0-only; the source archive and this notice are published with the modified binary to provide the required source access and license notice.

`artifacts/biolith-fabric-1.0.1-beta.1-endfix.jar` keeps Biolith installed on both sides and falls back to the selected End biome when its search tree is uninitialized. Its modified LGPL-3.0 source is at `sources/biolith-fabric-1.0.1-beta.1-endfix-source.zip`, with attribution in `artifacts/biolith-ATTRIBUTION.txt`.

The following user-supplied third-party jars are mirrored here for direct packwiz delivery: Adaptive Tooltips 1.3.0, Archers 1.3.0+1.20.1, AzureLib 2.0.41, Better Combat Particles (bc_particle) 0.0.6, End's Phantasm 1.4, Exposure 1.9.20, Extra RPG Attributes 1.3.2, Immersive Armors 1.7.2+1.20.1, Joy of Painting fabric-1.20.1-2.0.0, Necronomicon 1.6.0+1.20.1, Nullscape 1.2.8, RangedWeaponAPI 1.1.4+1.20.1, Roughly Enough Items 12.1.785, Shield Overhaul 2.0.4, Spellblades and Such 2.4.0+1.20.1, and Wizards 1.4.1+1.20.1.

Other third-party mods remain downloaded from their official Modrinth or CurseForge metadata. This directory must not contain unapproved third-party binary mirrors.