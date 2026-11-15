# Gangs Safe Graves

Server-only compatibility mod for the pack-pinned Universal Graves
3.0.3+1.20.1. Upstream jars are not modified.

## Placement

Only deaths below the world's bottom Y or at/above its top Y change placement.
The grave searches for three air blocks in the same X/Z column, near the
column's surface. An empty column prefers Y64. Graves can float; no support
block or platform is created. Positions stay inside the legal build height.

Existing upstream protection, predicates and block checks still apply.
Deaths within build height retain upstream behaviour. Outside the world border,
X/Z are clamped inside it. If the column has no permitted safe position, the
mod logs an error and lets the upstream placement search run.

## Death archives

Before `ServerPlayerEntity.onDeath` drops or transfers items, a compressed NBT
snapshot is written atomically to:

```text
<world>/gangs-death-backups/<player-uuid>/<timestamp>-<snapshot-uuid>.dat
```

Format version 2 includes full `Player` NBT (inventory, armour, offhand, XP and
mod-saved components), `DataVersion`, player name, dimension, death cause and
timestamp. When Sophisticated Backpacks is loaded, `BackpackContents` also
copies the exact external entries for referenced backpacks, including nested
backpacks, slot/count NBT, upgrades and settings. Duplicate/cyclic references
are included only once. These are independent copies, not mutable pointers.

`BackpackContentsComplete=false` and `MissingBackpackContents` explicitly
identify missing external storage; the player's NBT is still archived. Missing
contents and file I/O failures are logged and reported to the player.

Archives have no automatic expiry or restoration. Include this directory in
server backups and monitor disk usage. Restrict it to administrators: snapshots
contain player data. Keep only as much history as your recovery policy needs;
do not purge the only evidence of an unresolved death.

## Recovery

Select the relevant pre-death snapshot and compare against current inventory,
graves, containers and backpack references before returning anything. Do not
replace live player files while the server runs, or blindly restore full
`Player` NBT: it includes the dying player's position/health and mod state.

For a confirmed lost Sophisticated Backpack whose original storage survives,
the upstream admin command `sbp give <online-player> <backpack-uuid>` reconnects
the item to that exact storage. Never issue this for a backpack already held
or safely placed/stored elsewhere. If original storage is missing or changed,
the archived contents require a controlled, offline restoration with backups
and duplicate checks. There is no automatic admin restore command in this mod.

## Validation

With Java 17 and an accepted EULA in `tmp/gangsgravescompat-smoke/eula.txt`:

```powershell
gradle -p sources\gangsgravescompat build runGraveSmoke remapSmokeJar --console=plain
```

The smoke mod exercises real player deaths and deferred grave transactions,
ordinary floating placement, below/above-height relocation, empty columns,
upstream protection vetoes, exact inventory/armour/offhand/XP snapshots,
and nested external backpack contents with missing-data reporting.
When run with the real Sophisticated Backpacks dependencies, it also checks
the death hook's copy of live external storage. A production-mapped smoke jar
is built separately. Smoke mods must never be installed on the live server.

Installation requires the owner's next restart; never use `/reload`.
The staged [restart installer](../../scripts/hosted/GravesRestart.java) pins
the tested upstream jars, acquires the world lock, installs this mod without
replacing existing mods, and continues the previously selected Mount/Claim
launcher. Its [builder](../../scripts/hosted/Build-GravesRestart.ps1) takes
`-GravesJar`, `-BackpacksJar` and `-CoreJar` paths for the exact upstream
fixtures and runs installation regression tests before packaging.
