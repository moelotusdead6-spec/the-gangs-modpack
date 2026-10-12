# Published Artifacts

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