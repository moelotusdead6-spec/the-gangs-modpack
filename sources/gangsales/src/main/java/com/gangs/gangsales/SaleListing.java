package com.gangs.gangsales;

import java.util.UUID;

import net.minecraft.item.ItemStack;

public final class SaleListing {
	private final UUID id;
	private final UUID sellerId;
	private final String sellerName;
	private final ItemStack stack;
	private final long price;
	private final long createdAt;
	private final long expiresAt;
	private ListingStatus status;
	private UUID buyerId;
	private String buyerName;
	private long completedAt;

	public SaleListing(UUID id, UUID sellerId, String sellerName, ItemStack stack, long price, long createdAt, long expiresAt) {
		this.id = id;
		this.sellerId = sellerId;
		this.sellerName = sellerName;
		this.stack = stack;
		this.price = price;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
		this.status = ListingStatus.ACTIVE;
	}

	public UUID getId() { return id; }
	public UUID getSellerId() { return sellerId; }
	public String getSellerName() { return sellerName; }
	public ItemStack getStack() { return stack.copy(); }
	public long getPrice() { return price; }
	public long getCreatedAt() { return createdAt; }
	public long getExpiresAt() { return expiresAt; }
	public ListingStatus getStatus() { return status; }
	public UUID getBuyerId() { return buyerId; }
	public String getBuyerName() { return buyerName; }
	public long getCompletedAt() { return completedAt; }

	public void expire() {
		if (status == ListingStatus.ACTIVE) status = ListingStatus.EXPIRED;
	}

	public void complete(UUID buyerId, String buyerName, long completedAt) {
		status = ListingStatus.SOLD;
		this.buyerId = buyerId;
		this.buyerName = buyerName;
		this.completedAt = completedAt;
	}

	public void claim() {
		status = ListingStatus.CLAIMED;
	}

	static SaleListing restore(UUID id, UUID sellerId, String sellerName, ItemStack stack, long price, long createdAt, long expiresAt, ListingStatus status, UUID buyerId, String buyerName, long completedAt) {
		SaleListing listing = new SaleListing(id, sellerId, sellerName, stack, price, createdAt, expiresAt);
		listing.status = status;
		listing.buyerId = buyerId;
		listing.buyerName = buyerName;
		listing.completedAt = completedAt;
		return listing;
	}
}