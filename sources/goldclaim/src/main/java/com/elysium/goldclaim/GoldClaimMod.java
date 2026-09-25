/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  com.mojang.authlib.GameProfile
 *  com.mojang.brigadier.arguments.ArgumentType
 *  com.mojang.brigadier.arguments.IntegerArgumentType
 *  com.mojang.brigadier.arguments.StringArgumentType
 *  com.mojang.brigadier.builder.LiteralArgumentBuilder
 *  com.mojang.brigadier.context.CommandContext
 *  com.mojang.brigadier.tree.CommandNode
 *  net.fabricmc.api.ModInitializer
 *  net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
 *  net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
 *  net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
 *  net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
 *  net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
 *  net.fabricmc.fabric.api.event.player.AttackEntityCallback
 *  net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
 *  net.fabricmc.fabric.api.event.player.UseBlockCallback
 *  net.fabricmc.fabric.api.event.player.UseEntityCallback
 *  net.fabricmc.fabric.api.event.player.UseItemCallback
 *  net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
 *  net.minecraft.entity.ai.pathing.WaterPathNodeMaker4
 *  net.minecraft.entity.ai.pathing.WaterPathNodeMaker68
 *  net.minecraft.entity.ai.pathing.WaterPathNodeMaker69
 *  net.minecraft.util.TypedActionResult
 *  net.minecraft.util.crash.CrashReportSection3
 *  net.minecraft.util.crash.CrashReportSection4
 *  net.minecraft.util.crash.CrashReportSection7
 *  net.minecraft.item.map.MapBannerMarker99
 *  net.minecraft.item.Items
 *  net.minecraft.loot.condition.LootConditionConsumingBuilder8
 *  net.minecraft.loot.condition.LootConditionConsumingBuilder8$class_4310
 *  net.minecraft.advancement.CriterionMerger5
 *  net.minecraft.advancement.CriterionMerger7
 *  net.minecraft.server.command.ServerCommandSource
 *  net.minecraft.server.command.CommandManager
 *  net.minecraft.util.math.BlockPos
 *  net.minecraft.util.math.Direction
 *  net.minecraft.util.hit.HitResult4
 *  net.minecraft.util.hit.HitResult8
 *  net.minecraft.util.math.Vec3d
 *  net.minecraft.util.shape.FractionalPairList7
 *  net.minecraft.nbt.NbtList
 *  net.minecraft.nbt.NbtString
 *  net.minecraft.nbt.NbtElement
 *  net.minecraft.text.ClickEvent
 *  net.minecraft.text.ClickEvent$Action
 *  net.minecraft.text.Text
 *  net.minecraft.text.Text$class_2562
 *  net.minecraft.text.HoverEvent
 *  net.minecraft.text.HoverEvent$class_5247
 *  net.minecraft.text.Style
 *  net.minecraft.network.packet.Packet
 *  net.minecraft.scoreboard.Team
 *  net.minecraft.scoreboard.Scoreboard
 *  net.minecraft.client.render.VertexFormatElement0
 *  net.minecraft.scoreboard.ServerScoreboard
 *  net.minecraft.server.world.ServerWorld
 *  net.minecraft.server.network.ServerPlayerEntity
 *  net.minecraft.client.gui.screen.advancement.AdvancementTabType8
 *  net.minecraft.client.gui.screen.pack.PackListWidget8
 *  net.minecraft.client.gui.screen.world.EditWorldScreen4
 *  net.minecraft.client.gui.screen.world.CreateWorldScreen0
 *  net.minecraft.text.TextColor
 *  net.minecraft.registry.RegistryKey
 *  net.minecraft.network.packet.s2c.play.SubtitleS2CPacket
 *  net.minecraft.network.packet.s2c.play.TitleS2CPacket
 *  net.minecraft.client.render.entity.model.PolarBearEntityModel5
 *  net.minecraft.loot.entry.LootPoolEntry24
 *  net.minecraft.server.MinecraftServer
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.elysium.goldclaim;

import com.elysium.goldclaim.ClaimManager;
import com.elysium.goldclaim.GoldClaimConfig;
import com.elysium.goldclaim.data.Claim;
import com.elysium.goldclaim.data.SelectionManager;
import com.elysium.goldclaim.mixin.CommandNodeAccessor;
import com.elysium.goldclaim.portal.ClaimPortalRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.command.CommandSource;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.Items;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.nbt.NbtElement;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.WorldView;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap;
import net.minecraft.util.WorldSavePath;
import net.minecraft.network.packet.Packet;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.text.TextColor;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameRules;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoldClaimMod
implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger((String)"GoldClaim");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Predicate<ServerCommandSource> PUBLIC_COMMAND = source -> true;
    private static final String OVERWORLD_DIMENSION = "minecraft:overworld";
    private static GoldClaimMod INSTANCE;
    private GoldClaimConfig config;
    private ClaimManager claimManager;
    private SelectionManager selectionManager;
    private ClaimPortalRegistry portalRegistry;
    private final Path lastWildLocationsPath = Path.of("config", "goldclaim", "last-wild-locations.json");
    private final Path playerHomesPath = Path.of("config", "goldclaim", "player-homes.json");
    private final Path rswResetStatePath = Path.of("config", "goldclaim", "rsw-reset.json");
    private final Map<UUID, SavedLocation> lastWildLocations = new HashMap<UUID, SavedLocation>();
    private final Map<UUID, Map<String, SavedHome>> playerHomes = new HashMap<UUID, Map<String, SavedHome>>();
    private final Map<UUID, VisualizationSession> visualizationSessions = new HashMap<UUID, VisualizationSession>();
    private final Map<UUID, String> lastClaimNotificationByPlayer = new HashMap<UUID, String>();
    private final Map<UUID, Integer> pendingHubTeleports = new HashMap<UUID, Integer>();
    private final Set<UUID> playersInPvp = new HashSet<UUID>();
    private static final long TELEPORT_REQUEST_TIMEOUT_MS = 60000L;
    private static final String PVP_DIMENSION = "multiworld:pvp";
    private static final String RSW_DIMENSION = "multiworld:rsw";
    private static final boolean RSW_RESET_ENABLED = false;
    private static final long RSW_RESET_INTERVAL_MS = 24L * 60L * 60L * 1000L;
    private long nextRswResetAtMs;
    private int worldMaintenanceTicks;
    private int playerMaintenanceTicks;
    private boolean initializeTestingWorlds;
    private final Map<UUID, TeleportRequest> pendingTeleportRequestsByTarget = new HashMap<UUID, TeleportRequest>();
    private final Map<UUID, UUID> pendingTeleportRequestTargetByRequester = new HashMap<UUID, UUID>();
    private static final TextColor HUB_TITLE_COLOR;

    public static boolean isHubWorld(ServerWorld world) {
        return INSTANCE != null && GoldClaimMod.INSTANCE.config != null && GoldClaimMod.INSTANCE.config.portalHubDimension.equals(world.getRegistryKey().getValue().toString());
    }

    public static boolean isTestingWorld(ServerWorld world) {
        String dimensionId = world.getRegistryKey().getValue().toString();
        return PVP_DIMENSION.equals(dimensionId) || RSW_DIMENSION.equals(dimensionId);
    }

    public static boolean isPvpWorld(ServerWorld world) {
        return PVP_DIMENSION.equals(world.getRegistryKey().getValue().toString());
    }

    public static boolean shouldKeepInventoryOnDeath(ServerPlayerEntity player) {
        return GoldClaimMod.isPvpWorld(player.getServerWorld());
    }

    public static boolean shouldFreezeHubFallingBlocks(ServerWorld world) {
        return GoldClaimMod.isHubWorld(world) && GoldClaimMod.INSTANCE.config.freezeHubFallingBlocks;
    }

    public static boolean shouldFreezeHubFluids(ServerWorld world) {
        return GoldClaimMod.isHubWorld(world) && GoldClaimMod.INSTANCE.config.freezeHubFluids;
    }

    public static boolean shouldPreventHubFire(ServerWorld world) {
        return GoldClaimMod.isHubWorld(world) && GoldClaimMod.INSTANCE.config.preventHubFire;
    }

    public static boolean shouldPreserveUnsupportedHubBlocks(WorldView world) {
        ServerWorld serverWorld;
        return world instanceof ServerWorld && GoldClaimMod.isHubWorld(serverWorld = (ServerWorld)world) && GoldClaimMod.INSTANCE.config.preserveUnsupportedHubBlocks;
    }

    public void onInitialize() {
        INSTANCE = this;
        this.config = GoldClaimConfig.load();
        Path claimsPath = Path.of("config", "goldclaim", "claims.json");
        this.claimManager = new ClaimManager(claimsPath, this.config);
        this.selectionManager = new SelectionManager();
        this.portalRegistry = new ClaimPortalRegistry(Path.of("config", "goldclaim", "claim-portals.json"), this.config);
        this.loadLastWildLocations();
        this.loadPlayerHomes();
        this.loadRswResetState();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            this.portalRegistry.syncToServer(server);
            this.forcePublicCommandPermissions(server);
            this.runGangsFixesLoad(server);
            this.initializeTestingWorlds = true;
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                server.execute(() -> {
                    this.forcePublicCommandPermissions(server);
                    this.runGangsFixesLoad(server);
                });
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            this.saveLastWildLocations();
            this.savePlayerHomes();
        });
        this.registerEvents();
        this.registerCommands();
        this.registerDisconnectCleanup();
        this.registerTickHandlers();
        this.registerJoinHandler();
    }

    private void registerEvents() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity)) {
                return true;
            }
            ServerPlayerEntity victim = (ServerPlayerEntity)entity;
            if (this.config.hubFullyInvulnerable && GoldClaimMod.isHubWorld(victim.getServerWorld())) {
                return false;
            }
            Entity patt7533$temp = source.getAttacker();
            if (!(patt7533$temp instanceof ServerPlayerEntity)) {
                return true;
            }
            ServerPlayerEntity attacker = (ServerPlayerEntity)patt7533$temp;
            return !GoldClaimMod.isHubWorld(victim.getServerWorld()) || !GoldClaimMod.isHubWorld(attacker.getServerWorld());
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            ItemStack usedStack = player.getStackInHand(hand);
            if (usedStack.isOf(Items.ARROW)) {
                if (world.isClient) {
                    return ActionResult.SUCCESS;
                }
                if (player instanceof ServerPlayerEntity) {
                    ServerPlayerEntity serverPlayer = (ServerPlayerEntity)player;
                    BlockPos pos = hitResult.getBlockPos();
                    if (serverPlayer.isSneaking()) {
                        this.selectionManager.setPortalBase(serverPlayer.getUuid(), pos);
                        serverPlayer.sendMessage((Text)Text.literal((String)("Portal base selected at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ".")));
                        return ActionResult.SUCCESS;
                    }
                    this.identifyClaim(serverPlayer, world.getRegistryKey().getValue().toString(), pos);
                    return ActionResult.SUCCESS;
                }
                return ActionResult.PASS;
            }
            if (!(player instanceof ServerPlayerEntity)) {
                return ActionResult.PASS;
            }
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity)player;
            BlockPos pos = hitResult.getBlockPos();
            String dimension = world.getRegistryKey().getValue().toString();
            if (usedStack.isOf(this.config.getClaimToolItem())) {
                this.handleClaimSelection(serverPlayer, dimension, pos);
                return ActionResult.SUCCESS;
            }
            if (this.isUniversalGraveOwner(serverPlayer, world, pos)) {
                return ActionResult.PASS;
            }
            boolean fullAccess = this.claimManager.canModify(serverPlayer.getUuid(), serverPlayer.getGameProfile().getName(), serverPlayer.hasPermissionLevel(2), dimension, pos.getX(), pos.getZ());
            if (world.getBlockEntity(pos) instanceof Inventory && !fullAccess) {
                this.deny(serverPlayer, "You cannot access inventories in this claim.");
                return ActionResult.FAIL;
            }
            if (!this.claimManager.canInteract(serverPlayer.getUuid(), serverPlayer.getGameProfile().getName(), serverPlayer.hasPermissionLevel(2), dimension, pos.getX(), pos.getZ())) {
                this.deny(serverPlayer, "You cannot interact here. This area is claimed.");
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (hand != Hand.MAIN_HAND || !player.getMainHandStack().isOf(Items.ARROW)) {
                return TypedActionResult.pass(player.getMainHandStack());
            }
            if (world.isClient) {
                return TypedActionResult.success(player.getMainHandStack());
            }
            if (!(player instanceof ServerPlayerEntity)) {
                return TypedActionResult.pass(player.getMainHandStack());
            }
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity)player;
            this.identifyClaim(serverPlayer, new BlockPos(serverPlayer.getBlockX(), serverPlayer.getBlockY(), serverPlayer.getBlockZ()));
            return TypedActionResult.success(player.getMainHandStack());
        });
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient) {
                return true;
            }
            String dimensionId = world.getRegistryKey().getValue().toString();
            if (PVP_DIMENSION.equals(dimensionId)) {
                if (player instanceof ServerPlayerEntity) {
                    ServerPlayerEntity serverPlayer = (ServerPlayerEntity)player;
                    this.deny(serverPlayer, "Blocks cannot be broken in PVP.");
                }
                return false;
            }
            if (this.portalRegistry.isProtectedBase(dimensionId, pos.getX(), pos.getY(), pos.getZ())) {
                if (player instanceof ServerPlayerEntity) {
                    ServerPlayerEntity serverPlayer = (ServerPlayerEntity)player;
                    this.deny(serverPlayer, "This portal base is locked. Use /claim portal remove <id> as an op.");
                }
                return false;
            }
            boolean allowed = this.claimManager.canModify(player.getUuid(), player.getGameProfile().getName(), player.hasPermissionLevel(2), dimensionId, pos.getX(), pos.getZ());
            if (!allowed && player instanceof ServerPlayerEntity) {
                ServerPlayerEntity serverPlayer = (ServerPlayerEntity)player;
                this.deny(serverPlayer, "You cannot break blocks in this claim.");
            }
            return allowed;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!this.config.protectEntityInteractions || world.isClient || !(player instanceof ServerPlayerEntity)) {
                return ActionResult.PASS;
            }
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity)player;
            BlockPos pos = entity.getBlockPos();
            String dimension = world.getRegistryKey().getValue().toString();
            if (!this.claimManager.canModify(serverPlayer.getUuid(), serverPlayer.getGameProfile().getName(), serverPlayer.hasPermissionLevel(2), dimension, pos.getX(), pos.getZ())) {
                this.deny(serverPlayer, "You cannot use entities in this claim.");
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity)) {
                return ActionResult.PASS;
            }
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity)player;
            String dimension = world.getRegistryKey().getValue().toString();
            if (entity instanceof ServerPlayerEntity) {
                if (dimension.equals(this.config.portalHubDimension)) {
                    this.deny(serverPlayer, "PvP is disabled in the hub.");
                    return ActionResult.FAIL;
                }
                return ActionResult.PASS;
            }
            if (!this.config.protectEntityInteractions) {
                return ActionResult.PASS;
            }
            BlockPos pos = entity.getBlockPos();
            if (!this.claimManager.canModify(serverPlayer.getUuid(), serverPlayer.getGameProfile().getName(), serverPlayer.hasPermissionLevel(2), dimension, pos.getX(), pos.getZ())) {
                this.deny(serverPlayer, "You cannot damage entities in this claim.");
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("claim")
                .then(CommandManager.literal("trust")
                    .then(CommandManager.literal("interact")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .suggests(this::suggestOnlinePlayerNames)
                            .executes(ctx -> this.trustPlayer(ctx.getSource(), StringArgumentType.getString(ctx, "player"), true))))
                    .then(CommandManager.literal("manager")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .suggests(this::suggestOnlinePlayerNames)
                            .executes(ctx -> this.trustManager(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .suggests(this::suggestOnlinePlayerNames)
                        .executes(ctx -> this.trustPlayer(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                .then(CommandManager.literal("untrust")
                    .then(CommandManager.literal("interact")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .suggests(this::suggestOnlinePlayerNames)
                            .executes(ctx -> this.untrustPlayer(ctx.getSource(), StringArgumentType.getString(ctx, "player"), true))))
                    .then(CommandManager.literal("manager")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .suggests(this::suggestOnlinePlayerNames)
                            .executes(ctx -> this.untrustManager(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .suggests(this::suggestOnlinePlayerNames)
                        .executes(ctx -> this.untrustPlayer(ctx.getSource(), StringArgumentType.getString(ctx, "player"))))));
            dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal((String)"claim").executes(ctx -> this.sendHelp((ServerCommandSource)ctx.getSource()))).then(CommandManager.literal((String)"help").executes(ctx -> this.sendHelp((ServerCommandSource)ctx.getSource())))).then(CommandManager.literal((String)"info").executes(ctx -> this.showClaimInfo((ServerCommandSource)ctx.getSource())))).then(CommandManager.literal((String)"visualize").executes(ctx -> this.visualizeClaimAtFeet((ServerCommandSource)ctx.getSource())))).then(CommandManager.literal((String)"list").executes(ctx -> this.listClaims((ServerCommandSource)ctx.getSource())))).then(CommandManager.literal((String)"expand").then(CommandManager.argument((String)"amount", (ArgumentType)IntegerArgumentType.integer((int)1)).executes(ctx -> this.expandClaimAtFeet((ServerCommandSource)ctx.getSource(), IntegerArgumentType.getInteger((CommandContext)ctx, (String)"amount")))))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal((String)"admin").then(CommandManager.literal((String)"help").executes(ctx -> this.sendAdminHelp((ServerCommandSource)ctx.getSource())))).then(((LiteralArgumentBuilder)CommandManager.literal((String)"create").executes(ctx -> this.sendAdminCreateUsage((ServerCommandSource)ctx.getSource()))).then(CommandManager.argument((String)"x1", (ArgumentType)IntegerArgumentType.integer()).then(CommandManager.argument((String)"z1", (ArgumentType)IntegerArgumentType.integer()).then(CommandManager.argument((String)"x2", (ArgumentType)IntegerArgumentType.integer()).then(CommandManager.argument((String)"z2", (ArgumentType)IntegerArgumentType.integer()).executes(ctx -> this.createAdminClaim((ServerCommandSource)ctx.getSource(), IntegerArgumentType.getInteger((CommandContext)ctx, (String)"x1"), IntegerArgumentType.getInteger((CommandContext)ctx, (String)"z1"), IntegerArgumentType.getInteger((CommandContext)ctx, (String)"x2"), IntegerArgumentType.getInteger((CommandContext)ctx, (String)"z2"))))))))).then(CommandManager.literal((String)"unclaim").executes(ctx -> this.unclaimAdminAtFeet((ServerCommandSource)ctx.getSource())))).then(CommandManager.literal((String)"trust").then(CommandManager.argument((String)"player", (ArgumentType)StringArgumentType.word()).executes(ctx -> this.adminTrustPlayer((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"player")))))).then(CommandManager.literal((String)"untrust").then(CommandManager.argument((String)"player", (ArgumentType)StringArgumentType.word()).executes(ctx -> this.adminUntrustPlayer((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"player"))))))).then(CommandManager.literal((String)"unclaim").executes(ctx -> this.unclaimAtFeet((ServerCommandSource)ctx.getSource())))).then(CommandManager.literal((String)"trust").then(CommandManager.argument((String)"player", (ArgumentType)StringArgumentType.word()).executes(ctx -> this.trustPlayer((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"player")))))).then(CommandManager.literal((String)"untrust").then(CommandManager.argument((String)"player", (ArgumentType)StringArgumentType.word()).executes(ctx -> this.untrustPlayer((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"player")))))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal((String)"portal").then(CommandManager.literal((String)"help").executes(ctx -> this.sendPortalHelp((ServerCommandSource)ctx.getSource())))).then(CommandManager.literal((String)"add").then(CommandManager.argument((String)"id", (ArgumentType)StringArgumentType.word()).executes(ctx -> this.addClaimPortal((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"id")))))).then(CommandManager.literal((String)"addhub").then(CommandManager.argument((String)"id", (ArgumentType)StringArgumentType.word()).executes(ctx -> this.addHubPortal((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"id")))))).then(CommandManager.literal((String)"remove").then(CommandManager.argument((String)"id", (ArgumentType)StringArgumentType.word()).executes(ctx -> this.removeClaimPortal((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"id")))))).then(CommandManager.literal((String)"delete").then(CommandManager.argument((String)"id", (ArgumentType)StringArgumentType.word()).executes(ctx -> this.removeClaimPortal((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"id")))))).then(CommandManager.literal((String)"list").executes(ctx -> this.listClaimPortals((ServerCommandSource)ctx.getSource())))));
            dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal((String)"adminclaim").executes(ctx -> this.sendAdminHelp((ServerCommandSource)ctx.getSource()))).then(CommandManager.literal((String)"help").executes(ctx -> this.sendAdminHelp((ServerCommandSource)ctx.getSource())))).then(((LiteralArgumentBuilder)CommandManager.literal((String)"create").executes(ctx -> this.sendAdminCreateUsage((ServerCommandSource)ctx.getSource()))).then(CommandManager.argument((String)"x1", (ArgumentType)IntegerArgumentType.integer()).then(CommandManager.argument((String)"z1", (ArgumentType)IntegerArgumentType.integer()).then(CommandManager.argument((String)"x2", (ArgumentType)IntegerArgumentType.integer()).then(CommandManager.argument((String)"z2", (ArgumentType)IntegerArgumentType.integer()).executes(ctx -> this.createAdminClaim((ServerCommandSource)ctx.getSource(), IntegerArgumentType.getInteger((CommandContext)ctx, (String)"x1"), IntegerArgumentType.getInteger((CommandContext)ctx, (String)"z1"), IntegerArgumentType.getInteger((CommandContext)ctx, (String)"x2"), IntegerArgumentType.getInteger((CommandContext)ctx, (String)"z2"))))))))).then(CommandManager.literal((String)"unclaim").executes(ctx -> this.unclaimAdminAtFeet((ServerCommandSource)ctx.getSource())))).then(CommandManager.literal((String)"trust").then(CommandManager.argument((String)"player", (ArgumentType)StringArgumentType.word()).executes(ctx -> this.adminTrustPlayer((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"player")))))).then(CommandManager.literal((String)"untrust").then(CommandManager.argument((String)"player", (ArgumentType)StringArgumentType.word()).executes(ctx -> this.adminUntrustPlayer((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"player"))))));
            dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal((String)"goldclaimadmin").executes(ctx -> this.sendAdminHelp((ServerCommandSource)ctx.getSource()))).then(CommandManager.literal((String)"help").executes(ctx -> this.sendAdminHelp((ServerCommandSource)ctx.getSource())))).then(((LiteralArgumentBuilder)CommandManager.literal((String)"create").executes(ctx -> this.sendAdminCreateUsage((ServerCommandSource)ctx.getSource()))).then(CommandManager.argument((String)"x1", (ArgumentType)IntegerArgumentType.integer()).then(CommandManager.argument((String)"z1", (ArgumentType)IntegerArgumentType.integer()).then(CommandManager.argument((String)"x2", (ArgumentType)IntegerArgumentType.integer()).then(CommandManager.argument((String)"z2", (ArgumentType)IntegerArgumentType.integer()).executes(ctx -> this.createAdminClaim((ServerCommandSource)ctx.getSource(), IntegerArgumentType.getInteger((CommandContext)ctx, (String)"x1"), IntegerArgumentType.getInteger((CommandContext)ctx, (String)"z1"), IntegerArgumentType.getInteger((CommandContext)ctx, (String)"x2"), IntegerArgumentType.getInteger((CommandContext)ctx, (String)"z2"))))))))).then(CommandManager.literal((String)"unclaim").executes(ctx -> this.unclaimAdminAtFeet((ServerCommandSource)ctx.getSource())))).then(CommandManager.literal((String)"trust").then(CommandManager.argument((String)"player", (ArgumentType)StringArgumentType.word()).executes(ctx -> this.adminTrustPlayer((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"player")))))).then(CommandManager.literal((String)"untrust").then(CommandManager.argument((String)"player", (ArgumentType)StringArgumentType.word()).executes(ctx -> this.adminUntrustPlayer((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"player"))))));
            dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal((String)"gcadmin").executes(ctx -> this.sendAdminHelp((ServerCommandSource)ctx.getSource()))).then(CommandManager.literal((String)"help").executes(ctx -> this.sendAdminHelp((ServerCommandSource)ctx.getSource())))).then(((LiteralArgumentBuilder)CommandManager.literal((String)"create").executes(ctx -> this.sendAdminCreateUsage((ServerCommandSource)ctx.getSource()))).then(CommandManager.argument((String)"x1", (ArgumentType)IntegerArgumentType.integer()).then(CommandManager.argument((String)"z1", (ArgumentType)IntegerArgumentType.integer()).then(CommandManager.argument((String)"x2", (ArgumentType)IntegerArgumentType.integer()).then(CommandManager.argument((String)"z2", (ArgumentType)IntegerArgumentType.integer()).executes(ctx -> this.createAdminClaim((ServerCommandSource)ctx.getSource(), IntegerArgumentType.getInteger((CommandContext)ctx, (String)"x1"), IntegerArgumentType.getInteger((CommandContext)ctx, (String)"z1"), IntegerArgumentType.getInteger((CommandContext)ctx, (String)"x2"), IntegerArgumentType.getInteger((CommandContext)ctx, (String)"z2"))))))))).then(CommandManager.literal((String)"unclaim").executes(ctx -> this.unclaimAdminAtFeet((ServerCommandSource)ctx.getSource())))).then(CommandManager.literal((String)"trust").then(CommandManager.argument((String)"player", (ArgumentType)StringArgumentType.word()).executes(ctx -> this.adminTrustPlayer((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"player")))))).then(CommandManager.literal((String)"untrust").then(CommandManager.argument((String)"player", (ArgumentType)StringArgumentType.word()).executes(ctx -> this.adminUntrustPlayer((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"player"))))));
            dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal((String)"gcportal").then(CommandManager.literal((String)"help").executes(ctx -> this.sendPortalHelp((ServerCommandSource)ctx.getSource())))).then(CommandManager.literal((String)"add").then(CommandManager.argument((String)"id", (ArgumentType)StringArgumentType.word()).executes(ctx -> this.addClaimPortal((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"id")))))).then(CommandManager.literal((String)"addhub").then(CommandManager.argument((String)"id", (ArgumentType)StringArgumentType.word()).executes(ctx -> this.addHubPortal((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"id")))))).then(CommandManager.literal((String)"remove").then(CommandManager.argument((String)"id", (ArgumentType)StringArgumentType.word()).executes(ctx -> this.removeClaimPortal((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"id")))))).then(CommandManager.literal((String)"delete").then(CommandManager.argument((String)"id", (ArgumentType)StringArgumentType.word()).executes(ctx -> this.removeClaimPortal((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"id")))))).then(CommandManager.literal((String)"list").executes(ctx -> this.listClaimPortals((ServerCommandSource)ctx.getSource()))));
            dispatcher.register((LiteralArgumentBuilder)this.publicCommand("hub").executes(ctx -> this.teleportToHub((ServerCommandSource)ctx.getSource())));
            dispatcher.register((LiteralArgumentBuilder)this.publicCommand("wild").executes(ctx -> this.teleportToWild((ServerCommandSource)ctx.getSource())));
            dispatcher.register((LiteralArgumentBuilder)this.publicCommand("pvp").executes(ctx -> this.teleportToTestingWorld((ServerCommandSource)ctx.getSource(), PVP_DIMENSION, "PVP", true)));
            dispatcher.register((LiteralArgumentBuilder)CommandManager.literal("rsw").requires(source -> source.hasPermissionLevel(2)).executes(ctx -> this.teleportToTestingWorld((ServerCommandSource)ctx.getSource(), RSW_DIMENSION, "RSW", false)));
            dispatcher.register((LiteralArgumentBuilder)this.publicCommand("rtp").executes(ctx -> this.randomTeleport((ServerCommandSource)ctx.getSource())));
            dispatcher.register((LiteralArgumentBuilder)this.publicCommand("randomteleport").executes(ctx -> this.randomTeleport((ServerCommandSource)ctx.getSource())));
            dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)this.publicCommand("sethome").executes(ctx -> this.sendSetHomeUsage((ServerCommandSource)ctx.getSource()))).then(CommandManager.argument((String)"name", (ArgumentType)StringArgumentType.greedyString()).executes(ctx -> this.setHome((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"name")))));
            dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal((String)"delhome").executes(ctx -> this.sendDelHomeUsage((ServerCommandSource)ctx.getSource()))).then(CommandManager.literal((String)"confirm").then(CommandManager.argument((String)"name", (ArgumentType)StringArgumentType.greedyString()).suggests(this::suggestOwnHomeNames).executes(ctx -> this.confirmDeleteHome((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"name")))))).then(CommandManager.argument((String)"name", (ArgumentType)StringArgumentType.greedyString()).suggests(this::suggestOwnHomeNames).executes(ctx -> this.promptDeleteHome((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"name")))));
            dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)this.publicCommand("home").executes(ctx -> this.sendHomeUsage((ServerCommandSource)ctx.getSource()))).then(CommandManager.literal((String)"list").requires(PUBLIC_COMMAND).executes(ctx -> this.listHomes((ServerCommandSource)ctx.getSource())))).then(CommandManager.literal((String)"public").requires(PUBLIC_COMMAND).then(CommandManager.argument((String)"name", (ArgumentType)StringArgumentType.greedyString()).suggests(this::suggestOwnHomeNames).executes(ctx -> this.setHomeVisibility((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"name"), true))))).then(CommandManager.literal((String)"private").requires(PUBLIC_COMMAND).then(CommandManager.argument((String)"name", (ArgumentType)StringArgumentType.greedyString()).suggests(this::suggestOwnHomeNames).executes(ctx -> this.setHomeVisibility((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"name"), false))))).then(CommandManager.argument((String)"name", (ArgumentType)StringArgumentType.greedyString()).suggests(this::suggestOwnHomeNames).executes(ctx -> this.teleportToHome((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"name")))));
            dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal((String)"phome").executes(ctx -> this.sendPublicHomeUsage((ServerCommandSource)ctx.getSource()))).then(CommandManager.literal((String)"list").executes(ctx -> this.listPublicHomes((ServerCommandSource)ctx.getSource())))).then(CommandManager.argument((String)"name", (ArgumentType)StringArgumentType.greedyString()).executes(ctx -> this.teleportToPublicHome((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"name")))));
            dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal((String)"tpa").executes(ctx -> this.sendTpaUsage((ServerCommandSource)ctx.getSource()))).then(CommandManager.argument((String)"player", (ArgumentType)StringArgumentType.word()).executes(ctx -> this.requestTeleportToPlayer((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"player")))));
            dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal((String)"tpahere").executes(ctx -> this.sendTpaHereUsage((ServerCommandSource)ctx.getSource()))).then(CommandManager.argument((String)"player", (ArgumentType)StringArgumentType.word()).executes(ctx -> this.requestTeleportPlayerHere((ServerCommandSource)ctx.getSource(), StringArgumentType.getString((CommandContext)ctx, (String)"player")))));
            dispatcher.register((LiteralArgumentBuilder)CommandManager.literal((String)"tpaccept").executes(ctx -> this.acceptTeleportRequest((ServerCommandSource)ctx.getSource())));
            dispatcher.register((LiteralArgumentBuilder)CommandManager.literal((String)"tpdecline").executes(ctx -> this.declineTeleportRequest((ServerCommandSource)ctx.getSource())));
            dispatcher.register((LiteralArgumentBuilder)CommandManager.literal((String)"servertps").executes(ctx -> this.showTps((ServerCommandSource)ctx.getSource())));
            dispatcher.register((LiteralArgumentBuilder)this.publicCommand("feed").executes(ctx -> this.feedPlayer((ServerCommandSource)ctx.getSource())));
        });
    }

    private LiteralArgumentBuilder<ServerCommandSource> publicCommand(String name) {
        return CommandManager.literal((String)name).requires(PUBLIC_COMMAND);
    }

    private void forcePublicCommandPermissions(MinecraftServer server) {
        HashMap<String, String> commandStatus = new HashMap<String, String>();
        for (String commandName : List.of("hub", "wild", "pvp", "rtp", "randomteleport", "sethome", "delhome", "home", "phome", "tpa", "tpahere", "tpaccept", "tpdecline", "servertps", "feed")) {
            CommandNode node = server.getCommandManager().getDispatcher().getRoot().getChild(commandName);
            if (node != null) {
                this.forcePublicCommandTree(node);
                commandStatus.put(commandName, "public");
                continue;
            }
            commandStatus.put(commandName, "missing");
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            server.getCommandManager().sendCommandTree(player);
        }
        LOGGER.info("Command permissions: /hub={}, /wild={}, /pvp={}, /rtp={}, /randomteleport={}, /sethome={}, /delhome={}, /home={}, /phome={}, /tpa={}, /tpahere={}, /tpaccept={}, /tpdecline={}, /servertps={}, /feed={} (server-side override active)", new Object[]{commandStatus.get("hub"), commandStatus.get("wild"), commandStatus.get("pvp"), commandStatus.get("rtp"), commandStatus.get("randomteleport"), commandStatus.get("sethome"), commandStatus.get("delhome"), commandStatus.get("home"), commandStatus.get("phome"), commandStatus.get("tpa"), commandStatus.get("tpahere"), commandStatus.get("tpaccept"), commandStatus.get("tpdecline"), commandStatus.get("servertps"), commandStatus.get("feed")});
    }

    private void forcePublicCommandTree(CommandNode node) {
        ((CommandNodeAccessor)node).goldclaim$setRequirement(source -> true);
        for (Object child : node.getChildren()) {
            if (child instanceof CommandNode) {
                this.forcePublicCommandTree((CommandNode)child);
            }
        }
    }

    private void registerDisconnectCleanup() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            this.selectionManager.clear(oldPlayer.getUuid());
            this.visualizationSessions.remove(oldPlayer.getUuid());
            this.pendingHubTeleports.remove(oldPlayer.getUuid());
            if (!alive && GoldClaimMod.isPvpWorld(oldPlayer.getServerWorld())) {
                newPlayer.getInventory().clone(oldPlayer.getInventory());
                newPlayer.experienceLevel = oldPlayer.experienceLevel;
                newPlayer.totalExperience = oldPlayer.totalExperience;
                newPlayer.experienceProgress = oldPlayer.experienceProgress;
                newPlayer.sendAbilitiesUpdate();
                this.teleportToHub(newPlayer, false);
                return;
            }
            // Only fall back to the hub on death when the player has no bed/anchor spawn point set;
            // otherwise let vanilla respawn them at their set spawn point.
            if (!alive && newPlayer.getSpawnPointPosition() == null) {
                this.teleportToHub(newPlayer, false);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> {
            ServerPlayerEntity player = handler.getPlayer();
            if (player != null) {
                this.pendingHubTeleports.remove(player.getUuid());
                this.clearTeleportRequestsForPlayer(player, server);
                this.rememberWildLocation(player);
            }
        }));
    }

    private void runGangsFixesLoad(MinecraftServer server) {
        server.getGameRules().get(GameRules.KEEP_INVENTORY).set(false, server);
        server.getGameRules().get(GameRules.DO_FIRE_TICK).set(false, server);

        ServerScoreboard scoreboard = server.getScoreboard();
        for (String teamName : new String[]{"rankbadges_owner", "rankbadges_mod", "rankbadges_player", "owner", "mod", "player"}) {
            Team team = scoreboard.getTeam(teamName);
            if (team != null) {
                team.setColor(Formatting.RESET);
            }
        }

        for (String objectiveName : new String[]{"gangs_time", "gangs_cycle_start", "gangs_claimed_w1", "gangs_claimed_w2",
                "gangs_claimed_w3", "gangs_claimed_m", "gangs_perm_w1", "gangs_perm_w2", "gangs_perm_w3", "gangs_perm_m", "gangs_temp"}) {
            if (scoreboard.getNullableObjective(objectiveName) == null) {
                scoreboard.addObjective(objectiveName, ScoreboardCriterion.DUMMY, Text.literal(objectiveName), ScoreboardCriterion.RenderType.INTEGER);
            }
        }

        this.applyDefaultGroupPermissions();
    }

    private void applyDefaultGroupPermissions() {
        String[][] permissions = {
            {"essentialcommands.nickname.self", "true"},
            {"essentialcommands.nickname.style.color", "true"},
            {"essentialcommands.nickname.style.fancy", "true"},
            {"essentialcommands.nickname.style.hover", "true"},
            {"essentialcommands.nickname.style.click", "true"},
            {"essentialcommands.feed.self", "true"},
            {"essentialcommands.home.self", "true"},
            {"essentialcommands.home.tp", "true"},
            {"essentialcommands.home.set", "true"},
            {"essentialcommands.home.delete", "true"},
            {"essentialcommands.rtp", "true"},
            {"essentialcommands.randomteleport", "true"},
            {"goldclaim.command.feed", "true"},
            {"goldclaim.command.home", "true"},
            {"goldclaim.command.sethome", "true"},
            {"goldclaim.command.delhome", "true"},
            {"goldclaim.command.rtp", "true"},
            {"goldclaim.command.randomteleport", "true"},
            {"goldclaim.command.pvp", "true"},
            {"universal_graves.list", "true"},
            {"gangshats.command.hat", "true"},
            {"gangshats.command.nick", "true"},
            {"gangsales.command.ec", "true"},
            {"skieskits.command.base", "true"},
            {"skieskits.command.claim", "true"},
            {"kits.kit.1_weekly_1", "true"},
            {"kits.kit.2_weekly_2", "false"},
            {"kits.kit.3_weekly_3", "false"},
            {"kits.kit.4_monthly", "false"},
            {"gangsales.command.gs", "true"},
            {"gangsales.command.gs.history", "true"},
            {"gangsales.command.gs.mine", "true"},
            {"gangsales.command.gs.add", "true"},
        };
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            Group defaultGroup = luckPerms.getGroupManager().getGroup("default");
            if (defaultGroup == null) {
                LOGGER.warn("LuckPerms default group not found; skipping permission setup.");
                return;
            }
            for (String[] permission : permissions) {
                defaultGroup.data().add(Node.builder(permission[0]).value(Boolean.parseBoolean(permission[1])).build());
            }
            luckPerms.getGroupManager().saveGroup(defaultGroup).join();
        } catch (IllegalStateException e) {
            LOGGER.warn("LuckPerms API not available; skipping permission setup.", e);
        }
    }

    private void registerJoinHandler() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            ServerPlayerEntity player = handler.getPlayer();
            if (this.isFirstJoin(player, server)) {
                this.giveFirstJoinTools(player);
            }
            this.assignDefaultRankTeam(player, server);
            this.pendingHubTeleports.put(player.getUuid(), 20);
            this.forcePublicCommandPermissions(server);
            server.getCommandManager().sendCommandTree(player);
            server.getCommandFunctionManager().getFunction(new Identifier("gangs_kits", "check_join_notify")).ifPresent(function ->
                server.getCommandFunctionManager().execute(function, player.getCommandSource().withLevel(2).withSilent())
            );
        }));
    }

    private void assignDefaultRankTeam(ServerPlayerEntity player, MinecraftServer server) {
        ServerScoreboard scoreboard = server.getScoreboard();
        Team ownerTeam = this.getOrCreateRankTeam((Scoreboard)scoreboard, "owner", Formatting.BLUE, (Text)Text.literal((String)"[OWNER] ").formatted(new Formatting[]{Formatting.BLUE, Formatting.BOLD}));
        Team modTeam = this.getOrCreateRankTeam((Scoreboard)scoreboard, "mod", Formatting.GREEN, (Text)Text.literal((String)"[MOD] ").formatted(new Formatting[]{Formatting.GREEN, Formatting.BOLD}));
        Team playerTeam = this.getOrCreateRankTeam((Scoreboard)scoreboard, "player", Formatting.WHITE, (Text)Text.literal((String)"[PLAYER] ").formatted(Formatting.GRAY));
        String playerName = player.getGameProfile().getName();
        if ("Oux_y".equalsIgnoreCase(playerName)) {
            scoreboard.addPlayerToTeam(playerName, ownerTeam);
            return;
        }
        if ("Rootamiss".equalsIgnoreCase(playerName)) {
            scoreboard.addPlayerToTeam(playerName, modTeam);
            return;
        }
        if (scoreboard.getPlayerTeam(playerName) == null) {
            scoreboard.addPlayerToTeam(player.getName().getString(), playerTeam);
        }
    }

    private Team getOrCreateRankTeam(Scoreboard scoreboard, String name, Formatting color, Text prefix) {
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.addTeam(name);
        }
        team.setColor(Formatting.RESET);
        team.setPrefix(prefix);
        return team;
    }

    private boolean isFirstJoin(ServerPlayerEntity player, MinecraftServer server) {
        Path playerDataFile = server.getSavePath(WorldSavePath.PLAYERDATA).resolve(player.getUuidAsString() + ".dat");
        return !Files.exists(playerDataFile, new LinkOption[0]);
    }

    private void giveFirstJoinTools(ServerPlayerEntity player) {
        if (!this.config.giveClaimKitOnFirstJoin) {
            return;
        }
        ItemStack claimTool = new ItemStack((ItemConvertible)this.config.getClaimToolItem());
        claimTool.setCustomName((Text)Text.literal((String)"Claim Tool").setStyle(Style.EMPTY.withItalic(Boolean.valueOf(false))));
        this.setLore(claimTool, "Right-click two corners of land to create a claim.");
        ItemStack claimInspector = new ItemStack((ItemConvertible)Items.ARROW);
        claimInspector.setCustomName((Text)Text.literal((String)"Claim Inspector").setStyle(Style.EMPTY.withItalic(Boolean.valueOf(false))));
        this.setLore(claimInspector, "Right-click a block to see who owns this claim.");
        this.giveOrDrop(player, claimTool);
        this.giveOrDrop(player, claimInspector);
    }

    private void setLore(ItemStack stack, String line) {
        NbtCompound display = stack.getOrCreateSubNbt("display");
        NbtList loreList = new NbtList();
        MutableText loreText = Text.literal((String)line).setStyle(Style.EMPTY.withItalic(Boolean.valueOf(false)).withColor(Formatting.GRAY));
        loreList.add(NbtString.of(Text.Serializer.toJson((Text)loreText)));
        display.put("Lore", (NbtElement)loreList);
    }

    private void giveOrDrop(ServerPlayerEntity player, ItemStack stack) {
        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
    }

    private void registerTickHandlers() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (this.initializeTestingWorlds) {
                this.initializeTestingWorlds = false;
                this.ensureTestingWorlds(server);
            }
            if (++this.worldMaintenanceTicks >= 1200) {
                this.worldMaintenanceTicks = 0;
                this.maintainTestingWorlds(server);
            }
            boolean runPlayerMaintenance = false;
            if (++this.playerMaintenanceTicks >= 20) {
                this.playerMaintenanceTicks = 0;
                runPlayerMaintenance = true;
                ServerScoreboard scoreboard = server.getScoreboard();
                ScoreboardObjective objective = scoreboard.getNullableObjective("gangs_time");
                if (objective == null) {
                    objective = scoreboard.addObjective("gangs_time", ScoreboardCriterion.DUMMY, Text.literal("Gangs Time"), ScoreboardCriterion.RenderType.INTEGER);
                }
                scoreboard.getPlayerScore("#epoch", objective).setScore((int)(System.currentTimeMillis() / 1000L));
            }

            ServerPlayerEntity player;
            if (!this.pendingTeleportRequestsByTarget.isEmpty()) {
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<UUID, TeleportRequest>> requestIterator = this.pendingTeleportRequestsByTarget.entrySet().iterator();
                while (requestIterator.hasNext()) {
                    ServerPlayerEntity target;
                    Map.Entry<UUID, TeleportRequest> entry2 = requestIterator.next();
                    TeleportRequest request = entry2.getValue();
                    if (now - request.createdAtMs < 60000L) continue;
                    requestIterator.remove();
                    this.pendingTeleportRequestTargetByRequester.remove(request.requesterUuid);
                    ServerPlayerEntity requester = server.getPlayerManager().getPlayer(request.requesterUuid);
                    if (requester != null) {
                        requester.sendMessage((Text)Text.literal((String)("Your teleport request to " + request.targetName + " expired.")), false);
                    }
                    if ((target = server.getPlayerManager().getPlayer(request.targetUuid)) == null) continue;
                    target.sendMessage((Text)Text.literal((String)("Teleport request from " + request.requesterName + " expired.")), false);
                }
            }
            if (!this.pendingHubTeleports.isEmpty()) {
                Iterator<Map.Entry<UUID, Integer>> pendingIterator = this.pendingHubTeleports.entrySet().iterator();
                while (pendingIterator.hasNext()) {
                    Map.Entry<UUID, Integer> entry = pendingIterator.next();
                    player = server.getPlayerManager().getPlayer(entry.getKey());
                    if (player == null || player.isDisconnected()) {
                        pendingIterator.remove();
                        continue;
                    }
                    int ticksRemaining = entry.getValue() - 1;
                    if (ticksRemaining > 0) {
                        entry.setValue(ticksRemaining);
                        continue;
                    }
                    pendingIterator.remove();
                    this.teleportToHub(player, true, false);
                }
            }
            if (!this.visualizationSessions.isEmpty()) {
                Iterator<Map.Entry<UUID, VisualizationSession>> iterator = this.visualizationSessions.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<UUID, VisualizationSession> entry = iterator.next();
                    player = server.getPlayerManager().getPlayer(entry.getKey());
                    if (player == null || player.isDisconnected()) {
                        iterator.remove();
                        continue;
                    }
                    VisualizationSession session = entry.getValue();
                    this.renderClaimOutlineFrame(player, session.claim, session.phase);
                    ++session.phase;
                    --session.ticksRemaining;
                    if (session.ticksRemaining > 0) continue;
                    iterator.remove();
                }
            }
            if (runPlayerMaintenance) {
                for (ServerPlayerEntity player2 : server.getPlayerManager().getPlayerList()) {
                    String dimensionId = player2.getServerWorld().getRegistryKey().getValue().toString();
                    if (PVP_DIMENSION.equals(dimensionId)) {
                        if (this.playersInPvp.add(player2.getUuid())) {
                            this.disablePvpFlight(player2);
                        }
                    } else if (RSW_DIMENSION.equals(dimensionId) && !player2.hasPermissionLevel(2)) {
                        player2.sendMessage(Text.literal("RSW is currently admin-only."), false);
                        this.teleportToHub(player2, false, false);
                        continue;
                    } else {
                        this.playersInPvp.remove(player2.getUuid());
                    }
                    this.updateClaimEntryNotification(player2);
                }
            }
        });
    }

    private void ensureTestingWorlds(MinecraftServer server) {
        this.createMultiworldIfMissing(server, PVP_DIMENSION, "VOID");
        this.createMultiworldIfMissing(server, RSW_DIMENSION, "NORMAL");
        this.applyWorldBorder(server, PVP_DIMENSION, 1000.0);
        this.applyWorldBorder(server, RSW_DIMENSION, 5000.0);
        this.applyPvpWorldRules(server);
        if (RSW_RESET_ENABLED && this.nextRswResetAtMs <= 0L) {
            this.nextRswResetAtMs = System.currentTimeMillis() + RSW_RESET_INTERVAL_MS;
            this.saveRswResetState();
        }
    }

    private void applyPvpWorldRules(MinecraftServer server) {
        ServerWorld pvpWorld = this.getWorld(server, PVP_DIMENSION);
        if (pvpWorld == null) {
            return;
        }
        pvpWorld.setTimeOfDay(6000L);
        for (ServerPlayerEntity player : pvpWorld.getPlayers()) {
            this.disablePvpFlight(player);
        }
    }

    private void disablePvpFlight(ServerPlayerEntity player) {
        if (player.getAbilities().creativeMode) {
            return;
        }
        boolean changed = player.getAbilities().flying;
        player.getAbilities().flying = false;
        if (changed) {
            player.sendAbilitiesUpdate();
        }
    }

    private void maintainTestingWorlds(MinecraftServer server) {
        this.ensureTestingWorlds(server);
        if (!RSW_RESET_ENABLED || System.currentTimeMillis() < this.nextRswResetAtMs) {
            return;
        }
        ServerWorld rswWorld = this.getWorld(server, RSW_DIMENSION);
        if (rswWorld != null) {
            for (ServerPlayerEntity player : new ArrayList<>(rswWorld.getPlayers())) {
                this.teleportToHub(player, false, false);
            }
        }
        ServerCommandSource console = server.getCommandSource().withLevel(4).withSilent();
        server.getCommandManager().executeWithPrefix(console, "mw delete rsw");
        server.getCommandManager().executeWithPrefix(console, "mw delete rsw");
        this.createMultiworldIfMissing(server, RSW_DIMENSION, "NORMAL");
        if (this.getWorld(server, RSW_DIMENSION) == null) {
            LOGGER.error("RSW rotation failed; the world was not recreated. It will be retried in 60 seconds.");
            return;
        }
        this.applyWorldBorder(server, RSW_DIMENSION, 5000.0);
        this.nextRswResetAtMs = System.currentTimeMillis() + RSW_RESET_INTERVAL_MS;
        this.saveRswResetState();
        server.getPlayerManager().broadcast(Text.literal("RSW has reset with a fresh world."), false);
    }

    private void createMultiworldIfMissing(MinecraftServer server, String dimensionId, String generatorName) {
        if (this.getWorld(server, dimensionId) != null) {
            return;
        }
        try {
            Class<?> createCommandClass = Class.forName("me.isaiah.multiworld.command.CreateCommand");
            Class<?> multiworldClass = Class.forName("me.isaiah.multiworld.MultiworldMod");
            Object generator = this.invokeStatic(createCommandClass, "get_chunk_gen", server, generatorName);
            if (generator == null) {
                throw new IllegalStateException("Unknown Multiworld generator: " + generatorName);
            }
            long seed = ThreadLocalRandom.current().nextLong();
            ServerWorld world = (ServerWorld)this.invokeStatic(multiworldClass, "create_world", dimensionId,
                new Identifier("minecraft", "overworld"), generator, net.minecraft.world.Difficulty.NORMAL, seed);
            this.invokeStatic(createCommandClass, "make_config", world, "NORMAL", seed,
                "VOID".equals(generatorName) ? generatorName : null);
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Could not create {} with Multiworld generator {}.", dimensionId, generatorName, e);
        }
        if (this.getWorld(server, dimensionId) == null) {
            LOGGER.error("Multiworld creation returned without loading {}.", dimensionId);
        }
    }

    private Object invokeStatic(Class<?> owner, String methodName, Object... arguments) throws ReflectiveOperationException {
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == arguments.length) {
                return method.invoke(null, arguments);
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + methodName);
    }

    private void applyWorldBorder(MinecraftServer server, String dimensionId, double size) {
        ServerWorld world = this.getWorld(server, dimensionId);
        if (world == null) {
            return;
        }
        world.getWorldBorder().setCenter(0.0, 0.0);
        world.getWorldBorder().setSize(size);
        world.getWorldBorder().setWarningBlocks(0);
    }

    private ServerWorld getWorld(MinecraftServer server, String dimensionId) {
        Identifier id = Identifier.tryParse(dimensionId);
        if (id == null) {
            return null;
        }
        RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
        return server.getWorld(key);
    }

    private void loadRswResetState() {
        if (Files.exists(this.rswResetStatePath)) {
            try (BufferedReader reader = Files.newBufferedReader(this.rswResetStatePath)) {
                JsonObject state = JsonParser.parseReader(reader).getAsJsonObject();
                this.nextRswResetAtMs = state.get("nextResetAtMs").getAsLong();
            }
            catch (Exception e) {
                LOGGER.warn("Could not read RSW reset state; a new 24-hour cycle will begin.", e);
            }
        }
    }

    private void saveRswResetState() {
        try {
            Files.createDirectories(this.rswResetStatePath.getParent());
            JsonObject state = new JsonObject();
            state.addProperty("nextResetAtMs", this.nextRswResetAtMs);
            try (BufferedWriter writer = Files.newBufferedWriter(this.rswResetStatePath)) {
                GSON.toJson(state, writer);
            }
        }
        catch (IOException e) {
            LOGGER.error("Could not save RSW reset state.", e);
        }
    }

    private void rememberWildLocation(ServerPlayerEntity player) {
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        if (!OVERWORLD_DIMENSION.equals(dimension)) {
            return;
        }
        this.rememberWildLocationFromPosition(player.getUuid(), dimension, player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
    }

    private void rememberWildLocationFromPosition(UUID playerUuid, String dimension, double x, double y, double z, float yaw, float pitch) {
        SavedLocation nextLocation = new SavedLocation(dimension, x, y, z, yaw, pitch);
        SavedLocation currentLocation = this.lastWildLocations.get(playerUuid);
        if (nextLocation.equals(currentLocation)) {
            return;
        }
        this.lastWildLocations.put(playerUuid, nextLocation);
        this.saveLastWildLocations();
    }

    private void loadLastWildLocations() {
        this.lastWildLocations.clear();
        if (!Files.exists(this.lastWildLocationsPath, new LinkOption[0])) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(this.lastWildLocationsPath);){
            JsonElement root = JsonParser.parseReader((Reader)reader);
            if (!root.isJsonArray()) {
                return;
            }
            for (JsonElement element : root.getAsJsonArray()) {
                JsonObject obj;
                if (!element.isJsonObject() || !(obj = element.getAsJsonObject()).has("playerUuid") || !obj.has("worldId") || !obj.has("x") || !obj.has("y") || !obj.has("z")) continue;
                try {
                    UUID playerUuid = UUID.fromString(obj.get("playerUuid").getAsString());
                    SavedLocation location = new SavedLocation(obj.get("worldId").getAsString(), obj.get("x").getAsDouble(), obj.get("y").getAsDouble(), obj.get("z").getAsDouble(), obj.has("yaw") ? obj.get("yaw").getAsFloat() : 0.0f, obj.has("pitch") ? obj.get("pitch").getAsFloat() : 0.0f);
                    this.lastWildLocations.put(playerUuid, location);
                }
                catch (IllegalArgumentException illegalArgumentException) {}
            }
        }
        catch (IOException iOException) {
            // empty catch block
        }
    }

    private void saveLastWildLocations() {
        try {
            Files.createDirectories(this.lastWildLocationsPath.getParent(), new FileAttribute[0]);
            try (BufferedWriter writer = Files.newBufferedWriter(this.lastWildLocationsPath, new OpenOption[0]);){
                JsonArray entries = new JsonArray();
                for (Map.Entry<UUID, SavedLocation> entry : this.lastWildLocations.entrySet()) {
                    SavedLocation location = entry.getValue();
                    JsonObject obj = new JsonObject();
                    obj.addProperty("playerUuid", entry.getKey().toString());
                    obj.addProperty("worldId", location.worldId);
                    obj.addProperty("x", (Number)location.x);
                    obj.addProperty("y", (Number)location.y);
                    obj.addProperty("z", (Number)location.z);
                    obj.addProperty("yaw", (Number)Float.valueOf(location.yaw));
                    obj.addProperty("pitch", (Number)Float.valueOf(location.pitch));
                    entries.add((JsonElement)obj);
                }
                GSON.toJson((JsonElement)entries, (Appendable)writer);
            }
        }
        catch (IOException iOException) {
            // empty catch block
        }
    }

    private void updateClaimEntryNotification(ServerPlayerEntity player) {
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        Optional<Claim> claimOpt = this.claimManager.getClaimAt(dimension, player.getBlockX(), player.getBlockZ());
        String currentKey = claimOpt.map(this::claimKey).orElse("");
        String previousKey = this.lastClaimNotificationByPlayer.get(player.getUuid());
        if (currentKey.isEmpty()) {
            this.lastClaimNotificationByPlayer.remove(player.getUuid());
            return;
        }
        if (!currentKey.equals(previousKey)) {
            Claim claim = claimOpt.get();
            player.sendMessage((Text)Text.literal((String)("You entered " + this.getClaimOwnerLabel(claim) + " in " + this.getClaimDimensionLabel(claim.dimension) + ".")), true);
            this.lastClaimNotificationByPlayer.put(player.getUuid(), currentKey);
        }
    }

    private String claimKey(Claim claim) {
        return claim.dimension + "|" + claim.minX + "|" + claim.maxX + "|" + claim.minZ + "|" + claim.maxZ + "|" + claim.ownerUuid + "|" + claim.adminClaim;
    }

    private String getClaimOwnerLabel(Claim claim) {
        if (claim.adminClaim) {
            return "an admin claim";
        }
        if (claim.ownerName != null && !claim.ownerName.isBlank()) {
            return claim.ownerName + "'s claim";
        }
        return "a player claim (owner: " + claim.ownerUuid + ")";
    }

    private void handleClaimSelection(ServerPlayerEntity player, String dimension, BlockPos currentPos) {
        UUID playerUuid = player.getUuid();
        Optional<BlockPos> firstCorner = this.selectionManager.getFirstCorner(playerUuid);
        if (firstCorner.isEmpty()) {
            this.selectionManager.setFirstCorner(playerUuid, currentPos);
            player.sendMessage((Text)Text.literal((String)("First corner set at " + currentPos.getX() + ", " + currentPos.getZ() + ". Right-click second corner with your gold shovel.")));
            return;
        }
        BlockPos first = firstCorner.get();
        int minX = Math.min(first.getX(), currentPos.getX());
        int maxX = Math.max(first.getX(), currentPos.getX());
        int minZ = Math.min(first.getZ(), currentPos.getZ());
        int maxZ = Math.max(first.getZ(), currentPos.getZ());
        ClaimManager.CreateResult result = this.claimManager.createClaim(playerUuid, player.getName().getString(), dimension, minX, maxX, minZ, maxZ);
        if (!result.success()) {
            if (result.error() == ClaimManager.CreateError.TOO_SMALL) {
                player.sendMessage((Text)Text.literal((String)("Claim too small. Minimum size is " + this.config.minimumClaimWidth + "x" + this.config.minimumClaimDepth + ".")));
            } else if (result.error() == ClaimManager.CreateError.CLAIM_LIMIT_EXCEEDED) {
                player.sendMessage((Text)Text.literal((String)("Claim limit exceeded. Max blocks per dimension is " + this.config.maxClaimBlocksPerPlayerPerDimension + ".")));
            } else {
                player.sendMessage((Text)Text.literal((String)"That area overlaps an existing claim."));
            }
            this.selectionManager.clear(playerUuid);
            return;
        }
        this.selectionManager.clear(playerUuid);
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        player.sendMessage((Text)Text.literal((String)("Claim created: " + width + "x" + depth + " from [" + minX + "," + minZ + "] to [" + maxX + "," + maxZ + "].")));
        this.claimManager.getClaimAt(dimension, player.getBlockX(), player.getBlockZ()).ifPresent(claim -> this.showClaimOutline(player, (Claim)claim));
    }

    private int sendHelp(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal((String)("Claim tool: hold a gold shovel, right-click 2 corners to claim (minimum " + this.config.minimumClaimWidth + "x" + this.config.minimumClaimDepth + ", max " + this.config.maxClaimBlocksPerPlayerPerDimension + " blocks per dimension).")), false);
        source.sendFeedback(() -> Text.literal((String)"Commands: /claim info, /claim visualize, /claim expand <amount>, /claim list, /claim trust <player|uuid>, /claim trust interact <player|uuid>, /claim trust manager <player|uuid>, /claim untrust <player|uuid>, /claim untrust interact <player|uuid>, /claim untrust manager <player|uuid>, /claim unclaim"), false);
        source.sendFeedback(() -> Text.literal((String)"Homes: /sethome <name>, /home <name>, /home list, /delhome <name>, /home public <name>, /home private <name>, /phome <name>, /phome list (up to 10 per player)"), false);
        source.sendFeedback(() -> Text.literal((String)"Admin: /claim admin help"), false);
        return 1;
    }

    private int sendSetHomeUsage(ServerCommandSource source) {
        source.sendError((Text)Text.literal((String)"Usage: /sethome <name>"));
        return 0;
    }

    private int sendDelHomeUsage(ServerCommandSource source) {
        source.sendError((Text)Text.literal((String)"Usage: /delhome <name> or /delhome confirm <name>"));
        return 0;
    }

    private int sendHomeUsage(ServerCommandSource source) {
        source.sendError((Text)Text.literal((String)"Usage: /home <name>, /home list, /home public <name>, /home private <name>"));
        return 0;
    }

    private int sendPublicHomeUsage(ServerCommandSource source) {
        source.sendError((Text)Text.literal((String)"Usage: /phome <name> or /phome list"));
        return 0;
    }

    private int sendTpaUsage(ServerCommandSource source) {
        source.sendError((Text)Text.literal((String)"Usage: /tpa <player>"));
        return 0;
    }

    private int sendTpaHereUsage(ServerCommandSource source) {
        source.sendError((Text)Text.literal((String)"Usage: /tpahere <player>"));
        return 0;
    }

    private int requestTeleportToPlayer(ServerCommandSource source, String targetName) {
        return this.createTeleportRequest(source, targetName, TeleportRequestType.REQUESTER_TO_TARGET);
    }

    private int requestTeleportPlayerHere(ServerCommandSource source, String targetName) {
        return this.createTeleportRequest(source, targetName, TeleportRequestType.TARGET_TO_REQUESTER);
    }

    private int createTeleportRequest(ServerCommandSource source, String targetName, TeleportRequestType requestType) {
        ServerPlayerEntity previousRequester;
        TeleportRequest replacedIncoming;
        ServerPlayerEntity previousTarget;
        ServerPlayerEntity requester;
        try {
            requester = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(targetName);
        if (target == null) {
            source.sendError((Text)Text.literal((String)("Player is not online: " + targetName)));
            return 0;
        }
        if (target.getUuid().equals(requester.getUuid())) {
            source.sendError((Text)Text.literal((String)"You cannot send a teleport request to yourself."));
            return 0;
        }
        TeleportRequest replacedOutgoing = this.removeTeleportRequestByRequester(requester.getUuid());
        if (replacedOutgoing != null && (previousTarget = source.getServer().getPlayerManager().getPlayer(replacedOutgoing.targetUuid)) != null) {
            previousTarget.sendMessage((Text)Text.literal((String)(requester.getName().getString() + " cancelled their previous teleport request.")), false);
        }
        if ((replacedIncoming = this.removeTeleportRequestByTarget(target.getUuid())) != null && (previousRequester = source.getServer().getPlayerManager().getPlayer(replacedIncoming.requesterUuid)) != null) {
            previousRequester.sendMessage((Text)Text.literal((String)(target.getName().getString() + " already had a pending request. Yours was replaced.")), false);
        }
        TeleportRequest request = new TeleportRequest(requester.getUuid(), requester.getName().getString(), target.getUuid(), target.getName().getString(), requestType, System.currentTimeMillis());
        this.pendingTeleportRequestsByTarget.put(target.getUuid(), request);
        this.pendingTeleportRequestTargetByRequester.put(requester.getUuid(), target.getUuid());
        if (requestType == TeleportRequestType.REQUESTER_TO_TARGET) {
            requester.sendMessage((Text)Text.literal((String)("Teleport request sent to " + target.getName().getString() + ".")), false);
            this.sendTeleportRequestPrompt(target, request, Text.literal((String)(requester.getName().getString() + " wants to teleport to you. ")));
        } else {
            requester.sendMessage((Text)Text.literal((String)("Teleport-here request sent to " + target.getName().getString() + ".")), false);
            this.sendTeleportRequestPrompt(target, request, Text.literal((String)(requester.getName().getString() + " wants you to teleport to them. ")));
        }
        return 1;
    }

    private void sendTeleportRequestPrompt(ServerPlayerEntity target, TeleportRequest request, MutableText prefix) {
        MutableText prompt = Text.empty();
        prompt.append((Text)prefix.formatted(Formatting.YELLOW));
        prompt.append((Text)this.actionButton("[Accept]", "/tpaccept", Formatting.GREEN, "Accept teleport request from " + request.requesterName));
        prompt.append(ScreenTexts.SPACE);
        prompt.append((Text)this.actionButton("[Decline]", "/tpdecline", Formatting.RED, "Decline teleport request from " + request.requesterName));
        target.sendMessage((Text)prompt, false);
        this.playTeleportRequestPing(target);
    }

    private void playTeleportRequestPing(ServerPlayerEntity target) {
        RegistryEntry<net.minecraft.sound.SoundEvent> sound = SoundEvents.BLOCK_NOTE_BLOCK_PLING;
        target.networkHandler.sendPacket((Packet)new PlaySoundS2CPacket(sound, SoundCategory.MASTER, target.getX(), target.getY(), target.getZ(), 1.0f, 1.5f, target.getWorld().getRandom().nextLong()));
    }

    private int acceptTeleportRequest(ServerCommandSource source) {
        ServerPlayerEntity target;
        try {
            target = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        TeleportRequest request = this.removeTeleportRequestByTarget(target.getUuid());
        if (request == null) {
            source.sendError((Text)Text.literal((String)"You have no pending teleport requests."));
            return 0;
        }
        ServerPlayerEntity requester = source.getServer().getPlayerManager().getPlayer(request.requesterUuid);
        if (requester == null) {
            target.sendMessage((Text)Text.literal((String)"That player is no longer online."), false);
            return 0;
        }
        ServerPlayerEntity teleportingPlayer = request.requestType == TeleportRequestType.REQUESTER_TO_TARGET ? requester : target;
        ServerPlayerEntity destinationPlayer = request.requestType == TeleportRequestType.REQUESTER_TO_TARGET ? target : requester;
        ServerWorld destinationWorld = destinationPlayer.getServerWorld();
        this.loadDestinationArea(destinationWorld, destinationPlayer.getX(), destinationPlayer.getZ());
        teleportingPlayer.teleport(destinationWorld, destinationPlayer.getX(), destinationPlayer.getY(), destinationPlayer.getZ(), destinationPlayer.getYaw(), destinationPlayer.getPitch());
        requester.sendMessage((Text)Text.literal((String)(target.getName().getString() + " accepted your teleport request.")), false);
        target.sendMessage((Text)Text.literal((String)("You accepted " + requester.getName().getString() + "'s teleport request.")), false);
        return 1;
    }

    private int declineTeleportRequest(ServerCommandSource source) {
        ServerPlayerEntity target;
        try {
            target = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        TeleportRequest request = this.removeTeleportRequestByTarget(target.getUuid());
        if (request == null) {
            source.sendError((Text)Text.literal((String)"You have no pending teleport requests."));
            return 0;
        }
        target.sendMessage((Text)Text.literal((String)("You declined " + request.requesterName + "'s teleport request.")), false);
        ServerPlayerEntity requester = source.getServer().getPlayerManager().getPlayer(request.requesterUuid);
        if (requester != null) {
            requester.sendMessage((Text)Text.literal((String)(target.getName().getString() + " declined your teleport request.")), false);
        }
        return 1;
    }

    private TeleportRequest removeTeleportRequestByTarget(UUID targetUuid) {
        TeleportRequest request = this.pendingTeleportRequestsByTarget.remove(targetUuid);
        if (request != null) {
            this.pendingTeleportRequestTargetByRequester.remove(request.requesterUuid);
        }
        return request;
    }

    private TeleportRequest removeTeleportRequestByRequester(UUID requesterUuid) {
        UUID targetUuid = this.pendingTeleportRequestTargetByRequester.remove(requesterUuid);
        if (targetUuid == null) {
            return null;
        }
        return this.pendingTeleportRequestsByTarget.remove(targetUuid);
    }

    private void clearTeleportRequestsForPlayer(ServerPlayerEntity player, MinecraftServer server) {
        ServerPlayerEntity requester;
        TeleportRequest incoming;
        ServerPlayerEntity target;
        TeleportRequest outgoing = this.removeTeleportRequestByRequester(player.getUuid());
        if (outgoing != null && (target = server.getPlayerManager().getPlayer(outgoing.targetUuid)) != null) {
            target.sendMessage((Text)Text.literal((String)(player.getName().getString() + " went offline. Their teleport request was cancelled.")), false);
        }
        if ((incoming = this.removeTeleportRequestByTarget(player.getUuid())) != null && (requester = server.getPlayerManager().getPlayer(incoming.requesterUuid)) != null) {
            requester.sendMessage((Text)Text.literal((String)(player.getName().getString() + " went offline. Your teleport request was cancelled.")), false);
        }
    }

    private int setHome(ServerCommandSource source, String homeName) {
        String homeKey;
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        String normalizedHomeName = this.normalizeHomeName(homeName);
        if (normalizedHomeName.isEmpty()) {
            source.sendError((Text)Text.literal((String)"Home name cannot be empty."));
            return 0;
        }
        Map<String, SavedHome> homes = this.getOrCreateHomesForPlayer(player.getUuid());
        if (!homes.containsKey(homeKey = normalizedHomeName.toLowerCase()) && homes.size() >= 10) {
            source.sendError((Text)Text.literal((String)"Home limit reached. You can save up to 10 homes."));
            return 0;
        }
        SavedHome savedHome = new SavedHome(normalizedHomeName, player.getName().getString(), player.getWorld().getRegistryKey().getValue().toString(), player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch(), false);
        homes.put(homeKey, savedHome);
        this.savePlayerHomes();
        source.sendFeedback(() -> Text.literal((String)("Saved home '" + savedHome.displayName + "'.")), false);
        return 1;
    }

    private int promptDeleteHome(ServerCommandSource source, String homeName) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        String normalizedHomeName = this.normalizeHomeName(homeName);
        if (normalizedHomeName.isEmpty()) {
            source.sendError((Text)Text.literal((String)"Home name cannot be empty."));
            return 0;
        }
        Map<String, SavedHome> homes = this.playerHomes.get(player.getUuid());
        if (homes == null || homes.isEmpty()) {
            source.sendError((Text)Text.literal((String)"You do not have any homes saved."));
            return 0;
        }
        SavedHome existingHome = homes.get(normalizedHomeName.toLowerCase());
        if (existingHome == null) {
            source.sendError((Text)Text.literal((String)("Unknown home: " + normalizedHomeName)));
            return 0;
        }
        MutableText prompt = Text.literal((String)("Delete home '" + existingHome.displayName + "'? "));
        prompt.append((Text)this.actionButton("[Confirm Delete]", "/delhome confirm " + existingHome.displayName, Formatting.RED, "Delete this home now"));
        prompt.append(ScreenTexts.SPACE);
        prompt.append((Text)this.suggestButton("[Cancel]", "/home list", Formatting.GRAY, "Do nothing and show your homes again"));
        source.sendFeedback(() -> prompt, false);
        return 1;
    }

    private int confirmDeleteHome(ServerCommandSource source, String homeName) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        String normalizedHomeName = this.normalizeHomeName(homeName);
        if (normalizedHomeName.isEmpty()) {
            source.sendError((Text)Text.literal((String)"Home name cannot be empty."));
            return 0;
        }
        Map<String, SavedHome> homes = this.playerHomes.get(player.getUuid());
        if (homes == null || homes.isEmpty()) {
            source.sendError((Text)Text.literal((String)"You do not have any homes saved."));
            return 0;
        }
        SavedHome removedHome = homes.remove(normalizedHomeName.toLowerCase());
        if (removedHome == null) {
            source.sendError((Text)Text.literal((String)("Unknown home: " + normalizedHomeName)));
            return 0;
        }
        if (homes.isEmpty()) {
            this.playerHomes.remove(player.getUuid());
        }
        this.savePlayerHomes();
        source.sendFeedback(() -> Text.literal((String)("Deleted home '" + removedHome.displayName + "'.")), false);
        return 1;
    }

    private int teleportToHome(ServerCommandSource source, String homeName) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        String normalizedHomeName = this.normalizeHomeName(homeName);
        if (normalizedHomeName.isEmpty()) {
            source.sendError((Text)Text.literal((String)"Home name cannot be empty."));
            return 0;
        }
        Map<String, SavedHome> homes = this.playerHomes.get(player.getUuid());
        if (homes == null || homes.isEmpty()) {
            source.sendError((Text)Text.literal((String)"You do not have any homes saved."));
            return 0;
        }
        SavedHome savedHome = homes.get(normalizedHomeName.toLowerCase());
        if (savedHome == null) {
            source.sendError((Text)Text.literal((String)("Unknown home: " + normalizedHomeName)));
            return 0;
        }
        return this.teleportPlayerToSavedHome(player, savedHome, "Teleported to home '");
    }

    private int teleportToPublicHome(ServerCommandSource source, String homeName) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        String normalizedHomeName = this.normalizeHomeName(homeName);
        if (normalizedHomeName.isEmpty()) {
            source.sendError((Text)Text.literal((String)"Home name cannot be empty."));
            return 0;
        }
        List<SavedHome> matches = this.findPublicHomesByName(normalizedHomeName);
        if (matches.isEmpty()) {
            source.sendError((Text)Text.literal((String)("Unknown public home: " + normalizedHomeName)));
            return 0;
        }
        if (matches.size() > 1) {
            source.sendError((Text)Text.literal((String)("Public home '" + normalizedHomeName + "' is ambiguous. Ask an admin to rename or privatize duplicates.")));
            return 0;
        }
        SavedHome savedHome = matches.get(0);
        return this.teleportPlayerToSavedHome(player, savedHome, "Teleported to public home '", true);
    }

    private int teleportPlayerToSavedHome(ServerPlayerEntity player, SavedHome savedHome, String successPrefix) {
        return this.teleportPlayerToSavedHome(player, savedHome, successPrefix, false);
    }

    private int teleportPlayerToSavedHome(ServerPlayerEntity player, SavedHome savedHome, String successPrefix, boolean includeOwner) {
        ServerCommandSource source = player.getCommandSource();
        Identifier targetId = Identifier.tryParse((String)savedHome.worldId);
        if (targetId == null) {
            source.sendError((Text)Text.literal((String)("Saved home has an invalid dimension: " + savedHome.worldId)));
            return 0;
        }
        RegistryKey targetKey = RegistryKey.of((RegistryKey)RegistryKeys.WORLD, (Identifier)targetId);
        ServerWorld targetWorld = player.getServer().getWorld(targetKey);
        if (targetWorld == null) {
            source.sendError((Text)Text.literal((String)("Home world is not loaded: " + savedHome.worldId)));
            return 0;
        }
        this.loadDestinationArea(targetWorld, savedHome.x, savedHome.z);
        player.teleport(targetWorld, savedHome.x, savedHome.y, savedHome.z, savedHome.yaw, savedHome.pitch);
        String ownerSuffix = includeOwner ? " owned by " + savedHome.ownerName : "";
        source.sendFeedback(() -> Text.literal((String)(successPrefix + savedHome.displayName + "'" + ownerSuffix + ".")), false);
        return 1;
    }

    private int setHomeVisibility(ServerCommandSource source, String homeName, boolean isPublic) {
        SavedHome conflict;
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        String normalizedHomeName = this.normalizeHomeName(homeName);
        if (normalizedHomeName.isEmpty()) {
            source.sendError((Text)Text.literal((String)"Home name cannot be empty."));
            return 0;
        }
        Map<String, SavedHome> homes = this.playerHomes.get(player.getUuid());
        if (homes == null || homes.isEmpty()) {
            source.sendError((Text)Text.literal((String)"You do not have any homes saved."));
            return 0;
        }
        String homeKey = normalizedHomeName.toLowerCase();
        SavedHome currentHome = homes.get(homeKey);
        if (currentHome == null) {
            source.sendError((Text)Text.literal((String)("Unknown home: " + normalizedHomeName)));
            return 0;
        }
        if (currentHome.isPublic == isPublic) {
            source.sendError((Text)Text.literal((String)("Home '" + currentHome.displayName + "' is already " + (isPublic ? "public" : "private") + ".")));
            return 0;
        }
        if (isPublic && (conflict = this.findConflictingPublicHome(player.getUuid(), homeKey)) != null) {
            source.sendError((Text)Text.literal((String)("Cannot make '" + currentHome.displayName + "' public because '" + conflict.displayName + "' from " + conflict.ownerName + " already uses that public name.")));
            return 0;
        }
        SavedHome updatedHome = currentHome.withVisibility(isPublic).withOwnerName(player.getName().getString());
        homes.put(homeKey, updatedHome);
        this.savePlayerHomes();
        source.sendFeedback(() -> Text.literal((String)("Home '" + updatedHome.displayName + "' is now " + (isPublic ? "public" : "private") + ".")), false);
        return 1;
    }

    private int listHomes(ServerCommandSource source) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        Map<String, SavedHome> homes = this.playerHomes.get(player.getUuid());
        if (homes == null || homes.isEmpty()) {
            source.sendError((Text)Text.literal((String)"You do not have any homes saved."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal((String)"Your homes:"), false);
        for (SavedHome home : homes.values()) {
            MutableText line = Text.literal((String)"- ");
            line.append((Text)this.actionButton("[Teleport]", "/home " + home.displayName, Formatting.GREEN, "Teleport to this home"));
            line.append(ScreenTexts.SPACE);
            line.append((Text)this.actionButton(home.isPublic ? "[Make Private]" : "[Make Public]", home.isPublic ? "/home private " + home.displayName : "/home public " + home.displayName, home.isPublic ? Formatting.GOLD : Formatting.AQUA, home.isPublic ? "Make this home private" : "Make this home public"));
            line.append(ScreenTexts.SPACE);
            line.append((Text)this.actionButton("[Delete]", "/delhome " + home.displayName, Formatting.RED, "Open a delete confirmation prompt"));
            line.append(ScreenTexts.SPACE);
            line.append((Text)Text.literal((String)(home.displayName + " (" + (home.isPublic ? "public" : "private") + ")")).formatted(Formatting.WHITE));
            if (home.isPublic) {
                line.append((Text)Text.literal((String)(" via /phome " + home.displayName)).formatted(Formatting.GRAY));
            }
            source.sendFeedback(() -> line, false);
        }
        return 1;
    }

    private int listPublicHomes(ServerCommandSource source) {
        ArrayList<SavedHome> publicHomes = new ArrayList<SavedHome>();
        for (Map<String, SavedHome> homes : this.playerHomes.values()) {
            for (SavedHome home : homes.values()) {
                if (!home.isPublic) continue;
                publicHomes.add(home);
            }
        }
        if (publicHomes.isEmpty()) {
            source.sendError((Text)Text.literal((String)"There are no public homes available."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal((String)"Public homes:"), false);
        for (SavedHome home : publicHomes) {
            MutableText line = Text.literal((String)"- ");
            line.append((Text)this.actionButton("[Teleport]", "/phome " + home.displayName, Formatting.GREEN, "Teleport to this public home"));
            line.append(ScreenTexts.SPACE);
            line.append((Text)Text.literal((String)(home.displayName + " by " + home.ownerName)).formatted(Formatting.WHITE));
            line.append((Text)Text.literal((String)(" in " + home.worldId)).formatted(Formatting.GRAY));
            source.sendFeedback(() -> line, false);
        }
        return 1;
    }

    private void loadPlayerHomes() {
        this.playerHomes.clear();
        if (!Files.exists(this.playerHomesPath, new LinkOption[0])) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(this.playerHomesPath);){
            JsonElement root = JsonParser.parseReader((Reader)reader);
            if (!root.isJsonArray()) {
                return;
            }
            for (JsonElement element : root.getAsJsonArray()) {
                JsonObject obj;
                if (!element.isJsonObject() || !(obj = element.getAsJsonObject()).has("playerUuid") || !obj.has("name") || !obj.has("worldId") || !obj.has("x") || !obj.has("y") || !obj.has("z")) continue;
                try {
                    UUID playerUuid = UUID.fromString(obj.get("playerUuid").getAsString());
                    String homeName = obj.get("name").getAsString();
                    String ownerName = obj.has("ownerName") ? obj.get("ownerName").getAsString() : "Unknown";
                    SavedHome home = new SavedHome(homeName, ownerName, obj.get("worldId").getAsString(), obj.get("x").getAsDouble(), obj.get("y").getAsDouble(), obj.get("z").getAsDouble(), obj.has("yaw") ? obj.get("yaw").getAsFloat() : 0.0f, obj.has("pitch") ? obj.get("pitch").getAsFloat() : 0.0f, obj.has("public") && obj.get("public").getAsBoolean());
                    this.getOrCreateHomesForPlayer(playerUuid).put(homeName.toLowerCase(), home);
                }
                catch (IllegalArgumentException illegalArgumentException) {}
            }
        }
        catch (IOException iOException) {
            // empty catch block
        }
    }

    private void savePlayerHomes() {
        try {
            Files.createDirectories(this.playerHomesPath.getParent(), new FileAttribute[0]);
            try (BufferedWriter writer = Files.newBufferedWriter(this.playerHomesPath, new OpenOption[0]);){
                JsonArray entries = new JsonArray();
                for (Map.Entry<UUID, Map<String, SavedHome>> playerEntry : this.playerHomes.entrySet()) {
                    for (SavedHome home : playerEntry.getValue().values()) {
                        JsonObject obj = new JsonObject();
                        obj.addProperty("playerUuid", playerEntry.getKey().toString());
                        obj.addProperty("name", home.displayName);
                        obj.addProperty("ownerName", home.ownerName);
                        obj.addProperty("public", Boolean.valueOf(home.isPublic));
                        obj.addProperty("worldId", home.worldId);
                        obj.addProperty("x", (Number)home.x);
                        obj.addProperty("y", (Number)home.y);
                        obj.addProperty("z", (Number)home.z);
                        obj.addProperty("yaw", (Number)Float.valueOf(home.yaw));
                        obj.addProperty("pitch", (Number)Float.valueOf(home.pitch));
                        entries.add((JsonElement)obj);
                    }
                }
                GSON.toJson((JsonElement)entries, (Appendable)writer);
            }
        }
        catch (IOException iOException) {
            // empty catch block
        }
    }

    private Map<String, SavedHome> getOrCreateHomesForPlayer(UUID playerUuid) {
        return this.playerHomes.computeIfAbsent(playerUuid, ignored -> new LinkedHashMap());
    }

    private boolean isUniversalGravesGrave(WorldView world, BlockPos pos) {
        Identifier blockId = Registries.BLOCK.getId(world.getBlockState(pos).getBlock());
        return "universal_graves".equals(blockId.getNamespace()) && "grave".equals(blockId.getPath());
    }

    private boolean isUniversalGraveOwner(ServerPlayerEntity player, WorldView world, BlockPos pos) {
        if (!this.isUniversalGravesGrave(world, pos)) {
            return false;
        }
        try {
            Object blockEntity = world.getBlockEntity(pos);
            if (blockEntity == null) {
                return false;
            }
            Method getGrave = blockEntity.getClass().getMethod("getGrave", new Class[0]);
            Object grave = getGrave.invoke(blockEntity, new Object[0]);
            if (grave == null) {
                return false;
            }
            Method isOwner = grave.getClass().getMethod("isOwner", ServerPlayerEntity.class);
            return Boolean.TRUE.equals(isOwner.invoke(grave, player));
        }
        catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private CompletableFuture<Suggestions> suggestOwnHomeNames(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        ServerPlayerEntity player;
        try {
            player = context.getSource().getPlayerOrThrow();
        }
        catch (Exception e) {
            return builder.buildFuture();
        }
        Map<String, SavedHome> homes = this.playerHomes.get(player.getUuid());
        if (homes == null || homes.isEmpty()) {
            return builder.buildFuture();
        }
        ArrayList<String> names = new ArrayList<String>();
        for (SavedHome home : homes.values()) {
            names.add(home.displayName);
        }
        return CommandSource.suggestMatching(names, builder);
    }

    private CompletableFuture<Suggestions> suggestOnlinePlayerNames(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        ArrayList<String> names = new ArrayList<String>();
        for (ServerPlayerEntity player : context.getSource().getServer().getPlayerManager().getPlayerList()) {
            names.add(player.getGameProfile().getName());
        }
        return CommandSource.suggestMatching(names, builder);
    }

    private String normalizeHomeName(String homeName) {
        return homeName == null ? "" : homeName.trim();
    }

    private List<SavedHome> findPublicHomesByName(String homeName) {
        String homeKey = homeName.toLowerCase();
        ArrayList<SavedHome> matches = new ArrayList<SavedHome>();
        for (Map<String, SavedHome> homes : this.playerHomes.values()) {
            SavedHome home = homes.get(homeKey);
            if (home == null || !home.isPublic) continue;
            matches.add(home);
        }
        return matches;
    }

    private SavedHome findConflictingPublicHome(UUID ownerUuid, String homeKey) {
        for (Map.Entry<UUID, Map<String, SavedHome>> entry : this.playerHomes.entrySet()) {
            SavedHome home;
            if (entry.getKey().equals(ownerUuid) || (home = entry.getValue().get(homeKey)) == null || !home.isPublic) continue;
            return home;
        }
        return null;
    }

    private MutableText actionButton(String label, String command, Formatting color, String hoverText) {
        return Text.literal((String)label).styled(style -> style.withColor(color).withUnderline(Boolean.valueOf(true)).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal((String)hoverText))));
    }

    private MutableText suggestButton(String label, String command, Formatting color, String hoverText) {
        return Text.literal((String)label).styled(style -> style.withColor(color).withUnderline(Boolean.valueOf(true)).withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command)).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal((String)hoverText))));
    }

    private int sendAdminHelp(ServerCommandSource source) {
        if (!this.hasAdminPermission(source)) {
            source.sendError((Text)Text.literal((String)"You do not have permission to use admin claim commands."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal((String)"Admin commands: /claim admin create <x1> <z1> <x2> <z2>, /claim admin unclaim, /claim admin trust <player|uuid>, /claim admin untrust <player|uuid>"), false);
        source.sendFeedback(() -> Text.literal((String)"Portal commands: /claim portal add <id>, /claim portal addhub <id>, /claim portal remove <id>, /claim portal list"), false);
        source.sendFeedback(() -> Text.literal((String)"Fallback roots: /adminclaim, /goldclaimadmin, /gcadmin"), false);
        return 1;
    }

    private int sendPortalHelp(ServerCommandSource source) {
        if (!this.hasAdminPermission(source)) {
            source.sendError((Text)Text.literal((String)"You do not have permission to manage portals."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal((String)"Portal commands: /claim portal ... or /gcportal ..."), false);
        source.sendFeedback(() -> Text.literal((String)"Examples: /gcportal add <id>, /gcportal addhub <id>, /gcportal remove|delete <id>, /gcportal list"), false);
        source.sendFeedback(() -> Text.literal((String)"Base selection: sneak + right-click a block with an arrow to set the portal base (one-point selection)."), false);
        return 1;
    }

    private int sendAdminCreateUsage(ServerCommandSource source) {
        source.sendError((Text)Text.literal((String)"Usage: /claim admin create <x1> <z1> <x2> <z2>"));
        return 0;
    }

    private int showClaimInfo(ServerCommandSource source) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        Optional<Claim> claimOpt = this.claimManager.getClaimAt(dimension, player.getBlockX(), player.getBlockZ());
        if (claimOpt.isEmpty()) {
            source.sendFeedback(() -> Text.literal((String)"No claim at your current location."), false);
            return 1;
        }
        Claim claim = claimOpt.get();
        String ownerLabel = this.getClaimOwnerLabel(claim);
        source.sendFeedback(() -> Text.literal((String)("Claim owner: " + ownerLabel + " | Type: " + (claim.adminClaim ? "admin" : "player") + " | Size: " + claim.width() + "x" + claim.depth() + " | Dimension: " + this.getClaimDimensionLabel(claim.dimension) + " | Corners: [" + claim.minX + "," + claim.minZ + "] -> [" + claim.maxX + "," + claim.maxZ + "]")), false);
        this.showClaimOutline(player, claim);
        return 1;
    }

    private int addClaimPortal(ServerCommandSource source, String portalId) {
        ServerPlayerEntity player;
        if (!this.hasAdminPermission(source)) {
            source.sendError((Text)Text.literal((String)"You do not have permission to manage portals."));
            return 0;
        }
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        Optional<BlockPos> baseOpt = this.selectionManager.getPortalBase(player.getUuid());
        if (baseOpt.isEmpty()) {
            source.sendError((Text)Text.literal((String)"Select portal base first: sneak + right-click a block with an arrow."));
            return 0;
        }
        BlockPos base = baseOpt.get();
        ClaimPortalRegistry.Result result = this.portalRegistry.addClaimSide(player, this.claimManager, portalId, base);
        if (!result.success) {
            source.sendError((Text)Text.literal((String)result.message));
            return 0;
        }
        this.selectionManager.clearPortalBase(player.getUuid());
        source.sendFeedback(() -> Text.literal((String)result.message), false);
        return 1;
    }

    private int addHubPortal(ServerCommandSource source, String portalId) {
        ServerPlayerEntity player;
        if (!this.hasAdminPermission(source)) {
            source.sendError((Text)Text.literal((String)"You do not have permission to manage portals."));
            return 0;
        }
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        String worldId = player.getWorld().getRegistryKey().getValue().toString();
        if (!this.config.portalHubDimension.equals(worldId)) {
            source.sendError((Text)Text.literal((String)("Run this command in the hub dimension: " + this.config.portalHubDimension + ".")));
            return 0;
        }
        Optional<BlockPos> baseOpt = this.selectionManager.getPortalBase(player.getUuid());
        if (baseOpt.isEmpty()) {
            source.sendError((Text)Text.literal((String)"Select portal base first: sneak + right-click a block with an arrow."));
            return 0;
        }
        BlockPos base = baseOpt.get();
        ClaimPortalRegistry.Result result = this.portalRegistry.addHubSide(player, portalId, base);
        if (!result.success) {
            source.sendError((Text)Text.literal((String)result.message));
            return 0;
        }
        this.selectionManager.clearPortalBase(player.getUuid());
        source.sendFeedback(() -> Text.literal((String)result.message), false);
        return 1;
    }

    private int removeClaimPortal(ServerCommandSource source, String portalId) {
        if (!this.hasAdminPermission(source)) {
            source.sendError((Text)Text.literal((String)"You do not have permission to manage portals."));
            return 0;
        }
        ClaimPortalRegistry.Result result = this.portalRegistry.removePortal(portalId, source.getServer());
        if (!result.success) {
            source.sendError((Text)Text.literal((String)result.message));
            return 0;
        }
        source.sendFeedback(() -> Text.literal((String)result.message), false);
        return 1;
    }

    private int listClaimPortals(ServerCommandSource source) {
        if (!this.hasAdminPermission(source)) {
            source.sendError((Text)Text.literal((String)"You do not have permission to manage portals."));
            return 0;
        }
        List<ClaimPortalRegistry.ClaimPortalRecord> portals = this.portalRegistry.listPortals();
        if (portals.isEmpty()) {
            source.sendFeedback(() -> Text.literal((String)"No claim portals are registered."), false);
            return 1;
        }
        source.sendFeedback(() -> Text.literal((String)("Registered claim portals: " + portals.size())), false);
        for (ClaimPortalRegistry.ClaimPortalRecord record : portals) {
            source.sendFeedback(() -> Text.literal((String)(record.portalId + " | claim " + record.claimDimension + " [" + record.claimMinX + "," + record.claimMinZ + "] -> [" + record.claimMaxX + "," + record.claimMaxZ + "]")), false);
        }
        return 1;
    }

    private int teleportToHub(ServerCommandSource source) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        if (!this.teleportToHub(player)) {
            return 0;
        }
        source.sendFeedback(() -> Text.literal((String)"Teleported to hub."), false);
        return 1;
    }

    private boolean teleportToHub(ServerPlayerEntity player) {
        return this.teleportToHub(player, true, true);
    }

    private boolean teleportToHub(ServerPlayerEntity player, boolean rememberLocation) {
        return this.teleportToHub(player, rememberLocation, false);
    }

    private boolean teleportToHub(ServerPlayerEntity player, boolean rememberLocation, boolean showTitle) {
        Identifier targetId;
        this.pendingHubTeleports.remove(player.getUuid());
        if (rememberLocation) {
            this.rememberWildLocation(player);
        }
        if ((targetId = Identifier.tryParse((String)this.config.portalHubDimension)) == null) {
            player.sendMessage((Text)Text.literal((String)("Invalid hub dimension in config: " + this.config.portalHubDimension)));
            return false;
        }
        RegistryKey targetKey = RegistryKey.of((RegistryKey)RegistryKeys.WORLD, (Identifier)targetId);
        ServerWorld targetWorld = player.getServer().getWorld(targetKey);
        if (targetWorld == null) {
            player.sendMessage((Text)Text.literal((String)("Hub world is not loaded: " + this.config.portalHubDimension)));
            return false;
        }
        this.loadDestinationArea(targetWorld, (double)this.config.hubTeleportX + 0.5, (double)this.config.hubTeleportZ + 0.5);
        player.teleport(targetWorld, (double)this.config.hubTeleportX + 0.5, (double)this.config.hubTeleportY, (double)this.config.hubTeleportZ + 0.5, this.config.hubTeleportYaw, this.config.hubTeleportPitch);
        if (showTitle) {
            this.sendHubWildTitle(player, "Welcome to the Hub");
        }
        return true;
    }

    private int teleportToWild(ServerCommandSource source) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        if (!this.teleportToWild(player)) {
            return 0;
        }
        source.sendFeedback(() -> Text.literal((String)"Teleported to wild."), false);
        return 1;
    }

    private boolean teleportToWild(ServerPlayerEntity player) {
        return this.teleportToWild(player, true);
    }

    private boolean teleportToWild(ServerPlayerEntity player, boolean showTitle) {
        Identifier targetId;
        this.pendingHubTeleports.remove(player.getUuid());
        SavedLocation savedLocation = this.lastWildLocations.get(player.getUuid());
        targetId = Identifier.tryParse((String)OVERWORLD_DIMENSION);
        if (targetId == null) {
            player.sendMessage((Text)Text.literal((String)"Invalid wild destination dimension."));
            return false;
        }
        RegistryKey targetKey = RegistryKey.of((RegistryKey)RegistryKeys.WORLD, (Identifier)targetId);
        ServerWorld targetWorld = player.getServer().getWorld(targetKey);
        if (targetWorld == null) {
            player.sendMessage((Text)Text.literal((String)("Wild world is not loaded: " + OVERWORLD_DIMENSION)));
            return false;
        }
        if (savedLocation != null && OVERWORLD_DIMENSION.equals(savedLocation.worldId)) {
            this.loadDestinationArea(targetWorld, savedLocation.x, savedLocation.z);
            player.teleport(targetWorld, savedLocation.x, savedLocation.y, savedLocation.z, savedLocation.yaw, savedLocation.pitch);
            if (showTitle) {
                this.sendHubWildTitle(player, "The Wild");
            }
            return true;
        }
        BlockPos spawn = targetWorld.getSpawnPos();
        this.loadDestinationArea(targetWorld, (double)spawn.getX() + 0.5, (double)spawn.getZ() + 0.5);
        player.teleport(targetWorld, (double)spawn.getX() + 0.5, (double)spawn.getY(), (double)spawn.getZ() + 0.5, targetWorld.getSpawnAngle(), 0.0f);
        if (showTitle) {
            this.sendHubWildTitle(player, "The Wild");
        }
        return true;
    }

    private int teleportToTestingWorld(ServerCommandSource source, String dimensionId, String label, boolean voidWorld) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError(Text.literal("Only players can run this command."));
            return 0;
        }
        ServerWorld targetWorld = this.getWorld(player.getServer(), dimensionId);
        if (targetWorld == null) {
            source.sendError(Text.literal(label + " is not loaded."));
            return 0;
        }
        BlockPos spawn = targetWorld.getSpawnPos();
        double x = spawn.getX() + 0.5;
        double y = voidWorld ? 68.0 : spawn.getY();
        double z = spawn.getZ() + 0.5;
        this.loadDestinationArea(targetWorld, x, z);
        player.teleport(targetWorld, x, y, z, targetWorld.getSpawnAngle(), 0.0f);
        this.sendHubWildTitle(player, label);
        source.sendFeedback(() -> Text.literal("Teleported to " + label + "."), false);
        return 1;
    }

    private int randomTeleport(ServerCommandSource source) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        if (!this.randomTeleport(player)) {
            return 0;
        }
        source.sendFeedback(() -> Text.literal((String)"Randomly teleported in the overworld."), false);
        return 1;
    }

    private boolean randomTeleport(ServerPlayerEntity player) {
        Identifier targetId = Identifier.tryParse((String)this.config.wildDimension);
        if (targetId == null) {
            player.sendMessage((Text)Text.literal((String)("Invalid random teleport dimension: " + this.config.wildDimension)));
            return false;
        }
        RegistryKey targetKey = RegistryKey.of((RegistryKey)RegistryKeys.WORLD, (Identifier)targetId);
        ServerWorld targetWorld = player.getServer().getWorld(targetKey);
        if (targetWorld == null) {
            player.sendMessage((Text)Text.literal((String)("Random teleport world is not loaded: " + this.config.wildDimension)));
            return false;
        }
        BlockPos spawn = targetWorld.getSpawnPos();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int radius = Math.max(1, this.config.randomTeleportRadius);
        int attempts = Math.max(1, this.config.randomTeleportAttempts);
        for (int attempt = 0; attempt < attempts; ++attempt) {
            double angle = random.nextDouble(Math.PI * 2.0);
            double distance = Math.sqrt(random.nextDouble()) * (double)radius;
            int x = spawn.getX() + (int)Math.round(Math.cos(angle) * distance);
            int z = spawn.getZ() + (int)Math.round(Math.sin(angle) * distance);
            targetWorld.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
            int y = targetWorld.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feetPos = new BlockPos(x, y, z);
            if (!this.isSafeRandomTeleportDestination(targetWorld, feetPos)) {
                continue;
            }
            this.pendingHubTeleports.remove(player.getUuid());
            player.teleport(targetWorld, (double)x + 0.5, (double)y, (double)z + 0.5, player.getYaw(), player.getPitch());
            return true;
        }
        player.sendMessage((Text)Text.literal((String)"Could not find a safe random teleport location. Try again."));
        return false;
    }

    private boolean isSafeRandomTeleportDestination(ServerWorld world, BlockPos feetPos) {
        BlockPos groundPos = feetPos.down();
        BlockPos headPos = feetPos.up();
        BlockState groundState = world.getBlockState(groundPos);
        BlockState feetState = world.getBlockState(feetPos);
        BlockState headState = world.getBlockState(headPos);
        return groundState.isSolidBlock((WorldView)world, groundPos) && groundState.getFluidState().isEmpty() && feetState.isAir() && feetState.getFluidState().isEmpty() && headState.isAir() && headState.getFluidState().isEmpty();
    }

    private void sendHubWildTitle(ServerPlayerEntity player, String message) {
        MutableText titleText = Text.literal((String)message).setStyle(Style.EMPTY.withColor(HUB_TITLE_COLOR));
        player.networkHandler.sendPacket((Packet)new TitleFadeS2CPacket(5, 50, 5));
        player.networkHandler.sendPacket((Packet)new SubtitleS2CPacket((Text)Text.empty()));
        player.networkHandler.sendPacket((Packet)new TitleS2CPacket((Text)titleText));
    }

    private void loadDestinationArea(ServerWorld world, double x, double z) {
        int centerChunkX = Math.floorDiv((int)Math.floor(x), 16);
        int centerChunkZ = Math.floorDiv((int)Math.floor(z), 16);
        for (int chunkX = centerChunkX - 1; chunkX <= centerChunkX + 1; ++chunkX) {
            for (int chunkZ = centerChunkZ - 1; chunkZ <= centerChunkZ + 1; ++chunkZ) {
                world.getChunk(chunkX, chunkZ);
            }
        }
    }

    private int showTps(ServerCommandSource source) {
        double mspt = this.getAverageMsPerTick(source.getServer());
        if (mspt <= 0.0) {
            source.sendFeedback(() -> Text.literal((String)"TPS unavailable right now."), false);
            return 1;
        }
        double tps = Math.min(20.0, 1000.0 / mspt);
        source.sendFeedback(() -> Text.literal((String)String.format("Server TPS: %.2f | MSPT: %.2f", tps, mspt)), false);
        return 1;
    }

    private int feedPlayer(ServerCommandSource source) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 20, 255, true, false, true));
        player.sendMessage((Text)Text.literal((String)"You have been fed."), false);
        return 1;
    }

    private double getAverageMsPerTick(Object server) {
        Double directValue = this.invokeNumericServerMetric(server, "getAverageNanosPerTick");
        if (directValue != null && directValue > 0.0) {
            return directValue / 1000000.0;
        }
        Double fallbackValue = this.invokeNumericServerMetric(server, "getAverageTickTime");
        if (fallbackValue != null && fallbackValue > 0.0) {
            return fallbackValue;
        }
        Double finalFallback = this.invokeNumericServerMetric(server, "getTickTime");
        if (finalFallback != null && finalFallback > 0.0) {
            return finalFallback;
        }
        return -1.0;
    }

    private Double invokeNumericServerMetric(Object server, String methodName) {
        try {
            Method method = server.getClass().getMethod(methodName, new Class[0]);
            Object value = method.invoke(server, new Object[0]);
            if (value instanceof Number) {
                Number number = (Number)value;
                return number.doubleValue();
            }
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            // empty catch block
        }
        return null;
    }

    public static GoldClaimMod getInstance() {
        return INSTANCE;
    }

    public void identifyClaim(ServerPlayerEntity player, BlockPos pos) {
        this.identifyClaim(player, player.getWorld().getRegistryKey().getValue().toString(), pos);
    }

    private void identifyClaim(ServerPlayerEntity player, String dimension, BlockPos pos) {
        Optional<Claim> claimOpt = this.claimManager.getClaimAt(dimension, pos.getX(), pos.getZ());
        if (claimOpt.isEmpty()) {
            player.sendMessage((Text)Text.literal((String)"No claim here."), true);
            return;
        }
        Claim claim = claimOpt.get();
        player.sendMessage((Text)Text.literal((String)("Claim: " + this.getClaimOwnerLabel(claim) + " | " + this.getClaimDimensionLabel(claim.dimension) + " | " + claim.width() + "x" + claim.depth())), true);
        this.showClaimOutline(player, claim);
    }

    private int visualizeClaimAtFeet(ServerCommandSource source) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        Optional<Claim> claimOpt = this.claimManager.getClaimAt(dimension, player.getBlockX(), player.getBlockZ());
        if (claimOpt.isEmpty()) {
            source.sendError((Text)Text.literal((String)"No claim at your current location."));
            return 0;
        }
        this.showClaimOutline(player, claimOpt.get());
        source.sendFeedback(() -> Text.literal((String)"Claim border visualization shown."), false);
        return 1;
    }

    private int listClaims(ServerCommandSource source) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        List<Claim> claims = this.claimManager.getClaimsForOwner(player.getUuid());
        if (claims.isEmpty()) {
            source.sendFeedback(() -> Text.literal((String)"You do not own any claims yet."), false);
            return 1;
        }
        source.sendFeedback(() -> Text.literal((String)("You own " + claims.size() + " claim(s).")), false);
        for (int i = 0; i < claims.size(); ++i) {
            Claim claim = claims.get(i);
            int idx = i + 1;
            String type = claim.adminClaim ? "[admin] " : "";
            source.sendFeedback(() -> Text.literal((String)("#" + idx + " " + type + this.getClaimDimensionLabel(claim.dimension) + " [" + claim.minX + "," + claim.minZ + "] -> [" + claim.maxX + "," + claim.maxZ + "]")), false);
        }
        return 1;
    }

    private int expandClaimAtFeet(ServerCommandSource source, int amount) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        ClaimManager.ExpandResult result = this.claimManager.expandClaimAt(player.getUuid(), player.getWorld().getRegistryKey().getValue().toString(), player.getBlockX(), player.getBlockZ(), amount, this.getHorizontalLookDirection(player), this.config.allowOpsBypass, player.hasPermissionLevel(2));
        if (!result.success()) {
            if (result.error() == ClaimManager.ExpandError.NOT_FOUND) {
                source.sendError((Text)Text.literal((String)"No claim at your location."));
            } else if (result.error() == ClaimManager.ExpandError.NOT_OWNER) {
                source.sendError((Text)Text.literal((String)"You can only expand your own claim."));
            } else if (result.error() == ClaimManager.ExpandError.CLAIM_LIMIT_EXCEEDED) {
                int availableArea;
                int addedArea;
                int missingArea = Math.max(0, (addedArea = Math.max(0, result.addedArea())) - (availableArea = Math.max(0, result.availableArea())));
                source.sendError((Text)Text.literal((String)("Not enough claim blocks to expand by " + amount + ". Need " + addedArea + " additional blocks, have " + availableArea + " available" + (String)(missingArea > 0 ? " (short by " + missingArea + ")." : "."))));
            } else {
                source.sendError((Text)Text.literal((String)"Expansion overlaps another claim."));
            }
            return 0;
        }
        Claim claim = result.claim();
        int remainingBlocks = this.config.maxClaimBlocksPerPlayerPerDimension - this.claimManager.getTotalClaimBlocks(player.getUuid(), player.getWorld().getRegistryKey().getValue().toString());
        source.sendFeedback(() -> Text.literal((String)("Claim expanded by " + amount + ". New size: " + claim.width() + "x" + claim.depth() + ". Remaining claim blocks in this dimension: " + Math.max(0, remainingBlocks) + ".")), false);
        this.showClaimOutline(player, claim);
        return 1;
    }

    private Direction getHorizontalLookDirection(ServerPlayerEntity player) {
        Direction facing = player.getHorizontalFacing();
        if (facing.getAxis().isHorizontal()) {
            return facing;
        }
        Vec3d look = player.getRotationVec(1.0f);
        if (Math.abs(look.x) > Math.abs(look.z)) {
            return look.x >= 0.0 ? Direction.EAST : Direction.WEST;
        }
        return look.z >= 0.0 ? Direction.SOUTH : Direction.NORTH;
    }

    private int unclaimAtFeet(ServerCommandSource source) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        boolean removed = this.claimManager.removeClaimAt(player.getUuid(), player.getWorld().getRegistryKey().getValue().toString(), player.getBlockX(), player.getBlockZ(), this.config.allowOpsBypass, player.hasPermissionLevel(2));
        if (!removed) {
            source.sendError((Text)Text.literal((String)"No removable claim at your location."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal((String)"Claim removed."), false);
        return 1;
    }

    private int createAdminClaim(ServerCommandSource source, int x1, int z1, int x2, int z2) {
        ServerPlayerEntity player;
        if (!this.hasAdminPermission(source)) {
            source.sendError((Text)Text.literal((String)"You do not have permission to create admin claims."));
            return 0;
        }
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        ClaimManager.CreateResult result = this.claimManager.createAdminClaim(dimension, minX, maxX, minZ, maxZ);
        if (!result.success()) {
            if (result.error() == ClaimManager.CreateError.TOO_SMALL) {
                source.sendError((Text)Text.literal((String)("Admin claim too small. Minimum size is " + this.config.minimumClaimWidth + "x" + this.config.minimumClaimDepth + ".")));
            } else {
                source.sendError((Text)Text.literal((String)"Admin claim overlaps an existing claim."));
            }
            return 0;
        }
        this.claimManager.getClaimAt(dimension, player.getBlockX(), player.getBlockZ()).ifPresent(claim -> this.showClaimOutline(player, (Claim)claim));
        source.sendFeedback(() -> Text.literal((String)("Admin claim created: [" + minX + "," + minZ + "] -> [" + maxX + "," + maxZ + "].")), false);
        return 1;
    }

    private int unclaimAdminAtFeet(ServerCommandSource source) {
        ServerPlayerEntity player;
        if (!this.hasAdminPermission(source)) {
            source.sendError((Text)Text.literal((String)"You do not have permission to remove admin claims."));
            return 0;
        }
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        boolean removed = this.claimManager.removeAdminClaimAt(player.getWorld().getRegistryKey().getValue().toString(), player.getBlockX(), player.getBlockZ());
        if (!removed) {
            source.sendError((Text)Text.literal((String)"No admin claim at your location."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal((String)"Admin claim removed."), false);
        return 1;
    }

    private int adminTrustPlayer(ServerCommandSource source, String targetName) {
        ServerPlayerEntity player;
        if (!this.hasAdminPermission(source)) {
            source.sendError((Text)Text.literal((String)"You do not have permission to trust players in admin claims."));
            return 0;
        }
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        Optional<GameProfile> profileOpt = this.resolveTargetProfile(source, targetName);
        if (profileOpt.isEmpty()) {
            source.sendError((Text)Text.literal((String)("Unknown player: " + targetName + ". Use exact name or UUID.")));
            return 0;
        }
        ClaimManager.TrustResult trusted = this.claimManager.trustPlayerAt(player.getUuid(), profileOpt.get().getId(), profileOpt.get().getName(), player.getWorld().getRegistryKey().getValue().toString(), player.getBlockX(), player.getBlockZ(), this.config.allowOpsBypass, true);
        if (!trusted.success()) {
            if (trusted.error() == ClaimManager.TrustError.NOT_FOUND) {
                source.sendError((Text)Text.literal((String)"No admin claim at your location."));
            } else {
                if (trusted.error() == ClaimManager.TrustError.ALREADY_TRUSTED) {
                    source.sendFeedback(() -> Text.literal((String)(((GameProfile)profileOpt.get()).getName() + " (" + String.valueOf(((GameProfile)profileOpt.get()).getId()) + ") is already trusted in this admin claim.")), false);
                    return 1;
                }
                source.sendError((Text)Text.literal((String)"Stand in an admin claim to trust players there."));
            }
            return 0;
        }
        source.sendFeedback(() -> Text.literal((String)(((GameProfile)profileOpt.get()).getName() + " (" + String.valueOf(((GameProfile)profileOpt.get()).getId()) + ") is now trusted in this admin claim.")), false);
        ServerPlayerEntity targetPlayer = source.getServer().getPlayerManager().getPlayer(profileOpt.get().getId());
        if (targetPlayer != null && !targetPlayer.getUuid().equals(player.getUuid())) {
            targetPlayer.sendMessage((Text)Text.literal((String)("You were trusted in an admin claim by " + player.getName().getString() + ".")));
        }
        return 1;
    }

    private int adminUntrustPlayer(ServerCommandSource source, String targetName) {
        ServerPlayerEntity player;
        if (!this.hasAdminPermission(source)) {
            source.sendError((Text)Text.literal((String)"You do not have permission to untrust players in admin claims."));
            return 0;
        }
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        Optional<GameProfile> profileOpt = this.resolveTargetProfile(source, targetName);
        if (profileOpt.isEmpty()) {
            source.sendError((Text)Text.literal((String)("Unknown player: " + targetName + ". Use exact name or UUID.")));
            return 0;
        }
        ClaimManager.TrustResult untrusted = this.claimManager.untrustPlayerAt(player.getUuid(), profileOpt.get().getId(), profileOpt.get().getName(), player.getWorld().getRegistryKey().getValue().toString(), player.getBlockX(), player.getBlockZ(), this.config.allowOpsBypass, true);
        if (!untrusted.success()) {
            if (untrusted.error() == ClaimManager.TrustError.NOT_FOUND) {
                source.sendError((Text)Text.literal((String)"No admin claim at your location."));
            } else {
                if (untrusted.error() == ClaimManager.TrustError.NOT_TRUSTED) {
                    source.sendFeedback(() -> Text.literal((String)(((GameProfile)profileOpt.get()).getName() + " is not trusted in this admin claim.")), false);
                    return 1;
                }
                source.sendError((Text)Text.literal((String)"Stand in an admin claim to untrust players there."));
            }
            return 0;
        }
        source.sendFeedback(() -> Text.literal((String)(((GameProfile)profileOpt.get()).getName() + " (" + String.valueOf(((GameProfile)profileOpt.get()).getId()) + ") is no longer trusted in this admin claim.")), false);
        ServerPlayerEntity targetPlayer = source.getServer().getPlayerManager().getPlayer(profileOpt.get().getId());
        if (targetPlayer != null && !targetPlayer.getUuid().equals(player.getUuid())) {
            targetPlayer.sendMessage((Text)Text.literal((String)("You were untrusted in an admin claim by " + player.getName().getString() + ".")));
        }
        return 1;
    }

    private int trustPlayer(ServerCommandSource source, String targetName) {
        return this.trustPlayer(source, targetName, false);
    }

    private int trustPlayer(ServerCommandSource source, String targetName, boolean interactOnly) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        Optional<GameProfile> profileOpt = this.resolveTargetProfile(source, targetName);
        if (profileOpt.isEmpty()) {
            source.sendError((Text)Text.literal((String)("Unknown player: " + targetName + ". Use exact name or UUID.")));
            return 0;
        }
        ClaimManager.TrustResult trusted = this.claimManager.trustPlayerAt(player.getUuid(), profileOpt.get().getId(), profileOpt.get().getName(), player.getWorld().getRegistryKey().getValue().toString(), player.getBlockX(), player.getBlockZ(), this.config.allowOpsBypass, player.hasPermissionLevel(2), interactOnly);
        if (!trusted.success()) {
            if (trusted.error() == ClaimManager.TrustError.NOT_FOUND) {
                source.sendError((Text)Text.literal((String)"No claim at your location."));
            } else {
                if (trusted.error() == ClaimManager.TrustError.ALREADY_TRUSTED) {
                    source.sendFeedback(() -> Text.literal((String)(((GameProfile)profileOpt.get()).getName() + " (" + String.valueOf(((GameProfile)profileOpt.get()).getId()) + ") is already trusted in this claim.")), false);
                    return 1;
                }
                source.sendError((Text)Text.literal((String)"Stand in your claim to trust players there."));
            }
            return 0;
        }
        source.sendFeedback(() -> Text.literal((String)(((GameProfile)profileOpt.get()).getName() + (interactOnly ? " may now interact in this claim." : " is now trusted in this claim."))), false);
        ServerPlayerEntity targetPlayer = source.getServer().getPlayerManager().getPlayer(profileOpt.get().getId());
        if (targetPlayer != null && !targetPlayer.getUuid().equals(player.getUuid())) {
            targetPlayer.sendMessage((Text)Text.literal((String)("You were trusted in a claim by " + player.getName().getString() + ".")));
        }
        return 1;
    }

    private int trustManager(ServerCommandSource source, String targetName) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        Optional<GameProfile> profileOpt = this.resolveTargetProfile(source, targetName);
        if (profileOpt.isEmpty()) {
            source.sendError((Text)Text.literal((String)("Unknown player: " + targetName + ". Use exact name or UUID.")));
            return 0;
        }
        ClaimManager.TrustResult managed = this.claimManager.managePlayerAt(player.getUuid(), profileOpt.get().getId(), profileOpt.get().getName(), player.getWorld().getRegistryKey().getValue().toString(), player.getBlockX(), player.getBlockZ(), this.config.allowOpsBypass, player.hasPermissionLevel(2));
        if (!managed.success()) {
            if (managed.error() == ClaimManager.TrustError.NOT_FOUND) {
                source.sendError((Text)Text.literal((String)"No claim at your location."));
            } else {
                if (managed.error() == ClaimManager.TrustError.ALREADY_TRUSTED) {
                    source.sendFeedback(() -> Text.literal((String)(((GameProfile)profileOpt.get()).getName() + " is already a manager of this claim.")), false);
                    return 1;
                }
                source.sendError((Text)Text.literal((String)"Stand in your claim, as its owner, to appoint managers there."));
            }
            return 0;
        }
        source.sendFeedback(() -> Text.literal((String)(((GameProfile)profileOpt.get()).getName() + " is now a manager of this claim and has full control over it.")), false);
        ServerPlayerEntity targetPlayer = source.getServer().getPlayerManager().getPlayer(profileOpt.get().getId());
        if (targetPlayer != null && !targetPlayer.getUuid().equals(player.getUuid())) {
            targetPlayer.sendMessage((Text)Text.literal((String)("You were made a manager of a claim by " + player.getName().getString() + ". You now have full control over it.")));
        }
        return 1;
    }

    private int untrustManager(ServerCommandSource source, String targetName) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        Optional<GameProfile> profileOpt = this.resolveTargetProfile(source, targetName);
        if (profileOpt.isEmpty()) {
            source.sendError((Text)Text.literal((String)("Unknown player: " + targetName + ". Use exact name or UUID.")));
            return 0;
        }
        ClaimManager.TrustResult unmanaged = this.claimManager.unmanagePlayerAt(player.getUuid(), profileOpt.get().getId(), profileOpt.get().getName(), player.getWorld().getRegistryKey().getValue().toString(), player.getBlockX(), player.getBlockZ(), this.config.allowOpsBypass, player.hasPermissionLevel(2));
        if (!unmanaged.success()) {
            if (unmanaged.error() == ClaimManager.TrustError.NOT_FOUND) {
                source.sendError((Text)Text.literal((String)"No claim at your location."));
            } else {
                if (unmanaged.error() == ClaimManager.TrustError.NOT_TRUSTED) {
                    source.sendFeedback(() -> Text.literal((String)(((GameProfile)profileOpt.get()).getName() + " is not a manager of this claim.")), false);
                    return 1;
                }
                source.sendError((Text)Text.literal((String)"Stand in your claim, as its owner, to remove managers there."));
            }
            return 0;
        }
        source.sendFeedback(() -> Text.literal((String)(((GameProfile)profileOpt.get()).getName() + " is no longer a manager of this claim.")), false);
        ServerPlayerEntity targetPlayer = source.getServer().getPlayerManager().getPlayer(profileOpt.get().getId());
        if (targetPlayer != null && !targetPlayer.getUuid().equals(player.getUuid())) {
            targetPlayer.sendMessage((Text)Text.literal((String)("You are no longer a manager of a claim owned by " + player.getName().getString() + ".")));
        }
        return 1;
    }

    private int untrustPlayer(ServerCommandSource source, String targetName) {
        return this.untrustPlayer(source, targetName, false);
    }

    private int untrustPlayer(ServerCommandSource source, String targetName, boolean interactOnly) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        }
        catch (Exception e) {
            source.sendError((Text)Text.literal((String)"Only players can run this command."));
            return 0;
        }
        Optional<GameProfile> profileOpt = this.resolveTargetProfile(source, targetName);
        if (profileOpt.isEmpty()) {
            source.sendError((Text)Text.literal((String)("Unknown player: " + targetName + ". Use exact name or UUID.")));
            return 0;
        }
        ClaimManager.TrustResult untrusted = this.claimManager.untrustPlayerAt(player.getUuid(), profileOpt.get().getId(), profileOpt.get().getName(), player.getWorld().getRegistryKey().getValue().toString(), player.getBlockX(), player.getBlockZ(), this.config.allowOpsBypass, player.hasPermissionLevel(2), interactOnly);
        if (!untrusted.success()) {
            if (untrusted.error() == ClaimManager.TrustError.NOT_FOUND) {
                source.sendError((Text)Text.literal((String)"No claim at your location."));
            } else {
                if (untrusted.error() == ClaimManager.TrustError.NOT_TRUSTED) {
                    source.sendFeedback(() -> Text.literal((String)(((GameProfile)profileOpt.get()).getName() + " is not trusted in this claim.")), false);
                    return 1;
                }
                source.sendError((Text)Text.literal((String)"Stand in your claim to untrust players there."));
            }
            return 0;
        }
        source.sendFeedback(() -> Text.literal((String)(((GameProfile)profileOpt.get()).getName() + (interactOnly ? " can no longer interact in this claim." : " is no longer trusted in this claim."))), false);
        ServerPlayerEntity targetPlayer = source.getServer().getPlayerManager().getPlayer(profileOpt.get().getId());
        if (targetPlayer != null && !targetPlayer.getUuid().equals(player.getUuid())) {
            targetPlayer.sendMessage((Text)Text.literal((String)("You were untrusted in a claim by " + player.getName().getString() + ".")));
        }
        return 1;
    }

    private Optional<GameProfile> resolveTargetProfile(ServerCommandSource source, String input) {
        try {
            ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(UUID.fromString(input));
            if (player != null) {
                return Optional.of(player.getGameProfile());
            }
        }
        catch (IllegalArgumentException e) {
        }
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            if (player.getGameProfile().getName().equalsIgnoreCase(input)) {
                return Optional.of(player.getGameProfile());
            }
        }
        return Optional.empty();
    }

    private String getClaimDimensionLabel(String claimDimensionId) {
        if ("minecraft:overworld".equals(claimDimensionId)) {
            return "Wild";
        }
        return claimDimensionId;
    }

    private void showClaimOutline(ServerPlayerEntity player, Claim claim) {
        this.visualizationSessions.put(player.getUuid(), new VisualizationSession(claim, Math.max(1, this.config.visualizationDurationTicks)));
    }

    private void renderClaimOutlineFrame(ServerPlayerEntity player, Claim claim, int phase) {
        World class_19372 = player.getWorld();
        if (!(class_19372 instanceof ServerWorld)) {
            return;
        }
        ServerWorld serverWorld = (ServerWorld)class_19372;
        int step = Math.max(1, this.config.visualizationParticleStep);
        int frame = Math.floorMod(phase, step);
        int y = player.getBlockY() + this.config.visualizationHeightOffset;
        for (int x = claim.minX; x <= claim.maxX; ++x) {
            if (Math.floorMod(x - claim.minX, step) != frame) continue;
            this.spawnBorderParticle(serverWorld, x, y, claim.minZ, player);
            this.spawnBorderParticle(serverWorld, x, y, claim.maxZ, player);
        }
        for (int z = claim.minZ; z <= claim.maxZ; ++z) {
            if (Math.floorMod(z - claim.minZ, step) != frame) continue;
            this.spawnBorderParticle(serverWorld, claim.minX, y, z, player);
            this.spawnBorderParticle(serverWorld, claim.maxX, y, z, player);
        }
        if (frame == 0) {
            this.spawnBorderParticle(serverWorld, claim.minX, y, claim.minZ, player);
            this.spawnBorderParticle(serverWorld, claim.minX, y, claim.maxZ, player);
            this.spawnBorderParticle(serverWorld, claim.maxX, y, claim.minZ, player);
            this.spawnBorderParticle(serverWorld, claim.maxX, y, claim.maxZ, player);
        }
    }

    private void spawnBorderParticle(ServerWorld world, int x, int y, int z, ServerPlayerEntity viewer) {
        world.spawnParticles(viewer, (ParticleEffect)ParticleTypes.END_ROD, true, (double)x + 0.5, (double)y + 0.15, (double)z + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private void deny(ServerPlayerEntity player, String message) {
        player.sendMessage((Text)Text.literal((String)message), this.config.denyMessageInActionBar);
    }

    private boolean hasAdminPermission(ServerCommandSource source) {
        return source.hasPermissionLevel(2);
    }

    static {
        HUB_TITLE_COLOR = TextColor.fromRgb((int)4286945);
    }

    private static final class SavedLocation {
        private final String worldId;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;

        private SavedLocation(String worldId, double x, double y, double z, float yaw, float pitch) {
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof SavedLocation)) {
                return false;
            }
            SavedLocation other = (SavedLocation)obj;
            return Double.compare(this.x, other.x) == 0 && Double.compare(this.y, other.y) == 0 && Double.compare(this.z, other.z) == 0 && Float.compare(this.yaw, other.yaw) == 0 && Float.compare(this.pitch, other.pitch) == 0 && this.worldId.equals(other.worldId);
        }

        public int hashCode() {
            int result = this.worldId.hashCode();
            result = 31 * result + Double.hashCode(this.x);
            result = 31 * result + Double.hashCode(this.y);
            result = 31 * result + Double.hashCode(this.z);
            result = 31 * result + Float.hashCode(this.yaw);
            result = 31 * result + Float.hashCode(this.pitch);
            return result;
        }
    }

    private static enum TeleportRequestType {
        REQUESTER_TO_TARGET,
        TARGET_TO_REQUESTER;

    }

    private static final class TeleportRequest {
        private final UUID requesterUuid;
        private final String requesterName;
        private final UUID targetUuid;
        private final String targetName;
        private final TeleportRequestType requestType;
        private final long createdAtMs;

        private TeleportRequest(UUID requesterUuid, String requesterName, UUID targetUuid, String targetName, TeleportRequestType requestType, long createdAtMs) {
            this.requesterUuid = requesterUuid;
            this.requesterName = requesterName;
            this.targetUuid = targetUuid;
            this.targetName = targetName;
            this.requestType = requestType;
            this.createdAtMs = createdAtMs;
        }
    }

    private static final class SavedHome {
        private final String displayName;
        private final String ownerName;
        private final String worldId;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;
        private final boolean isPublic;

        private SavedHome(String displayName, String ownerName, String worldId, double x, double y, double z, float yaw, float pitch, boolean isPublic) {
            this.displayName = displayName;
            this.ownerName = ownerName;
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.isPublic = isPublic;
        }

        private SavedHome withVisibility(boolean nextPublic) {
            return new SavedHome(this.displayName, this.ownerName, this.worldId, this.x, this.y, this.z, this.yaw, this.pitch, nextPublic);
        }

        private SavedHome withOwnerName(String nextOwnerName) {
            return new SavedHome(this.displayName, nextOwnerName, this.worldId, this.x, this.y, this.z, this.yaw, this.pitch, this.isPublic);
        }
    }

    private static class VisualizationSession {
        private final Claim claim;
        private int ticksRemaining;
        private int phase;

        private VisualizationSession(Claim claim, int ticksRemaining) {
            this.claim = claim;
            this.ticksRemaining = ticksRemaining;
            this.phase = 0;
        }
    }
}
