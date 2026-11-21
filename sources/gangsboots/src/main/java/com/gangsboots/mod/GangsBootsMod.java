package com.gangsboots.mod;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.inventory.Inventory;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.util.math.random.Random;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import net.minecraft.command.argument.EntityArgumentType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class GangsBootsMod implements ModInitializer {
    public static final String MOD_ID = "gangsboots";
    private static final boolean ENABLE_FLIGHT_TICK = true;
    private static final String PVP_DIMENSION = "multiworld:pvp";

    private static final int PLAYER_BOOTS_ROLL_BOUND = 400;
    private static final int TICKS_PER_DURABILITY = 72;
    private static final int FALL_PROTECTION_MAX_TICKS = 20 * 30;
    private static final Map<UUID, Integer> FLY_TICKS = new HashMap<>();
    private static final Map<UUID, Integer> FALL_PROTECTION_TICKS = new HashMap<>();

    // PAL integration (resolved reflectively to avoid a hard compile dependency).
    private static final boolean PAL_AVAILABLE;
    private static final Object PAL_FLIGHT_SOURCE;
    private static final Object PAL_ALLOW_FLYING_ABILITY;
    private static final Object PAL_FLYING_ABILITY;
    private static final Method PAL_GRANT_ABILITY;
    private static final Method PAL_REVOKE_ABILITY;

    static {
        boolean available = false;
        Object source = null;
        Object allowFlying = null;
        Object flying = null;
        Method grant = null;
        Method revoke = null;

        try {
            Class<?> palClass = Class.forName("io.github.ladysnake.pal.Pal");
            Class<?> vanillaAbilitiesClass = Class.forName("io.github.ladysnake.pal.VanillaAbilities");

            Method getAbilitySource = palClass.getMethod("getAbilitySource", String.class, String.class);
            source = getAbilitySource.invoke(null, MOD_ID, "boots_of_swiftness_flight");

            Field allowFlyingField = vanillaAbilitiesClass.getField("ALLOW_FLYING");
            Field flyingField = vanillaAbilitiesClass.getField("FLYING");
            allowFlying = allowFlyingField.get(null);
            flying = flyingField.get(null);

            // Resolve by name + arity to remain compatible with obfuscated runtime class names.
            for (Method method : palClass.getMethods()) {
                if (method.getName().equals("grantAbility") && method.getParameterCount() == 3) {
                    grant = method;
                } else if (method.getName().equals("revokeAbility") && method.getParameterCount() == 3) {
                    revoke = method;
                }
            }

            if (grant == null || revoke == null) {
                throw new NoSuchMethodException("Unable to resolve PAL grant/revoke methods");
            }

            available = true;
        } catch (Throwable ignored) {
            // Fallback path will use vanilla ability toggles if PAL is absent or unavailable.
        }

        PAL_AVAILABLE = available;
        PAL_FLIGHT_SOURCE = source;
        PAL_ALLOW_FLYING_ABILITY = allowFlying;
        PAL_FLYING_ABILITY = flying;
        PAL_GRANT_ABILITY = grant;
        PAL_REVOKE_ABILITY = revoke;
    }

    @Override
    public void onInitialize() {
        ModItems.register();
        if (ENABLE_FLIGHT_TICK) {
            registerFlightAndFuelTick();
        }
        registerFallProtection();
        registerAdminCommand();
    }

    private static void registerFlightAndFuelTick() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                handlePlayerTick(player);
            }
        });
    }

    private static void handlePlayerTick(ServerPlayerEntity player) {
        convertLegacyBoots(player);
        tickFallProtection(player);

        UUID uuid = player.getUuid();
        ItemStack feet = player.getEquippedStack(EquipmentSlot.FEET);
        boolean wearing = feet.isOf(ModItems.BOOTS_OF_LIGHTNESS);

        if (!wearing) {
            FLY_TICKS.remove(uuid);
            enforceNoFlightWithoutBoots(player);
            return;
        }

        if (isCreativeOrSpectator(player)) {
            return;
        }

        if (isPvpDimension(player)) {
            FLY_TICKS.remove(uuid);
            disableFlight(player, uuid);
            return;
        }

        if (feet.getDamage() >= feet.getMaxDamage()) {
            breakBoots(player, feet, uuid);
            return;
        }

        enableFlight(player, uuid);

        if (player.getAbilities().flying) {
            int ticks = FLY_TICKS.getOrDefault(uuid, 0) + 1;
            if (ticks >= TICKS_PER_DURABILITY) {
                ticks = 0;
                feet.setDamage(Math.min(feet.getDamage() + 1, feet.getMaxDamage()));
                if (feet.getDamage() >= feet.getMaxDamage()) {
                    breakBoots(player, feet, uuid);
                    return;
                }
            }
            FLY_TICKS.put(uuid, ticks);
        }
    }

    private static void registerFallProtection() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity player)) {
                return true;
            }
            if (!isFallDamage(source)) {
                return true;
            }
            if (FALL_PROTECTION_TICKS.remove(player.getUuid()) == null) {
                return true;
            }
            player.fallDistance = 0.0f;
            return false;
        });
    }

    public static void rollPlayerBoots(LootTable table, Inventory inventory, LootContextParameterSet parameters) {
        if (table == LootTable.EMPTY || table.getType() != LootContextTypes.CHEST) {
            return;
        }
        if (!(parameters.getOptional(LootContextParameters.THIS_ENTITY) instanceof ServerPlayerEntity player)
                || !inventory.getClass().getName().equals("net.zestyblaze.lootr.data.SpecialChestInventory")) {
            return;
        }
        Random random = parameters.getWorld().getRandom();
        if (random.nextInt(PLAYER_BOOTS_ROLL_BOUND) != 0) {
            return;
        }
        List<Integer> emptySlots = new ArrayList<>();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isEmpty()) {
                emptySlots.add(slot);
            }
        }
        ItemStack reward = new ItemStack(ModItems.BOOTS_OF_LIGHTNESS);
        if (emptySlots.isEmpty()) {
            if (!player.giveItemStack(reward) && !reward.isEmpty()) {
                player.dropItem(reward, false);
            }
            return;
        }
        inventory.setStack(emptySlots.get(random.nextInt(emptySlots.size())), reward);
    }

    private static void registerAdminCommand() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            for (String command : List.of("give_lightness_boots", "give_swiftness_boots")) {
                dispatcher.register(literal(command)
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                        player.giveItemStack(new ItemStack(ModItems.BOOTS_OF_LIGHTNESS));
                        context.getSource().sendFeedback(() -> Text.literal("Gave Boots of Lightness."), false);
                        return 1;
                    })
                    .then(argument("targets", EntityArgumentType.players())
                        .executes(context -> {
                            int count = 1;
                            for (ServerPlayerEntity target : EntityArgumentType.getPlayers(context, "targets")) {
                                ItemStack stack = new ItemStack(ModItems.BOOTS_OF_LIGHTNESS, count);
                                target.giveItemStack(stack);
                            }
                            context.getSource().sendFeedback(() -> Text.literal("Gave Boots of Lightness."), false);
                            return 1;
                        })
                        .then(argument("count", IntegerArgumentType.integer(1, 64))
                            .executes(context -> {
                                int count = IntegerArgumentType.getInteger(context, "count");
                                for (ServerPlayerEntity target : EntityArgumentType.getPlayers(context, "targets")) {
                                    ItemStack stack = new ItemStack(ModItems.BOOTS_OF_LIGHTNESS, count);
                                    target.giveItemStack(stack);
                                }
                                context.getSource().sendFeedback(() -> Text.literal("Gave Boots of Lightness."), false);
                                return 1;
                            }))));
            }
        });
    }

    private static boolean isCreativeOrSpectator(ServerPlayerEntity player) {
        return player.isCreative() || player.isSpectator();
    }

    private static boolean isPvpDimension(ServerPlayerEntity player) {
        return PVP_DIMENSION.equals(player.getServerWorld().getRegistryKey().getValue().toString());
    }

    private static boolean isFallDamage(DamageSource source) {
        return "fall".equals(source.getName());
    }

    private static void breakBoots(ServerPlayerEntity player, ItemStack boots, UUID uuid) {
        disableFlight(player, uuid);
        FALL_PROTECTION_TICKS.put(uuid, 0);
        player.fallDistance = 0.0f;
        player.sendEquipmentBreakStatus(EquipmentSlot.FEET);
        boots.decrement(1);
        FLY_TICKS.remove(uuid);
    }

    private static void tickFallProtection(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        Integer ticks = FALL_PROTECTION_TICKS.get(uuid);
        if (ticks == null) {
            return;
        }
        player.fallDistance = 0.0f;
        if (player.isOnGround() || player.isClimbing() || player.isTouchingWater() || player.isInLava() || ticks >= FALL_PROTECTION_MAX_TICKS) {
            FALL_PROTECTION_TICKS.remove(uuid);
            return;
        }
        FALL_PROTECTION_TICKS.put(uuid, ticks + 1);
    }

    private static void enableFlight(ServerPlayerEntity player, UUID uuid) {
        if (PAL_AVAILABLE) {
            try {
                PAL_GRANT_ABILITY.invoke(null, player, PAL_ALLOW_FLYING_ABILITY, PAL_FLIGHT_SOURCE);
                return;
            } catch (Throwable ignored) {
                // Fall back to vanilla toggles if reflective PAL call fails.
            }
        }

        if (player.getAbilities().allowFlying) {
            return;
        }

        player.getAbilities().allowFlying = true;
        player.sendAbilitiesUpdate();
    }

    private static void disableFlight(ServerPlayerEntity player, UUID uuid) {
        if (PAL_AVAILABLE) {
            try {
                PAL_REVOKE_ABILITY.invoke(null, player, PAL_ALLOW_FLYING_ABILITY, PAL_FLIGHT_SOURCE);
                PAL_REVOKE_ABILITY.invoke(null, player, PAL_FLYING_ABILITY, PAL_FLIGHT_SOURCE);
            } catch (Throwable ignored) {
                // Continue with vanilla fallback below.
            }
        }

        if (player.getAbilities().flying) {
            player.getAbilities().flying = false;
        }
        player.getAbilities().allowFlying = false;
        player.sendAbilitiesUpdate();
    }

    private static void enforceNoFlightWithoutBoots(ServerPlayerEntity player) {
        if (isCreativeOrSpectator(player)) {
            return;
        }

        if (player.getAbilities().allowFlying || player.getAbilities().flying) {
            disableFlight(player, player.getUuid());
        }
    }

    private static void convertLegacyBoots(ServerPlayerEntity player) {
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isOf(Items.DIAMOND_BOOTS) || !stack.hasNbt() || !stack.getNbt().contains("BoS")) {
                continue;
            }

            ItemStack replacement = new ItemStack(ModItems.BOOTS_OF_LIGHTNESS);
            int mappedDamage = Math.round((stack.getDamage() / 429.0f) * replacement.getMaxDamage());
            replacement.setDamage(Math.min(Math.max(mappedDamage, 0), replacement.getMaxDamage()));
            player.getInventory().setStack(slot, replacement);
        }
    }
}
