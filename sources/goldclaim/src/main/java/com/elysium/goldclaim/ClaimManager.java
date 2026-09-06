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
 *  net.minecraft.util.math.Direction
 */
package com.elysium.goldclaim;

import com.elysium.goldclaim.GoldClaimConfig;
import com.elysium.goldclaim.data.Claim;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.util.math.Direction;

public class ClaimManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path claimsPath;
    private final GoldClaimConfig config;
    private final List<Claim> claims = new ArrayList<Claim>();

    public ClaimManager(Path claimsPath, GoldClaimConfig config) {
        this.claimsPath = claimsPath;
        this.config = config;
        this.load();
    }

    public synchronized CreateResult createClaim(UUID ownerUuid, String ownerName, String dimension, int minX, int maxX, int minZ, int maxZ) {
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        int area = width * depth;
        if (width < this.config.minimumClaimWidth || depth < this.config.minimumClaimDepth) {
            return CreateResult.fail(CreateError.TOO_SMALL);
        }
        if (this.intersectsExisting(dimension, minX, maxX, minZ, maxZ, null)) {
            return CreateResult.fail(CreateError.OVERLAPS_EXISTING);
        }
        if (this.getTotalClaimBlocks(ownerUuid, dimension) + area > this.config.maxClaimBlocksPerPlayerPerDimension) {
            return CreateResult.fail(CreateError.CLAIM_LIMIT_EXCEEDED);
        }
        this.claims.add(new Claim(ownerUuid, ownerName, dimension, minX, maxX, minZ, maxZ));
        this.save();
        return CreateResult.ok();
    }

    public synchronized CreateResult createAdminClaim(String dimension, int minX, int maxX, int minZ, int maxZ) {
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        if (width < this.config.minimumClaimWidth || depth < this.config.minimumClaimDepth) {
            return CreateResult.fail(CreateError.TOO_SMALL);
        }
        if (this.intersectsExisting(dimension, minX, maxX, minZ, maxZ, null)) {
            return CreateResult.fail(CreateError.OVERLAPS_EXISTING);
        }
        this.claims.add(new Claim("ADMIN", true, dimension, minX, maxX, minZ, maxZ));
        this.save();
        return CreateResult.ok();
    }

    public synchronized ExpandResult expandClaimAt(UUID actorUuid, String dimension, int x, int z, int amount, Direction facing, boolean allowOpBypass, boolean isOp) {
        UUID ownerUuid;
        int availableArea;
        Optional<Claim> claimOpt = this.getClaimAt(dimension, x, z);
        if (claimOpt.isEmpty()) {
            return ExpandResult.fail(ExpandError.NOT_FOUND);
        }
        Claim claim = claimOpt.get();
        if (claim.adminClaim && !isOp) {
            return ExpandResult.fail(ExpandError.NOT_OWNER);
        }
        if (!(claim.adminClaim || claim.isOwner(actorUuid) || allowOpBypass && isOp)) {
            return ExpandResult.fail(ExpandError.NOT_OWNER);
        }
        int newMinX = claim.minX;
        int newMaxX = claim.maxX;
        int newMinZ = claim.minZ;
        int newMaxZ = claim.maxZ;
        switch (facing) {
            case NORTH: {
                newMinZ -= amount;
                break;
            }
            case SOUTH: {
                newMaxZ += amount;
                break;
            }
            case WEST: {
                newMinX -= amount;
                break;
            }
            case EAST: {
                newMaxX += amount;
                break;
            }
            default: {
                throw new IllegalStateException("Unexpected horizontal facing: " + String.valueOf(facing));
            }
        }
        int newArea = (newMaxX - newMinX + 1) * (newMaxZ - newMinZ + 1);
        int addedArea = newArea - claim.area();
        if (this.intersectsExisting(dimension, newMinX, newMaxX, newMinZ, newMaxZ, claim)) {
            return ExpandResult.fail(ExpandError.OVERLAPS_EXISTING);
        }
        if (!claim.adminClaim && addedArea > (availableArea = this.config.maxClaimBlocksPerPlayerPerDimension - this.getTotalClaimBlocks(ownerUuid = UUID.fromString(claim.ownerUuid), dimension))) {
            return ExpandResult.failLimit(addedArea, availableArea);
        }
        claim.minX = newMinX;
        claim.maxX = newMaxX;
        claim.minZ = newMinZ;
        claim.maxZ = newMaxZ;
        this.save();
        return ExpandResult.ok(claim);
    }

    public synchronized Optional<Claim> getClaimAt(String dimension, int x, int z) {
        return this.claims.stream().filter(c -> c.dimension.equals(dimension) && c.contains(x, z)).findFirst();
    }

    public synchronized List<Claim> getClaimsForOwner(UUID ownerUuid) {
        String owner = ownerUuid.toString();
        return this.claims.stream().filter(c -> c.ownerUuid.equals(owner)).toList();
    }

    public synchronized int getTotalClaimBlocks(UUID ownerUuid, String dimension) {
        String owner = ownerUuid.toString();
        return this.claims.stream().filter(c -> c.ownerUuid.equals(owner) && c.dimension.equals(dimension)).mapToInt(Claim::area).sum();
    }

    public synchronized boolean removeClaimAt(UUID ownerUuid, String dimension, int x, int z, boolean allowOpBypass, boolean isOp) {
        Optional<Claim> claimOpt = this.getClaimAt(dimension, x, z);
        if (claimOpt.isEmpty()) {
            return false;
        }
        Claim claim = claimOpt.get();
        if (claim.adminClaim) {
            return false;
        }
        if (!(claim.isOwner(ownerUuid) || allowOpBypass && isOp)) {
            return false;
        }
        boolean removed = this.claims.remove(claim);
        if (removed) {
            this.save();
        }
        return removed;
    }

    public synchronized TrustResult trustPlayerAt(UUID actorUuid, UUID targetUuid, String targetName, String dimension, int x, int z, boolean allowOpBypass, boolean isOp) {
        Optional<Claim> claimOpt = this.getClaimAt(dimension, x, z);
        if (claimOpt.isEmpty()) {
            return TrustResult.fail(TrustError.NOT_FOUND);
        }
        Claim claim = claimOpt.get();
        if (claim.adminClaim ? !isOp : !claim.isOwner(actorUuid) && (!allowOpBypass || !isOp)) {
            return TrustResult.fail(TrustError.NOT_ALLOWED);
        }
        if (claim.isTrusted(targetUuid)) {
            if (!claim.isTrustedName(targetName)) {
                claim.addTrustedName(targetName);
                this.save();
            }
            return TrustResult.fail(TrustError.ALREADY_TRUSTED);
        }
        claim.addTrusted(targetUuid);
        claim.addTrustedName(targetName);
        this.save();
        return TrustResult.ok();
    }

    public synchronized TrustResult untrustPlayerAt(UUID actorUuid, UUID targetUuid, String targetName, String dimension, int x, int z, boolean allowOpBypass, boolean isOp) {
        Optional<Claim> claimOpt = this.getClaimAt(dimension, x, z);
        if (claimOpt.isEmpty()) {
            return TrustResult.fail(TrustError.NOT_FOUND);
        }
        Claim claim = claimOpt.get();
        if (claim.adminClaim ? !isOp : !claim.isOwner(actorUuid) && (!allowOpBypass || !isOp)) {
            return TrustResult.fail(TrustError.NOT_ALLOWED);
        }
        if (!claim.isTrusted(targetUuid) && !claim.isTrustedName(targetName)) {
            return TrustResult.fail(TrustError.NOT_TRUSTED);
        }
        claim.removeTrusted(targetUuid);
        claim.removeTrustedName(targetName);
        this.save();
        return TrustResult.ok();
    }

    public synchronized boolean canModify(UUID playerUuid, String playerName, boolean isOp, String dimension, int x, int z) {
        Optional<Claim> claimOpt = this.getClaimAt(dimension, x, z);
        if (claimOpt.isEmpty()) {
            return true;
        }
        Claim claim = claimOpt.get();
        if (!claim.adminClaim && claim.isOwner(playerUuid)) {
            return true;
        }
        if (claim.isTrusted(playerUuid)) {
            return true;
        }
        if (claim.isTrustedName(playerName)) {
            return true;
        }
        return this.config.allowOpsBypass && isOp;
    }

    public synchronized boolean removeAdminClaimAt(String dimension, int x, int z) {
        Optional<Claim> claimOpt = this.getClaimAt(dimension, x, z);
        if (claimOpt.isEmpty() || !claimOpt.get().adminClaim) {
            return false;
        }
        boolean removed = this.claims.remove(claimOpt.get());
        if (removed) {
            this.save();
        }
        return removed;
    }

    private boolean intersectsExisting(String dimension, int minX, int maxX, int minZ, int maxZ, Claim ignoredClaim) {
        for (Claim claim : this.claims) {
            if (claim == ignoredClaim || !claim.dimension.equals(dimension) || !claim.intersects(minX, maxX, minZ, maxZ)) continue;
            return true;
        }
        return false;
    }

    private synchronized void load() {
        this.claims.clear();
        if (!Files.exists(this.claimsPath, new LinkOption[0])) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(this.claimsPath);){
            ClaimStore store = (ClaimStore)GSON.fromJson((Reader)reader, ClaimStore.class);
            if (store != null && store.claims != null) {
                this.claims.addAll(store.claims);
            }
        }
        catch (IOException iOException) {
            // empty catch block
        }
        if (this.backfillOwnerNamesFromUserCache()) {
            this.save();
        }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(this.claimsPath.getParent(), new FileAttribute[0]);
            try (BufferedWriter writer = Files.newBufferedWriter(this.claimsPath, new OpenOption[0]);){
                ClaimStore store = new ClaimStore();
                store.claims = new ArrayList<Claim>(this.claims);
                GSON.toJson((Object)store, (Appendable)writer);
            }
        }
        catch (IOException iOException) {
            // empty catch block
        }
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private boolean backfillOwnerNamesFromUserCache() {
        Path userCachePath = Path.of("usercache.json", new String[0]);
        if (!Files.exists(userCachePath, new LinkOption[0])) {
            return false;
        }
        HashMap<String, String> namesByUuid = new HashMap<String, String>();
        try (BufferedReader reader = Files.newBufferedReader(userCachePath);){
            JsonElement root = JsonParser.parseReader((Reader)reader);
            if (!root.isJsonArray()) {
                boolean bl = false;
                return bl;
            }
            JsonArray entries = root.getAsJsonArray();
            for (JsonElement element : entries) {
                JsonObject obj;
                if (!element.isJsonObject() || !(obj = element.getAsJsonObject()).has("uuid") || !obj.has("name")) continue;
                String uuid = obj.get("uuid").getAsString();
                String name = obj.get("name").getAsString();
                if (uuid.isBlank() || name.isBlank()) continue;
                namesByUuid.put(uuid, name);
            }
        }
        catch (IOException ignored) {
            return false;
        }
        boolean changed = false;
        Iterator<Claim> iterator = this.claims.iterator();
        while (iterator.hasNext()) {
            String ownerName;
            Claim claim = iterator.next();
            if (claim.adminClaim || claim.ownerName != null && !claim.ownerName.isBlank() || claim.ownerUuid == null || (ownerName = (String)namesByUuid.get(claim.ownerUuid)) == null || ownerName.isBlank()) continue;
            claim.ownerName = ownerName;
            changed = true;
        }
        return changed;
    }

    public static enum CreateError {
        TOO_SMALL,
        OVERLAPS_EXISTING,
        CLAIM_LIMIT_EXCEEDED;

    }

    public record CreateResult(boolean success, CreateError error) {
        public static CreateResult ok() {
            return new CreateResult(true, null);
        }

        public static CreateResult fail(CreateError error) {
            return new CreateResult(false, error);
        }
    }

    public static enum ExpandError {
        NOT_FOUND,
        NOT_OWNER,
        OVERLAPS_EXISTING,
        CLAIM_LIMIT_EXCEEDED;

    }

    public record ExpandResult(boolean success, ExpandError error, Claim claim, int addedArea, int availableArea) {
        public static ExpandResult ok(Claim claim) {
            return new ExpandResult(true, null, claim, 0, 0);
        }

        public static ExpandResult fail(ExpandError error) {
            return new ExpandResult(false, error, null, 0, 0);
        }

        public static ExpandResult failLimit(int addedArea, int availableArea) {
            return new ExpandResult(false, ExpandError.CLAIM_LIMIT_EXCEEDED, null, addedArea, availableArea);
        }
    }

    public static enum TrustError {
        NOT_FOUND,
        NOT_ALLOWED,
        ALREADY_TRUSTED,
        NOT_TRUSTED;

    }

    public record TrustResult(boolean success, TrustError error) {
        public static TrustResult ok() {
            return new TrustResult(true, null);
        }

        public static TrustResult fail(TrustError error) {
            return new TrustResult(false, error);
        }
    }

    private static class ClaimStore {
        List<Claim> claims = new ArrayList<Claim>();

        private ClaimStore() {
        }
    }
}
