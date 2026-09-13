/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  net.minecraft.block.Blocks
 *  net.minecraft.item.map.MapState48
 *  net.minecraft.util.math.BlockPos
 *  net.minecraft.util.math.Direction
 *  net.minecraft.util.math.Direction$class_2351
 *  net.minecraft.scoreboard.Team0
 *  net.minecraft.state.property.Properties
 *  net.minecraft.state.property.Property
 *  net.minecraft.client.render.VertexFormatElement0
 *  net.minecraft.server.world.ServerWorld
 *  net.minecraft.server.network.ServerPlayerEntity
 *  net.minecraft.registry.RegistryKey
 *  net.minecraft.loot.entry.LootPoolEntry23
 *  net.minecraft.loot.entry.LootPoolEntry24
 *  net.minecraft.server.MinecraftServer
 */
package com.elysium.goldclaim.portal;

import com.elysium.goldclaim.ClaimManager;
import com.elysium.goldclaim.GoldClaimConfig;
import com.elysium.goldclaim.data.Claim;
import com.elysium.goldclaim.portal.MultiworldPortalBridge;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;

public class ClaimPortalRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CLAIM_PORTAL_SUFFIX = "_claim";
    private static final String HUB_PORTAL_SUFFIX = "_hub";
    private static final String PORTAL_DESTINATION_PREFIX = "p:";
    private static final Identifier CUSTOM_PORTAL_BLOCK_ID = Identifier.of((String)"customportalapi", (String)"customportalblock");
    private final Path registryPath;
    private final Path multiworldPortalsPath;
    private final GoldClaimConfig config;
    private final List<ClaimPortalRecord> records = new ArrayList<ClaimPortalRecord>();

    public ClaimPortalRegistry(Path registryPath, GoldClaimConfig config) {
        this.registryPath = registryPath;
        this.multiworldPortalsPath = Path.of("config", "multiworld", "portals.yml");
        this.config = config;
        this.load();
    }

    public synchronized Result addClaimSide(ServerPlayerEntity player, ClaimManager claimManager, String portalId, BlockPos basePos) {
        String currentWorldId = player.getWorld().getRegistryKey().getValue().toString();
        if (!this.config.wildDimension.equals(currentWorldId)) {
            return Result.fail("Run this command in the wild dimension: " + this.config.wildDimension + ".");
        }
        Optional<Claim> claimOpt = claimManager.getClaimAt(currentWorldId, basePos.getX(), basePos.getZ());
        if (claimOpt.isEmpty()) {
            return Result.fail("No claim exists at the selected base location.");
        }
        Claim claim = claimOpt.get();
        ClaimPortalRecord record = this.findOrCreate(portalId);
        record.ownerUuid = claim.ownerUuid;
        record.ownerName = claim.ownerName;
        record.claimDimension = claim.dimension;
        record.claimMinX = claim.minX;
        record.claimMaxX = claim.maxX;
        record.claimMinZ = claim.minZ;
        record.claimMaxZ = claim.maxZ;
        record.claimEndpoint = this.buildEndpoint(player, basePos, ClaimPortalRegistry.portalName(portalId, CLAIM_PORTAL_SUFFIX), ClaimPortalRegistry.portalDestination(ClaimPortalRegistry.portalName(portalId, HUB_PORTAL_SUFFIX)));
        record.portalId = ClaimPortalRegistry.normalize(portalId);
        this.saveAndSync(player.getServer());
        return Result.ok("Claim-side portal saved for " + portalId + ".");
    }

    public synchronized Result addHubSide(ServerPlayerEntity player, String portalId, BlockPos basePos) {
        ClaimPortalRecord record = this.findRecord(portalId).orElse(null);
        if (record == null || record.claimEndpoint == null) {
            return Result.fail("Create the claim-side portal first.");
        }
        String currentWorldId = player.getWorld().getRegistryKey().getValue().toString();
        if (!this.config.portalHubDimension.equals(currentWorldId)) {
            return Result.fail("Run this command in the hub dimension: " + this.config.portalHubDimension + ".");
        }
        record.hubEndpoint = this.buildEndpoint(player, basePos, ClaimPortalRegistry.portalName(portalId, HUB_PORTAL_SUFFIX), ClaimPortalRegistry.portalDestination(ClaimPortalRegistry.portalName(portalId, CLAIM_PORTAL_SUFFIX)));
        record.portalId = ClaimPortalRegistry.normalize(portalId);
        this.saveAndSync(player.getServer());
        return Result.ok("Hub-side portal saved for " + portalId + ".");
    }

    public synchronized Result removePortal(String portalId, MinecraftServer server) {
        Optional<ClaimPortalRecord> recordOpt = this.findRecord(portalId);
        if (recordOpt.isEmpty()) {
            return Result.fail("No portal with that id exists.");
        }
        ClaimPortalRecord record = recordOpt.get();
        this.clearEndpoint(server, record.claimEndpoint);
        this.clearEndpoint(server, record.hubEndpoint);
        this.records.remove(record);
        this.save();
        this.rewriteMultiworldConfig();
        MultiworldPortalBridge.removeFromKnownPortals(ClaimPortalRegistry.portalName(portalId, CLAIM_PORTAL_SUFFIX));
        MultiworldPortalBridge.removeFromKnownPortals(ClaimPortalRegistry.portalName(portalId, HUB_PORTAL_SUFFIX));
        MultiworldPortalBridge.reload(server);
        return Result.ok("Portal removed: " + portalId + ".");
    }

    public synchronized List<ClaimPortalRecord> listPortals() {
        return new ArrayList<ClaimPortalRecord>(this.records);
    }

    public synchronized void syncToServer(MinecraftServer server) {
        this.saveAndSync(server);
    }

    public synchronized boolean isProtectedBase(String worldId, int x, int y, int z) {
        for (ClaimPortalRecord record : this.records) {
            if (this.matchesProtectedBase(record.claimEndpoint, worldId, x, y, z)) {
                return true;
            }
            if (!this.matchesProtectedBase(record.hubEndpoint, worldId, x, y, z)) continue;
            return true;
        }
        return false;
    }

    private boolean matchesProtectedBase(PortalEndpoint endpoint, String worldId, int x, int y, int z) {
        if (endpoint == null || endpoint.worldId == null) {
            return false;
        }
        return endpoint.worldId.equals(worldId) && endpoint.baseX == x && endpoint.baseZ == z && endpoint.baseY == y;
    }

    private ClaimPortalRecord findOrCreate(String portalId) {
        Optional<ClaimPortalRecord> recordOpt = this.findRecord(portalId);
        if (recordOpt.isPresent()) {
            return recordOpt.get();
        }
        ClaimPortalRecord record = new ClaimPortalRecord();
        record.portalId = ClaimPortalRegistry.normalize(portalId);
        this.records.add(record);
        return record;
    }

    private Optional<ClaimPortalRecord> findRecord(String portalId) {
        String normalized = ClaimPortalRegistry.normalize(portalId);
        return this.records.stream().filter(record -> normalized.equals(record.portalId)).findFirst();
    }

    private void saveAndSync(MinecraftServer server) {
        this.save();
        this.rewriteMultiworldConfig();
        MultiworldPortalBridge.reload(server);
        this.renderAllEndpoints(server);
    }

    private void save() {
        try {
            Files.createDirectories(this.registryPath.getParent(), new FileAttribute[0]);
            try (BufferedWriter writer = Files.newBufferedWriter(this.registryPath, new OpenOption[0]);){
                RegistryFile file = new RegistryFile();
                file.portals = new ArrayList<ClaimPortalRecord>(this.records);
                GSON.toJson((Object)file, (Appendable)writer);
            }
        }
        catch (IOException iOException) {
            // empty catch block
        }
    }

    private void load() {
        this.records.clear();
        if (!Files.exists(this.registryPath, new LinkOption[0])) {
            this.save();
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(this.registryPath);){
            RegistryFile file = (RegistryFile)GSON.fromJson((Reader)reader, RegistryFile.class);
            if (file != null && file.portals != null) {
                this.records.addAll(file.portals);
            }
        }
        catch (IOException iOException) {
            // empty catch block
        }
        if (this.migrateLegacyPortalDestinations()) {
            this.save();
        }
    }

    private void rewriteMultiworldConfig() {
        try {
            if (!Files.exists(this.multiworldPortalsPath, new LinkOption[0])) {
                return;
            }
            List<String> input = Files.readAllLines(this.multiworldPortalsPath);
            ArrayList<String> output = new ArrayList<String>();
            boolean inPortalsSection = false;
            int index = 0;
            while (index < input.size()) {
                String line2 = input.get(index);
                if (!inPortalsSection) {
                    output.add(line2);
                    if (line2.trim().equals("portals:")) {
                        inPortalsSection = true;
                    }
                    ++index;
                    continue;
                }
                if (line2.startsWith("    ") && line2.trim().endsWith(":")) {
                    String next;
                    int blockEnd;
                    String portalName = line2.trim().substring(0, line2.trim().length() - 1);
                    for (blockEnd = index + 1; !(blockEnd >= input.size() || (next = input.get(blockEnd)).startsWith("    ") && next.trim().endsWith(":")); ++blockEnd) {
                    }
                    if (!portalName.startsWith("goldclaim_")) {
                        for (int i = index; i < blockEnd; ++i) {
                            output.add(input.get(i));
                        }
                    }
                    index = blockEnd;
                    continue;
                }
                output.add(line2);
                ++index;
            }
            if (output.stream().noneMatch(line -> line.trim().equals("portals:"))) {
                output.add("portals:");
            }
            for (ClaimPortalRecord record : this.records) {
                if (record.claimEndpoint != null) {
                    this.appendPortalBlock(output, record.claimEndpoint);
                }
                if (record.hubEndpoint == null) continue;
                this.appendPortalBlock(output, record.hubEndpoint);
            }
            Files.write(this.multiworldPortalsPath, output, new OpenOption[0]);
        }
        catch (IOException iOException) {
            // empty catch block
        }
    }

    private void appendPortalBlock(List<String> output, PortalEndpoint endpoint) {
        output.add("    " + endpoint.portalName + ":");
        output.add("        entryfee:");
        output.add("            amount: 0.0");
        output.add("        safeteleport: true");
        output.add("        teleportnonplayers: false");
        output.add("        handlerscript: ''");
        output.add("        owner: " + ClaimPortalRegistry.escapeYaml(endpoint.ownerName));
        output.add("        location: " + endpoint.location);
        output.add("        world: " + endpoint.worldId);
        output.add("        destination: " + endpoint.destination);
    }

    private void clearEndpoint(MinecraftServer server, PortalEndpoint endpoint) {
        if (endpoint == null) {
            return;
        }
        Identifier worldId = Identifier.tryParse((String)endpoint.worldId);
        if (worldId == null) {
            return;
        }
        RegistryKey key = RegistryKey.of((RegistryKey)RegistryKeys.WORLD, (Identifier)worldId);
        ServerWorld world = server.getWorld(key);
        if (world == null) {
            return;
        }
        Block portalBlock = ClaimPortalRegistry.getPortalBlock();
        for (int x = endpoint.shellMinX; x <= endpoint.shellMaxX; ++x) {
            for (int y = endpoint.shellMinY; y <= endpoint.shellMaxY; ++y) {
                for (int z = endpoint.shellMinZ; z <= endpoint.shellMaxZ; ++z) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!ClaimPortalRegistry.isManagedPortalBlock(world.getBlockState(pos).getBlock(), portalBlock)) continue;
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                }
            }
        }
    }

    private PortalEndpoint buildEndpoint(ServerPlayerEntity player, BlockPos base, String portalName, String destination) {
        BlockPos shellMaxPos;
        BlockPos shellMinPos;
        Direction facing = player.getHorizontalFacing();
        BlockPos renderMinPos = new BlockPos(base.getX(), base.getY() + 1, base.getZ());
        BlockPos renderMaxPos = new BlockPos(base.getX(), base.getY() + 2, base.getZ());
        if (facing.getAxis() == Direction.Axis.X) {
            shellMinPos = new BlockPos(base.getX(), base.getY(), base.getZ() - 1);
            shellMaxPos = new BlockPos(base.getX(), base.getY() + 3, base.getZ() + 1);
        } else {
            shellMinPos = new BlockPos(base.getX() - 1, base.getY(), base.getZ());
            shellMaxPos = new BlockPos(base.getX() + 1, base.getY() + 3, base.getZ());
        }
        PortalEndpoint endpoint = new PortalEndpoint();
        endpoint.portalName = portalName;
        endpoint.ownerName = player.getGameProfile().getName();
        endpoint.worldId = player.getWorld().getRegistryKey().getValue().toString();
        endpoint.baseX = base.getX();
        endpoint.baseY = base.getY();
        endpoint.baseZ = base.getZ();
        endpoint.minX = Math.min(renderMinPos.getX(), renderMaxPos.getX());
        endpoint.minY = Math.min(renderMinPos.getY(), renderMaxPos.getY());
        endpoint.minZ = Math.min(renderMinPos.getZ(), renderMaxPos.getZ());
        endpoint.maxX = Math.max(renderMinPos.getX(), renderMaxPos.getX());
        endpoint.maxY = Math.max(renderMinPos.getY(), renderMaxPos.getY());
        endpoint.maxZ = Math.max(renderMinPos.getZ(), renderMaxPos.getZ());
        endpoint.shellMinX = Math.min(shellMinPos.getX(), shellMaxPos.getX());
        endpoint.shellMinY = Math.min(shellMinPos.getY(), shellMaxPos.getY());
        endpoint.shellMinZ = Math.min(shellMinPos.getZ(), shellMaxPos.getZ());
        endpoint.shellMaxX = Math.max(shellMinPos.getX(), shellMaxPos.getX());
        endpoint.shellMaxY = Math.max(shellMinPos.getY(), shellMaxPos.getY());
        endpoint.shellMaxZ = Math.max(shellMinPos.getZ(), shellMaxPos.getZ());
        endpoint.location = MultiworldPortalBridge.formatLocation(shellMinPos, shellMaxPos);
        endpoint.destination = destination;
        endpoint.axis = facing.getAxis() == Direction.Axis.X ? "x" : "z";
        return endpoint;
    }

    private void renderAllEndpoints(MinecraftServer server) {
        for (ClaimPortalRecord record : this.records) {
            this.renderEndpoint(server, record.claimEndpoint);
            this.renderEndpoint(server, record.hubEndpoint);
        }
    }

    private void renderEndpoint(MinecraftServer server, PortalEndpoint endpoint) {
        if (endpoint == null) {
            return;
        }
        Identifier worldId = Identifier.tryParse((String)endpoint.worldId);
        if (worldId == null) {
            return;
        }
        RegistryKey key = RegistryKey.of((RegistryKey)RegistryKeys.WORLD, (Identifier)worldId);
        ServerWorld world = server.getWorld(key);
        if (world == null) {
            return;
        }
        Block portalBlock = ClaimPortalRegistry.getPortalBlock();
        if (portalBlock == Blocks.AIR) {
            return;
        }
        MultiworldPortalBridge.clearPortalBlocks(server, new BlockPos(endpoint.shellMinX, endpoint.shellMinY, endpoint.shellMinZ), new BlockPos(endpoint.shellMaxX, endpoint.shellMaxY, endpoint.shellMaxZ), endpoint.worldId);
        try {
            MultiworldPortalBridge.savePortal(endpoint.portalName, endpoint.ownerName, endpoint.worldId, endpoint.destination, new BlockPos(endpoint.minX, endpoint.minY, endpoint.minZ), new BlockPos(endpoint.maxX, endpoint.maxY, endpoint.maxZ));
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            // empty catch block
        }
    }

    private boolean migrateLegacyPortalDestinations() {
        boolean changed = false;
        for (ClaimPortalRecord record : this.records) {
            changed |= this.migrateEndpointDestination(record.claimEndpoint);
            changed |= this.migrateEndpointDestination(record.hubEndpoint);
        }
        return changed;
    }

    private boolean migrateEndpointDestination(PortalEndpoint endpoint) {
        if (endpoint == null || endpoint.destination == null || endpoint.destination.isBlank()) {
            return false;
        }
        String destination = endpoint.destination;
        if (destination.contains(":")) {
            return false;
        }
        if (!destination.startsWith("goldclaim_")) {
            return false;
        }
        endpoint.destination = ClaimPortalRegistry.portalDestination(destination);
        return true;
    }

    private static String portalDestination(String portalName) {
        return PORTAL_DESTINATION_PREFIX + portalName;
    }

    private static Block getPortalBlock() {
        Block portalBlock = (Block)Registries.BLOCK.get(CUSTOM_PORTAL_BLOCK_ID);
        return portalBlock == null ? Blocks.AIR : portalBlock;
    }

    private static boolean isManagedPortalBlock(Block block, Block portalBlock) {
        return block == Blocks.OBSIDIAN || block == Blocks.NETHER_PORTAL || block == Blocks.REINFORCED_DEEPSLATE || block == portalBlock;
    }

    private BlockState withAxis(BlockState state, String axisName) {
        Direction.Axis axis;
        Direction.Axis class_23512 = axis = "x".equalsIgnoreCase(axisName) ? Direction.Axis.X : Direction.Axis.Z;
        if (state.contains((Property)Properties.AXIS)) {
            return (BlockState)state.with((Property)Properties.AXIS, (Comparable)axis);
        }
        for (Property property : state.getProperties()) {
            if (!"axis".equals(property.getName())) continue;
            Property raw = property;
            for (Object value : raw.getValues()) {
                Direction.Axis candidate;
                if (!(value instanceof Direction.Axis) || (candidate = (Direction.Axis)value) != axis) continue;
                return (BlockState)state.with(raw, (Comparable)candidate);
            }
        }
        return state;
    }

    private static String escapeYaml(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(58) >= 0 || value.indexOf(32) >= 0) {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }
        return value;
    }

    private static String normalize(String portalId) {
        return portalId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    private static String portalName(String portalId, String suffix) {
        return "goldclaim_" + ClaimPortalRegistry.normalize(portalId) + suffix;
    }

    public static final class Result {
        public final boolean success;
        public final String message;

        private Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }

    public static class ClaimPortalRecord {
        public String portalId;
        public String ownerUuid;
        public String ownerName;
        public String claimDimension;
        public int claimMinX;
        public int claimMaxX;
        public int claimMinZ;
        public int claimMaxZ;
        public PortalEndpoint claimEndpoint;
        public PortalEndpoint hubEndpoint;
    }

    public static class PortalEndpoint {
        public String portalName;
        public String ownerName;
        public String worldId;
        public String location;
        public String destination;
        public String axis;
        public int baseX;
        public int baseY;
        public int baseZ;
        public int minX;
        public int minY;
        public int minZ;
        public int maxX;
        public int maxY;
        public int maxZ;
        public int shellMinX;
        public int shellMinY;
        public int shellMinZ;
        public int shellMaxX;
        public int shellMaxY;
        public int shellMaxZ;
    }

    public static class RegistryFile {
        public List<ClaimPortalRecord> portals = new ArrayList<ClaimPortalRecord>();
    }
}
