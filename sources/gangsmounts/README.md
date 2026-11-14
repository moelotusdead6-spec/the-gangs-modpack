# Gangs Mounts

Mount integration for the existing Gangs Cosmetics wardrobe.

This addon requires the **unmodified live Gangs Cosmetics 2.0.2 jar**. The build
checks its SHA-256 against the production manifest. It does not replace or bundle
that jar, its wardrobe, its existing halos/weapons/head/body/wings categories, or
its models. A single Mounts category is inserted before Settings.

## Test instance

Use Fabric 1.20.1 / Java 17 with Gangs Cosmetics 2.0.2, Mythic Mounts Fabric 7.2,
GeckoLib 4.8.4, Patchouli 85, Fabric API, and this addon on both sides. Do not
install retired Gangs Hats or experimental Gangs Cosmetics 2.1.0 alongside it.

For Loom development runs, extract Patchouli's bundled `fiber-0.23.0-2.jar` and
GeckoLib's bundled `mclib-20.jar` into the dependency directory; Loom does not
expose these local jars' nested dependencies automatically. The original
cosmetics renderer's shaded libraries do not supply GeckoLib 4's MCLib API.

Include the pack-pinned Simply Swords and WilderNature jars to retain the live
key/voucher icons, plus their Architectury, Cloth Config, Trinkets and Cardinal
Components dependencies. Normal Fabric installs discover bundled libraries
automatically; use these **production jars** for the exact visual comparison.
All these are unmodified upstream jars, not copied replacement textures.

The provided separate test launcher uses the ordinary Fabric client entrypoint,
the official Minecraft client/assets, and the production addon jar. It does not
join or update the live server. Its offline `MountTester` profile is for
single-player testing only.

Loom development runs are available with `runClient`, `mountTestModsDir`, and
`mountTestRunDir`, but are not the exact visual test: WilderNature has upstream
named-mapping conflicts and is excluded there, so its voucher icon is missing.
Expose Simply Swords' nested SpruceUI, Cardinal Components' nested modules, and
Cloth Config's Basic Math when running that additional development environment.
`writeClientLibraries -PmountClientLibraries=<output-file>` exports the resolved
standard libraries for preparing a standalone test client.

In the **isolated client's** `config\gangscosmetics.json`, set
`unlockAllForTesting` to `true` and `testMode` to `false`. This uses the live
wardrobe's existing test switch: all original cosmetics and mounts are available
immediately. Never copy test config/world data to production.

Open `/cosmetics` and choose Mounts. Every tile contains a real 3D mount preview.
Click an unlocked mount to summon it; click its active tile to recall and store
it. Only one can be active at a time. Locked tiles remain visible for previews.
The client validates every preview's actual renderer texture resource when the
catalog arrives and reports missing resources explicitly.
Preview animation uses advancing entity age and fractional-frame interpolation,
not an unticked entity whose animation clock loops backward. Hover rotation is
continuous and honors the original wardrobe's animated-preview setting.

In **/cosmetics > Settings**, beside Open Wardrobe, assign **Summon Last Mount**
and **Recall Mount**. Click a shortcut box, then press the desired key; Backspace
clears it and Escape cancels. Both start unbound to avoid existing pack conflicts.
The same bindings also remain available in Minecraft's Controls menu, and
previously assigned keys are preserved.
Successful menu selections are remembered across recall, logout and restarts.
The summon shortcut restores that last mount using the same ownership, one-active
mount and safe-space checks as the GUI. Shortcuts only work outside other screens.

Mounts are real adult, tamed, owned, saddled Mythic Mounts. Every damage call and
direct health reduction is blocked, including damage that bypasses ordinary
invulnerability; `/kill` is blocked too. They cannot die or breed;
vanilla horses and other mods' entities are unaffected. Normal native flight remains available. Recall saves
native NBT, including names, variants, stats and equipment. Logout, shutdown,
chunk unload, dimension changes and approaching the void trigger recall.

Mythic Mounts are **cosmetics-only**. Their entries are removed from every biome's
spawn pool. World spawn routes reject mounts not created through this addon's
validated summon service (including spawn eggs, spawners and vanilla `/summon`).
Previously saved unmanaged/wild Mythic mounts are removed when their chunks load;
stored cosmetic mounts retain their existing recovery/recall behavior. Keys and
vouchers unlock menu ownership, never randomly spawn animals in the world.

The Mythic Mounts Summoning Staff recipe and use are disabled, and the staff is
hidden from creative item lists. Existing staff items are preserved but cannot
summon or link mounts; use the cosmetics menu or mount shortcuts instead.

The addon bridges Mythic Mounts 7.2's old renderer texture override to GeckoLib
4.8.4's current entity texture method. It uses the mod's own texture lookup and
unmodified assets; no replacement skins or copied third-party assets are shipped.

## Keys and vouchers

Cosmetic Keys retain the live server's authenticated token ledger and reward
behavior, with mounts appended to the pool. First rolls unlock ownership;
duplicates produce authenticated, mount-specific vouchers. Anyone without that
mount can redeem its voucher once. An existing owner retains the mount voucher
for trading. Copied or `/give`-created tokens remain invalid.

To test redemption rather than ownership override, disable
`unlockAllForTesting` in the test config and restart the test.

## Validation

With an accepted EULA and loopback-only server properties in a separate run dir:

```powershell
.\gradlew.bat runServer build "-PmountTestModsDir=C:\MountTest\mods" "-PmountTestRunDir=C:\MountTest\smoke" -PmountSmoke
```

The fixture also requires Gang Shop with the Mythic Mounts exclusion. It runs
against the pinned production cosmetics jar and tests the
preserved catalog, real cosmetic-key mount unlocks/duplicates, voucher transfers
and copy rejection, all mount summon/recall lifecycles, invulnerability, breeding,
NBT persistence and travel/logout recall. It stops automatically and requires a
fresh success marker. In-game visual/UI/flight validation is separate from these
server tests.
The fixture also attacks every mount with every registered damage type while its
vanilla invulnerability flag is disabled, attempts direct health reduction, and
checks shortcut summon/recall plus persisted last-selection state.
It checks every biome spawn pool, rejects unmanaged spawns of every mount type,
removes loaded unmanaged mounts, and confirms ordinary horse spawning still works.

`.\gradlew.bat remapSmokeJar build` builds the fixture for an ordinary Fabric
server as well. Install the `gangsmounts-smoke-1.0.0-production.jar` only in a
disposable, loopback-bound validation server with `unlockAllForTesting: false`.
It runs the same assertions and shuts down automatically.
