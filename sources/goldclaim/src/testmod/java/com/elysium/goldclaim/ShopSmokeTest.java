package com.elysium.goldclaim;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashSet;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

final class ShopSmokeTest {
    static void verify() {
        try {
            Object catalog = Class.forName("com.gangs.gangshop.GangShopMod").getField("CATALOG").get(null);
            var entryMethod = catalog.getClass().getMethod("getEntry", Identifier.class);
            var namespaces = new HashSet<String>();
            int count = 0;
            for (var item : Registries.ITEM) {
                Identifier id = Registries.ITEM.getId(item);
                if (!id.getNamespace().startsWith("mcw")) continue;
                Object entry = entryMethod.invoke(catalog, id);
                if (entry == null) throw new IllegalStateException("Macaw's item is absent from shop: " + id);
                Object category = entry.getClass().getMethod("category").invoke(entry);
                if (!"macaws".equals(category.getClass().getMethod("getId").invoke(category))) {
                    throw new IllegalStateException("Macaw's item has a separate shop category: " + id);
                }
                namespaces.add(id.getNamespace());
                count++;
            }
            if (namespaces.size() != 5 || count == 0) {
                throw new IllegalStateException("Shop fixture did not exercise all five Macaw's mods.");
            }
            var categories = (Collection<?>)catalog.getClass().getMethod("categories").invoke(catalog);
            long macaws = categories.stream().filter(category -> {
                try {
                    return "macaws".equals(category.getClass().getMethod("getId").invoke(category));
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Cannot inspect shop category.", failure);
                }
            }).count();
            if (macaws != 1) throw new IllegalStateException("Shop must contain exactly one Macaw's category.");
            System.out.println("PASS: all " + count + " registered Macaw's items from five mods share one shop category.");
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException("Shop integration fixture failed.", failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot inspect optional Gang Shop integration.", failure);
        }
    }
}
