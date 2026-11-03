package com.elysium.goldclaim;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

public final class AlexCavesProtection {
    private static final Identifier LUXTRUCTOSAURUS = new Identifier("alexscaves", "luxtructosaurus");
    private static final Identifier TREMORZILLA = new Identifier("alexscaves", "tremorzilla");
    private static final Identifier FORSAKEN = new Identifier("alexscaves", "forsaken");
    private static final Identifier HULLBREAKER = new Identifier("alexscaves", "hullbreaker");
    private static final Identifier MAGNETRON = new Identifier("alexscaves", "magnetron");
    private static final Identifier TREMORZILLA_EGG = new Identifier("alexscaves", "tremorzilla_egg");
    private static final Map<Identifier, Float> BOSS_HEALTH_TARGETS = Map.of(
        TREMORZILLA, 75_000.0f,
        LUXTRUCTOSAURUS, 50_000.0f,
        HULLBREAKER, 25_000.0f,
        FORSAKEN, 25_000.0f);
    private static final Set<Identifier> PROTECTED_TYPES = Set.of(
        LUXTRUCTOSAURUS,
        TREMORZILLA,
        FORSAKEN,
        HULLBREAKER,
        MAGNETRON,
        new Identifier("alexscaves", "tephra"));
    private static final Map<Identifier, UUID> BOSS_HEALTH_MODIFIERS = Map.of(
        TREMORZILLA, UUID.fromString("f345b5f7-82b3-41f0-98ce-983c8e483bb1"),
        LUXTRUCTOSAURUS, UUID.fromString("be08dbe8-e75c-4c64-acb2-0d5eb5b876c4"),
        HULLBREAKER, UUID.fromString("07951f02-4721-41c5-97bb-1014d7b4d662"),
        FORSAKEN, UUID.fromString("75f72922-254b-4717-91d2-48bb0eeb99da"));
    private static final ThreadLocal<Entity> PROTECTED_ACTOR = new ThreadLocal<>();

    private AlexCavesProtection() {}

    static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            Float targetHealth = BOSS_HEALTH_TARGETS.get(Registries.ENTITY_TYPE.getId(entity.getType()));
            if (entity instanceof LivingEntity living && targetHealth != null) {
                setBossHealth(living, targetHealth);
            }
        });
    }

    public static boolean isLuxtructosaurus(Entity entity) {
        return LUXTRUCTOSAURUS.equals(Registries.ENTITY_TYPE.getId(entity.getType()));
    }

    public static boolean isMagnetron(Entity entity) {
        return entity != null && MAGNETRON.equals(Registries.ENTITY_TYPE.getId(entity.getType()));
    }

    public static boolean isProtected(Entity entity) {
        return entity != null && PROTECTED_TYPES.contains(Registries.ENTITY_TYPE.getId(entity.getType()));
    }

    public static boolean preventBlockChanges(Entity actor) {
        return PROTECTED_ACTOR.get() != null || isProtected(actor);
    }

    public static Entity protectedActor() {
        return PROTECTED_ACTOR.get();
    }

    /** Tremorzilla may still lay its egg, but only into an empty (air) block. */
    public static boolean allowsBlockChange(BlockState current, BlockState next) {
        Entity actor = PROTECTED_ACTOR.get();
        return actor != null && TREMORZILLA.equals(Registries.ENTITY_TYPE.getId(actor.getType()))
            && current.isAir() && TREMORZILLA_EGG.equals(Registries.BLOCK.getId(next.getBlock()));
    }

    /** Victim damage/death logic runs under the victim's own rules: guarded for bosses, unguarded for everyone else. */
    public static <T> T asVictim(Entity victim, Supplier<T> action) {
        Entity actor = PROTECTED_ACTOR.get();
        boolean guard = victim.getWorld() instanceof ServerWorld && isProtected(victim);
        if (guard) PROTECTED_ACTOR.set(victim);
        else if (actor == null) return action.get();
        else PROTECTED_ACTOR.remove();
        try {
            return action.get();
        } finally {
            if (actor == null) PROTECTED_ACTOR.remove();
            else PROTECTED_ACTOR.set(actor);
        }
    }

    public static void whileActing(Entity actor, Runnable action) {
        if (!(actor.getWorld() instanceof ServerWorld) || !isProtected(actor)) {
            action.run();
            return;
        }
        Entity previous = PROTECTED_ACTOR.get();
        PROTECTED_ACTOR.set(actor);
        try {
            action.run();
        } finally {
            if (previous == null) PROTECTED_ACTOR.remove();
            else PROTECTED_ACTOR.set(previous);
        }
    }

    private static void setBossHealth(LivingEntity entity, float targetHealth) {
        EntityAttributeInstance attribute = entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (attribute == null) {
            throw new IllegalStateException("Alex's Caves boss has no maximum-health attribute: "
                + Registries.ENTITY_TYPE.getId(entity.getType()));
        }
        UUID modifierId = BOSS_HEALTH_MODIFIERS.get(Registries.ENTITY_TYPE.getId(entity.getType()));
        EntityAttributeModifier existing = attribute.getModifier(modifierId);
        float oldMaximum = entity.getMaxHealth();
        float oldHealth = entity.getHealth();
        double maximumWithoutModifier = oldMaximum;
        if (existing != null && existing.getOperation() == EntityAttributeModifier.Operation.MULTIPLY_TOTAL) {
            maximumWithoutModifier /= 1.0 + existing.getValue();
        }
        if (maximumWithoutModifier <= 0.0) {
            throw new IllegalStateException("Invalid Alex's Caves boss maximum health: " + maximumWithoutModifier);
        }
        double modifierValue = targetHealth / maximumWithoutModifier - 1.0;
        if (existing == null || existing.getOperation() != EntityAttributeModifier.Operation.MULTIPLY_TOTAL
                || existing.getValue() != modifierValue) {
            if (existing != null) attribute.removeModifier(modifierId);
            attribute.addPersistentModifier(new EntityAttributeModifier(modifierId,
                "GoldClaim Alex's Caves boss health", modifierValue,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        entity.setHealth(oldHealth / oldMaximum * entity.getMaxHealth());
    }
}
