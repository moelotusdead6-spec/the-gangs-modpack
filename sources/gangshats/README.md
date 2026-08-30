# Gangs Hats

Fabric 1.20.1 companion mod for The Gangs Modpack.

## Behavior

- `/hat` equips the entire active main-hand stack into the player head slot.
- If the current head slot has Curse of Binding, `/hat` fails without changing inventory.
- If the old head item can fit in the player's inventory, it is inserted there.
- If the inventory is full, the old head item is placed into the now-empty active hand slot.
- `/hat` never drops items.
- Direct helmet-slot insertion is handled by Server Hats in the pack, with `config/serverhats.json` set to allow all non-helmet items.

## Rendering

- Vanilla placeable block items render around the player head at a compact block scale.
- Modded block items render through their item models to avoid incompatibilities with placed-block renderers.
- Non-placeable items render above the player head like a crown.
- Plushie namespaces are forced into crown rendering even when the items are placeable.
