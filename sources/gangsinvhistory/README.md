# Gangs Inventory History

Server-only Fabric mod for Minecraft 1.20.1. It captures the main inventory,
hotbar, armour, offhand and ender chest on join, leave, death (before drops) and
dimension change. With Sophisticated Backpacks installed it also archives storage
referenced by carried backpacks, including nested bags.

## Usage and permissions

- `/invhistory list <player> [page]` lists snapshots newest first, ten per page.
  Click `[View]` or run `/invhistory view <player> <snapshotId>` in game.
  Use the player's UUID if their name cannot be resolved.
- History is read-only for everyone, including level-4 operators: no editing,
  item copying, or rollback. Views are logged. Use the Ender chest button or
  click a backpack to inspect its archived contents.

| Permission | Allows | Vanilla fallback |
| --- | --- | --- |
| `gangsinvhistory.view` | List and open snapshots | Operator level 3 |

With Fabric Permissions API and LuckPerms, grant `gangsinvhistory.view` to
the intended staff groups instead of granting operator access. This is the
only history permission. InvView is a separate live-inventory tool with its
own permissions.

## Setup, retention and limits

Install only `gangsinvhistory-1.0.0.jar` in the server's `mods` directory for the
next normal restart. Requires Java 17+, Fabric Loader 0.15.6+ and Fabric API for
1.20.1. No extra client mod is required. Do not use `/reload` to install or
configure it, and do not deploy the smoke jars.

`config\gangsinvhistory.json` is created at startup. `retentionDays` defaults to
`7` and accepts `1`–`365`; changes take effect at the next restart. The server
prunes expired snapshots on startup and hourly. Compressed NBT is stored under
`<world>\gangs-inventory-history\<player-uuid>\`. Restrict access to those
files and monitor disk usage; copied backups are not pruned by the mod.

History is event-based, not continuous or retroactive. It does not record
placed backpacks or guarantee a final snapshot on crashes or forced kills.
Keep separate server backups. The shutdown fix catches and logs runtime
exceptions from the player disconnect phase so vanilla can still save the
worlds. It does not fix the initial crash or protect against forced termination
or a failure in the save itself.

## Build and smoke tests

From this directory:

```powershell
.\gradlew.bat build
.\gradlew.bat runHistorySmoke "-PhistorySmokeFailDisconnect=true"
.\gradlew.bat runHistoryProductionSmoke
```

The dev runner uses the repository's `tmp\gangsinvhistory-smoke` fixture. Omit
the property for a normal smoke run without fault injection. The production
task uses the existing local `tmp\gangsinvhistory-production` Fabric fixture
(launcher, dependencies including Sophisticated Backpacks, accepted EULA,
loopback address and ephemeral port). It tests the remapped jars and asserts
that a fresh chunk marker and exact backpack contents reach disk after an
injected `RejectedExecutionException`. This simulates the disconnect failure;
it does not run LuckPerms.
