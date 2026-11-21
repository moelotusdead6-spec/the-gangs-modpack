package com.gangsboots.smoke;

import com.gangsboots.mod.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

public final class BootsSmoke implements DedicatedServerModInitializer {
    private boolean ran;

    @Override
    public void onInitializeServer() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ran) return;
            ran = true;
            if (!Files.exists(Path.of("LOCAL_TEST_ONLY"))) {
                throw new IllegalStateException("Boots smoke test requires a local fixture marker");
            }
            ItemStack boots = new ItemStack(ModItems.BOOTS_OF_LIGHTNESS);
            require(Registries.ITEM.getId(boots.getItem()).equals(new Identifier("gangsboots", "boots_of_swiftness")),
                "Existing item ID changed");
            boots.setDamage(200);
            require(!boots.damage(1000, Random.create(), null) && boots.getDamage() == 200,
                "Direct damage consumed boots fuel");
            var zombie = EntityType.ZOMBIE.create(server.getOverworld());
            require(zombie != null, "Could not create fixture entity");
            boots.damage(1000, zombie, entity -> { throw new IllegalStateException("Boots broke from damage"); });
            require(boots.getDamage() == 200 && boots.getCount() == 1, "Armor damage consumed or broke boots");
            boots.setDamage(201);
            require(boots.getDamage() == 201, "Flight fuel cannot consume durability");
            ItemStack normalBoots = new ItemStack(Items.DIAMOND_BOOTS);
            normalBoots.damage(1, Random.create(), null);
            require(normalBoots.getDamage() == 1, "Normal armor no longer takes damage");
            require(server.getCommandManager().getDispatcher().getRoot().getChild("give_lightness_boots") != null
                && server.getCommandManager().getDispatcher().getRoot().getChild("give_swiftness_boots") != null,
                "Boots command or compatibility alias missing");
            System.out.println("BOOTS_SMOKE_PASS");
            server.stop(false);
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
