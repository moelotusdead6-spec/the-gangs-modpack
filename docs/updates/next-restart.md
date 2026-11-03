# Neon Wall Wonders 11.0.0 prepared (2026-11-03)

The original CreativeMode download `neon-wall-wonders-gwbzt3mk.jar` is
version **11.0.0**, SHA-256
`0c9fcc4a8d8468b477f11214efdf8220bfd3e3402b7d56478caeda84efc26f29`.
Its 87 item models include all 65 in the pack's version 8.0.0 jar plus 22
new IDs. The existing weekly and monthly neon kit tag contains all 87; none
are added to the Gang Shop. The local shop deny list covers all 87 and ten
additional IDs found in version 9.

**Version 9 compatibility:** A separate version 9 download has ten neon IDs
that are absent from version 11: `neon_adventurer_backpack`, `neon_campfire`,
`neon_community_emblem`, `neon_enchanted_forest`, `neon_explorer_compass`,
`neon_moonlit_mountains`, `neon_rustic_cabin`, `neon_sunset_trail_marker`,
`neon_treasure_chest`, and `neon_wilderness_lantern`. If version 9 was ever
used on a server or in a world, those blocks and items may be lost on upgrade.
Back up the world and resolve their absence in the upstream mod before
loading that world with version 11.

This update is prepared locally only: the running server, its startup
selection and the public Packwiz manifest were not changed. At a future
coordinated maintenance window, back up the world and live shop configuration,
verify the installed Neon version, and install the new jar, kit tag and
shop-deny entries together before starting. Merge the neon deny entries into
the current live shop configuration rather than replacing unrelated settings.
Keep only one Neon Wall Wonders jar on the server and each client; clients and
server need the same mod version. Do not publish the client update before the
server is ready for it.

The historical all-in-one builder and its server installer still target
GoldClaim 1.0.44 and older bundled mods. The builder now refuses to create a
current release ZIP, and the installer refuses to downgrade newer GoldClaim
versions. Neither is a deployment method for this update.

# GoldClaim claim-permission update prepared (2026-11-03)

GoldClaim 1.0.64 was built with stricter claim interaction protection:
interact trust can use controls such as levers, buttons, elevators and
pressure plates, while containers and machines including composters remain
owner/full-trust only. The new `/claim trustlist` command shows trusted
players and their permission level in the claim where it is run. The new
`/claimblocks` command reports used and remaining claim blocks for the current
dimension. Direct item placement and bucket use are also checked at claim
boundaries. This update does not add protection against cross-claim piston
movement, fluid flow, hoppers, or other automated changes.

The local artifact is
`artifacts/goldclaim-1.0.64.jar` (SHA-256
`bfed5f9a22681b235d792d6809b4f59c103547a3a6ded5218986b6bde77e7b8b`).
Packwiz metadata points at this checksum-pinned artifact. The historical
1.0.63 artifact was restored from the checksum-verified
`gangs-neon-loot-restart-1.0.64.jar` payload; live files were not re-read.
This update was not installed or activated on the live server.

Before a future coordinated restart, check the selected server startup
launcher as well as the active mod and configuration files. The previously
selected Neon loot launcher expects GoldClaim 1.0.63 and its earlier kit
files; it will refuse an unexpected GoldClaim 1.0.64 jar or changed kit
files. Use a verified deployment and startup selection instead, with exactly
one GoldClaim jar, after backing up the current server files.

## Fiw join allowance and crash recovery (2026-11-01)

Fiw's server-side `timeout_seconds` is now **120**, increased from 60.
This gives slower clients more time to submit the required verification report;
it does not disable verification or require a client update. A longer timeout
does not guarantee that a slow or incompatible client can join.

The server crashed with `OutOfMemoryError: Java heap space` at its 9 GiB heap
limit after sustained lag. The crash report identifies where the error was
observed, not the allocation or mod that exhausted the heap. The memory
problem remains unproven and may recur; the Fiw timeout change is not a fix
for it. After a later independent start and stop, the server logged that all
dimensions were saved; this does not prove that all pre-crash progress survived.

With the server stopped, the existing Fiw configuration was backed up to
`backups/fiw-timeout-120-20261101/config.json` (SHA-256
`a439ded292076820dea603bb700b2ce2cb3a655db303ed6a9c4ce6c01bb6cea4`).
Only the timeout value was changed; the installed file's SHA-256 is
`c481fb4576afb643d1079f6b8ac488e991089915124e9df27b84fc5f49f85512`.
The original `gangs-neon-loot-restart-1.0.64.jar` launcher remains selected.
The approved recovery start reached `Done`, Fiw loaded, and the Minecraft
status handshake succeeded with players reconnecting.

A console-only Spark health snapshot at 12:52 reported 20 TPS, 3.9 GB used
of the 9.0 GB Java heap, and 3.6 GB in old generation after the last GC.
The panel's roughly 10.2 GiB container-memory figure is not the same as
used Java heap. This snapshot does not establish whether memory will grow
again. No heap dump or remote profile was created.

One successful Fiw verification produced 19 Teleport Optimizer
`requestTeleport` preload messages at the same position in eight seconds.
The optimizer can skip or deduplicate preload work but logs the message
regardless, so these messages alone do not prove repeated chunk loading or
identify the cause of the heap exhaustion. No optimizer change was deployed.

To roll back, stop the server and change only `timeout_seconds` back to 60.
Use the backup for reference; restoring the entire old file could overwrite
later exemption or monitoring changes. No experimental client scanning
modification was deployed.

## Neon loot rewards live (2026-10-31)

The 1.0.63 launcher failed safely before applying anything because the live
monthly kit table had been edited since staging (`File changed since staging`).

`gangs-neon-loot-restart-1.0.64.jar` (SHA-256
`ddae35da627805be68569a955d629fa2b921ce6e99e0e753bac5f3925ab398ca`) replaced
it. It keeps every existing monthly pool, preserves the file's UTF-8 BOM,
appends the two-roll neon pool, and refuses malformed JSON before writing.
Tests include a dry run against the exact live monthly table (10 pools
preserved, neon pool added).

It was uploaded (hash read back), selected as `SERVER_JARFILE`, and the server
was started. The installer applied: GoldClaim 1.0.63 is the only active
GoldClaim, the neon tag and all kit tables are installed, and the previous
files are in `backups/neon-loot-restart-1.0.64/` (monthly backup
`966da7af…8cf7`). The server reached `Done` and players joined.

Live now: one random neon sign per weekly kit, two per monthly kit, and Neon
Fox / Neon Owl each at a 0.5% independent chance per Lootr chest generation.
Neon signs remain denied in the Gang Shop. The launcher is idempotent, so it
can stay selected; rollback restores the files in the backup directory.
# Prepared next-restart update

## Neon Wall Wonders staged (2026-10-31)

The original `neon-wall-wonders-gwbzt3mk (10).jar` (mod version **8.0.0**)
is uploaded to the hosted server's `mods/` directory. Server download
readback verified SHA-256
`a0b36248530bfcbc398167b8cb7cade47d494053167822f33d2abe17d42158cc`.
It loads on the next normal restart. No power action, reload or console
command was sent, and the existing `gangs-gameplay-restart-1.0.62.jar`
startup selection was not changed.

The matching **v1.1.17** client update is published through Packwiz.
Close Minecraft and press Play in the same auto-updating Prism instance
before rejoining after restart. Manual update, attribution and rollback:
[Neon Wall Wonders update](v1.1.17.md).

## Player completion, sponges, carver borders and End libraries (2026-10-30)

The prepared launcher is `gangs-gameplay-restart-1.0.62.jar`.
Host upload SHA-256 `819df708acd9b85551c86d11739a242f252da8ba37981eb8a5762373113c7237`
and persisted `SERVER_JARFILE` selection were read back and verified.
The server remains running on its existing installation; no restart, stop,
Minecraft reload or console command was sent. The update applies on the
owner's next normal restart.
It upgrades the currently installed GoldClaim 1.0.61, BCLib 3.0.14 and
Better End 4.0.11 to **GoldClaim 1.0.62, BCLib 3.0.16 and Better End 4.0.12**.
It also updates only the two UUID-specific Fiw exemptions for **Oux_y**
and **Rootamiss**. Other players still undergo verification; claims, banned
items, world borders and ordinary permissions remain enforced for everyone.
AntiXray's existing configuration is unchanged.

- Player-name word arguments without their own completion provider now
  suggest online players, including claim admin commands and their aliases.
  Prefix matching is case-insensitive. Existing command-specific suggestions,
  entity selectors, player profiles and home-name suggestions are preserved.
- Sponge absorption budgets double from 64 to 128 water blocks **per pass**,
  retaining vanilla reach, waterlogging handling, dry/wet conversion and
  neighbor updates. Vanilla can trigger additional passes during placement;
  the isolated per-pass baseline and new limit were both measured.
- Alex's Mobs' shattered dimensional carver portal resolves its destination
  inside the destination world's current border with a three-block safety
  margin. This applies before destination loading, including saved portals
  and portals whose border shrinks later. Ordinary dimensional carvers,
  vertical destinations and unrelated teleport commands are unchanged.

The installer verifies the original Fabric launcher, all three current mod
hashes and the exact existing Fiw configuration before writing anything.
It refuses an active world lock, unexpected duplicate mod versions or
changed inputs, backs up originals to `backups/gameplay-restart-1.0.62/`,
and is safe to repeat or resume after an interrupted installation.
It does not replace shop prices, recipes, rewards, terrain or other configs.
The prior GoldClaim fixes remain byte-identical except for the shared border
helper's public visibility and the three new mixins.

Production-mapped integration tests pass with the actual updated libraries,
Alex's Mobs 2.0.12 and Fiw 2.2.0: all claim completion paths, mixed-case
prefixes, unchanged home suggestions, baseline/new absorption counts,
wet/dry behavior, million-block portal exits in all four directions,
shifted/shrinking borders, unchanged ordinary portals and UUID-only bypasses.
The restart installer regression tests also pass, including exact backups,
idempotency, interrupted recovery, world-lock and changed-file rejection.
GoldClaim continues compiling against the pinned older compatible End APIs;
the new upstream jars require a newer Loom toolchain for development remapping,
so compatibility was validated with their unmodified production jars instead.

**Clients must update both End libraries before rejoining after restart.**
Use `artifacts/The-Gangs-End-Client-Update-20261030.zip`: close Minecraft,
replace the two old jars with the supplied new jars, and keep only one
version of each enabled. GoldClaim is server-only and is not in that ZIP.
The pack metadata is updated locally; this is not a published pack release.

Rollback before restart: select
`gangs-alex-weapon-restart-1.0.61-mining-20261029.jar` again.
After installation, stop the server, restore the three original mod jars and
Fiw configuration from `backups/gameplay-restart-1.0.62/`, remove the three
new mod jars, and restore the old launcher selection. Clients must likewise
restore their old BCLib/Better End pair when rolling the server back.

## Pending-login damage protection prepared locally (2026-10-30)

The live log recorded a player suffocating before the automatic hub transfer
completed. GoldClaim now blocks damage while that transfer is pending, covering
Fiw verification and the settling delay. Existing hub invulnerability and PvP
rules are unchanged. The change and focused tests are local only; it has not
been built into the active host jar or deployed.

## Chipped material removal and near-instant SELL caps (2026-10-29)

The owner selected all Chipped copper, iron, gold, diamond, emerald and
netherite/ancient-debris material variants, including raw/storage blocks and
iron bars. 389 explicit Chipped IDs are denied shop-wide, so neither the
category UI nor search can bypass the removal. Ordinary vanilla materials
and raw storage blocks remain available unless previously denied separately.
Material families come from installed Chipped tags plus material-specific ID
patterns. Diamond-patterned wood/glass and other unrelated decorative shapes
are not diamond resources and are retained.

The confirmed mining rule is **diamond tools, no enchantments or Haste,
at most 0.5 seconds (10 game ticks)**. An isolated local registry measurement
covered 15,825 block items using registered hardness, tool mining speed and
harvest suitability. All Chipped listings were measured. The best of bare
hand and diamond pickaxe/axe/shovel/hoe/sword was used, standing on the ground
out of water, using each block's default state. This includes stone (6 ticks),
cobblestone/logs (8), grass (3) and netherrack (2); deepslate (12) and obsidian
(188) do not qualify directly.

11,908 eligible, non-denied, positive-price stored entries have literal SELL 1
overrides. Dependent recipes propagate reductions, changing 12,273 SELL prices
in total. BUY prices and all existing zero-price entries are preserved.
The live pre-edit snapshot contained four owner changes since the prior
deployment, including netherrack BUY 10 / SELL 1; those BUY edits are retained.
No SELL price increases. Non-block items are not classified as mineable blocks.

The owner approved a whole-dollar batch exception: if ingredient SELL cost
would force an output below 1, all outputs may remain at the minimum 1.
There are 1,202 such modeled recipes. Otherwise the combined output SELL
value cannot exceed ingredient SELL cost. All 16,898 fully priced recipes
were independently checked, including that output SELL value does not exceed
ingredient BUY cost. Reductions cannot introduce a new purchase/resale profit
route because every BUY is preserved and every SELL is unchanged or lower.
This is not a guarantee against unrelated mod mechanics or accepted trades.

`Apply-ShopMiningRules.py` generates the explicit denies/caps from installed
resources and measured properties. Use `Balance-ShopPrices.py --sell-percent
60 --sell-caps <generated-caps.json> --previous-sell-report <previous-report.json>
--allow-batch-floor` with the usual resource/condition/reference arguments to
propagate only downward. The batch relaxation is opt-in and permits excess
output value only when every output is exactly SELL 1.
`moddedBlocksSellMultiplier` is explicitly 1: the shop's default multiplier
of 2 applies to item overrides and would otherwise turn literal SELL 1 into
SELL 2 for modded blocks. Existing stored non-overridden prices bypass this
multiplier and remain unchanged except for the calculated reductions.

New restrictions SHA-256:
`72361b4129384752aa5f321eb1b54c68a7e4cf3a8c1153587d49481a420635f1`.
New formatted prices SHA-256:
`45f577dee51aeaff47f9183e675642a0ed03950e19d2004d97f7b87cafe28da5`;
canonical prices SHA-256:
`330ece1876167b215d18b29a85325b723e9b0d11e04c3affbe69d73c837e7015`.
Backup filenames:
`config/gangshop/overrides.pre-mining-20261029.json` and
`config/gangshop/prices.pre-mining-20261029.json`.

The matching launcher is
`gangs-alex-weapon-restart-1.0.61-mining-20261029.jar`, SHA-256
`bc442669bf18bf3a56c175de53af26d78429e92737de7ad3753025f81f91f75d`.
Compared with the original 1.0.61 artifact, only its embedded shop overrides
and payload hash manifest differ; executable classes, event rewards and all
other payloads are byte-identical. Installer regression tests pass. Selecting
this matching launcher is necessary because the original verifies the old
shop-override hash even on an already-applied installation. Do not select the
old launcher with new overrides; rollback requires restoring both the old
override backup and old launcher selection together.

Deployment is live and verified: shop-only reload logged at 17:04:11; read-only
price checks confirmed SELL 1 for stone, netherrack, grass, oak logs and a
Chipped barrel, with anvil unchanged at BUY 1,005 / SELL 279. All 11,908 caps,
389 added denies, the complete canonical price hash, existing bans/category
settings and matching startup selection were checked. Live JSON formatting
was rewritten by the shop; its raw price SHA-256 is
`9fa91f4198459c5dc59d0df21cc07b28e610d73cc28ae96df589a6eed12fce6f`.
33 pricing regressions and the installer regression suite passed. No power
action or Minecraft `/reload` was sent; server uptime continued increasing.
Only the two temporary config uploads were removed; backups and the selected
launcher remain on the host. The server had already completed its owner's
normal 1.0.61 restart before this task began.

## Per-player event rewards: GoldClaim 1.0.61 launcher (2026-10-29)

Panel `SERVER_JARFILE` now selects `gangs-alex-weapon-restart-1.0.61.jar`
(SHA-256 `39a11b0a753b1c7b86999c45980b09a2d7e8867d54179091d40a1b7524864249`,
uploaded and read back from the host). It supersedes
`gangs-alex-weapon-restart-1.0.60-shop-20261029.jar` and ships everything
that launcher did (TPA fixes, shop overrides `8c43bfac…b70b`, recipe tags, cave
configs). The only payload differences:

- `mods/goldclaim-1.0.61.jar` (`b1347eaf…2211`) replaces 1.0.60; class diff
  against 1.0.60 is limited to `KitService` and the `gangskit event` command.
- New `config/paxi/datapacks/gangs_kits/data/gangs/loot_tables/events/reward.json`
  (`b04611a3…65eb`).
- `config/randomcollections/config.json` (`1c13a133…aa5d`) adds the
  `event_bow_staff` and `event_trinket_gem_totem` pools; the original host file
  (`5d243ca3…cc12`) is saved to `backups/alex-weapon-restart-1.0.61/`.

The installer writes these two event files even though 1.0.59 is already
applied; everything else must already match, exactly as before. Live shop
`prices.json` edits are not part of the payload, so `/gangshop reload` work is
unaffected. Installer regression tests pass, including changed-collections and
conflicting-event-table rejection.

Use `/gangskit event @a` (op level 2). Each player gets their own roll, using
`SecureRandom`, of the monthly kit contents plus 10–12 smelted Mythic Metals
ingots, 3 extra plush boxes, 1 extra golden plush box (2 total) and a 10%
chance at one of the three gang swords. Do not use `/loot give @a` for events:
it rolls once and gives everyone identical loot.

Rollback before restart: set `SERVER_JARFILE` back to
`gangs-alex-weapon-restart-1.0.60-shop-20261029.jar`. After restart: restore
`goldclaim-1.0.59.jar` and `config/randomcollections/config.json` from
`backups/alex-weapon-restart-1.0.61/` and delete `goldclaim-1.0.61.jar`.

## SELL payout flow correction (2026-10-29, after BUY correction)

The owner selected **60% of BUY, capped by ingredient SELL values**.
This supersedes the unchanged-SELL policy of the BUY-only step below.
Targets round down to whole dollars, retaining the existing minimum SELL 1.
The existing integer recipe solver now maximizes SELL toward these hard caps
without changing BUY, enabling disabled entries or modifying removed
Galosphere entries. Every modeled recipe's combined guaranteed output SELL
value must not exceed its cheapest ingredient SELL cost, including alternative
tags, batch yields, reversible conversions and multiple guaranteed outputs.
This also bounds multi-step crafting resale without repeatedly adding premiums.

13,337 stored SELL entries change (13,336 increases, one decrease); no BUY
price changes. Anvil is **BUY 1,005 / SELL 279**: its 60% target is 603, but
its ingredients sell for `3 * 81 + 4 * 9 = 279`, so the ingredient cap applies.
Iron ingots remain SELL 9 and iron blocks SELL 81 because the reversible
nugget recipes constrain their whole-dollar resale ratios. These are ceilings,
not a promise that every finished item pays exactly 60% of BUY.

Validation: 27 regression tests pass. The integer optimization reached the
exact optimum (zero gap); an independent numeric check passed all 16,898
fully valued recipe constraints and percentage ceilings. 589 recipes lack
complete priced inputs/outputs and 568 resource records are unsupported or
inactive, so those paths remain explicitly outside certification.

Generate from the BUY-only catalog using
`--sell-percent 60 --prices <premium-prices.json>
--buy-reference-report <premium-report.json> --resources <resources.json>
--conditions <conditions.json> --output <prices.json> --report <sell-report.json>`.
Generated SHA-256:
`af3719f0bfe279c7d9ced8dd43e012f2283e7c44641a83d0360ccd48f2ac4288`;
canonical JSON SHA-256:
`a789369f501f871b56f9e1c977d70d49b90d26975ad798f1be17579583a80d42`.
Live deployment backs up the BUY-only catalog to
`config/gangshop/prices.pre-sell-flow-60-20261029.json` and uses only
`gangshop reload`. Restrictions and category buying settings are unchanged.
Live verification confirmed the reload at 15:49:16, canonical catalog hash,
read-only in-memory anvil/iron/log prices and continuously increasing uptime.
The shop rewrote JSON formatting; live raw SHA-256 is
`147c302b360631634edda04b4830457d31522c15d0a715169cf5fb5962c8e12d`.
The temporary upload was removed, both price-step backups retained, and the
selected next-restart launcher is unchanged.
The three already-identified multiplication recipes still require the next
normal restart to activate their staged fixes. Villager trades remain
intentionally allowed; this is not a claim that every money exploit is closed.

## Crafted BUY pricing correction (2026-10-29, supersedes initial pricing rule)

The owner requested ingredient BUY cost plus 20%, replacing the earlier
crafted-price ceiling. `Balance-ShopPrices.py --crafted-premium` applies this
to the cheapest modeled recipe, including tag alternatives, ingredient counts,
batch yields and upstream crafted BUY prices. Integer prices round upward.
Existing BUY prices are floors; SELL payouts and disabled BUY entries are
unchanged. Thus this correction cannot make an existing shop purchase/resale
route more profitable. METALS and FOODS buying remain disabled.

The owner approved an exception for reversible material conversions: preserve
equal material value, rather than charging 20% in both directions. Only exact
inverse single-ingredient conversions with matching baseline material values
are grouped, including compression/unpacking and interchangeable variants.
Their cheapest external manufacturing route receives the premium once; all
members retain their material ratios, using whole-dollar material units.
Rounding that shared unit can exceed 20%. Tool recycling is not an
equivalence exception. Closed crafting cycles with no obtainable entry remain
baseline-priced primary market sources and are identified in the report.

The correction changes 5,987 stored BUY entries. Anvil BUY is now 1,005:
`(3 * 243 + 4 * 27) * 1.20 = 1004.4`, rounded up. SELL remains 12.
The earlier 670 example used the old iron BUY prices; iron itself has now
been repriced from its processing ingredients. Other examples: oak planks
BUY 6, oak button BUY 5 (the installed woodcutter makes five from a log),
and piston BUY 119. Removed listings are not re-enabled.

Validation: 21 regression tests, an independent check of 7,298 priced entry
components, and 16,898 fully valued recipe SELL constraints passed. No SELL
payout changed and no BUY price decreased. The scan modeled 16,872 recipes
for this correction; 568 unsupported/inactive records and 615 recipes with
missing ingredient values or multiple guaranteed outputs remain explicitly
reported rather than assigned speculative prices. There are 81 unrooted
market-source groups (96 item/reference IDs); these are not certified
manufacturing paths. This is not a guarantee against every modded exploit.

The generated price file SHA-256 is
`87725862ea48759a96723af7c589b5548ce339e7ecf32a588bdaddc55ebc5344`;
canonical JSON SHA-256 is
`9332bb859a5733b29d1796400e29473fa40c93abde33917d2436bdd3931aa96e`.
Reproduction requires the immutable pre-premium price snapshot, the extracted
installed resources, condition flags and original solver report. Use
`--crafted-premium --prices <pre-premium-prices.json> --resources <resources.json>
--conditions <conditions.json> --baseline-report <original-report.json>
--output <prices.json> --report <premium-report.json>`.
Do not use the already-premium catalog as a fresh baseline: that compounds
market-source floors and material-unit rounding on subsequent runs.

Live deployment backs up the old catalog to
`config/gangshop/prices.pre-crafted-premium-20261029.json` and activates only
with `gangshop reload`, never Minecraft `/reload` or a power action.
Prices are an external configuration file, not embedded in the selected
restart launcher, so that launcher does not need rebuilding for this change.
The three recipe-loop overrides still wait for the next normal restart.

## Live shop exploit restrictions (2026-10-29, after pricing audit)

The owner approved removing all Fortune-sensitive ore listings, confirmed
profitable block-drop source listings, and the four tall flowers. Villager
trades remain intentionally allowed. Raw-metal storage blocks are not ores
and are unchanged. Shop restrictions are not world-item or crafting bans.

The updated `denyItems` covers 87 Fortune-sensitive ore IDs verified against
the installed vanilla/mod loot tables (including IDs already denied), nine
source blocks and four flowers: 100 selected IDs total. Source blocks are
amethyst clusters, sea lanterns, ender chests, gravel, Regions Unexplored silt/
peat/chalk/argillite grass blocks and Biomes We've Gone lush grass blocks.
Sunflower, lilac, rose bush and peony are removed entirely, because this shop
has no per-item sell-disable setting and zero-price selling consumes items.
Existing prices, buy-category settings and previous bans remain unchanged.

Backup: `config/gangshop/overrides.pre-arbitrage-removal-20261029.json`.
New restriction SHA-256:
`8c43bfac5043c0fef442ba956271125451a8a6511c7ad4e7861adc18db9bb70b`.
Activation uses only `gangshop reload`; no Minecraft `/reload` or power action.
Live verification confirmed the shop-only reload at 14:34:54, all 100
selected denies, unchanged price-file hash and category settings, and a
running server without resetting uptime.

The pending launcher was updated with owner approval to
`gangs-alex-weapon-restart-1.0.60-shop-20261029.jar`, SHA-256
`31b7499cc00d6dab4d980db054a4530acd9c2e73dd07352598c9fa93ee927428`.
Its executable classes and all non-shop payloads are byte-identical to the
previous 1.0.60 launcher; only the embedded shop restrictions and payload
hash manifest differ. Installer regression tests passed both baselines,
repeat/resume, original backups, locked worlds, changed configurations,
conflicting tags, duplicate mods and tampering.
Rebuild with `scripts/hosted/Build-AlexWeaponRestart.ps1 -OutputName
'gangs-alex-weapon-restart-1.0.60-shop-20261029.jar'`.
The original artifact is retained as the historical fixture baseline.
Do not select the original 1.0.60 launcher with the new restrictions:
its shop hash check will reject them. If rolling restrictions back before
restart, also restore the matching previous launcher selection.

The three stone/limestone/copper recipe-loop fixes remain staged, not active.
Temporary choices, not applied: (1) leave them until the normal restart;
(2) temporarily remove all involved stone/cobblestone, limestone/cobbled
limestone and copper raw/ingot/storage listings, restoring them after
restart; (3) suspend shop access until restart. A price adjustment alone
cannot stop a resource-multiplication loop while retaining positive payouts.
Villager-trade arbitrage is accepted by the owner, so the shop must not be
described as eliminating every repeatable money source.
The later requested 20% crafted-buy premium was separate from this restriction
deployment; the superseding correction is documented above.

## Live shop prices; recipe-loop fixes await restart (2026-10-29)

The live price file was backed up to
`config/gangshop/prices.pre-recipe-balance-20261029.json` before replacing it.
Only `gangshop reload` was used to activate prices. The server confirmed
`Gang Shop reloaded (catalog + prices).` and rewrote the price file with the
same semantic SHA-256 as the validated catalog:
`c5b08343119468292e0567a3d5b4cb0999fdde32416b21c6b2e2d8b5639dfe2c`.
No Minecraft `/reload`, stop, restart, world reset or power action was sent.
Read-only price commands confirmed the effective in-memory values for oak
logs ($10 sell / $20 buy), oak planks ($2 / $4), bone blocks ($9 / $18) and
tin ingots ($9 / $18). The zero-priced foods, bans and buy-category settings
were preserved, and server uptime continued increasing.

- [Screenshot targets](../../scripts/shop-price-targets.json) cover 273 IDs
  already present in the shop's price table. Galosphere is ignored. Another
  30 requested boat/raft or ore-variant IDs are absent from this shop table;
  no new listings or unavailable ore variants were invented.
- [Prices](../../config/gangshop/prices.json) change 14,709 stored entries.
  Buy prices are twice sell prices, with a minimum $2 buy / $1 sell for
  priced entries. Existing zero-priced foods stay zero; foods and metals
  buying remain disabled. Item bans, category settings, category allowlists
  and overrides are unchanged.
- Targets are starting values, not unconditional markups. Prices account
  for actual input quantities, batch outputs, cheapest tag alternatives,
  multi-step recipes, stonecutting, smithing, log stripping, Chipped
  exchanges and the highest alloy-forge yields. Fuel, labor and tool wear
  are not charged. Reversible storage recipes constrain both directions;
  bone blocks, for example, equal nine bonemeal reference units.
- Raising old crafted-item markups must not inflate ingredients. The
  integer solver first minimizes price increases needed to keep every
  modeled output above the minimum, then fits the requested prices.
  Some screenshot values therefore change substantially.

Three approved overrides are already on disk in the live `gangs_fixes`
datapack, but **are not active until the owner's next normal restart**:

1. `farm_and_charm:mincer/cobblestone`: one stone returns one cobblestone,
   not two.
2. `conditional:farm_and_charm/mincer/cobbled_limestone_from_mincer`:
   one limestone returns one cobbled limestone, not four.
3. `farm_and_charm:mincer/raw_copper_from_copper_ingot`: disabled using a
   false Fabric load condition. Normal ore processing and forge bonuses
   remain unchanged.

Without these fixes, mincing/smelting or mincing/forging can multiply the
same resource indefinitely. No positive price can satisfy ingredient-only
limits around such a loop. The owner explicitly chose immediate shop-only
activation with these three temporary exceptions until restart. The
previously selected restart installer and its existing payload/configuration
hashes were not changed; these three new recipe paths do not replace its
payload files or datapack metadata.

Validation: 11 focused tests pass and all 17,218 modeled recipe or exchange
constraints pass exact integer checks. A separate pass over the persisted
catalog checks 16,898 fully valued recipes, the minimum, spread, protected
entries and all four new pack-index hashes. The audit does not pretend that
unavailable inputs cost zero: 468 recipes have no complete priced ingredient
path and are reported separately. Other skipped resources include inactive
integration recipes, deliberately empty uncraftable tags, custom conditions,
fluid/NBT/dynamic recipes and acquisition mechanics that are not item
crafting. Their costs are not certified by this audit.

Repricing tooling:
[Balance-ShopPrices.py](../../scripts/Balance-ShopPrices.py) and
[Test-ShopPrices.py](../../scripts/Test-ShopPrices.py) require
`scipy==1.17.1`. Capture the current price table and active mod filename/size
inventory before using them. Extract resources with `--minecraft`,
`--inventory`, `--archive`/`--mods`, `--datapacks` and `--resources`.
Use `--extra-resources` for separately inspected live jars not in local
archives. Then supply `--prices`, `--resources`, `--conditions`, `--output`
and `--report` to balance. Custom condition booleans must come from the
actual host configuration, not assumed defaults. This run verified
`mythicmetals:nuggets_enabled`, `mythicmetals:anvils_enabled`,
`farmersdelight:vanilla_crates_enabled` and
`seasonsextras:is_season_messing_crops` as true.

To roll back prices, restore the backup and use **only** `gangshop reload`.
To cancel the pending recipe changes, remove only the three recipe overrides
listed above before restarting. Do not select an older restart installer.

## Staged on host: exact teleport destinations and player autofill (2026-10-28)

GoldClaim 1.0.60 is embedded in the uploaded
`gangs-alex-weapon-restart-1.0.60.jar`, SHA-256
`f1379b4231e048643bb6f152f90e533a5d95687549d48dddc10a05d70cfcd3ae`.
The panel's saved `SERVER_JARFILE` now selects this installer.
No power action, reload or console command was sent.

Read-only inspection confirmed that the earlier 1.0.59 update had already
been applied: its completion marker exists and the active jar matches
`c212e89e0700dcbb3a84b9c5fcc173e17eb5de0d9c4901888f47a9ada055a808`.
All 39 baseline/payload readback checks passed. The installer supports this
already-applied baseline as well as the earlier pending 1.0.57 baseline;
it does not attempt to replay old configuration migrations over 1.0.59.

- `/tpa B`: A lands at B's exact dimension, position, yaw and pitch when B
  accepts. This is the same saved space, not an adjacent landing block.
- `/tpahere B`: B lands at A's exact dimension, position, yaw and pitch when
  A sent the request, even if A has since moved away or changed dimensions.
- Neither command adjusts the landing to avoid hazards. Players choose
  whether the destination is safe. Existing dimension/reset restrictions,
  request expiry and death/disconnect cancellation remain in place.
- Both commands suggest other online players after a space and filter
  typed prefixes case-insensitively. Chat prompts describe the timing.
- Terrain preparation freezes the appropriate snapshot; movement while it
  loads does not retarget the teleport.

On the next normal restart, the installer verifies the original launcher,
world lock, exact existing mod/configuration hashes and embedded payload,
backs up the active 1.0.59 jar under `backups/alex-weapon-restart-1.0.60/`,
installs only GoldClaim 1.0.60 on this baseline, and launches original Fabric.
Existing recipe tags, shop, cave configs and Command Limits are verified
and left unchanged. Repeated starts verify the result; interrupted updates
resume without replacing the original backup.

Before activation, cancel by restoring `SERVER_JARFILE` to
`gangs-alex-weapon-restart-1.0.59.jar` (the previously selected, already-applied
installer), or use `fabric-server-launch.jar`. After activation, stop first,
restore the backed-up 1.0.59 jar, remove `mods/goldclaim-1.0.60.jar`, and
select original Fabric startup. Keep exactly one active GoldClaim jar.
Older unapplied installers must not be selected.

Validation: the focused real-command teleport fixture passes both timing
rules, exact coordinates/facing, dimension changes before acceptance,
movement during preparation, duplicate/death cancellation and online-player
autocomplete. Cold enqueue was 17.1059 ms (required <250 ms), with 36 ticks
continuing and a longest preparation tick of 196.7562 ms (required <1000 ms).
The isolated runtime used the existing CorgiLib and NightConfig dependencies
and excluded the unmapped compile-only Fiw jar via a test-only Gradle init
script; production dependencies were unchanged. Installer fixtures cover
both baselines, repeated starts, interrupted recovery, exact backups,
locked worlds, changed configurations, duplicate mods and tampering.
The live teleport/autofill behavior awaits activation at the owner's restart.
Rebuild with `scripts/hosted/Build-AlexWeaponRestart.ps1` after building and
copying GoldClaim 1.0.60 into `artifacts/`.

## Staged on host: recipes, Wabbajack, staff locate and future worldgen (2026-10-28)

Uploaded and SHA-256 readback verified
`gangs-alex-weapon-restart-1.0.59.jar` in the host root:
`9866cbe15ebe5c6927f129c47502ac3f9eec5a17d3dda5c6c4bbabad33694cac`.
The panel's saved `SERVER_JARFILE` is now that installer, superseding the
unapplied 1.0.58 installer. No power action, console command or reload was
sent. The server remained running with increasing uptime; active GoldClaim
1.0.57, Command Limits 1.0.0, shop, datapack metadata and all six cave
configs retained their original hashes.

On the next normal restart, before Fabric loads, the installer:

1. Verifies the original Fabric launcher, existing datapack metadata,
   GoldClaim 1.0.57, Command Limits 1.0.0, shop, six cave configs and all payload hashes.
   Refuses an occupied world lock, unexpected mod version or conflicting
   target file rather than overwriting unknown changes.
2. Backs up the exact old mods, shop and six cave configurations under
   `backups/alex-weapon-restart-1.0.59/`.
3. Installs GoldClaim 1.0.59 and Command Limits 1.0.1, denies the Wabbajack
   in the shop, adds 29 recipe tags and enables RSW in six cave configs.
   GoldClaim includes the coastal terrain protection described below.
   Writes `APPLIED.txt` only after verification.
4. Starts the original Fabric launcher with the original arguments.
   Repeated starts verify the completed installation; interrupted installs
   can resume without losing the original backups.

GoldClaim 1.0.59 bans only `soulsweapons:withered_wabbajack` in addition to
the existing bans. Its recipes and item commands are rejected; new stacks
and saved copies in inventories, entities and nested containers are emptied
as they load. Other Soulslike weapons remain unchanged. This removes the
problem weapon, not the mod, and does not attempt to repair previously
created terrain or client rendering artifacts. Stack removal persists in
subsequent saves; restoring the old mod does not restore deleted weapons.

Validation: all 79 GoldClaim unit tests pass; installer fixtures cover exact
backups, repeat starts, interrupted recovery, live-world lock refusal,
changed launcher/mod/shop/cave configs, conflicting tags, duplicate mods, missing shop
configuration and post-install tampering. Offline recipe validation scans
551 recipes and resolves all 118 common-tag ingredient references. An isolated
production-mapped Fabric server passed 288 real surface-rule evaluations across
all three Expanded Ecosphere presets, checked the restored locate command tree,
and stopped itself. Live crafting and activation await the owner's restart.

To cancel before restarting, set `SERVER_JARFILE` back to
`fabric-server-launch.jar`; no active content has been replaced yet.
After installation, stop the server before rollback: restore the old mod
and shop plus all six cave configs from the backup, remove
`mods/goldclaim-1.0.59.jar`, `mods/commandlimits-1.0.1.jar` and the 29 newly
added tag files, and restore normal Fabric startup. Keep only the restored
GoldClaim 1.0.57 and Command Limits 1.0.0 jars active. Do not select any of the
older historical next-restart installers.

### Staff locate and Alex's Caves in RSW

Command Limits 1.0.0 cancelled vanilla `/locate` registration entirely.
The 1.0.1 compatibility build removes only that disabling mixin; 11 unrelated
files remain byte-identical. GoldClaim requires operator level 2 or the
primary/inherited LuckPerms groups `mod`, `moderator`, `admin` or `owner`.
Ordinary players are denied. Vanilla biome, structure and POI locate remain
intact; no client update is required.

All six live Alex's Caves configs previously allowed only `minecraft:overworld`.
Add `multiworld:rsw` to magnetic caves, primordial caves, toxic caves, abyssal
chasm, forlorn hollows and candy cavity, preserving every existing distance,
rarity and noise threshold. This enables future generation in RSW Overworld
only, not its Nether/End. Existing chunks are not regenerated, and this does
not certify caves already present in the saved world.

### Future beach terrain protection

Both reported RSW biomes are from Expanded Ecosphere 3.2.4:
`wythers:gravelly_beach` near (-1476, 63, 54) and
`wythers:tropical_beach` near (2681, 63, -872).
A read-only saved gravel chunk (-93, 3) has ground top Y=62 in all 256
columns. The mod's DEFAULT and FORCE_LARGE_BIOMES resources contain a
24-biome surface rule replacing terrain at Y>=63 with air, including these
beaches. This explains the sea-level cutoff; individual floating edge blocks
were not independently traced.

GoldClaim independently excludes six coastal biomes from that exact air-cut
rule: coastal mangroves, black beach, tropical beach and gravelly beach in
`wythers`, plus vanilla beach and snowy beach. The other 18 river/ocean/lake
cases are unchanged. Existing sand/gravel surface rules and terrain noise
remain intact. Expanded Ecosphere's upstream jar is not modified or redistributed.

Only newly generated chunks after activation benefit. Existing flat areas
and floating remnants are untouched. This removes artificial clipping; it
does not force every naturally low-lying beach to become hilly.

### Recipe tag compatibility

Prepared 29 additive item tags in the existing `gangs_fixes` datapack.
Alex's Mobs Continued 2.0.12 uses newer `c:` ingredient paths that are
missing in this Fabric 1.20.1 pack; Alex's Caves 2.1.8 retains several
unsupplied `forge:` ingredient paths. A recipe can therefore be displayed
without accepting the expected vanilla ingredient in a crafting table.

- Restore netherite ingots for Straddleboard and Dimensional Carver,
  iron nuggets for Enderiophage Rocket, sticks for Maraca, Shark Tooth
  Arrow and Tendon Whip, and vanilla glass for Rainbow Glass.
- Supply Alex's Caves' missing wooden barrel, concrete, dye, diamond,
  clear glass, gravel, gold nugget, stick, gold block and redstone block
  ingredient tags. All 16 dye/concrete colors are covered.
- Use `replace: false` throughout, retaining other mods' tag additions.
  Recipe shapes, quantities, outputs and intentional bans are unchanged.
- Existing release packaging and the stopped-server installer already
  include `gangs_fixes`; no mod jar or client update is needed for a
  server-side datapack installation.

Offline regression check (use a local extracted pack's mod directory):
`powershell -NoProfile -ExecutionPolicy Bypass -File scripts\Test-AlexRecipeTags.ps1 -ModsDirectory <mods-directory>`.
This checks common-tag resolution and expected vanilla ingredients against
the bundled recipes, including the original Straddleboard shape/output;
it is not a live crafting-table test.

The tag fix itself needs no client update. The combined restart payload
includes the server-only GoldClaim update for Wabbajack removal.

## Final reopening deployment (2026-10-27)

With approval, gracefully stopped production and backed up the existing
`world/ledger.sqlite` (1,982,406,656 bytes), `config/ledger.toml`, original
Ledger jar and GoldClaim 1.0.56 together. No SQLite WAL/SHM/journal siblings
were present in the stopped world directory. Host-side archive:
`backups/pre-reopening-20261027-ledger-goldclaim.tar.gz` (494,744,964 bytes).
This is a stopped backup, not a live database integrity certification.

Installed GoldClaim **1.0.57** and Ledger **1.2.8+gangs.compat.1**, replacing
only those two active jars. Original jars remain disabled alongside them.
Both uploads were SHA-256 verified before activation. Startup reports both
new versions and `Initializing Ledger 1.2.8+gangs.compat.1`.
Maintenance remains enabled for the owner's reopening decision.

Live checks after the first startup:
- `Done (10.354s)` and maintenance active confirmed.
- Owner confirmed arrival at `(0, 68, 0)` with terrain visible, and both
  `/rsw` and `/hub` working normally.
- Join log: Fiw verification passed at 16:49:40; exactly one hub transfer
  followed at 16:49:41. Earlier repeated `requestTeleport` messages were
  verification freeze corrections, not repeated automatic hub transfers.
- With approval, placed one stone at `(0, 300, 0)` in each of
  `minecraft:overworld` and `multiworld:pvp`, guarded by loaded/air checks.
  Both dimension-filtered Ledger searches returned the exact placement.
  Both blocks were restored to air; both break searches returned the cleanup.
  Temporary force-load tickets were removed; neither chunk had been
  force-loaded before the checks.
- Graceful shutdown at 16:54:03-04 saved all dimensions and exited with no
  missing-world/worker failure or Kotlin Duration linkage error in that run.
  Stopped post-test database/config/log backup:
  `backups/post-reopening-audit-test-20261027.tar.gz` (478,364,672 bytes).
  Final startup reached `Done (10.304s)` at 16:58:10 with maintenance active.
  At 16:58:31-32, all four scoped searches returned the original placement
  and cleanup records in their respective worlds, confirming persistence.
  No missing-world/worker failure or Kotlin Duration linkage error was found.
- The next owner join again passed verification at 16:58:48 and transferred
  once to hub spawn at 16:58:49.
- A scoped older-history query for owner block placements over the prior
  seven days returned no results. It does not certify completeness of old
  history; the original stopped backup is retained.

Ledger's repair registers a missing world in the same logging transaction
and replaces two removed experimental Kotlin Duration calls with stable API
calls. It leaves the database, schema, existing IDs, configuration and Kotlin
runtime in place. The original missing-world failure could drop audit actions;
those historical missing records cannot be recovered by this repair.
Production-mapped fixture tests cover actual vanilla/Multiworld actions and
queries, missing-world recovery, both shutdown paths, restart persistence,
SQLite integrity and foreign-key checks. These are fixture results, not
claims about live database integrity.
The LGPL corresponding-source bundle accompanies the patched jar in
`artifacts/` and is uploaded to the host's `backups/` directory; see
[ARTIFACTS.md](../../ARTIFACTS.md).

## Reopening repair: GoldClaim 1.0.57 RSW entry (2026-10-27)

The October 26 watchdog trace stops in `RswEntrySearch.advance`, called from
`/rsw`. Despite returning a future, `getChunkFutureSyncOnMainThread` waits for
terrain on the main server thread. The existing generic teleport preparation
already avoids this trap, but RSW's safe-ground search still used it.

GoldClaim **1.0.57** queues RSW entry without loading terrain in the command,
and reuses `TeleportChunkArea` to prepare a ticketed 3x3 arrival area through
the nonblocking chunk request invoker. Search ticks yield while chunks load;
safe-ground checks and the final transfer run only after the area is ready.
All completion/cancellation/failure paths release tickets. Terrain work has
the same explicit two-minute timeout as generic teleport preparation.
Duplicate requests, reset generation and player/world validity checks remain.
No world reset, terrain replacement or client update is needed.

Validation: all **76 unit tests** pass; production-mapped Fabric on an
initially unloaded RSW destination verifies enqueue below **250ms**,
**120** waiting ticks, longest preparation **89.64ms** (required <1000ms),
one safe cross-world arrival, duplicate rejection and deferred cancellation
after player death. The previous login/hub ordering repair is retained.

Kit investigation: the two failed legacy datapack functions call `rc` and
`kits resetusage` during early command parsing, which reaches LuckPerms before
its verbose handler initializes. The inspected live Monthly SkiesKits action
instead calls `gangskit monthly`, backed by GoldClaim's Java kit service.
These legacy function load errors alone do not prove a broken current claim
path. Existing player claim/cycle state has not been changed.
All four live kit configurations were confirmed to call the Java path.
The existing production-mapped SkiesKits integration test passes all four
actual reward claims, validates registered reward items and verifies
duplicate/ineligible/full-inventory failures do not consume claims. No
player's live kit was claimed or reset for this check.

## Installed: GoldClaim 1.0.56 login/hub ordering (2026-10-27)

After the host repaired startup, both Oux_y and Rootamiss reproduced a
login-position conflict. GoldClaim transferred them to the hub around one
second after joining; Fiw 2.2.0 then repeatedly restored the coordinates
captured before verification. Those corrections do not restore the original
dimension. Rootamiss's old coordinates (699, 89, 1421) were therefore applied
inside the hub, away from its built area. Rootamiss confirmed that `/hub`
after verification restored terrain.

GoldClaim **1.0.56** pauses its automatic hub countdown while Fiw reports the
player frozen. Once released, the existing 20-tick settling delay runs and
the player transfers once to the configured hub spawn. It does not disable
verification, repeatedly teleport players or change Fiw's freeze state.
Without Fiw installed, the previous login behavior is retained.
Disconnects still discard pending transfers. Manual travel and hub item
restrictions are unchanged.

Validation:
- All **76 unit tests** pass; main and integration source compilation pass.
- Production-mapped Fabric with the unmodified Fiw 2.2.0 jar verifies no
  transfer during a freeze longer than the old hub countdown, the full
  post-release delay, exactly one cross-dimension transfer, exact destination,
  stable position for another 30 ticks and server chunk tracking.
- The same production test passes with Fiw absent, including optional-class
  loading and the original 20-tick countdown.
- These server-side tests do not certify actual client terrain rendering.
  Production affected-player verification is recorded below.

With approval, the server was stopped, the new jar installed and read back
with SHA-256
`5227f39e47aedb447270e47892c788d1b4ef1eb8e514051e8a5816285cfee447`,
then startup was requested. Previous jar preserved exactly as
`mods/goldclaim-1.0.55.jar.disabled-before-1.0.56`
(SHA-256 `b7d814fa2b95f4be621e9458bcc7f59880aa2584039b551589e8b73cdc8b5663`).
Maintenance mode, other mods, saved worlds, player data and startup settings
were not edited. No client update is needed.
Startup verification confirmed `goldclaim 1.0.56`, `Done (11.452s)` and the
panel's running state. The user confirmed Rootamiss rejoins at the hub spawn
with terrain rendering. The corresponding server log shows verification
passing at 16:05:42 and the single automatic hub teleport at 16:05:43,
after verification rather than during the freeze. Fiw's correction messages
while verification is pending remain expected.

## Startup blocker and join verification timeout (2026-10-27)

The hosted JVM exits before Fabric starts because the host-managed startup
command requires `-javaagent:/home/container/dmc-sidecar.jar`, but that file
is missing. The console reports `Error opening zip file or JAR manifest
missing` and `agent library failed to init: instrument` (exit 1, not an
out-of-memory exit). The customer startup variables do not expose this agent
argument. DedicatedMC must restore its agent or remove the broken argument;
do not reinstall the server or reset worlds to address this failure.

Separately, the October 26 running log confirms repeated Fiw verification
timeouts exactly ten seconds after joining, including late reports and
players who passed verification on retry. With approval, changed
`config/fiw-mods-api/config.json`'s `timeout_seconds` from 10 to 60 on the
stopped server and in the repository. The exact original is saved on the host
as `config/fiw-mods-api/config.json.before-timeout-60-20261027`; readback
confirmed that only the timeout changed. Monitoring, staff alerts, report size limits and required client
verification remain unchanged. Clients do not need an update for this
server-side timeout change.

The timeout increase addresses confirmed premature kicks, not a verified
fix for every report of an invisible hub. Startup and actual player
login/chunk rendering still need verification after the host repair.

## Hotfix applied: GoldClaim 1.0.53 restart-loop fix (2026-10-24)

GoldClaim 1.0.52 crash-looped the hosted server. Its lunar migration threw
`Missing Enhanced Celestials forecast for multiworld:rsw` on the first world tick,
and the panel restarted the server about every four minutes.

**Fix**
- GoldClaim **1.0.53** defers the migration (and logs one warning) while a
  world has no forecast, instead of throwing.
- It was installed live as `mods/goldclaim-1.0.53.jar`
  (SHA-256 `0a5820f381d5e6d2d65fea9c836fcd2a0df8c452ba5de322191508349802bdfe`).
  `goldclaim-1.0.52.jar` was removed.
- `SERVER_JARFILE` is back to `fabric-server-launch.jar`. The 1.0.52 installer has
  already applied the datapack events; its backup stays in
  `backups/next-restart-goldclaim-1.0.52/`.

**Verification**
- The production-mapped smoke test passes on a fresh world.
- The hosted server is stable after restart.

## Next restart: RSW Blood Moons actually load (2026-10-24)

**Cause**
- The host had a stale, untracked 3-file copy of `gangs_fixes` at
  `global_packs/required_data/gangs_fixes`.
- Global Packs registers it with the same pack ID as the 24-file Paxi copy,
  and it is registered last, so it replaced the Paxi pack.
- Since at least 2026-10-19 the host therefore never loaded the Paxi-only content:
  - Enhanced Celestials event overrides: Wild kept Blood Moons.
  - The RSW dimension settings: RSW had no forecast, "lunar events are
    disabled for this world".
  - True Ending RSW-End functions and advancement.
  - BetterEnd Lootr convert tags.
  - The empty furniture-bin loot table.
  - The `gangs:rsw_nether` dimension type.

**Staged fix**
- The stale copy was moved to
  `backups/global_packs-gangs_fixes-stale-20261024`. No other file changed.
- Its `load` actions (`keepInventory false`, nickname and graves-list
  permissions) are already applied in Java by GoldClaim's `runGangsFixesLoad`.
- The fix applies at the next restart, because the Enhanced Celestials
  registries are dynamic and load only at startup.

**After the restart, expect**
- RSW: Blood, Super Blood, Blue, Super Blue, Harvest, Super Harvest and Super moons.
- Wild: the five non-blood moons only.
- GoldClaim 1.0.53 performs the one-time RSW forecast recompute.
- Check with `execute in multiworld:rsw run enhancedcelestials lunarForecast`.

**Verification**
- Reproduced locally: the production fixture plus Global Packs 19.3.6 and the
  stale copy fails exactly as the host does.
- With the copy removed, the full smoke test passes:
  - all seven RSW moons activate;
  - Wild keeps five non-blood events;
  - claim blast protection is intact.

**Rollback:** move the folder back into `global_packs/required_data/` while the
server is stopped.

**Note:** `global_packs/required_data/gangs_hub` likewise shadows the
repository's Paxi `gangs_hub`. The host copy is newer (it adds barrier/clear
functions and a straw statue recipe disable), so it was left in place.

## Next restart: cleanup, cannon claim protection and RSW moons (2026-10-24)

Server-only GoldClaim **1.0.52** and five Enhanced Celestials datapack
overrides are packaged in `gangs-claim-lunar-restart-1.0.52.jar`.
No public client-pack update is required.

- Ground-item cleanup runs every **15 minutes**, measured by wall clock,
  instead of five. Existing one-minute, thirty-second and final 10..1 warnings,
  item totals and non-item exclusions are unchanged.
- Supplementaries **3.1.44** bombs (normal, blue and spiky) and cannonballs
  cannot destroy blocks or containers in player/admin claims. Protection
  checks each affected block, not the shooter or blast origin, so firing
  from outside a claim, ownerless/redstone shots and boundary-straddling
  explosions cannot bypass it. Unclaimed blocks retain normal destruction.
  Entity damage is not disabled. The existing PvP terrain-preservation rule
  also applies to these custom explosions.
- RSW **Overworld** now has all seven Enhanced Celestials events: Blood,
  Blue, Harvest and Super Moons, including all three super-colored variants.
  Wild retains its Blue/Harvest moons, their super variants and ordinary
  Super Moons, but no Blood/Super Blood Moons. Nether/End worlds are not
  assigned moon cycles. Existing event probabilities, night spacing,
  full-moon restrictions, clear-weather requirement and effect values remain
  unchanged.
- Existing RSW data already allowed both Blood Moon variants, but excluded
  the five other events. Those events now explicitly include `multiworld:rsw`
  while retaining `minecraft:overworld`. Definitions are based on the
  unmodified [Enhanced Celestials 5.0.3.1 release](https://modrinth.com/mod/enhanced-celestials/version/bGoMMYXA);
  their visuals, loot and mob settings are otherwise unchanged.
- A one-time, per-world saved-state migration rebuilds RSW's persisted
  100-day forecast so the new event pool takes effect immediately. It also
  removes any historical queued Blood Moons from Wild without changing
  Wild's other scheduled events/history. New RSW worlds receive the same
  migration after a normal resource-world reset. This does not reset terrain,
  change the time/weather, force a moon tonight or alter natural-spawn rules.

Validation: all **76 unit tests** and both development and
**production-mapped Fabric** integration runs pass. Tests cover all bomb
variants/cannonballs, claimed stone and chest inventories, vertical coverage,
inside/outside origins and mixed protected/unprotected affected-block lists.
Native Enhanced Celestials verifies the exact seven/five event pools, activates
every RSW moon, checks both Blood Moon monster multipliers and tests stale
forecast migration. The production run launches through the actual restart
installer and original Fabric launcher. Separate installer tests cover exact
backup, repeat startup, interrupted installation, live-world locking and
refusal of changed/corrupt/conflicting files or duplicate active GoldClaim jars.
These are isolated tests, not certification of every production mod interaction.

Payload SHA-256:

| File | SHA-256 |
| --- | --- |
| `artifacts/goldclaim-1.0.52.jar` | `743a81154fec5e668ef37ae50bd22dec9c80ac3682d482c61d73d026d664bd4c` |
| `artifacts/gangs-claim-lunar-restart-1.0.52.jar` | `c34f1478c75ef75c9fbdf59f58104445cd6b87b61144e707e1ff09a46a6dada5` |

Build with `scripts/hosted/Build-ClaimLunarRestart.ps1`. The launcher embeds
the new mod and five JSON overrides, verifies the original launcher, pinned
Supplementaries/Enhanced Celestials versions and existing Blood Moon/RSW
settings, then acquires the world lock before writing anything.
Original GoldClaim 1.0.51 is saved to
`backups/next-restart-goldclaim-1.0.52/goldclaim-1.0.51.jar`.
Existing configs are not overwritten: conflicting new event files abort the
installation. Permissions, other mods and saved world blocks are untouched.

Hosted staging is complete: the uploaded launcher was read back and its
SHA-256 verified, then **Server Jar File** was set to
`gangs-claim-lunar-restart-1.0.52.jar` for the next normal start.
Final readback confirms the server is still running GoldClaim 1.0.51 and
only the original two lunar overrides are active. It installs before delegating
to `fabric-server-launch.jar`. No stop/start/restart or datapack reload is
sent; running GoldClaim remains 1.0.51 until restart.
After successful startup, the wrapper may remain selected (it verifies an
already-applied installation), or normal Fabric startup can be restored.

To cancel before restart, restore Server Jar File to
`fabric-server-launch.jar`; no gameplay payload has yet been installed.
To roll back after installation, stop the server, restore that startup setting,
move only GoldClaim 1.0.52 out of the active mods folder, restore the verified
1.0.51 backup, and remove only the five newly installed non-blood event JSONs
listed above. Keep Blood Moon/RSW settings and all saved worlds/player data.

## Installed while stopped: Vanish 1.5.5 for Minecraft 1.20.1 (2026-10-23)

Requested as a server-only addition, not a public client-pack update.
Source: the user's downloaded `vanish-1.5.5+1.20.1.jar` (Drex, MIT,
Fabric mod ID `melius-vanish`). The original unmodified jar is prepared in
`artifacts/vanish-1.5.5+1.20.1.jar`.

SHA-256:
`80615c026bffc24832616b99ad44c2a74c17d2fdf0eaaca9c85aa77b51f23010`.

Declared requirements are Minecraft 1.20 through 1.20.1, Java 17+, Fabric
Loader 0.15.10+ and Fabric API. Player Data API, Server Translations API,
Placeholder API and Fabric Permissions API are bundled. The host already
has Fabric API, Fabric Permissions API 0.3.1, LuckPerms 5.4.102 and the newer
compatible Placeholder API 2.1.4. No second standalone copy of the bundled
libraries should be installed. No existing Vanish or conflicting Styled Chat
jar was found in the host's active mod list.

Keep upstream defaults and existing live LuckPerms groups unchanged:

- `vanish.command.vanish`: `/vanish` and self enable/disable.
- `vanish.command.vanish.other`: change another player's vanish state.
- `vanish.command.vanish.reload`: reload Vanish configuration.
- `vanish.feature.view`: see vanished players.

These checks fall back to vanilla operator level 2 when no explicit permission
is configured. A moderator group name alone does not grant these nodes.
Do not grant vanish permissions to the default/public group.

The mod generates `config/vanish.hocon` on first start. Defaults include an
action-bar status, disabled chat and `/msg` while vanished, hidden-from-mobs
behavior, prevented pickups/collisions and fake join/disconnect messages.
Invulnerability is **off**; chunk loading remains enabled. No custom live
configuration has been written.

Install only after the user stops the server and offline state is verified.
Upload the checksum-verified original jar, retain normal Fabric startup and
all existing jars, and verify the uploaded bytes before enabling it. No client
installation or world migration is required. Do not stop or restart the
running server as part of preparation. Full production login/rendering and
compatibility with every installed mod require confirmation after installation;
custom command/tab/map integrations may have their own visibility behavior.

Regression entrypoint: `VanishSmokeTest` using
`GOLDCLAIM_VANISH_SMOKE=true` and the existing `runPolicySmoke` task.
The Loom development fixture needs nested bundled libraries exposed as test
dependencies recursively; production Fabric loads those from the original jar.
The final isolated Fabric run passed with GoldClaim 1.0.51, GangSales 1.0.3,
Gang Shop and real LuckPerms 5.4.102: command registration, ordinary-player
denial, permission-granted self toggle, hidden-from-ordinary-observer behavior,
explicit view permission, command-tree serialization, second-toggle visibility
restoration and default configuration generation. Synthetic players are bound
to LuckPerms' login session data before testing its Fabric permission provider.
This is a targeted integration check, not a full production-modpack certification.

After the user stopped the server, verified offline state, uploaded the original
jar as an inactive `.pending` file, read it back and checked SHA-256, then
renamed it to `mods/vanish-1.5.5+1.20.1.jar`. Read back the active file and
verified the same SHA-256 again. Exactly one Vanish jar is present.
GoldClaim 1.0.51 and GangSales 1.0.3 remain installed; startup remains
`fabric-server-launch.jar`. No permissions, configuration, worlds or other
mods were changed. The unchanged original jar is the only new runtime payload;
no GoldClaim update was needed for the test-only fixture additions.

**Server left offline; no start/restart was sent.** The next normal start loads
Vanish and generates its default configuration. Production login and visibility
still need confirmation. To roll back, stop the server and rename only
`mods/vanish-1.5.5+1.20.1.jar` to a non-`.jar` suffix; retain generated
configuration/player state unless intentionally resetting it.

## Installed while stopped: RSW Waystones and command/teleport recovery (2026-10-23)

With the hosted server verified **offline**, installed server-only GoldClaim
**1.0.51** and GangSales **1.0.3**, replacing GoldClaim 1.0.49 and GangSales
1.0.2. GoldClaim 1.0.51 includes the locally prepared 1.0.50 command/teleport
fix and the RSW Waystones policy below. The public Packwiz release is unchanged.

The user explicitly chose **prevent future generation/use, preserving existing
blocks**, not removal of saved Waystones. All three RSW dimensions now reject
Waystones generation through `ChunkRegion`, in addition to existing loaded-chunk
placement rejection, block interaction/item-use rejection, and inbound/outbound
Waystone teleport event cancellation. This closes the protochunk generation
gap. RSW chunk-load removal is disabled, and RSW entries are excluded from
the existing cleanup plan, including when an older plan is resumed. Existing
RSW blocks and registered destinations are left untouched, but cannot be used.
Wild/other-world policy and the separate historical legacy-dimension cleanup
are unchanged.

Installed files, read back from the host and SHA-256 verified:

| Active jar | SHA-256 |
| --- | --- |
| `mods/goldclaim-1.0.51.jar` | `42d7878c3c550bcb3f8a6053f087bdb69df8b5ade60cc8dbd04ce46a9c7bd3d5` |
| `mods/gangsales-1.0.3.jar` | `e1281a5c5541b0409d2b18babc7fc511f5c44b9e7dc2d5611a2a5266af2cf253` |

Exact previous jars retained and hash verified:

- `mods/goldclaim-1.0.49.jar.disabled-before-1.0.51`
  (`d48f42f137b7474ad470e16c52cb7da1f2ab5dfe3f5c7f0fad063f0bc95fdcd3`).
- `mods/gangsales-1.0.2.jar.disabled-before-1.0.3`
  (`78049ad4c246730824ff72862f8af0fdb18768f7583a83c8b9fffd5be4579a09`).

Exactly one active GoldClaim and GangSales jar remains. The Biolith concurrency
fix hash is unchanged. Normal `fabric-server-launch.jar` startup and the
Waystones configuration are unchanged. No saved worlds, chunks, inventories,
registered Waystones or other server configuration files were edited.
**Server left offline; no start or restart was sent.** Production behavior
still needs confirmation after the next authorized start.

Validation: build and all 76 unit tests pass. Native Fabric/Waystones tests
verify generation, placement, item/block use and both teleport directions
are rejected in every RSW, existing blocks survive ticks, and unrelated blocks
and outside-RSW Waystones remain available. Final GoldClaim 1.0.51/GangSales
1.0.3 also pass the real LuckPerms command suite and the request-time teleport
suite: enqueue **12.8996 ms**, **27** continued ticks and longest preparation
tick **89.503 ms**.

Command/teleport behavior:

- `/ec` and `/enderchest` open the same personal ender chest, using the
  existing `gangsales.command.ec` permission.
- `/gs`, `/gs history`, `/gs mine` and `/gs add <amount> <price>` are available
  by default when their LuckPerms nodes are undefined. Explicit denies,
  including contextual/inherited denies, remain enforced. Unavailable users
  and an uninitialized LuckPerms provider still log and deny access.
  This removes the dependency on GoldClaim having already populated the
  default group's public-command permissions.
- `/tpahere B` from A sends B to **A's position when A sent the request**.
  As corrected in GoldClaim 1.0.60, `/tpa B` from A sends A to **B's position
  when B accepts**. Dimension, exact coordinates, yaw and pitch are captured
  together at the respective time, never at terrain-preparation completion.
  Arrival uses the exact saved space with no nearby-block offset, safe-ground
  search or hazard avoidance; players control whether the landing is safe.
  Moving after sending `/tpahere`, or after accepting `/tpa`, does not move
  the saved destination. Both commands offer online-player name autofill
  after a space, filter names as you type (case-insensitive), and omit yourself.
- Tick-spread terrain preparation, duplicate rejection, death/disconnect
  cancellation and RSW reset protections remain active. Requests to an
  unloaded/replaced world or an RSW reset generation that has changed are
  rejected with a request to send a new invitation.

For future reinstalls, install only while the server is stopped: back up and replace the active
GoldClaim/GangSales jars with the two new jars from `artifacts`, retaining
exactly one active jar for each mod. Keep normal `fabric-server-launch.jar`
startup. Do not reuse a historical next-restart installer: it pins older
versions and does not contain these fixes. No world, inventory, sales data
or other configuration migration is required.

Regression entrypoints are `LoginPermissionSmokeTest` and `TeleportSmokeTest`
in the existing GoldClaim test mod. The login fixture uses a player whose
group has no inherited command grants, checks undefined/false/true permissions
for every command, executes sales commands and verifies both ender-chest
inventories. The teleport fixture executes actual request/accept commands
and checks exact acceptance-time `/tpa` and request-time `/tpahere` coordinates
and facing, online-player autocomplete, cross-dimension
movement, asynchronous terrain preparation, duplicate/death cancellation
and, in its full mode, safe `/rtp` arrivals. It resets only the isolated
fixture's RSW scheduling state before running.

With the existing accepted-EULA fixture and dependency directory containing
GangSales 1.0.3, Gang Shop, LuckPerms and Multiworld:

```powershell
$env:GOLDCLAIM_LOGIN_SMOKE = 'true'
.\sources\goldclaim\gradlew.bat -p .\sources\goldclaim runPolicySmoke --no-daemon `
  '-PsmokeModsDir=../../tmp/command-hotfix-smoke-dependencies'
Remove-Item Env:\GOLDCLAIM_LOGIN_SMOKE
$env:GOLDCLAIM_REQUEST_SMOKE = 'true'
.\sources\goldclaim\gradlew.bat -p .\sources\goldclaim runPolicySmoke --no-daemon `
  '-PsmokeModsDir=../../tmp/command-hotfix-smoke-dependencies'
Remove-Item Env:\GOLDCLAIM_REQUEST_SMOKE
```

The broader `GOLDCLAIM_TELEPORT_SMOKE` mode also runs `/rtp` performance and
landing checks. During this update's validation its separate whole-tick
timing assertion exceeded 1 second during `/rtp`; the request-time `/tpa`
and `/tpahere` assertions had already completed. The focused request suite
does not disable or relax that broader check.

Run the RSW Waystones native fixture using `GOLDCLAIM_WAYSTONE_SMOKE=true`
with the same `runPolicySmoke` task and dependencies. Run only one smoke-mode
environment variable at a time.

## Installed while stopped: Biolith concurrency crash fix (2026-10-22)

Crash report `crash-2026-10-22_17.01.05-server.txt` identifies a
`ConcurrentModificationException` in Biolith's
`DimensionBiomePlacement.ReplacementRequestSet.selectReplacement`, during
RSW Overworld generation at chunk **-135, 195**. This is separate from the
GangSales login failure below.

Biolith shares a placement coordinator between worlds of the same dimension
type. Starting another world re-completes its request sets while asynchronous
generation can still read them. The old code publishes an intermediate
`ArrayList`, then removes the vanilla placeholder, shuffles, sorts and replaces
entries in that shared list. A generator iterating it can fail.

The hotfix prepares the entire replacement list locally and publishes one
immutable snapshot only after completion. Sub-biome completion and registration
use the same publication discipline. Snapshot references are volatile; writers
and world-reinitialization calls are serialized. Normal selection remains
lock-free. Registry failures propagate and leave the previous complete list
published rather than hiding the error. This fixes the confirmed list race,
not every possible world-generation or performance problem.

Validation using the actual jar in the isolated Fabric/Multiworld fixture:

- The original jar fails the deterministic blocked-rebuild assertion because
  it publishes an intermediate list.
- Patched replacement and sub-biome rebuilds retain the old immutable list
  until complete; injected registry failures retain that list and propagate.
- **100 seeds x 1,001 noise values** preserve replacement selection, saved
  ordering, interval bounds and resolved entries.
- **800,000 parallel selection pairs** pass during **2,000 world-placement
  reinitializations**, with no incomplete ranges or unresolved real biomes.
- The vanilla placeholder still has no registry lookup, preserving the earlier
  End fix. Binary comparison confirms every non-placement-class entry,
  including `VanillaCompat`, is unchanged.

Installed and SHA-256 verified while the hosted server was offline:

- Active: `mods/biolith-fabric-1.0.1-beta.1-endfix-concurrency.jar`
  (`6092613e0b6c4fa0df01e850a819e6766fa6cae48e2e3725463a79fb9cbeb497`).
- Rollback: `mods/biolith-fabric-1.0.1-beta.1-endfix.jar.disabled-before-concurrency`
  (`eea2f3816d6d6d39e1e1d0ababcaa77b54dba3ba15e00958574df513efe80d92`).
- GoldClaim 1.0.49 and GangSales 1.0.2 hashes remain correct.
- No worlds, chunks, regions or inventories were removed. Teleport Optimizer
  remains enabled; Spawn Animations remains paused. Flight speed, movement
  checks and server startup target are unchanged.

**Server left offline; no start was sent.** Production startup, login and
RSW exploration still need confirmation after the next authorized start.
This is a server-only hotfix, not a public client release.

Build the targeted replacement with Java 17 from the repository root:

```powershell
.\sources\goldclaim\gradlew.bat -p .\sources\biolith-concurrency patchedJar patchedSources --no-daemon
```

The build verifies the exact previous End-fix jar, remaps only the placement
classes, and merges them into that jar. Complete modified LGPL-3.0 source is
distributed in
`sources/biolith-fabric-1.0.1-beta.1-endfix-concurrency-source.zip`.
The small compilation project uses the same Fabric Loom tooling as GoldClaim.

Regression entrypoint:
`sources/goldclaim/src/testmod/java/com/terraformersmc/biolith/impl/biome/BiolithConcurrencySmokeTest.java`.
With the existing accepted-EULA fixture and its normal dependencies plus
exactly one Biolith jar in `tmp/biolith-smoke-dependencies`:

```powershell
$env:GOLDCLAIM_BIOLITH_CONCURRENCY_SMOKE = 'true'
.\sources\goldclaim\gradlew.bat -p .\sources\goldclaim runPolicySmoke --no-daemon `
  '-PsmokeModsDir=../../tmp/biolith-smoke-dependencies' `
  '-PsmokeBiolithJar=../../tmp/biolith-smoke-dependencies/biolith-test.jar'
```

Point **both** compile and runtime configuration at the jar under test; otherwise
the test source set's compile classpath can shadow it with the original jar.
Do not run the completed 1.0.49 next-start installer, which still pins
GangSales 1.0.1.

## Installed while stopped: GangSales 1.0.2 login recovery (2026-10-22)

The 1.0.49 gameplay installer completed during the normal restart. Player
login then exposed a GangSales permission bug: reflective `getQueryOptions`
returned `QueryOptions`, but the code cast it to `Optional`. The uncaught
exception interrupted command-tree construction in `PlayerManager.onPlayerConnect`.
Players reported missing commands, slow login and an unrendered hub; subsequent
movement packets failed in `ChunkTicketManager.handleChunkLeave`. The login
failure is confirmed; whether it fully explains the chunk symptoms needs
production login verification.

GangSales 1.0.2 replaces that reflection with the typed LuckPerms 5.4 API,
using the user's contextual query options. Without LuckPerms the existing
command availability is preserved. With LuckPerms present, unavailable users
or an uninitialized provider are logged and denied rather than failing open.
Seller item-name correction is retained. A real Fabric/LuckPerms 5.4.102 test
passed denied/allowed `/gs` and `/ec` checks, command-tree serialization and
player chunk-position updates.

At the user's request, with the hosted server confirmed offline, installed
`mods/gangsales-1.0.2.jar` and retained the exact previous jar as
`mods/gangsales-1.0.1.jar.disabled-before-1.0.2`. SHA-256:
`78049ad4c246730824ff72862f8af0fdb18768f7583a83c8b9fffd5be4579a09`.
Restored **Server Jar File** to `fabric-server-launch.jar`; do not use the
completed 1.0.49 installer again, as it pins GangSales 1.0.1. GoldClaim 1.0.49,
Teleport Optimizer enabled and the Spawn Animations pause are unchanged and
verified. The server was left offline; no start was sent.

## Prepared: GoldClaim 1.0.49 / GangSales 1.0.1 (2026-10-22)

Server-only update over GoldClaim 1.0.48 and GangSales 1.0.0. No server
restart, shop reload or RSW reset is required during staging.

- **Hub teleport protection:** blocks ender pearls and chorus fruit in either
  hand without consuming them, rejects pearl impacts and random/local teleport
  APIs, and blocks same-world item teleports even when a mod uses the
  administrative teleport API. Explicit command/admin teleports and travel
  between dimensions remain available. Outside-hub behavior is unchanged.
- **Shop exclusions:** deny all 56 distinct item IDs inspected read-only in
  Oux_y's three shulkers (inventory slots 3, 4 and 5), plus `furniture:bin`.
  The existing 36 exclusions, prices and category multipliers are preserved,
  giving 93 deny entries. No inventory items are removed. This is the server
  shop catalog, not a ban on obtaining or using those items.
- **Roaming bosses:** the pinned Brutal Bosses Fabric 8.5 contains a natural
  spawn conversion method but no caller. GoldClaim wires natural mob
  initialization in `multiworld:rsw`, `multiworld:rsw_nether` and
  `multiworld:rsw_the_end`: one 1-in-100 roll per eligible hostile, followed
  by selection of a compatible existing boss profile. An Endborn Enderman
  profile supplies End coverage, including Enderman subclasses. Existing boss
  identity is saved; these encounters do not add mobs or make them persistent,
  and do not protect arbitrary blocks as treasure chests. Wild, hub, PVP,
  spawners, commands, eggs, boss summons and saved entities are not converted.
  Chest settings remain **30% chance / 100-block spacing**.
- **Ground cleanup:** the final ten warnings are `Clean-up in 10` through
  `Clean-up in 1`; the single final announcement is
  `Ground cleanup removed <amount> items.`. The amount counts individual
  items, not dropped stack entities. Five-minute timing, 1-minute/30-second
  warnings, lag catch-up behavior and grave contents remain unchanged.
- **Player sales:** `/gs` snapshots the display name before insertion mutates
  the transferred stack. Seller messages retain both ordinary and custom item
  names instead of reporting air. Payment, item transfer and history behavior
  are unchanged.
- **Performance follow-through:** Teleport Optimizer 1.0.0 remains enabled and
  unmodified, per the user's decision to isolate the Spawn Animations change.
  The previously staged disable operation has been removed. Inspection of the exact hosted
  binary found chunk-ticket mutation from its background executor, a
  dimension-independent preload cache, and redundant teleport interception.
  Its five-minute performance report only logs counters; timing correlation
  with movement warnings is not proof of causation. Vanilla movement checks,
  ServerCore unloaded-chunk protection, flight speed, simulation/view distance
  and GoldClaim's terrain-prepared teleports are unchanged.
- **Spawn Animations temporarily paused, not removed:** a dedicated Paxi pack
  overrides `spawnanimations:main` with an empty function and replaces the
  expensive scheduled loop with a five-second saved-mob recovery check.
  The original mod jar and configuration stay installed. New mobs spawn
  normally without the ground-climbing/poof entrance; existing hidden or
  mid-animation mobs are restored through the mod's own equipment/state
  recovery functions, including mobs loaded after startup. Mob populations,
  boss abilities and chest encounter rates are not reduced. Keeping the
  original functions available avoids stranding invisible mobs or their gear.

Validation: real isolated Fabric tests cover both hands, pearl impacts,
random/local teleportation, a mod-style item calling the direct teleport API,
command and cross-world exceptions, all 93 shop denies, actual normal/custom
sales with balances and transferred quantities, exact countdown text and
67 individual items removed. A deterministic 2,000-mob sample in each RSW
produced 17 bosses per dimension; boss NBT, non-natural and Wild exclusions
were checked. All five `LagControlsTest` unit tests pass. Installer tests cover
backups, idempotence, interruption recovery, changed-shop/corrupt/duplicate
rejection, live-world locking and Fabric classpath delegation. Players should
still confirm the in-game UI and encounters after the next restart.

Performance validation: an isolated Fabric/Paxi test loaded the exact
unmodified Spawn Animations datapack from its pinned jar below the pause
pack. (Its unused Java 25 classes cannot be remapped by the development
toolchain, so this test loads its native datapack rather than its initializer.)
It verified actual override priority, no new animations, and native recovery
of pending-hide, hidden and mid-animation mobs in all three vanilla dimension
types. Weapon/custom name, armor, position, visibility, original silence and
invulnerability were checked, plus a hidden mob introduced after the first
recovery pass. Extended installer tests passed exact active-optimizer/retained-spawn
checks, disabled/changed-optimizer/spawn/pause refusal, backup safety and
interrupted performance installation.

The next-start installer embeds both jars, shop overrides and the pause pack, verifies
checksums before installation and refuses to overwrite a shop file changed
since staging. Backups go to `backups/next-restart-goldclaim-1.0.49/`.
Use `gangs-next-restart-1.0.49.jar` as **Server Jar File** for the next start;
after a successful install restore `fabric-server-launch.jar` before future
updates. To cancel before the first start, restore that original filename.
Startup memory/Chunky flags and reset schedules are unchanged.

Performance evidence: the 10-minute filtered Spark profile
[JZj2BfuRMM](https://spark.lucko.me/JZj2BfuRMM) attributed 16.56% of sampled
slow-tick time to command functions (not 16.56% of all server CPU).
A bounded vanilla `/perf` capture identified Spawn Animations' repeated entity
selectors and position/equipment NBT reads as the main function workload;
True Ending was smaller and is left unchanged. An earlier `/debug start` /
`debug stop` check ran for 16.82 seconds and was stopped; `/perf` completed
its automatic ten-second run. No profiler was left running.

To resume Spawn Animations later, first restore the normal Fabric startup
filename so the installer does not enforce its staged payload. With the server
stopped, move `config/paxi/datapacks/gangs-performance-pause-1.0.0.zip` outside
the datapack search directories, then start normally. The original mod/settings
remain available for a future chosen-mob implementation. Do not remove the mod
while saved hidden mobs may still need recovery. Teleport Optimizer remains
enabled; this comparison changes only Spawn Animations among the performance
suspects.

After restart, verify `/datapack list enabled` includes the performance pause
and check startup for function-loading errors. Repeat
`/spark profiler start --timeout 600 --only-ticks-over 50` during comparable
travel/player activity and compare slow ticks, command-function share and
movement warnings. Reduced rubberbanding requires live confirmation; no
specific TPS improvement or elimination of rubberbanding is claimed.

Hosted staging: the embedded-payload installer was uploaded and **Server Jar
File** was set to `gangs-next-restart-1.0.49.jar`; the running server retains
GoldClaim 1.0.48, GangSales 1.0.0 and its original shop configuration until
restart. Installer SHA-256:
`43e12b92440d7e69f612c811568f74522d57d66de877ac0eccd520844320fa51`.

## Applied: GoldClaim 1.0.48 (2026-10-21)

Emergency RSW landing-safety fix after a player reported suffocating on `/rsw`.
Installed during an authorized restart; Fabric loaded 1.0.48 and reached `Done`
in approximately 3m33s. Installer completion marker and installed jar hash
verified. Normal `fabric-server-launch.jar` restored for future starts.
No RSW reset was sent; reset journal remains IDLE, generation 19.

- Saved `/rsw` positions are checked after loading terrain for full player
  collision clearance, standing headroom, fluids, supporting ground and border
  bounds. Unsafe saved positions use a verified nearby surface instead.
- Border recovery no longer falls back to an unchecked heightmap position.
  If nearby ground is unsafe, the existing safe-entry search near 0,0 is used,
  not a hub teleport or an unsafe landing.
- Repaired `/rsw` destinations are saved, and landing fall distance is cleared.
- Real Fabric regression checks cover head obstruction, adjacent-wall overlap,
  water, a leaf-obstructed heightmap surface, valid ground and full-body border
  bounds. These checks, 18 unit tests and installer tests passed. In-game player
  confirmation remains necessary.
- GoldClaim SHA-256:
  `e5aa5123210110f23d06079936706306ca5212b304c23be061c9c5c6e852b9f3`
- Installer SHA-256:
  `71daca425e5862f5331608ef939027148648a972cd6fcb8dc29af789fb3bc5c0`
- Previous jar is backed up in `backups/next-restart-goldclaim-1.0.48/`.

## Applied: GoldClaim 1.0.47 (2026-10-21)

Installed over 1.0.46 during an authorized clean restart on 2026-10-21.
The installer completion marker and Fabric log confirm 1.0.47; the installed
jar SHA-256 matches the artifact below. Startup took approximately 3m25s.
RSW reset state after startup was IDLE, generation 19. No manual reset was sent.
Player border-return behavior still needs an in-game player check.

- Players outside the RSW Overworld border are no longer sent to the hub. They
  are moved to the nearest safe surface 3 blocks inside it, searching up to 64
  blocks toward 0,0; if nothing safe is found, they go to that column's surface.
- `/rsw` with a saved position outside the border (or build limit) now lands
  just inside the border instead of failing with "notify an operator".
- Launcher `gangs-next-restart-1.0.47.jar` SHA-256 (uploaded and read back):
  `5b2e74bfe464d462dc341c923f6ce78a8218e973ee139439f5d51f3fa1a7cbf0`
- GoldClaim 1.0.47 SHA-256:
  `b726759535bc16cd49cdb70387f83d763a650fd48d3c20a2cb73d36b19fe8677`
- **Server Jar File** was switched back to `fabric-server-launch.jar` after
  successful installation. Backups are in
  `backups/next-restart-goldclaim-1.0.47/`.
- Startup flags remain `-Xmx9216M -Dchunky.maxWorkingCount=12`.

## Applied: GoldClaim 1.0.46 (2026-10-20)

GoldClaim 1.0.46 supersedes the unused 1.0.45 staging. It contains every
1.0.45 reliability fix below plus these lag controls. **No restart was sent**;
the running server still has GoldClaim 1.0.44.

- **RSW Overworld border: 7,000 blocks wide** (from 10,000), centered at 0,0.
  RSW Nether and End stay at 10,000. The border is reapplied at startup, so it
  shrinks the *current* RSW on the next restart. Anyone in the RSW Overworld
  outside the new border, including players who logged out there, is sent to
  the hub with a message instead of taking border damage.
- **Chunky pregenerates the central 60% of the border's area**: a square of
  radius 2,711 blocks from 0,0 (about 5,422 x 5,422 blocks, ~115,600 chunks,
  about 29% of the previous 393,129). The remaining outer ring generates as
  players explore it. RTP still uses the full 7,000-block border.
- **Natural mob caps are cut 12.5%** (vanilla totals 130 -> 112, -13.8%):
  monster 70 -> 61, creature 10 -> 9, ambient 15 -> 13, axolotls,
  underground water and water creatures 5 -> 4, water ambient 20 -> 17. This
  applies to both the global and per-player caps; spawners, commands, eggs and
  breeding are unaffected.
- **Ground cleanup every 5 minutes** (wall clock) removes every dropped item
  in loaded chunks in all worlds. Chat warnings at 1 minute and 30 seconds,
  then `10`..`1` and `Ground Cleared!`. If lag skips a second, only the latest
  number is shown. Graves store death loot, so it is not cleared.
  The number removed is logged.

Validation: `LagControlsTest` (countdown, stall, caps, 60% area),
`NaturalSpawnPolicyTest`, `RswResetServiceTest`, the real Fabric restart smoke
(now also verifying live mob caps and that cleanup removes items but not mobs)
and the installer safety tests passed. The first smoke run failed in the
existing spawner-registration check on the reused smoke world; the immediate
rerun passed all checks.

- Launcher `gangs-next-restart-1.0.46.jar` SHA-256 (uploaded and read back):
  `ddeb50748e4c6612eda3bb583bfeb9024dab767b457df54e01087d7bac4e7dfb`
- GoldClaim 1.0.46 SHA-256:
  `d697694195712ba23c7ae5f7076b5051e4b36236dc0fab5876cc4e1cf3805185`
- **Server Jar File** is `gangs-next-restart-1.0.46.jar`; backups go to
  `backups/next-restart-goldclaim-1.0.46/`. To cancel before first use, set it
  back to `fabric-server-launch.jar`. After a successful install, switch it to
  `fabric-server-launch.jar` before future updates.

### Dynamic Lights removed server-side (spark profile `cLWptdKtZ2`)

- Its per-tick datapack loop scanned players and up to 750 entities and was the
  main source of the ~13% "scheduled functions" tick cost.
- Live: `scoreboard players set $enable ts.dl.settings 0` (was 1), which also
  lets its loop clear existing light markers.
- Live: `mods/dynamiclights-v1.9.3-mc1.17-26.2.9-mod.jar` renamed to
  `.jar.disabled`; the mod stops loading at the next restart. Its pack entry is
  now `side = "client"`. A single "unknown function" warning for the leftover
  `dynamiclights:internal/*` schedule after restart is expected.
- Rollback: rename the jar back and set `$enable` to 1.
- Spawn Animations is intentionally kept.

### Applied 2026-10-20 (hardware upgrade)

- Host upgraded to 4 CPU threads and a 12 GiB container. `JAVA_ARGS` now ends
  with `-Xmx9216M -Dchunky.maxWorkingCount=12` (was `-Xmx6144M` / `8`). The
  final `-Xmx` overrides the panel's `-Xmx12288M`, leaving ~3 GiB native headroom.
- First start: `APPLIED.txt` reports GoldClaim 1.0.46 installed; Fabric loaded
  449 mods, and startup to `Done` took ~4 minutes.
- Note: vanilla `worldborder get` always reports the main Overworld border, even
  under `execute in multiworld:rsw`, so it cannot verify the 7,000-block RSW border.
- `SERVER_JARFILE` switched back to `fabric-server-launch.jar` after the
  successful install; the change takes effect at the next start.

## Superseded staging: GoldClaim 1.0.45 (2026-10-20)

GoldClaim 1.0.45 was staged on DedicatedMC for the next normal restart, then
replaced by 1.0.46 before it was used.
**No restart was sent.** The running server still has GoldClaim 1.0.44;
Rank Badges stays at 1.0.3. World files, reset journal, borders and the
daily/manual reset schedule were not changed.

### Confirmed incident and fixes

- The archived crash report from October 20 records `OutOfMemoryError:
  Java heap space` during chunk I/O. The effective heap is 6 GiB inside an
  8 GiB container; the final `-Xmx6144M` overrides the panel's earlier
  `-Xmx8192M`. Do not remove that headroom by blindly raising the heap.
- GoldClaim 1.0.44's chunk-population spawn filter uses the server world's
  random generator from worldgen workers. C2ME reports thousands of
  off-thread random-access exceptions originating in `NaturalSpawnPolicy`.
  Version 1.0.45 uses the population method's own random generator instead.
  The 95% reduction for the three targeted animals and Wild hostile-spawn
  policy are unchanged.
- Pregeneration now requests a FULL chunk once, with an explicit ticket held
  through validation and released on success or failure, instead of first
  using Chunky's loader and then requesting FULL again. Read/generation
  failures stop the owned task; they do not allow further generation to
  continue unchecked or reopen RSW.
- The panel's saved startup flags now include
  `-Dchunky.maxWorkingCount=8`, down from the pinned Chunky version's default
  of 50 outstanding requests per task. This applies **on next restart** and
  reduces generation pressure; it is not a measured throughput improvement.
  All other JVM flags, including the 6 GiB heap cap, were retained.

The recovery log confirms all **393,129** border chunks were processed and
saved. Its final recovery pass took **3:01:23**, including a fast scan of
existing chunks followed by new generation, usually around 10-25 chunks/s.
The journal was already `IDLE`, generation 18, when inspected after the
user's restart. There is no unfinished task that needs a new reset.

Recent Netty messages are `recvAddress(..) failed: Connection reset by peer`.
They also occur after generation finished. Those lines do not establish a
Chunky deadlock or identify whether a player, probe or network device closed
the connection. Connectivity's extra debug logging is enabled; no network
error filters, timeout changes or blanket exception suppression were applied.
Actual disconnects still require client logs and timestamps to correlate.

### Next start and rollback of staging

The uploaded `gangs-next-restart-1.0.45.jar` was read back and SHA-256 verified.
**Server Jar File** is saved as that filename. The installer checks the
original Fabric launcher, Chunky and GoldClaim 1.0.44 hashes and the stopped
world's session lock before replacing GoldClaim. It preserves the old jar
and the stopped LuckPerms database/configuration under
`backups/next-restart-goldclaim-1.0.45/`. Repeated and interrupted installations
are validated; unrelated installed mods are unchanged.

- Launcher SHA-256:
  `a42abe94538bdb8992e691ccc0ee0288777ebce30efddedf4cec074a7ebc5168`
- GoldClaim 1.0.45 SHA-256:
  `eb4d46afaf752f6e4e8b913df7aa5ec9292a7a82a0290ab4555ee519a25d9316`

To cancel **before its first run**, restore Server Jar File to
`gangs-next-restart-1.0.44.jar` and remove only
`-Dchunky.maxWorkingCount=8` from the custom startup flags. After a successful
1.0.45 installation, use `fabric-server-launch.jar` for subsequent mod
updates: both older version-pinned installers will reject a later mod jar.
Keep the Chunky working-count flag when using the original Fabric launcher.
Do not reinstall the server, force-upgrade worlds, delete task files or
modify the reset journal to apply this update.

Validation: 15 focused spawn/reset unit tests; isolated real Fabric/Chunky
generation of nine new chunks, a saved-chunk rescan, population-RNG isolation
and existing gameplay-policy regressions; installer backup/idempotence,
interrupted-install, checksum/duplicate, live-lock and Fabric-classpath tests.
The smoke run uses two processors and the eight-request Chunky limit. It is
not a full-pack/full-border benchmark and does not certify that OOM cannot
recur. Measure sustained new-chunk rate, heap and tick time on the next reset.

Full-border coverage still costs hours at the observed rates. No smaller
border, partial reopening or replacement-world pipeline was approved or
implemented. Pregenerating a replacement on this same allocation would still
compete with gameplay; a separately provisioned machine can remove that
generation load, but requires identical pack/worldgen versions and a tested
transfer/cutover process. The inspected account lists only one server and
reports no assigned Resource Pool; a second instance is not currently
available in the panel.

## Previous 1.0.44 update (historical)

GoldClaim **1.0.44** and Rank Badges **1.0.3** are prepared locally.
Building this update does not change a running server, live world or LuckPerms
database. Publishing the repository also does not deploy it. Hosted-server
next-start staging is described below; it does not restart the server.

## Changes

- Wild (`minecraft:overworld`): ordinary natural hostile spawns, phantoms,
  patrols and zombie sieges are disabled. Passive mobs, block spawners,
  spawn eggs and commands still work. Existing hostiles are not killed.
  RSW hostile spawning is unchanged. Triggered boss/raid encounters are not
  disabled by this natural-spawn policy.
- All dimensions: natural spawning of `crittersandcompanions:roly_poly`,
  `crittersandcompanions:snail` and `naturalist:snail` rejects 95% of attempts.
  This deliberately exceeds the requested 70% reduction: chunk population
  can retry each animal four times, and even four independent attempts have
  only an 18.55% acceptance probability. Existing animals, pets, breeding
  and artificial spawning are not removed.
- `bosses_of_mass_destruction:earthdive_spear` joins the existing banned-item
  policy. Its recipes are removed and stacks are emptied on creation/load,
  including loot, inventories and nested storage. `/give`, creative packets
  and stack-count mutations cannot restore it. Unloaded saved storage is
  sanitized when loaded rather than rewriting every region offline.
- Server rank assignment has a single owner: GoldClaim. The obsolete Rank
  Badges server join handler is removed in 1.0.3.
- New/default players become **Drifters**, with gray names and `[Drifters]`
  in tab. Only this case-insensitive statue roster gets gold `[Gang]`:
  `grimmnm`, `brooklyn0_0`, `vendettaaaaaaa`, `Ale432`, `Cletus_jp`,
  `hauntdHollow`, `starhauntu`, `koolkat404`, `wooberson`, `kunoisback`.
  Existing Owner/Mod membership takes precedence; the existing Oux_y owner
  and Rootamiss moderator assignments are retained.
- On startup, LuckPerms migrates stored and loaded users, inherited group
  references and promotion tracks from `player` to `drifters`. Player's
  permissions are copied before the old group is deleted. Contexts,
  negative membership and expiry are retained. Conflicting Drifters nodes or
  inheritance cycles stop the migration with an explicit error instead of
  silently dropping permissions. Fix the reported conflict and restart to
  retry. Staff groups and unrelated user permissions are not cleared.
- Tab order is **Owner > Mod > Gang > Drifters**, using ordered server teams.
  Clients need Rank Badges 1.0.3 to keep this order across game modes too:
  vanilla otherwise puts spectators below nonspectators regardless of team.
  Rank refreshes once per second, so LuckPerms changes need no relog.

## RSW reset and Chunky

The existing daily/manual reset schedule, synchronized seeds and
10,000-block borders are retained. On the next scheduled or requested reset,
the reset journal advances through world creation, **PREGENERATING** and
**FINALIZING** before returning to IDLE.

Chunky 1.3.146 must complete the RSW Overworld's full square border, centered
at its world-border center with a 5,000-block radius. Nether/End full-border
pregeneration is not added; existing End readiness/city checks remain.
All RSW dimensions stay closed to travel while this runs. Players can still
join the server and use other available worlds, although generation competes
for CPU/disk and may affect tick rate.

Do not run another Chunky job against RSW during reset. Existing RSW Chunky
jobs prevent unloading rather than racing the reset. An interrupted
PREGENERATING phase stays closed after restart and performs a new pass over
saved FULL chunks instead of trusting stale task counts or an old seed's
in-memory generated-chunk cache. If Chunky's own auto-continue is enabled,
the recovery pass waits for that task to finish first.

Completion uses the owned task's actual 100% progress, not Chunky's generic
"generation complete" event (which also fires for stopped jobs and before
all asynchronous requests necessarily finish). Chunk read/generation errors
keep RSW closed; generated chunks are checked for FULL status and flushed
before completion is journaled. A paused/cancelled owned task requires a
restart to rescan safely; it never reopens RSW just because the job vanished.
The console logs progress/rate/ETA approximately once a minute.

**Estimate:** approximately 392,000 chunks, including border-edge rounding.
Generation time is roughly `392000 / chunks-per-second`, plus reset,
End preparation and disk-flush overhead:

| Sustained rate | Approximate generation time |
| --- | --- |
| 10 chunks/s | 10.9 hours |
| 25 chunks/s | 4.4 hours |
| 50 chunks/s | 2.2 hours |
| 100 chunks/s | 1.1 hours |

Plan for **several hours, potentially 2-12+ hours** on this heavily modded
pack, not the old 15-minute estimate. This is not a measured live-server
benchmark; slower storage/worldgen can take longer. Use the actual Chunky
ETA after the first few minutes. No full-border benchmark is run against
the live server.

## Applying later

Keep a LuckPerms export and a server backup before the first migration.
Do not downgrade GoldClaim while a PREGENERATING/FINALIZING journal exists.

### DedicatedMC next-start staging

Staged on **2026-10-19** for The Gangs Modded Adventure: the uploaded launcher
checksum was verified and **Server Jar File** was saved as
`gangs-next-restart-1.0.44.jar`. The server remained running, the active
GoldClaim 1.0.43 / Rank Badges 1.0.2 jars were unchanged, and the next-start
installer had not run. No Stop, Restart or live console command was sent.

The Linux host launches a jar directly, not the Windows startup script.
`scripts/hosted/Build-NextRestart.ps1` builds
`artifacts/gangs-next-restart-1.0.44.jar`, containing both verified update jars.
Upload that launcher beside the unchanged `fabric-server-launch.jar` and set
the panel's **Server Jar File** to `gangs-next-restart-1.0.44.jar`.
Do not restart until ready. No active mod, world or LuckPerms file is changed
by the upload or startup-setting change.

At the next start the launcher checks the existing launcher, Chunky and old
mod hashes, rejects conflicting versions, and acquires the existing world's
session lock before changing anything. It backs up the stopped H2 LuckPerms
database and configuration, plus both old mod jars, into
`backups/next-restart-goldclaim-1.0.44/`. Then it replaces GoldClaim 1.0.43 and
Rank Badges 1.0.2 with the bundled updates before invoking the original Fabric
launcher in the same JVM. JVM memory flags, console input and normal shutdown
handling are retained. No world reset is triggered by the installer itself.

The installed hashes and `APPLIED.txt` marker make subsequent launches
idempotent; interrupted installations resume from validated backups. Failed
checks stop startup visibly rather than launching an incomplete update.
The launcher remains version-pinned: after successful installation, set
**Server Jar File** back to `fabric-server-launch.jar` before installing
future mod updates. To cancel staging **before its first run**, simply restore
that original setting; the active jars have not yet changed.

The inspected host has no `gangs_hub` Paxi datapack, so no partial datapack is
installed there. GoldClaim supplies the ranks directly. Client Rank Badges
1.0.3 still requires a separate client update; this host staging does not
publish client files.

Local installer tests cover exact backups, repeated launches, interrupted
installation, checksum/duplicate rejection and a locked running world. A real
Fabric 0.19.3 `--help` smoke test verifies the packaged launcher's classpath
delegation without starting a game server.

### Windows or stopped-server payload

The repository's pending-update scripts accept `-Version 1.0.44`.
The staging helper expects the existing checksum-pinned GoldClaim 1.0.43
launcher and pinned Chunky 1.3.146, and packages both new jars plus the two
hub rank functions. It changes active content only when the startup
installer runs. No staging helper has been run against a real server here.

Alternatively use the stopped-server installer from the rebuilt server
payload. Do **not** use `-ResetRswEnd`; no early world reset is needed.
Client auto-updates/manual client installer deliver Rank Badges 1.0.3
after publication. A server restart alone cannot update client mods.

## Verification

Focused JUnit tests cover exact spawn thresholds/retries, Wild-only scope,
restart-closed journal phases, rank roster/precedence/colors/order,
inheritance cycle detection, track replacement and spear stack/recipe
sanitization. An isolated Fabric test uses real LuckPerms to migrate offline
users and scoped/negative/temporary nodes, exercises injected spawning and
spawner/command/egg preservation, and runs real Chunky on a tiny flat-world
border. This is a functional check, not a production timing benchmark.
The tab comparator is compiled and unit-tested; no live-client UI is changed
or used for verification.
