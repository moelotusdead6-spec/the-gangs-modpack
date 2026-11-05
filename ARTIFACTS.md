# Published Artifacts

## Prepared locally: Simple Voice Chat 2.6.25

- The unpublished v1.1.21 metadata downloads the original
  `voicechat-fabric-1.20.1-2.6.25.jar` from the official Modrinth CDN
  (project `9eGKb6K1`, version `uctY8QSQ`); no copy is rehosted here.
- SHA-256: `314765c54a4967a971836469c8ba0a31ff0718f418d54bb770fef8a97f3f1036`.
- Created by Max Henkel; its bundled metadata states All Rights Reserved.
  [Official project](https://modrinth.com/mod/simple-voice-chat).
- The supplied Downloads jar differs only in `META-INF/MANIFEST.MF`;
  every other archive entry is byte-identical to the official release.
  The owner selected the official download.
- The server restart launcher `gangs-voicechat-restart-1.0.66.jar` embeds this
  jar, so it is built locally by `scripts/hosted/Build-VoiceChatRestart.ps1`
  and is intentionally not published here. See [voice chat setup](docs/updates/next-restart.md).

## Staged for next restart: claim trust and Neon 11 launcher

- `artifacts/gangs-claim-trust-restart-1.0.65.jar`:
  `9270d5dccf87cd95dab8ba467f315fb84e254ccea7470baf7e309df65bf01d9b`.
  Built by `scripts/hosted/Build-ClaimTrustRestart.ps1` from
  `ClaimTrustRestart.java` with regression tests against byte-exact live files.
- Uploaded and selected as `SERVER_JARFILE` on 2026-11-03; it applies GoldClaim
  1.0.64 and Neon Wall Wonders 11 on the next restart. See the
  [restart notes](docs/updates/next-restart.md).

## Staged: GoldClaim 1.0.64

- `artifacts/goldclaim-1.0.64.jar`:
  `bfed5f9a22681b235d792d6809b4f59c103547a3a6ded5218986b6bde77e7b8b`.
- Claim controls, sensitive block access and item/bucket boundaries are
  protected; `/claim trustlist` and `/claimblocks` report trust and usage.
- Installed by the claim trust restart launcher on the next server restart. See the
  [deployment and automation limitations](docs/updates/next-restart.md).

## Staged: Neon Wall Wonders 11.0.0

- Original, unmodified `artifacts/neon-wall-wonders-gwbzt3mk.jar`.
- SHA-256: `0c9fcc4a8d8468b477f11214efdf8220bfd3e3402b7d56478caeda84efc26f29`.
- The 87 neon item models include all 65 in the pack's version 8.0.0
  artifact. All 87 are in the kit tag and explicitly denied by Gang Shop.
- Created by oux_y using [CreativeMode.net](https://creativemode.net).
  [Official mod page](https://creativemode.net/mod/neon-wall-wonders-gwbzt3mk);
  [CreativeMode Mods License](https://creativemode.net/mod-license).
  Preserve the original filename and embedded notices when sharing for free.
- Published to clients and installed on the server at the next restart.
  Version 9 was a discarded update and was never used on the server; see the
  [compatibility and deployment notes](docs/updates/next-restart.md).

## v1.1.19 client log and model fixes

`artifacts/FoxFriend-fabric-1.18.2-1.21.1-6.0.3-gangs-nolog.jar` is FoxFriend
6.0.3 by suike (MIT, [upstream](https://github.com/sui-ke/Minecraft-Mod-FoxFriend))
with only the per-tick `System.out.println` in `FoxMixin.onTick` replaced by
no-ops. Its license and notice are in `artifacts/FoxFriend-LICENSE.txt`.
`artifacts/alexscaves-2.1.8.jar` and its source archive now have a
valid `grottoceratops_egg_template.json`.
[Details, checksums and rollback](docs/updates/v1.1.19.md).

## v1.1.18 installed server and supporting fixes

GoldClaim 1.0.62, GangSales 1.0.3, Command Limits 1.0.1, Ledger
1.2.8+gangs.compat.1, Biolith endfix-concurrency and Rank Badges 1.0.3
are published with checksum-pinned Packwiz metadata and corresponding source.
[Versions, checksums, source, update instructions and rollback](docs/updates/v1.1.18.md).
The exact server binaries were verified against hosted download readback.
Only Biolith and Rank Badges are client mod replacements in this release.

Ledger is LGPL-3.0; its complete corresponding-source bundle, including
upstream source, patch/build tools and license, is published alongside the jar:
`artifacts/ledger-1.2.8+gangs.compat.1-corresponding-source.zip`.

Biolith by TerraformersMC and gniftygnome is LGPL-3.0.
[Upstream source](https://github.com/TerraformersMC/Biolith).
The complete modified source is
`sources/biolith-fabric-1.0.1-beta.1-endfix-concurrency-source.zip`.
The targeted project in `sources/biolith-concurrency` compiles the concurrency
fix against the unchanged End-fix base and preserves unrelated jar entries.
See `artifacts/biolith-ATTRIBUTION.txt` for attribution and source links.

## Neon Wall Wonders 8.0.0

Original, unmodified `artifacts/neon-wall-wonders-gwbzt3mk (10).jar`.
SHA-256: `a0b36248530bfcbc398167b8cb7cade47d494053167822f33d2abe17d42158cc`.
The v1.1.17 update ZIP preserves this original filename in its `mods/` folder.
Packwiz installs the identical file on both server and clients.

Created by oux_y using [CreativeMode.net](https://creativemode.net).
[Official mod page](https://creativemode.net/mod/neon-wall-wonders-gwbzt3mk).
[CreativeMode Mods License](https://creativemode.net/mod-license).
Shared free of charge with the embedded license and notices preserved.

## Prepared: GoldClaim 1.0.62 gameplay and End library update

- `artifacts/goldclaim-1.0.62.jar`:
  `adb06111a1cdcde6961a8e062bd03a74fd9117d6c00d43bacfc0e344952042f9`.
- `artifacts/gangs-gameplay-restart-1.0.62.jar`:
  `819df708acd9b85551c86d11739a242f252da8ba37981eb8a5762373113c7237`.
- `artifacts/The-Gangs-End-Client-Update-20261030.zip`:
  `6b413c22b0c9eb1a78865bf2608112f0eca91567b97918edb75924a22c3f0eb9`.
- BCLib 3.0.16 (official, unmodified):
  `9266bcebcd2bf30adf569912736aa0ddb07c5e7f7580b1b9784d89d8cb6c01d3`.
- Better End 4.0.12 (official, unmodified):
  `9cc67d456953f853fee879ad697e8d1d1cf93c6536f31bddfd1d28b9f62fbba1`.
- Online player-name completion, double sponge absorption budget,
  current-border enforcement for shattered dimensional portals and
  UUID-scoped Fiw verification bypasses for two approved accounts.
- Production-mapped gameplay tests, original/new sponge measurements,
  updated-library startup and isolated restart installer regressions pass.
- The client ZIP contains only the two updated End libraries and replacement
  instructions. Deployment and rollback: [next restart](docs/updates/next-restart.md).
- Host upload checksum and persisted startup selection are verified.
  The live server was not restarted or reloaded; installation is deferred
  until the owner's next normal restart.
- Rebuild the remapped GoldClaim jar into `artifacts/`, download the two
  pinned upstream jars into `tmp/gameplay-smoke-dependencies/`, and run
  `scripts/hosted/Build-GameplayRestart.ps1`.

## Staged: GoldClaim 1.0.60 teleport destinations and player autofill

- `artifacts/goldclaim-1.0.60.jar`:
  `c08d6b91cc051066d425b946e9d08203c46deb83313aecaedf8ab77db1eb1574`.
- `artifacts/gangs-alex-weapon-restart-1.0.60.jar`:
  `f1379b4231e048643bb6f152f90e533a5d95687549d48dddc10a05d70cfcd3ae`.
- `/tpa` captures the destination's exact dimension, position and facing
  at acceptance; `/tpahere` retains the sender's request-time destination.
  Neither searches for safe ground or offsets the saved landing space.
  Both commands autocomplete other online players with prefix filtering.
- The real-command teleport fixture passes, including cross-dimension
  movement, exact yaw/pitch, asynchronous preparation, duplicate/death
  cancellation and player autocomplete. Cold enqueue took 17.1059 ms;
  36 ticks continued, with the longest preparation tick 196.7562 ms.
- Installer regressions pass for both the original pending update and
  upgrading an already-applied 1.0.59 installation, including exact backups,
  repeat/resumed starts, world locks, conflicting files and duplicate mods.
- Host readback verified all 39 baseline/payload files. The saved startup
  selection is the 1.0.60 installer; no restart, reload or console command
  was sent. Active GoldClaim remains 1.0.59 until the next normal restart.
  See [deployment and rollback](docs/updates/next-restart.md).

## Staged: GoldClaim 1.0.59 and combined next-restart installer

- `artifacts/goldclaim-1.0.59.jar`:
  `c212e89e0700dcbb3a84b9c5fcc173e17eb5de0d9c4901888f47a9ada055a808`.
- `artifacts/commandlimits-1.0.1.jar`:
  `871099548cf2117676566173d8e3afbc300ced11daa538b311882dab60a5f5be`.
- `artifacts/gangs-alex-weapon-restart-1.0.59.jar`:
  `9866cbe15ebe5c6927f129c47502ac3f9eec5a17d3dda5c6c4bbabad33694cac`.
- GoldClaim adds the Withered Wabbajack to the existing recipe/command/stack
  ban, including saved nested storage. Other weapons remain allowed.
- GoldClaim also gates restored vanilla `/locate` to staff/operators and
  protects six coastal biomes from Expanded Ecosphere's sea-level air cut.
  The upstream Ecosphere jar is unchanged.
- The dedicated next-start installer embeds both mods, the Wabbajack shop
  exclusion, 29 additive Alex recipe tags and six RSW-enabled cave configs.
  It pins the current host's launcher, old mods, shop and cave configs;
  backs up original files; refuses conflicts
  or a locked world; and starts original Fabric only after verification.
- Rebuild: build `sources/goldclaim`, copy its 1.0.59 remapped jar into
  `artifacts/`, then run `scripts/hosted/Build-AlexWeaponRestart.ps1`.
  The build runs isolated installer regression fixtures and removes them.
- All 79 GoldClaim unit tests pass. Recipe checks scan 551 recipes and
  resolve 118 common-tag references. Isolated production-mapped runtime checks
  pass 288 surface-rule evaluations and restored locate registration.
  Host upload checksum and saved startup
  selection verified; no live activation or power action was performed.
- Deployment status and rollback: [next restart](docs/updates/next-restart.md).
  These artifacts are staged on the host, not a new public modpack release.

## Ledger 1.2.8+gangs.compat.1 - dimension logging / Kotlin compatibility

- `artifacts/ledger-1.2.8+gangs.compat.1.jar`:
  `849cbd30aab935b71828c644d074c599aecd7a57c1c4931ff3ee11b5b27416ca`.
- LGPL corresponding source, original source, reproducible modification/build
  source and notices:
  `artifacts/ledger-1.2.8+gangs.compat.1-corresponding-source.zip`.
- Preserves existing database schema/IDs. Missing dimension registrations are
  inserted in the logging transaction; both removed experimental Kotlin
  Duration calls use stable APIs. No database reset or runtime downgrade.
- Actual block actions and Ledger queries passed across vanilla/Multiworld
  dimensions, including restart retention of 802 historical records, SQLite
  integrity checks and both nonempty-queue shutdown paths.
- Does not recover actions previously dropped by the original logging failure.

## GoldClaim 1.0.57 - nonblocking RSW entry

- `artifacts/goldclaim-1.0.57.jar`:
  `1bd258dbf520758c7a58ffab43ded0c2e3a190331a82a822c1541b3093bab581`.
- Reuses the ticketed asynchronous teleport-area loader for RSW entry rather
  than the blocking `getChunkFutureSyncOnMainThread` call. Commands only queue
  work; cancellation, failure, timeout and completion release chunk tickets.
- Retains the 1.0.56 verified-login fix and all previous gameplay policies.
- All 76 unit tests pass. Production Fabric cold-entry test: enqueue <250ms,
  120 waiting ticks, longest preparation 89.64ms (<1000ms), exactly one safe
  cross-world arrival, duplicate rejection and deferred death cancellation.

## GoldClaim 1.0.56 - verified-login hub transfer

Server-only hotfix; no client update is required.

- `artifacts/goldclaim-1.0.56.jar`:
  `5227f39e47aedb447270e47892c788d1b4ef1eb8e514051e8a5816285cfee447`.
- Automatic login transfers wait for Fiw's freeze to end before the existing
  20-tick settling delay. Without Fiw, the existing delay is unchanged.
- All 76 unit tests pass. Production-mapped Fabric tests pass with the original
  Fiw 2.2.0 jar and without Fiw: no transfer while frozen, exactly one transfer
  to the configured spawn in another dimension, stable final position and
  server chunk tracking.
- Installed while stopped and SHA-256 readback verified. Exact previous jar:
  `mods/goldclaim-1.0.55.jar.disabled-before-1.0.56` on the host.
- After restart, the user confirmed Rootamiss rejoins at hub spawn with terrain
  rendering. Server logs confirm verification completes before the hub transfer.

## GoldClaim 1.0.53 - restart-loop hotfix

- `artifacts/goldclaim-1.0.53.jar`:
  `0a5820f381d5e6d2d65fea9c836fcd2a0df8c452ba5de322191508349802bdfe`.
  It is identical to 1.0.52, except that the lunar policy defers instead of crashing
  when a world has no Enhanced Celestials forecast. Installed live; the
  1.0.52 launcher is no longer the startup jar.

## GoldClaim 1.0.52 - next-restart cleanup, cannon protection and RSW moons

Server-only patch; public Packwiz/client manifests are unchanged.
Fifteen-minute ground cleanup, per-block Supplementaries bomb/cannonball claim
protection, all seven RSW lunar events and preserved non-blood Wild moons.
Includes one-time saved-forecast migration, not a world reset.

- `artifacts/goldclaim-1.0.52.jar`:
  `743a81154fec5e668ef37ae50bd22dec9c80ac3682d482c61d73d026d664bd4c`.
- Embedded next-start installer `artifacts/gangs-claim-lunar-restart-1.0.52.jar`:
  `c34f1478c75ef75c9fbdf59f58104445cd6b87b61144e707e1ff09a46a6dada5`.
- All 76 unit tests, native and production-mapped Fabric projectile/lunar
  integration tests, and safe offline installer tests pass.
- Original 1.0.51 is backed up before replacement on the next normal start.
  No live restart or datapack reload is part of staging.

See [scope, validation, startup staging and rollback](docs/updates/next-restart.md).

## Vanish 1.5.5+1.20.1 - installed while stopped

The user's downloaded, unmodified Drex Vanish jar (MIT; server-only,
`melius-vanish`) was installed on the hosted server after the user stopped it
on 2026-10-23. Pending upload and final active jar were read back and SHA-256
verified. No permissions, configuration, worlds, other mods or startup settings
were changed. Not added to public Packwiz manifests. Server left offline;
no start/restart was sent. Production behavior awaits the next normal start.

- `artifacts/vanish-1.5.5+1.20.1.jar`:
  `80615c026bffc24832616b99ad44c2a74c17d2fdf0eaaca9c85aa77b51f23010`.
- Minecraft 1.20.1 / Java 17 / Fabric dependency checks pass.
- Isolated Fabric test with GoldClaim 1.0.51, GangSales 1.0.3 and LuckPerms
  passes actual command toggle, permission denial/grant, observer visibility,
  view permission, command-tree serialization and configuration generation.
- Keep bundled libraries inside the jar in production; do not separately
  install test-extracted libraries.
- Preserve upstream defaults: operator level 2 fallback or explicit
  permission grants. No client mod is required.

See [installation and validation details](docs/updates/next-restart.md).

## GoldClaim 1.0.51 / GangSales 1.0.3 - installed hosted hotfix

Installed and read-back SHA-256 verified with the hosted server offline on
2026-10-23. Includes the command/request-time teleport recovery below, blocks
future Waystones generation and use in all three RSWs, and disables existing
RSW Waystone cleanup per the user's selected preservation scope. Existing
blocks and registered destinations are untouched. Other-world policy remains
unchanged. Public Packwiz manifests are unchanged.

- `artifacts/goldclaim-1.0.51.jar`:
  `42d7878c3c550bcb3f8a6053f087bdb69df8b5ade60cc8dbd04ce46a9c7bd3d5`
- `artifacts/gangsales-1.0.3.jar`:
  `e1281a5c5541b0409d2b18babc7fc511f5c44b9e7dc2d5611a2a5266af2cf253`
- Rollback jars: `mods/goldclaim-1.0.49.jar.disabled-before-1.0.51` and
  `mods/gangsales-1.0.2.jar.disabled-before-1.0.3`, verified against their
  original hashes.
- Native Waystones generation/use/travel/preservation fixture passes in all
  three RSWs; outside-RSW generation/placement is preserved. All 76 unit tests,
  real LuckPerms command tests and final request/accept regressions pass.
- Biolith concurrency jar and normal Fabric startup are unchanged.
  **Server left offline; no start/restart sent.** See
  [installation and validation details](docs/updates/next-restart.md).

## GoldClaim 1.0.50 / GangSales 1.0.3 - command and teleport recovery

Historical locally prepared build, superseded on the host by GoldClaim 1.0.51
above; not selected by the public Packwiz manifest. Restore public-by-default `/gs`
commands while respecting explicit LuckPerms denies, share personal ender-chest
handling between `/ec` and `/enderchest`, and capture `/tpa` / `/tpahere`
destinations at request creation rather than acceptance. Existing terrain
preparation and RSW reset protections remain enabled.
See [installation and regression details](docs/updates/next-restart.md).

- `artifacts/gangsales-1.0.3.jar`:
  `e1281a5c5541b0409d2b18babc7fc511f5c44b9e7dc2d5611a2a5266af2cf253`
- `artifacts/goldclaim-1.0.50.jar`:
  `c762ee9a5e6541014d7ac2ccb0acee8d6f33dc46114a6885eff363a9f9af44a7`

Validation: both builds and GoldClaim's 76 unit tests pass. Real
Fabric/LuckPerms command availability/execution and focused request/accept
regressions pass. Request enqueue was 44.6753 ms; 61 server ticks continued
during preparation; the longest preparation tick was 304.2362 ms.
The broader `/rtp` smoke test exceeded its separate 1-second whole-tick
limit; that check is retained and documented rather than marked passing.

## Biolith End-fix concurrency hotfix - hosted server only

Installed with the hosted server confirmed offline after the 2026-10-22
RSW Overworld chunk-generation crash. Replacement and sub-biome lists are
rebuilt privately and published as immutable, volatile snapshots; writers are
serialized without locking normal biome reads. The existing End fix, biome
rates, saved ordering and all other jar entries are preserved. This is not a
public client release; the pack manifest remains unchanged.

- `artifacts/biolith-fabric-1.0.1-beta.1-endfix-concurrency.jar`:
  `6092613e0b6c4fa0df01e850a819e6766fa6cae48e2e3725463a79fb9cbeb497`
- Complete modified LGPL-3.0 source:
  `sources/biolith-fabric-1.0.1-beta.1-endfix-concurrency-source.zip`
  (`e16d3cba5eb04312f65552bcc3f1bdb791e2a35d979de24f4212cfc03026338d`).
- Targeted build source: `sources/biolith-concurrency`.
- Original hosted jar retained as
  `mods/biolith-fabric-1.0.1-beta.1-endfix.jar.disabled-before-concurrency`.
- Native Fabric regression: original fails unsafe-publication assertion;
  patched jar passes blocked/failed rebuild checks, 100 seeds x 1,001
  unchanged selections, and 800,000 parallel selection pairs during 2,000
  world reinitializations. Hosted hashes verified; server left offline.

## GangSales 1.0.2 - login permission recovery

Installed on the hosted server while stopped at the user's request. Uses the
typed LuckPerms API instead of an invalid reflective `Optional` cast during
login command-tree construction. Retains the 1.0.1 sold-item-name fix.
Real Fabric/LuckPerms permission allow/deny, command-tree serialization and
player chunk-position checks passed. Production login confirmation is pending.

- `artifacts/gangsales-1.0.2.jar`:
  `78049ad4c246730824ff72862f8af0fdb18768f7583a83c8b9fffd5be4579a09`
- Previous hosted jar retained as
  `mods/gangsales-1.0.1.jar.disabled-before-1.0.2`.
- Normal `fabric-server-launch.jar` startup restored; completed 1.0.49
  installer is historical and must not be reused against this hotfix.

## GoldClaim 1.0.49 / GangSales 1.0.1 - prepared server-only update

Staged for the next normal DedicatedMC restart, not a public client release.
Hub short-range teleport protection; 1% roaming bosses across the three RSWs
without changing chest rates; Oux_y's shulker-item/bin shop exclusions;
`Clean-up in 10` through `Clean-up in 1` with a final item total; correct
ordinary/custom item names in seller notifications. No restart was sent.
Keeps Teleport Optimizer enabled and pauses Spawn Animations via a
Paxi override; its original mod/settings and saved-mob recovery remain.
See [behavior and validation](docs/updates/next-restart.md).

- `artifacts/goldclaim-1.0.49.jar`:
  `d48f42f137b7474ad470e16c52cb7da1f2ab5dfe3f5c7f0fad063f0bc95fdcd3`
- `artifacts/gangsales-1.0.1.jar`:
  `e71ca9a5f0894dd389c7ec9daf72fb57dd8f8fba1806511791427c124033b5dd`
- `config/gangshop/overrides.json`:
  `96844b2537dc763d4e190d651b160e1b88647e206b65d59cedea57dd30b7ef89`
- Embedded-payload installer `artifacts/gangs-next-restart-1.0.49.jar`:
  `43e12b92440d7e69f612c811568f74522d57d66de877ac0eccd520844320fa51`
- `artifacts/gangs-performance-pause-1.0.0.zip`:
  `4ad553daf027e6f9da202bac560b9abba6ba16843b5aa77dcb0ad358de9ff4ef`
- Pinned compile-only Brutal Bosses 8.5 is the existing server mod, not a new
  runtime mod or an upgrade.

## GoldClaim 1.0.48 - RSW landing safety hotfix (applied)

Server-only hotfix over 1.0.47. Validates saved `/rsw` destinations for collision,
standing clearance, fluids and border bounds; removes the unsafe heightmap
fallback from border recovery. Falls back to safe ground near 0,0 if needed.
Installed and hash verified on 2026-10-21; no RSW reset.

- `artifacts/goldclaim-1.0.48.jar`:
  `e5aa5123210110f23d06079936706306ca5212b304c23be061c9c5c6e852b9f3`
- Installer `gangs-next-restart-1.0.48.jar`:
  `71daca425e5862f5331608ef939027148648a972cd6fcb8dc29af789fb3bc5c0`

## GoldClaim 1.0.47 - RSW border return instead of hub

Server-only next-start update over the live 1.0.46. Players outside the RSW
Overworld border are moved just inside it rather than to the hub, and `/rsw`
to a saved position outside the border lands just inside it.

- `artifacts/goldclaim-1.0.47.jar`:
  `b726759535bc16cd49cdb70387f83d763a650fd48d3c20a2cb73d36b19fe8677`
- Installer `gangs-next-restart-1.0.47.jar` (uploaded, hash verified):
  `5b2e74bfe464d462dc341c923f6ce78a8218e973ee139439f5d51f3fa1a7cbf0`

## GoldClaim 1.0.46 - staged RSW size, mob cap and ground cleanup update

Server-only next-start update that supersedes the unused 1.0.45 staging and
includes its fixes. RSW Overworld border 7,000 blocks (Nether/End 10,000);
Chunky pregenerates the central 60% of the area (radius 2,711); natural mob
caps cut 12.5%; dropped items are cleared every 5 minutes with countdown.

GoldClaim SHA-256:
`d697694195712ba23c7ae5f7076b5051e4b36236dc0fab5876cc4e1cf3805185`.
Installer `gangs-next-restart-1.0.46.jar` (uploaded, hash verified):
`ddeb50748e4c6612eda3bb583bfeb9024dab767b457df54e01087d7bac4e7dfb`.
See [staging details](docs/updates/next-restart.md).

## GoldClaim 1.0.45 - staged RSW generation reliability hotfix (superseded)

Server-only next-start update, not a published modpack release. Fixes the
off-thread world RNG access in chunk population, requests each FULL chunk
once with ticket cleanup, and stops an owned failed Chunky task.
RSW borders, coverage and reset schedules are unchanged.

GoldClaim SHA-256:
`eb4d46afaf752f6e4e8b913df7aa5ec9292a7a82a0290ab4555ee519a25d9316`.

The hosted next-start installer was uploaded and its hash verified:
`a42abe94538bdb8992e691ccc0ee0288777ebce30efddedf4cec074a7ebc5168`.
Startup selects `gangs-next-restart-1.0.45.jar` and adds
`-Dchunky.maxWorkingCount=8`. No restart or world mutation was performed.
See [staging details, incident evidence and validation](docs/updates/next-restart.md).

## GoldClaim 1.0.43 - grave recovery and teleport tick-stall hotfix

The server-only Packwiz entry selects `artifacts/goldclaim-1.0.43.jar`,
superseding 1.0.42. Grave owners can right-click or break their own Universal
Graves grave inside another player's claim without claim trust. Ownership is
checked using the grave's `getProfile()` UUID: the pinned Universal Graves
3.0.3 has no `isOwner(ServerPlayerEntity)` method, so the previous reflective
check silently denied the exception. API lookup failures now produce an error
log and do not bypass protection.

Other players' graves, ordinary blocks and inventories keep their existing
claim protections. Claim trust is not changed. PVP block-breaking restrictions
and locked portal bases still take precedence. Universal Graves retains its
own recovery rules; this exception only bypasses GoldClaim's claim checks.

Accepted `/tpa` and `/tpahere` requests and `/rtp` now prepare a ticketed 3x3
destination chunk area across server ticks rather than synchronously loading or
generating it inside the command handler. Players remain in their original
world until terrain is ready. Random candidates retain the existing attempt
limit, safety checks, dimension restrictions and 250-block RSW border inset;
unsafe candidates are retried across ticks. Success is announced only after
arrival. Terrain generation can still take time and consume CPU; this change
removes the blocking command-side wait, not all sources of server lag.

Only one preparation per player is allowed. Preparation cancels on player
death/disconnect, a source-world change, destination-world replacement or an
RSW reset/generation change, and times out explicitly after 120 seconds. For
accepted player requests, the destination player's accepted position/rotation
is retained. Either player may walk while terrain loads without cancelling
`/tpa` or `/tpahere`: arrival stays at the accepted location, not the destination
player's latest position. Destination death, disconnect or a world change
still cancels the request. Held chunk tickets are released after completion,
cancellation, exhausted candidates or failure. Other teleport routes are
unchanged.

SHA256: `8bb60d17a06799a9f0847b4b1b31972cf010c85d6625418c6c4fee3aa192bec7`.

Stage for the next normal restart (does not hot-load the mod):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\Stage-GameplayHotfix.ps1 -GameDirectory '<server directory>' -Version 1.0.43
```

The grave regression fixture runs against the real pinned Graves jar and checks
owner recovery permissions, UUID/name impersonation, unrelated grave protection,
ordinary block/inventory protection, and unchanged claim trust. From
`sources/goldclaim`, with the accepted fixture EULA and extracted nested Graves
dependencies plus the existing smoke-test dependencies:

```powershell
$env:GOLDCLAIM_GRAVE_SMOKE = 'true'
.\gradlew.bat runPolicySmoke "-PsmokeGravesJar=<Graves 3.0.3 jar>" "-PsmokeModsDir=<smoke dependencies directory>" --console=plain
```

The production-remapped teleport fixture uses `GOLDCLAIM_TELEPORT_SMOKE=true`
and the same smoke mod, with Multiworld, BetterEnd, the real Teleport Optimizer
and their dependencies. It checks cold cross-world RSW End arrival, continued
server ticks during generation, enqueue latency below 250ms, preparation ticks
below 1000ms, duplicate rejection, movement without cancellation or destination
tracking, target death cancellation, and the
actual RSW `/rtp` command's safe, border-inset landing.

Final movement-policy production fixture: cold End enqueue 3.13ms, 79
continuing server ticks, and longest preparation tick 344.54ms on the shared
test machine. Both players walking still arrives at the accepted location.
The combined hotfix also passed the real Graves ownership regression before
the movement-only adjustment and eight existing gameplay policy unit tests.

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

`artifacts/gangscosmetics-2.1.0-test.zip` is an experimental, manually installed
test bundle, not a production Packwiz update. It contains the matching
client/server test jar, an original CC0 crown import, and
[test instructions](artifacts/gangscosmetics-2.1.0-test-INSTRUCTIONS.txt).
It adds an OP-level-2 admin catalog with persistent enable/remove controls,
restricted static model imports, live Night.design metadata search (browse-only,
not third-party asset redistribution), and render-scoped wardrobe previews for
locked cosmetics. The unsupported shields stay excluded. Both sides need the
test jar; visual/multi-client tests are required before production deployment.
The production manifests and update installers remain pinned to 2.0.2.

`artifacts/polymer-bundled-0.5.20+1.20.1.jar` is the user-supplied official Polymer bundle used on both client and server for server-driven resources.

`artifacts/rankbadges-1.0.2.jar` is a first-party Rank Badges build. Its source is maintained in `sources/rankbadges`.

`artifacts/soulsbackpackscompat-1.1.0.jar` is the current both-sides compatibility mod for Marium's Soulslike Weaponry 1.4.9 and Sophisticated Core 1.2.7.15.166. It retains the 1.0.1 null ability-list repair and adds a guard around Sophisticated Core's capped multiplication helper: zero or negative item max-stack values return zero instead of reaching division by zero. This covers Polymer's client-side `VirtualClientItem` reporting a zero max count while a backpack stack upgrade is active. Backpack contents, upgrades and NBT are not modified. Replace older compatibility jars on both client and server; do not install multiple versions. Its source is maintained in `sources/soulsbackpackscompat`.

Compatibility validation: run `gradle test build` in `sources/soulsbackpackscompat`. For the real-mod smoke test, run `gradle runAbilitySmoke -PsmokeMods=<directory>` with the pack's Soulslike Weaponry, Sophisticated Backpacks/Core and their runtime dependencies (including extracted nested dependency jars). The isolated server-side fixture exits before creating a world and requires a fresh success marker, checking the zero-count guard, mutable per-item recovery, inventory ticking, existing weapon abilities and unchanged backpack NBT.

`artifacts/gangshop-1.0.8.jar` is the server-only first-party Gang Shop build. It provides the shared Gang Bucks wallet and the server shop GUI, including the updated catalog exclusions and Farmer's Delight/Nether's Delight food categorization.

`artifacts/gangsales-1.0.0.jar` is the server-only first-party GangSales build. Its source is maintained in `sources/gangsales`; it requires Gang Shop and uses its Gang Bucks wallet for player-to-player sales.

`artifacts/commandlimits-1.0.0.jar` is a server-only first-party mod that caps expensive `locate` command search radii.

`artifacts/Multiworld-Fabric-1.20.1.jar` is the Multiworld build used by GoldClaim for managed PVP/RSW dimensions. Its portal wand handler is patched to ignore wooden axes so it does not conflict with WorldEdit's wand; Multiworld itself remains installed.

`artifacts/alexscaves-2.1.8.jar` is the custom Fabric build used by this pack. Its corresponding source archive is at `sources/alexscaves-fabric-2.1.8-source.zip`. The upstream Alex's Caves project is LGPL-3.0-only; the source archive and this notice are published with the modified binary to provide the required source access and license notice.

`artifacts/alexscaves-2.1.8-gangs-levelseed.jar` (SHA-256 `ec1606e1efdf43f635cf0e74a559a229e133a9f90f85c64119a9027ff421ceeb`) is that build with four classes rebuilt so cave biomes and cave maps use each level's own seed (see `docs/updates/next-restart.md`). Its corresponding source archive is `sources/alexscaves-fabric-2.1.8-levelseed-source.zip`, and the patch project with its build and smoke test is `sources/alexscaves-levelseed`. Same LGPL-3.0-only notice as above. `artifacts/alexscaves-2.1.8.jar` is kept for rollback.

`artifacts/biolith-fabric-1.0.1-beta.1-endfix.jar` keeps Biolith installed on both sides and falls back to the selected End biome when its search tree is uninitialized. Its modified LGPL-3.0 source is at `sources/biolith-fabric-1.0.1-beta.1-endfix-source.zip`, with attribution in `artifacts/biolith-ATTRIBUTION.txt`.

The following user-supplied third-party jars are mirrored here for direct packwiz delivery: Adaptive Tooltips 1.3.0, Archers 1.3.0+1.20.1, AzureLib 2.0.41, Better Combat Particles (bc_particle) 0.0.6, End's Phantasm 1.4, Exposure 1.9.20, Extra RPG Attributes 1.3.2, Immersive Armors 1.7.2+1.20.1, Joy of Painting fabric-1.20.1-2.0.0, Necronomicon 1.6.0+1.20.1, Nullscape 1.2.8, RangedWeaponAPI 1.1.4+1.20.1, Roughly Enough Items 12.1.785, Shield Overhaul 2.0.4, Spellblades and Such 2.4.0+1.20.1, and Wizards 1.4.1+1.20.1.

Other third-party mods remain downloaded from their official Modrinth or CurseForge metadata. This directory must not contain unapproved third-party binary mirrors.
`artifacts/oux-badges-hiec7dbm.jar` (SHA-256 `7f48bbfac7ab4f3340271ba66806e21ac21b8674bb8ed77162e8b74fc3f3c810`) is **Oux Badges 6.0.0, made by oux_y using [CreativeMode.net](https://creativemode.net)**. Mod page: https://creativemode.net/mod/oux-badges-hiec7dbm. License: CreativeMode Mods License, https://creativemode.net/mod-license. It is the original, unmodified file exported by CreativeMode, with its original filename and embedded license kept. All rights are reserved by Argon, Inc. (d/b/a CreativeMode).

`artifacts/gangs-oux-badges-compat-1.0.2.jar` (SHA-256 `6df71ec3f2f9c87e8ee262f4ee22870537571cbf44c5517a3e4564e27347f54d`; earlier builds 1.0.0 and 1.0.1 are kept alongside it) is a first-party, client-only compatibility mod for Oux Badges 6.0.0, with source in `sources/ouxbadges-compat`. It contains no Oux Badges code or assets and is not endorsed by CreativeMode. `artifacts/gangs-oux-badges-restart-1.0.72.jar` (SHA-256 `befda734de424031052c8172df52479c126c45116591265100d795884cbe2a6d`) is the server restart launcher that installs Oux Badges and GoldClaim 1.0.66.
