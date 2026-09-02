/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.item.Item
 *  net.minecraft.util.Identifier
 */
package com.gangs.gangshop.shop;

import com.gangs.gangshop.shop.ShopCategory;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;

public record ShopEntry(Identifier id, Item item, ShopCategory category, long sellPrice, long buyPrice) {
}

