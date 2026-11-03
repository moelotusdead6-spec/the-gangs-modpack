package com.elysium.goldclaim;

import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

final class ClaimPermissionsSmokeTest {
    static void register() {
        Block itemMachine = Registry.register(Registries.BLOCK,
            new Identifier("goldclaim_policy_smoke", "item_machine"),
            new Block(AbstractBlock.Settings.create()));
        Block fluidMachine = Registry.register(Registries.BLOCK,
            new Identifier("goldclaim_policy_smoke", "fluid_machine"),
            new Block(AbstractBlock.Settings.create()));
        ItemStorage.SIDED.registerForBlocks((world, pos, state, blockEntity, side) ->
            side == Direction.NORTH ? Storage.empty() : null, itemMachine);
        FluidStorage.SIDED.registerForBlocks((world, pos, state, blockEntity, side) ->
            side == Direction.UP ? Storage.empty() : null, fluidMachine);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            verify(server, itemMachine, fluidMachine);
            try {
                Files.writeString(Path.of("policy-smoke-passed.txt"),
                    "PASS: claimed levers, buttons, pressure plates and real elevator travel, vanilla and Fabric storage protection, "
                            + "machine configuration, item and placement boundaries, trust levels and claim-block commands.\n");
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot record claim smoke-test result", failure);
            }
            server.stop(false);
        });
    }

    private static void verify(MinecraftServer server, Block itemMachine, Block fluidMachine) {
        ServerWorld world = server.getOverworld();
        RecordingPlayer owner = new RecordingPlayer(server, world, "OwnerFix");
        RecordingPlayer visitor = new RecordingPlayer(server, world, "VisitorFix");
        RecordingPlayer trusted = new RecordingPlayer(server, world, "TrustedFix");
        RecordingPlayer guest = new RecordingPlayer(server, world, "GuestFix");
        String dimension = world.getRegistryKey().getValue().toString();
        ClaimManager claims = claimManager();
        claims.removeClaimAt(owner.getUuid(), dimension, 300, 300, true, true);
        claims.removeClaimAt(owner.getUuid(), "minecraft:the_nether", 300, 300, true, true);
        require(claims.createClaim(owner.getUuid(), owner.getGameProfile().getName(),
            dimension, 300, 309, 300, 309).success(), "fixture claim must be created");
        require(claims.createClaim(owner.getUuid(), owner.getGameProfile().getName(),
            "minecraft:the_nether", 300, 304, 300, 304).success(), "other-dimension claim must be created");
        require(claims.trustPlayerAt(owner.getUuid(), visitor.getUuid(), visitor.getGameProfile().getName(),
            dimension, 300, 300, true, false, true).success(), "visitor must receive interaction trust");
        require(claims.trustPlayerAt(owner.getUuid(), trusted.getUuid(), trusted.getGameProfile().getName(),
            dimension, 300, 300, true, false).success(), "trusted player must receive full trust");

        BlockPos pos = new BlockPos(304, 64, 304);
        Identifier elevatorId = new Identifier("openblocks", "elevator_block");
        require(Registries.BLOCK.containsId(elevatorId), "the real OpenBlocks elevator mod must be installed");
        Block elevator = Registries.BLOCK.get(elevatorId);
        for (Block control : new Block[]{Blocks.LEVER, Blocks.STONE_BUTTON, Blocks.OAK_BUTTON,
                Blocks.STONE_PRESSURE_PLATE, Blocks.OAK_PRESSURE_PLATE, Blocks.CRAFTING_TABLE, elevator}) {
            world.setBlockState(pos, control.getDefaultState(), 2);
            require(use(visitor, world, pos) == ActionResult.PASS, "interaction trust must allow " + control);
            require(use(guest, world, pos) == ActionResult.FAIL, "untrusted interaction must remain protected");
        }
        for (Direction side : Direction.values()) {
            world.setBlockState(pos.offset(side), Blocks.STONE.getDefaultState(), 2);
        }
        world.setBlockState(pos, Blocks.LEVER.getDefaultState(), 2);
        require(interact(visitor, world, pos, Direction.UP).isAccepted()
                && world.getBlockState(pos).get(LeverBlock.POWERED),
            "interaction trust must actually activate a claimed lever");
        world.setBlockState(pos, Blocks.STONE_BUTTON.getDefaultState(), 2);
        require(interact(visitor, world, pos, Direction.UP).isAccepted()
                && world.getBlockState(pos).get(ButtonBlock.POWERED),
            "interaction trust must actually activate a claimed button");
        world.setBlockState(pos, elevator.getDefaultState(), 2);
        visitor.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE));
        require(interact(visitor, world, pos, Direction.UP) == ActionResult.FAIL,
            "interaction trust must not permit changing an elevator's appearance");
        require(UseBlockCallback.EVENT.invoker().interact(visitor, world, Hand.OFF_HAND,
                new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false)) == ActionResult.FAIL,
            "an off-hand interaction must not use a block from the main hand to alter an elevator");
        visitor.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        trusted.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE));
        require(use(trusted, world, pos) == ActionResult.PASS,
            "full trust must retain elevator configuration access");
        trusted.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        for (Block sensitive : new Block[]{Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL,
                Blocks.HOPPER, Blocks.DROPPER, Blocks.DISPENSER, Blocks.COMPOSTER, Blocks.FURNACE,
                Blocks.BREWING_STAND, Blocks.LECTERN, Blocks.JUKEBOX, Blocks.ENDER_CHEST,
                Blocks.REPEATER, Blocks.COMPARATOR, Blocks.DAYLIGHT_DETECTOR, Blocks.NOTE_BLOCK,
                Blocks.OAK_SIGN, Blocks.OAK_HANGING_SIGN, Blocks.BEEHIVE, Blocks.BEE_NEST,
                Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE, Blocks.CAULDRON, Blocks.WATER_CAULDRON,
                itemMachine, fluidMachine}) {
            world.setBlockState(pos, sensitive.getDefaultState(), 2);
            require(use(visitor, world, pos) == ActionResult.FAIL, "interaction trust must not access " + sensitive);
            require(use(owner, world, pos) == ActionResult.PASS, "owner must retain access to " + sensitive);
            require(use(trusted, world, pos) == ActionResult.PASS, "full trust must retain access to " + sensitive);
        }
        require(claims.setPublicTrustAt(owner.getUuid(), dimension, 300, 300,
            com.elysium.goldclaim.data.Claim.TrustLevel.INTERACT, true).success(),
            "public interaction trust must be enabled");
        world.setBlockState(pos, Blocks.LEVER.getDefaultState(), 2);
        require(use(guest, world, pos) == ActionResult.PASS, "public interaction must permit controls");
        world.setBlockState(pos, Blocks.CHEST.getDefaultState(), 2);
        require(use(guest, world, pos) == ActionResult.FAIL, "public interaction must not permit containers");

        BlockPos lower = new BlockPos(306, 64, 306);
        BlockPos upper = lower.up(6);
        for (int y = 1; y < 6; y++) world.setBlockState(lower.up(y), Blocks.AIR.getDefaultState(), 2);
        world.setBlockState(lower, elevator.getDefaultState(), 2);
        world.setBlockState(upper, elevator.getDefaultState(), 2);
        visitor.refreshPositionAndAngles(306.5, 71, 306.5, 0, 0);
        visitor.setSneaking(true);
        visitor.setPose(EntityPose.CROUCHING);
        elevator.onSteppedOn(world, upper, world.getBlockState(upper), visitor);
        require(Math.abs(visitor.getY() - 65.0) < 0.01, "interaction-trusted player must travel on the real elevator");
        visitor.setSneaking(false);
        visitor.setPose(EntityPose.STANDING);

        BlockPos log = new BlockPos(302, 64, 302);
        world.setBlockState(log, Blocks.OAK_LOG.getDefaultState(), 2);
        visitor.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        require(interact(visitor, world, log, Direction.UP) == ActionResult.FAIL,
            "interaction trust must not permit stripping a claimed block");
        require(world.getBlockState(log).isOf(Blocks.OAK_LOG), "denied item use must not change the block");
        trusted.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        require(interact(trusted, world, log, Direction.UP).isAccepted(), "full trust must permit item use");
        require(world.getBlockState(log).isOf(Blocks.STRIPPED_OAK_LOG), "full-trust item use must execute");

        BlockPos outside = new BlockPos(299, 64, 305);
        BlockPos inside = outside.east();
        world.setBlockState(outside, Blocks.STONE.getDefaultState(), 2);
        world.setBlockState(inside, Blocks.AIR.getDefaultState(), 2);
        visitor.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE));
        require(interact(visitor, world, outside, Direction.EAST) == ActionResult.FAIL,
            "placing across a claim boundary must be denied");
        require(world.getBlockState(inside).isAir(), "denied placement must not alter the claim");
        owner.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE));
        require(interact(owner, world, outside, Direction.EAST).isAccepted(), "owner must be able to place into the claim");
        require(world.getBlockState(inside).isOf(Blocks.STONE), "owner placement must execute");

        BlockPos waterSource = new BlockPos(308, 64, 302);
        for (int y = 1; y <= 4; y++) world.setBlockState(waterSource.up(y), Blocks.AIR.getDefaultState(), 2);
        world.setBlockState(waterSource, Blocks.WATER.getDefaultState(), 2);
        require(world.getFluidState(waterSource).isStill(), "bucket fixture must contain source water");
        visitor.refreshPositionAndAngles(308.5, 65, 302.5, 0, 90);
        visitor.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BUCKET));
        HitResult visitorWaterHit = visitor.raycast(5.0, 1.0f, true);
        require(visitorWaterHit.getType() == HitResult.Type.BLOCK
                && ((BlockHitResult)visitorWaterHit).getBlockPos().equals(waterSource),
            "visitor bucket fixture must target the water source; hit "
                + visitorWaterHit.getType() + " at " + ((BlockHitResult)visitorWaterHit).getBlockPos());
        require(Items.BUCKET.use(world, visitor, Hand.MAIN_HAND).getResult() == ActionResult.FAIL,
            "interaction trust must not permit removing claimed water");
        require(world.getBlockState(waterSource).isOf(Blocks.WATER), "denied bucket use must leave water intact");
        owner.refreshPositionAndAngles(308.5, 65, 302.5, 0, 90);
        owner.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BUCKET));
        HitResult waterHit = owner.raycast(5.0, 1.0f, true);
        require(waterHit.getType() == HitResult.Type.BLOCK
                && ((BlockHitResult)waterHit).getBlockPos().equals(waterSource),
            "bucket fixture must target the water source; hit " + waterHit + " from " + owner.getEyePos());
        var ownerBucketResult = Items.BUCKET.use(world, owner, Hand.MAIN_HAND).getResult();
        require(ownerBucketResult.isAccepted(), "owner must be able to collect claimed water; result="
            + ownerBucketResult + ", fluid=" + world.getFluidState(waterSource)
            + ", vanillaModify=" + world.canPlayerModifyAt(owner, waterSource)
            + ", vanillaPlace=" + owner.canPlaceOn(waterSource.up(), Direction.UP, owner.getMainHandStack())
            + ", messages=" + owner.messages);
        require(world.getBlockState(waterSource).isAir(), "owner bucket use must collect water");

        world.setBlockState(inside, Blocks.AIR.getDefaultState(), 2);
        world.setBlockState(inside.east(), Blocks.AIR.getDefaultState(), 2);
        visitor.refreshPositionAndAngles(301.5, 63, 305.5, 90, 0);
        visitor.setHeadYaw(90);
        visitor.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        HitResult boundaryHit = visitor.raycast(5.0, 1.0f, false);
        require(boundaryHit.getType() == HitResult.Type.BLOCK
                && ((BlockHitResult)boundaryHit).getBlockPos().equals(outside)
                && ((BlockHitResult)boundaryHit).getSide() == Direction.EAST,
            "bucket boundary fixture must target the outside block's east face; hit "
                + boundaryHit.getType() + " at " + ((BlockHitResult)boundaryHit).getBlockPos()
                + " side=" + ((BlockHitResult)boundaryHit).getSide() + ", eye=" + visitor.getEyePos()
                + ", yaw=" + visitor.getYaw() + ", headYaw=" + visitor.getHeadYaw()
                + ", pitch=" + visitor.getPitch()
                + ", outside=" + world.getBlockState(outside));
        require(Items.WATER_BUCKET.use(world, visitor, Hand.MAIN_HAND).getResult() == ActionResult.FAIL,
            "placing fluid across a claim boundary must be denied");
        require(world.getBlockState(inside).isAir(), "denied bucket use must not place fluid in the claim");
        owner.refreshPositionAndAngles(301.5, 63, 305.5, 90, 0);
        owner.setHeadYaw(90);
        owner.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        require(Items.WATER_BUCKET.use(world, owner, Hand.MAIN_HAND).getResult().isAccepted(),
            "owner must be able to place fluid into the claim");
        require(world.getBlockState(inside).isOf(Blocks.WATER), "owner bucket use must place water");
        world.setBlockState(inside, Blocks.AIR.getDefaultState(), 2);
        world.setBlockState(outside, Blocks.OAK_FENCE.getDefaultState(), 2);
        visitor.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        require(Items.WATER_BUCKET.use(world, visitor, Hand.MAIN_HAND).getResult().isAccepted(),
            "an unclaimed block may be waterlogged next to a claim");
        require(world.getBlockState(outside).get(Properties.WATERLOGGED) && world.getBlockState(inside).isAir(),
            "outside waterlogging must not modify the neighboring claim");

        owner.refreshPositionAndAngles(301.5, 65, 301.5, 0, 0);
        server.getUserCache().add(visitor.getGameProfile());
        server.getUserCache().add(trusted.getGameProfile());
        owner.messages.clear();
        execute(server, owner, "claim trustlist", 1);
        require(owner.messages.stream().anyMatch(message -> message.equals("- PUBLIC: INTERACT")),
            "trust list must show public interaction trust");
        require(count(owner.messages, "VisitorFix (") == 1
                && owner.messages.stream().anyMatch(message -> message.contains("VisitorFix (")
                    && message.endsWith(": INTERACT")), "interaction player must appear exactly once");
        require(count(owner.messages, "TrustedFix (") == 1
                && owner.messages.stream().anyMatch(message -> message.contains("TrustedFix (")
                    && message.endsWith(": FULL")), "full-trust player must appear exactly once");
        require(claims.managePlayerAt(owner.getUuid(), trusted.getUuid(), trusted.getGameProfile().getName(),
            dimension, 300, 300, true, false).success(), "owner must be able to grant management");
        owner.messages.clear();
        execute(server, owner, "claim trustlist", 1);
        require(count(owner.messages, "TrustedFix (") == 1
                && owner.messages.stream().anyMatch(message -> message.contains("TrustedFix (")
                    && message.endsWith(": MANAGER")), "manager must appear once at the highest level");

        owner.messages.clear();
        execute(server, owner, "claim help", 1);
        require(owner.messages.stream().anyMatch(message -> message.contains("/claim trustlist")
                && message.contains("/claimblocks")), "claim help must list the new commands");

        owner.messages.clear();
        execute(server, owner, "claimblocks", 1);
        String blocks = owner.messages.stream().filter(message -> message.contains("Claim blocks in ")).findFirst()
            .orElseThrow(() -> new IllegalStateException("claimblocks did not report usage"));
        Matcher totals = Pattern.compile("used (\\d+) / (\\d+), remaining (\\d+)").matcher(blocks);
        require(totals.find() && Integer.parseInt(totals.group(1)) == 100
                && Integer.parseInt(totals.group(3)) == Math.max(0,
                    Integer.parseInt(totals.group(2)) - 100),
            "claimblocks must show current-dimension usage and remaining allowance: " + blocks);

        visitor.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        world.onPlayerConnected(visitor);
        try {
            for (Block plate : new Block[]{Blocks.STONE_PRESSURE_PLATE, Blocks.OAK_PRESSURE_PLATE}) {
                world.setBlockState(pos, plate.getDefaultState(), 2);
                plate.onEntityCollision(world.getBlockState(pos), world, pos, visitor);
                require(world.getBlockState(pos).get(Properties.POWERED),
                    "interaction-trusted player must actually activate " + plate);
            }
        } finally {
            world.removePlayer(visitor, Entity.RemovalReason.DISCARDED);
        }

        claims.removeClaimAt(owner.getUuid(), dimension, 300, 300, true, false);
        claims.removeClaimAt(owner.getUuid(), "minecraft:the_nether", 300, 300, true, false);
    }

    private static ClaimManager claimManager() {
        try {
            var field = GoldClaimMod.class.getDeclaredField("claimManager");
            field.setAccessible(true);
            return (ClaimManager)field.get(GoldClaimMod.getInstance());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot access fixture claim manager", failure);
        }
    }

    private static ActionResult use(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        return UseBlockCallback.EVENT.invoker().interact(player, world, Hand.MAIN_HAND,
            new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));
    }

    private static ActionResult interact(ServerPlayerEntity player, ServerWorld world, BlockPos pos, Direction side) {
        return player.interactionManager.interactBlock(player, world, player.getMainHandStack(), Hand.MAIN_HAND,
            new BlockHitResult(Vec3d.ofCenter(pos), side, pos, false));
    }

    private static void execute(MinecraftServer server, RecordingPlayer player, String command, int expected) {
        try {
            require(server.getCommandManager().getDispatcher().execute(command, player.getCommandSource()) == expected,
                "Unexpected result for /" + command);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new IllegalStateException("Cannot execute /" + command, failure);
        }
    }

    private static long count(List<String> messages, String text) {
        return messages.stream().filter(message -> message.contains(text)).count();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class RecordingPlayer extends ServerPlayerEntity {
        final List<String> messages = new ArrayList<>();

        RecordingPlayer(MinecraftServer server, ServerWorld world, String name) {
            super(server, world, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
            this.networkHandler = new ServerPlayNetworkHandler(server,
                new ClientConnection(NetworkSide.SERVERBOUND), this) {
                @Override public void sendPacket(Packet<?> packet) {
                    if (packet instanceof GameMessageS2CPacket message) messages.add(message.content().getString());
                }
            };
        }

        @Override public void sendMessage(Text message, boolean overlay) { messages.add(message.getString()); }
        @Override public void sendMessageToClient(Text message, boolean overlay) { messages.add(message.getString()); }
        @Override public boolean isDisconnected() { return false; }
    }
}
