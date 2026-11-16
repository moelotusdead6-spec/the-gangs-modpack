# Gangs Dragon Loot

Server-only Fabric 1.20.1 patch for Simply Swords 1.56.0-1.20.1.
Install only the production `gangsdragonloot-1.0.0.jar`, never the smoke jar.
Upstream jars and client installations do not change.

The vanilla entity-death loot call keeps its normal context and non-weapon
results. For Ender Dragons only, Simply Swords/Simply More unique-tagged weapon
results are replaced by two different, uniformly selected Simply Swords uniques.
Selection uses the world's advancing RNG, not a saved `DeathLootTableSeed`.
Pairs may recur on later kills, but never contain the same weapon twice.
The pool is all registered `simplyswords:` items in `#simplyswords:uniques`,
including transformed unique variants. Remnants are not unique-tagged weapons.

Other entity loot, chest loot, XP, direct drops from other mods and the
`doMobLoot` gamerule retain their normal behavior. This does not claim that a
fixed seed was proven to cause the reported live issue: the inspected upstream
configuration supplied two independent 50% pools, not a guaranteed pair.

## Validation

Build with Java 17 using the existing wrapper:

```powershell
sources\goldclaim\gradlew.bat -p sources\gangsdragonloot build remapSmokeJar --no-daemon --max-workers=2
```

The production-mapped smoke mod requires the actual pack-pinned Simply Swords,
Simply More and Alex's Mobs jars, Fabric API and their dependencies. Run it in
an isolated Fabric server with an accepted EULA, loopback-only random port,
flat test world and `scripts/hosted/farseer-disabled.json` copied to that test
server's `config/alexsmobs/farseer.json`. A successful run stops itself and writes
`dragon-loot-smoke-passed.txt`. Missing results or any exception are failures.

It verifies 2,000 rolls sharing one saved seed, distinct pairs, every eligible
unique reached, non-weapon/other-entity loot preservation, 12 actual dragon
deaths, `doMobLoot=false`, and the native Alex's Mobs biome matcher rejecting
every registered biome for Farseer.

The [next-restart builder](../../scripts/hosted/Build-DragonFarseerRestart.ps1)
also runs regression tests for backups, repeat/interrupted installation,
changed configs/dependencies, corrupt payloads, renamed duplicate mods, world
locking, packaged payloads and transitive predecessor class loading.
`GalosphereConsoleTest.java` tests the real Log4j configuration: Galosphere
WARN/ERROR messages disappear only from console; file exceptions and other
console errors, INFO and FATAL remain.

Activate only at the owner's next restart. No live reload is needed.
