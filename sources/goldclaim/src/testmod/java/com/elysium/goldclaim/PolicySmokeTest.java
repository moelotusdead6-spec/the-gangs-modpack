package com.elysium.goldclaim;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.command.argument.ItemStackArgument;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class PolicySmokeTest implements ModInitializer {
    @Override
    public void onInitialize() {
        Item inception = Registry.register(Registries.ITEM, new Identifier(BannedContent.INCEPTION_UPGRADE),
            new Item(new Item.Settings()));
        Registry.register(Registries.ITEM, new Identifier("goldclaim_policy_smoke:other_plushie"),
            new Item(new Item.Settings()));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            require(new ItemStack(inception).isEmpty(), "new inception stacks must be empty");
            ItemStack refilled = new ItemStack(inception);
            refilled.setCount(64);
            require(refilled.isEmpty(), "inception stacks cannot be refilled");
            NbtCompound installed = stack(BannedContent.INCEPTION_UPGRADE);
            require(ItemStack.fromNbt(installed).isEmpty(), "saved/installed inception must load empty");
            NbtList contents = new NbtList();
            contents.add(stack(BannedContent.INCEPTION_UPGRADE));
            contents.add(stack("minecraft:diamond"));
            NbtCompound tag = new NbtCompound();
            tag.put("Items", contents);
            ItemStack container = new ItemStack(Items.SHULKER_BOX);
            container.setNbt(tag);
            require(contents.size() == 1 && contents.getCompound(0).getString("id").equals("minecraft:diamond"),
                "nested storage must retain unrelated contents");
            try {
                new ItemStackArgument(Registries.ITEM.getEntry(inception), null).createStack(1, false);
                throw new IllegalStateException("banned item command was not rejected");
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
                require(expected.getMessage().contains(BannedContent.INCEPTION_UPGRADE),
                    "command rejection must identify the banned item");
            }
            require(server.getRecipeManager().get(new Identifier("goldclaim_policy_smoke:inception")).isEmpty(),
                "inception recipe must be removed");
            require(server.getRecipeManager().get(new Identifier("goldclaim_policy_smoke:other_plushie")).isEmpty(),
                "other plushie recipe must be removed");
            require(server.getRecipeManager().get(new Identifier("sarosplayerplushiemod:plushie")).isPresent(),
                "actual Saro plushie recipe must remain");
            require(!new ItemStack(Items.DIAMOND).isEmpty(), "unrelated items must remain usable");
            try {
                Files.writeString(Path.of("policy-smoke-passed.txt"),
                    "PASS: runtime item creation, refill, NBT load, nested storage, commands, recipe removal, actual Saro recipe.\n");
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot record smoke-test result", failure);
            }
            server.stop(false);
        });
    }

    private static NbtCompound stack(String id) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("id", id);
        nbt.putByte("Count", (byte)1);
        return nbt;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
