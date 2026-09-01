package com.gangs.gangsales;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.gangs.gangshop.GangShopMod;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

public final class SalesStore {
	public static final int MAX_ACTIVE_LISTINGS = 30;
	public static final long EXPIRATION_MILLIS = 7L * 24 * 60 * 60 * 1000;
	public static final long HISTORY_MILLIS = 31L * 24 * 60 * 60 * 1000;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final List<SaleListing> listings = new ArrayList<>();
	private Path path;

	public synchronized void load(MinecraftServer server) {
		path = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("gangsales.json");
		listings.clear();
		if (!Files.exists(path)) return;
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonObject root = GSON.fromJson(reader, JsonObject.class);
			if (root == null || !root.has("listings")) return;
			for (JsonElement element : root.getAsJsonArray("listings")) loadListing(element.getAsJsonObject());
		} catch (Exception exception) {
			GangSalesMod.LOGGER.error("Could not load GangSales data from {}", path, exception);
		}
		expireAndPrune(System.currentTimeMillis());
	}

	public synchronized void save() {
		if (path == null) return;
		JsonObject root = new JsonObject();
		JsonArray entries = new JsonArray();
		for (SaleListing listing : listings) entries.add(writeListing(listing));
		root.add("listings", entries);
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException exception) {
			GangSalesMod.LOGGER.error("Could not save GangSales data to {}", path, exception);
		}
	}

	public synchronized SaleListing create(ServerPlayerEntity seller, int amount, long price) {
		ItemStack heldStack = seller.getMainHandStack();
		if (price < 1 || amount < 1 || heldStack.isEmpty() || amount > heldStack.getCount()) return null;
		expireAndPrune(System.currentTimeMillis());
		long activeCount = listings.stream().filter(listing -> listing.getSellerId().equals(seller.getUuid()) && listing.getStatus() == ListingStatus.ACTIVE).count();
		if (activeCount >= MAX_ACTIVE_LISTINGS) return null;
		long now = System.currentTimeMillis();
		ItemStack saleStack = heldStack.copy();
		saleStack.setCount(amount);
		heldStack.decrement(amount);
		seller.getInventory().markDirty();
		SaleListing listing = new SaleListing(UUID.randomUUID(), seller.getUuid(), seller.getName().getString(), saleStack, price, now, now + EXPIRATION_MILLIS);
		listings.add(listing);
		save();
		return listing;
	}

	public synchronized PurchaseResult purchase(ServerPlayerEntity buyer, UUID listingId) {
		expireAndPrune(System.currentTimeMillis());
		SaleListing listing = find(listingId);
		if (listing == null || listing.getStatus() != ListingStatus.ACTIVE) return PurchaseResult.NOT_AVAILABLE;
		if (listing.getSellerId().equals(buyer.getUuid())) return PurchaseResult.OWN_LISTING;
		ItemStack stack = listing.getStack();
		if (!hasRoomFor(buyer, stack)) return PurchaseResult.INVENTORY_FULL;
		if (!GangShopMod.ECONOMY.subtract(buyer.getUuid(), listing.getPrice())) return PurchaseResult.INSUFFICIENT_FUNDS;
		buyer.getInventory().insertStack(stack);
		buyer.getInventory().markDirty();
		GangShopMod.ECONOMY.add(listing.getSellerId(), listing.getPrice());
		listing.complete(buyer.getUuid(), buyer.getName().getString(), System.currentTimeMillis());
		save();
		return PurchaseResult.SUCCESS;
	}

	public synchronized boolean retrieveListing(ServerPlayerEntity seller, UUID listingId) {
		expireAndPrune(System.currentTimeMillis());
		SaleListing listing = find(listingId);
		if (listing == null || !listing.getSellerId().equals(seller.getUuid()) || (listing.getStatus() != ListingStatus.ACTIVE && listing.getStatus() != ListingStatus.EXPIRED)) return false;
		ItemStack stack = listing.getStack();
		if (!hasRoomFor(seller, stack)) return false;
		seller.getInventory().insertStack(stack);
		seller.getInventory().markDirty();
		listing.claim();
		save();
		return true;
	}

	public synchronized List<SaleListing> activeListings() {
		expireAndPrune(System.currentTimeMillis());
		return listings.stream().filter(listing -> listing.getStatus() == ListingStatus.ACTIVE).sorted(Comparator.comparingLong(SaleListing::getCreatedAt).reversed()).toList();
	}

	public synchronized List<SaleListing> historyFor(UUID playerId) {
		expireAndPrune(System.currentTimeMillis());
		return listings.stream().filter(listing -> listing.getSellerId().equals(playerId) || playerId.equals(listing.getBuyerId())).sorted(Comparator.comparingLong(this::historyTime).reversed()).toList();
	}

	private long historyTime(SaleListing listing) {
		return listing.getCompletedAt() == 0 ? listing.getCreatedAt() : listing.getCompletedAt();
	}

	private SaleListing find(UUID id) {
		return listings.stream().filter(listing -> listing.getId().equals(id)).findFirst().orElse(null);
	}

	private void expireAndPrune(long now) {
		for (SaleListing listing : listings) if (listing.getStatus() == ListingStatus.ACTIVE && now >= listing.getExpiresAt()) listing.expire();
		Iterator<SaleListing> iterator = listings.iterator();
		while (iterator.hasNext()) {
			SaleListing listing = iterator.next();
			if ((listing.getStatus() == ListingStatus.SOLD || listing.getStatus() == ListingStatus.CLAIMED) && now - historyTime(listing) > HISTORY_MILLIS) iterator.remove();
		}
	}

	private boolean hasRoomFor(ServerPlayerEntity player, ItemStack incoming) {
		int remaining = incoming.getCount();
		for (int slot = 0; slot < player.getInventory().size(); slot++) {
			ItemStack existing = player.getInventory().getStack(slot);
			if (existing.isEmpty()) remaining -= incoming.getMaxCount();
			else if (ItemStack.canCombine(existing, incoming)) remaining -= existing.getMaxCount() - existing.getCount();
			if (remaining <= 0) return true;
		}
		return false;
	}

	private JsonObject writeListing(SaleListing listing) {
		JsonObject object = new JsonObject();
		object.addProperty("id", listing.getId().toString());
		object.addProperty("sellerId", listing.getSellerId().toString());
		object.addProperty("sellerName", listing.getSellerName());
		object.addProperty("stack", listing.getStack().writeNbt(new NbtCompound()).asString());
		object.addProperty("price", listing.getPrice());
		object.addProperty("createdAt", listing.getCreatedAt());
		object.addProperty("expiresAt", listing.getExpiresAt());
		object.addProperty("status", listing.getStatus().name());
		if (listing.getBuyerId() != null) object.addProperty("buyerId", listing.getBuyerId().toString());
		if (listing.getBuyerName() != null) object.addProperty("buyerName", listing.getBuyerName());
		object.addProperty("completedAt", listing.getCompletedAt());
		return object;
	}

	private void loadListing(JsonObject object) throws Exception {
		NbtCompound stackNbt = (NbtCompound) StringNbtReader.parse(object.get("stack").getAsString());
		UUID buyerId = object.has("buyerId") ? UUID.fromString(object.get("buyerId").getAsString()) : null;
		String buyerName = object.has("buyerName") ? object.get("buyerName").getAsString() : null;
		listings.add(SaleListing.restore(UUID.fromString(object.get("id").getAsString()), UUID.fromString(object.get("sellerId").getAsString()), object.get("sellerName").getAsString(), ItemStack.fromNbt(stackNbt), object.get("price").getAsLong(), object.get("createdAt").getAsLong(), object.get("expiresAt").getAsLong(), ListingStatus.valueOf(object.get("status").getAsString()), buyerId, buyerName, object.get("completedAt").getAsLong()));
	}

	public enum PurchaseResult {
		SUCCESS, NOT_AVAILABLE, OWN_LISTING, INVENTORY_FULL, INSUFFICIENT_FUNDS
	}
}