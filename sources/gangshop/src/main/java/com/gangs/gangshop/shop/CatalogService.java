/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  net.fabricmc.loader.api.FabricLoader
 *  net.minecraft.block.Block
 *  net.minecraft.block.BlockState
 *  net.minecraft.item.ArmorItem
 *  net.minecraft.item.AxeItem
 *  net.minecraft.item.BlockItem
 *  net.minecraft.item.BowItem
 *  net.minecraft.item.CrossbowItem
 *  net.minecraft.item.ElytraItem
 *  net.minecraft.item.Item
 *  net.minecraft.item.MiningToolItem
 *  net.minecraft.item.ShieldItem
 *  net.minecraft.item.SwordItem
 *  net.minecraft.item.TridentItem
 *  net.minecraft.registry.Registries
 *  net.minecraft.util.Identifier
 *  net.minecraft.util.math.BlockPos
 *  net.minecraft.world.BlockView
 *  net.minecraft.world.EmptyBlockView
 */
package com.gangs.gangshop.shop;

import com.gangs.gangshop.shop.PriceConfigService;
import com.gangs.gangshop.shop.ShopCategory;
import com.gangs.gangshop.shop.ShopEntry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.Item;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.EmptyBlockView;

public class CatalogService {
    private final PriceConfigService priceConfig;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> VANILLA_MOB_DROP_IDS = new HashSet<String>(Arrays.asList("minecraft:rotten_flesh", "minecraft:bone", "minecraft:arrow", "minecraft:string", "minecraft:spider_eye", "minecraft:gunpowder", "minecraft:slime_ball", "minecraft:magma_cream", "minecraft:ender_pearl", "minecraft:blaze_rod", "minecraft:ghast_tear", "minecraft:prismarine_shard", "minecraft:prismarine_crystals", "minecraft:shulker_shell", "minecraft:phantom_membrane", "minecraft:ink_sac", "minecraft:glow_ink_sac", "minecraft:leather", "minecraft:feather", "minecraft:rabbit_hide", "minecraft:rabbit_foot", "minecraft:porkchop", "minecraft:beef", "minecraft:chicken", "minecraft:mutton", "minecraft:rabbit", "minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish", "minecraft:pufferfish", "minecraft:poppy", "minecraft:egg", "minecraft:nautilus_shell"));
    private static final Set<String> VANILLA_MINERAL_IDS = new HashSet<String>(Arrays.asList("minecraft:coal", "minecraft:coal_block", "minecraft:flint", "minecraft:redstone", "minecraft:redstone_block", "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore", "minecraft:coal_ore", "minecraft:deepslate_coal_ore", "minecraft:raw_iron", "minecraft:iron_ingot", "minecraft:iron_block", "minecraft:raw_iron_block", "minecraft:iron_ore", "minecraft:deepslate_iron_ore", "minecraft:raw_copper", "minecraft:copper_ingot", "minecraft:copper_block", "minecraft:copper_ore", "minecraft:deepslate_copper_ore", "minecraft:raw_gold", "minecraft:gold_ingot", "minecraft:gold_block", "minecraft:raw_gold_block", "minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore", "minecraft:diamond", "minecraft:diamond_block", "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore", "minecraft:emerald", "minecraft:emerald_block", "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore", "minecraft:lapis_lazuli", "minecraft:lapis_block", "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore", "minecraft:amethyst_shard", "minecraft:amethyst_block", "minecraft:quartz", "minecraft:quartz_block", "minecraft:nether_quartz_ore"));
    private static final Set<String> VANILLA_REDSTONE_EXTRA_IDS = new HashSet<String>(Arrays.asList("minecraft:repeater", "minecraft:comparator", "minecraft:observer", "minecraft:piston", "minecraft:sticky_piston", "minecraft:dispenser", "minecraft:dropper", "minecraft:hopper", "minecraft:target", "minecraft:daylight_detector", "minecraft:tripwire_hook", "minecraft:lever", "minecraft:note_block", "minecraft:sculk_sensor", "minecraft:calibrated_sculk_sensor"));
    private static final Set<String> HARD_DENY_EXACT_IDS = new HashSet<String>(Arrays.asList("minecraft:nether_star", "minecraft:wither_skeleton_skull", "minecraft:wither_skeleton_wall_skull", "minecraft:beacon", "minecraft:bedrock", "minecraft:barrier", "minecraft:ancient_debris", "minecraft:reinforced_deepslate", "minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block", "minecraft:jigsaw", "minecraft:light", "minecraft:spawner", "minecraft:structure_void", "minecraft:structure_block", "minecraft:dragon_head", "minecraft:dragon_wall_head", "minecraft:dragon_egg", "minecraft:sniffer_egg", "minecraft:frogspawn", "minecraft:farmland", "minecraft:dirt_path"));
    private static final Set<String> SURVIVAL_UNOBTAINABLE_VANILLA_IDS = new HashSet<String>(Arrays.asList("minecraft:bedrock", "minecraft:barrier", "minecraft:light", "minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block", "minecraft:structure_block", "minecraft:structure_void", "minecraft:jigsaw", "minecraft:spawner", "minecraft:end_portal_frame", "minecraft:end_portal", "minecraft:end_gateway", "minecraft:reinforced_deepslate", "minecraft:debug_stick"));
    private static final Set<String> VANILLA_VEGETATION_GROUND_IDS = new HashSet<String>(Arrays.asList("minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt", "minecraft:podzol", "minecraft:mycelium", "minecraft:mud", "minecraft:muddy_mangrove_roots", "minecraft:moss_block", "minecraft:moss_carpet"));
    private static final Set<String> VANILLA_VEGETATION_IDS = new HashSet<String>(Arrays.asList("minecraft:azalea", "minecraft:flowering_azalea", "minecraft:carrot", "minecraft:carrots", "minecraft:carved_pumpkin", "minecraft:glow_berries", "minecraft:hanging_roots", "minecraft:lily_of_the_valley", "minecraft:pitcher_pod", "minecraft:potato", "minecraft:potatoes", "minecraft:pumpkin", "minecraft:pumpkin_stem", "minecraft:attached_pumpkin_stem", "minecraft:sugar_cane", "minecraft:sweet_berries", "minecraft:sweet_berry_bush"));
    private static final Set<String> LETS_DO_NAMESPACES = new HashSet<String>(Arrays.asList("bakery", "beachparty", "brewery", "candlelight", "farm_and_charm", "herbalbrews", "meadow", "vinery"));
    private static final Set<String> SHOP_EXCLUDED_NAMESPACES = new HashSet<String>(Arrays.asList("advancednetherite", "alexsmobs", "bosses_of_mass_destruction", "creeperoverhaul", "crittersandcompanions", "endermanoverhaul", "ftbquests", "gobber2", "lootr", "moonlight", "mythicmetals", "paladins", "runes", "spell_engine", "universal_graves", "waystones"));
    private static final Set<String> CAMPING_BAG_IDS = new HashSet<String>(Arrays.asList("enderbag", "enderpack", "goodybag", "sheepbag", "wanderer_bag", "large_backpack", "small_backpack", "wanderer_backpack"));
    private static final Map<String, Long> MINERAL_PRICE_BY_ID = new HashMap<String, Long>();
    private final Map<ShopCategory, List<ShopEntry>> byCategory = new LinkedHashMap<ShopCategory, List<ShopEntry>>();
    private final Map<Identifier, ShopEntry> byId = new HashMap<Identifier, ShopEntry>();
    private final Map<ShopCategory, Set<String>> explicitCategoryAllowlists = new HashMap<ShopCategory, Set<String>>();

    public CatalogService(PriceConfigService priceConfig) {
        this.priceConfig = priceConfig;
        for (ShopCategory category : ShopCategory.vanillaCategories()) {
            this.byCategory.put(category, new ArrayList());
        }
        this.explicitCategoryAllowlists.put(ShopCategory.LIGHTING, new HashSet());
        this.explicitCategoryAllowlists.put(ShopCategory.STAIRS_SLABS, new HashSet());
        this.explicitCategoryAllowlists.put(ShopCategory.COLOR_MATERIALS, new HashSet());
        this.explicitCategoryAllowlists.put(ShopCategory.SAND_GLASS, new HashSet());
        this.explicitCategoryAllowlists.put(ShopCategory.OCEAN, new HashSet());
    }

    public void reload() {
        this.loadCategoryAllowlists();
        this.byId.clear();
        this.byCategory.clear();
        for (ShopCategory category : ShopCategory.vanillaCategories()) {
            this.byCategory.put(category, new ArrayList());
        }
        for (Item item : Registries.ITEM) {
            ShopCategory category;
            Identifier id = Registries.ITEM.getId(item);
            if (id.getNamespace().equals("minecraft") && id.getPath().equals("air") || !this.isAllowedItem(item, id)) continue;
            category = this.detectCategory(id);
            long sell = this.priceConfig.ensureSellPrice(id, category, CatalogService.defaultSellPrice(id, category));
            long buy = this.priceConfig.ensureBuyPrice(id, sell);
            ShopEntry entry = new ShopEntry(id, item, category, sell, buy);
            this.byCategory.computeIfAbsent(category, ignored -> new ArrayList<>()).add(entry);
            this.byId.put(id, entry);
        }
        for (Map.Entry<ShopCategory, List<ShopEntry>> categoryEntries : this.byCategory.entrySet()) {
            ShopCategory category = categoryEntries.getKey();
            List<ShopEntry> entries = categoryEntries.getValue();
            entries.sort((left, right) -> {
                int tierOrder = Integer.compare(this.sortTier(left, category), this.sortTier(right, category));
                if (tierOrder != 0) {
                    return tierOrder;
                }
                int pathOrder = left.id().getPath().compareToIgnoreCase(right.id().getPath());
                return pathOrder != 0 ? pathOrder : left.id().toString().compareToIgnoreCase(right.id().toString());
            });
        }
        this.priceConfig.savePrices();
    }

    private int sortTier(ShopEntry entry, ShopCategory category) {
        String path = entry.id().getPath().toLowerCase();
        if (category == ShopCategory.END) {
            return CatalogService.isVanillaShulkerBox(entry.id()) ? 1 : 0;
        }
        if (category == ShopCategory.SAND_GLASS) {
            return CatalogService.isSandPath(path) ? 0 : CatalogService.isGlassPath(path) ? 1 : 2;
        }
        if (category == ShopCategory.COLOR_MATERIALS) {
            if (path.contains("concrete")) return 0;
            if (path.contains("terracotta") || path.contains("clay")) return 1;
            if (path.contains("wool")) return 2;
            if (path.contains("carpet")) return 3;
            return 4;
        }
        return 0;
    }

    private void loadCategoryAllowlists() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("gangshop");
        Path allowlistFile = configDir.resolve("category_allowlists.json");
        try {
            Files.createDirectories(configDir, new FileAttribute[0]);
            if (!Files.exists(allowlistFile, new LinkOption[0])) {
                this.writeDefaultCategoryAllowlists(allowlistFile);
            }
            try (BufferedReader reader = Files.newBufferedReader(allowlistFile, StandardCharsets.UTF_8);){
                JsonObject root = (JsonObject)GSON.fromJson((Reader)reader, JsonObject.class);
                if (root == null || !root.has("allowlists") || !root.get("allowlists").isJsonObject()) {
                    this.writeDefaultCategoryAllowlists(allowlistFile);
                    try (BufferedReader retryReader = Files.newBufferedReader(allowlistFile, StandardCharsets.UTF_8);){
                        root = (JsonObject)GSON.fromJson((Reader)retryReader, JsonObject.class);
                    }
                }
                if (root == null || !root.has("allowlists") || !root.get("allowlists").isJsonObject()) {
                    return;
                }
                JsonObject lists = root.getAsJsonObject("allowlists");
                for (ShopCategory category : this.explicitCategoryAllowlists.keySet()) {
                    Set<String> target = this.explicitCategoryAllowlists.get((Object)category);
                    target.clear();
                    if (!lists.has(category.getId()) || !lists.get(category.getId()).isJsonArray()) continue;
                    for (JsonElement element : lists.getAsJsonArray(category.getId())) {
                        if (!element.isJsonPrimitive()) continue;
                        target.add(element.getAsString());
                    }
                }
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private void writeDefaultCategoryAllowlists(Path allowlistFile) throws Exception {
        JsonObject root = new JsonObject();
        root.addProperty("version", (Number)1);
        JsonObject lists = new JsonObject();
        Map<ShopCategory, Set<String>> generated = new HashMap<>();
        generated.put(ShopCategory.LIGHTING, new TreeSet());
        generated.put(ShopCategory.STAIRS_SLABS, new TreeSet());
        generated.put(ShopCategory.COLOR_MATERIALS, new TreeSet());
        generated.put(ShopCategory.SAND_GLASS, new TreeSet());
        generated.put(ShopCategory.OCEAN, new TreeSet());
        for (Item item : Registries.ITEM) {
            Identifier id = Registries.ITEM.getId(item);
            String path = id.getPath().toLowerCase();
            if (CatalogService.isLightingPath(path)) {
                ((Set)generated.get((Object)ShopCategory.LIGHTING)).add(id.toString());
            }
            if (CatalogService.isStairsOrSlabPath(path)) {
                ((Set)generated.get((Object)ShopCategory.STAIRS_SLABS)).add(id.toString());
            }
            if (CatalogService.isColorMaterialsPath(path)) {
                ((Set)generated.get((Object)ShopCategory.COLOR_MATERIALS)).add(id.toString());
            }
            if (CatalogService.isSandGlassPath(path)) {
                ((Set)generated.get((Object)ShopCategory.SAND_GLASS)).add(id.toString());
            }
            if (!CatalogService.isOceanPath(path)) continue;
            ((Set)generated.get((Object)ShopCategory.OCEAN)).add(id.toString());
        }
        for (Map.Entry<ShopCategory, Set<String>> entry : generated.entrySet()) {
            JsonArray arr = new JsonArray();
            for (String id : entry.getValue()) {
                arr.add(id);
            }
            lists.add(entry.getKey().getId(), arr);
        }
        root.add("allowlists", (JsonElement)lists);
        try (BufferedWriter writer = Files.newBufferedWriter(allowlistFile, StandardCharsets.UTF_8, new OpenOption[0]);){
            GSON.toJson((JsonElement)root, (Appendable)writer);
        }
    }

    private boolean isExplicitCategoryMember(ShopCategory category, Identifier id) {
        Set<String> allowlist = this.explicitCategoryAllowlists.get((Object)category);
        if (allowlist == null) {
            return false;
        }
        return allowlist.contains(id.toString());
    }

    private boolean isAllowedItem(Item item, Identifier id) {
        if (SHOP_EXCLUDED_NAMESPACES.contains(id.getNamespace().toLowerCase())) {
            return false;
        }
        if (item.isFood() && !(item instanceof BlockItem) && !CatalogService.isVanillaVegetation(id)) {
            return false;
        }
        if ("camping".equals(id.getNamespace()) && CAMPING_BAG_IDS.contains(id.getPath())) {
            return false;
        }
        if ("naturalist".equals(id.getNamespace()) && id.getPath().contains("teddy")) {
            return false;
        }
        if (!"minecraft".equals(id.getNamespace()) && id.getPath().contains("banner")) {
            return false;
        }
        if ("betterarcheology".equals(id.getNamespace()) && id.getPath().contains("fossil")) {
            return false;
        }
        if (id.getPath().contains("suspicious")) {
            return false;
        }
        if ("bettercaves".equals(id.getNamespace()) && "rare_ice".equals(id.getPath())) {
            return false;
        }
        if ("minecraft:tnt".equals(id.toString()) || "minecraft:respawn_anchor".equals(id.toString())) {
            return false;
        }
        if ("toms_storage".equals(id.getNamespace())) {
            return false;
        }
        if (CatalogService.isVanillaSurvivalUnobtainable(id)) {
            return false;
        }
        if (CatalogService.isHardDenied(id)) {
            return false;
        }
        if (this.priceConfig.isDenied(id)) {
            return false;
        }
        if (CatalogService.isVanillaMineral(id) || CatalogService.isVanillaRedstoneItem(id) || CatalogService.isVanillaMobDrop(id)) {
            return true;
        }
        if (CatalogService.isItemFrame(id)) {
            return true;
        }
        if (!"minecraft".equals(id.getNamespace()) && CatalogService.isModdedMineralVariant(id.getPath().toLowerCase())) {
            return false;
        }
        if (!(item instanceof BlockItem)) {
            return false;
        }
        if (item instanceof ArmorItem || item instanceof ElytraItem || item instanceof ShieldItem || item instanceof SwordItem || item instanceof AxeItem || item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem || item instanceof MiningToolItem) {
            return false;
        }
        String path = id.getPath().toLowerCase();
        if (path.contains("sword") || path.contains("shield") || path.contains("helmet") || path.contains("chestplate") || path.contains("leggings") || path.contains("boots") || path.contains("elytra") || path.contains("bow") || path.contains("crossbow") || path.contains("trident")) {
            return false;
        }
        return true;
    }

    private static boolean isHardDenied(Identifier id) {
        String key = id.toString();
        String path = id.getPath().toLowerCase();
        String namespace = id.getNamespace().toLowerCase();
        if (namespace.equals("a_man_with_plushies") || namespace.equals("alexscaves")) {
            return true;
        }
        if (path.contains("totem")) {
            return true;
        }
        if (path.contains("anvil") && !key.equals("minecraft:anvil")) {
            return true;
        }
        if (!(!namespace.equals("minecraft") || !path.contains("copper") || path.equals("raw_copper") || path.equals("copper_ingot") || path.equals("copper_block") || path.equals("copper_ore") || path.equals("deepslate_copper_ore"))) {
            return true;
        }
        if (HARD_DENY_EXACT_IDS.contains(key)) {
            return true;
        }
        if (path.contains("spawn_egg")) {
            return true;
        }
        if (path.contains("egg") && !key.equals("minecraft:egg")) {
            return true;
        }
        if (path.contains("mob_ward") || path.contains("volcano_core") || path.contains("uranium_ore")) {
            return true;
        }
        if (path.contains("evoker_trap") || path.contains("man_with_plushie") || (path.contains("shulker_box") && !CatalogService.isVanillaShulkerBox(id)) || path.contains("spawner")) {
            return true;
        }
        if (namespace.contains("letsdo") && path.contains("standard")) {
            return true;
        }
        return path.contains("plush");
    }

    private static boolean isFunctionalModdedBlock(Item item, Identifier id) {
        if (!(item instanceof BlockItem)) {
            return true;
        }
        BlockItem blockItem = (BlockItem)item;
        Block block = blockItem.getBlock();
        if (block.getDefaultState().hasBlockEntity()) {
            return true;
        }
        String path = id.getPath().toLowerCase();
        return path.contains("beholder") || path.contains("chest") || path.contains("crate") || path.contains("barrel") || path.contains("drawer") || path.contains("cabinet") || path.contains("locker") || path.contains("machine") || path.contains("generator") || path.contains("furnace") || path.contains("smoker") || path.contains("blast") || path.contains("engine") || path.contains("reactor") || path.contains("altar") || path.contains("pedestal") || path.contains("spawner") || path.contains("sensor") || path.contains("controller") || path.contains("terminal") || path.contains("pipe") || path.contains("cable") || path.contains("wire") || path.contains("panel");
    }

    private static boolean isFullCubeBlock(Item item) {
        if (!(item instanceof BlockItem)) {
            return false;
        }
        BlockItem blockItem = (BlockItem)item;
        BlockState state = blockItem.getBlock().getDefaultState();
        return state.isFullCube((BlockView)EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
    }

    private static boolean isVanillaMobDrop(Identifier id) {
        return "minecraft".equals(id.getNamespace()) && VANILLA_MOB_DROP_IDS.contains(id.toString());
    }

    private static boolean isVanillaMineral(Identifier id) {
        String key = id.toString();
        if (!"minecraft".equals(id.getNamespace())) {
            return false;
        }
        if (key.equals("minecraft:ancient_debris") || key.equals("minecraft:netherite_ingot") || key.equals("minecraft:netherite_block") || key.equals("minecraft:netherite_scrap")) {
            return false;
        }
        return VANILLA_MINERAL_IDS.contains(key);
    }

    private static boolean isVanillaRedstoneItem(Identifier id) {
        if (!"minecraft".equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        if (path.equals("redstone") || path.equals("redstone_block") || path.equals("redstone_ore") || path.equals("deepslate_redstone_ore")) {
            return false;
        }
        return path.contains("redstone") || VANILLA_REDSTONE_EXTRA_IDS.contains(id.toString());
    }

    private static boolean isVanillaVegetationGround(Identifier id) {
        return "minecraft".equals(id.getNamespace()) && VANILLA_VEGETATION_GROUND_IDS.contains(id.toString());
    }

    private static boolean isVanillaVegetation(Identifier id) {
        return "minecraft".equals(id.getNamespace()) && (VANILLA_VEGETATION_GROUND_IDS.contains(id.toString()) || VANILLA_VEGETATION_IDS.contains(id.toString()));
    }

    private static boolean isItemFrame(Identifier id) {
        return "minecraft:item_frame".equals(id.toString()) || "minecraft:glow_item_frame".equals(id.toString());
    }

    private static boolean isVanillaShulkerBox(Identifier id) {
        return "minecraft".equals(id.getNamespace()) && ("shulker_box".equals(id.getPath()) || id.getPath().endsWith("_shulker_box"));
    }

    private static boolean isVanillaSurvivalUnobtainable(Identifier id) {
        return "minecraft".equals(id.getNamespace()) && SURVIVAL_UNOBTAINABLE_VANILLA_IDS.contains(id.toString());
    }

    private static boolean isVegetationPath(String path) {
        return path.contains("leaf") || path.contains("leaves") || path.contains("grass") || path.contains("flower") || path.contains("vine") || path.contains("sapling") || path.contains("crop") || path.contains("seed") || path.contains("beans") || path.contains("bean") || path.contains("fern") || path.contains("dead_bush") || path.contains("dandelion") || path.contains("glow_lichen") || path.contains("melon") || path.contains("cactus") || path.contains("lily_pad") || path.contains("fungus") || path.contains("mushroom") || path.contains("allium") || path.contains("daisy") || path.contains("lilac") || path.contains("orchid") || path.contains("tulip") || path.contains("poppy") || path.contains("peony") || path.contains("cornflower") || path.contains("rose") || path.contains("bluet") || path.contains("petal") || path.contains("blossom") || path.contains("sunflower") || path.contains("lavender") || path.contains("clover");
    }

    private static boolean isSandPath(String path) {
        return path.contains("sand") || path.contains("sandstone");
    }

    private static boolean isGlassPath(String path) {
        return path.contains("glass") || path.contains("pane");
    }

    private static boolean isRedstoneUtilityPath(String path) {
        return path.contains("pressure_plate") || path.contains("button") || path.contains("detector_rail") || path.equals("activator_rail") || path.equals("powered_rail") || path.equals("iron_door") || path.equals("iron_trapdoor");
    }

    private static boolean isModdedMineralVariant(String path) {
        return path.contains("ore") || path.contains("raw_") || path.contains("ingot") || path.contains("nugget") || path.contains("gem") || path.contains("shard") || path.contains("crystal") || path.contains("dust");
    }

    private static long defaultSellPrice(Identifier id, ShopCategory category) {
        String itemId = id.toString();
        if ("openblocks:elevator_block".equals(itemId)) {
            return 5000L;
        }
        if (CatalogService.isVanillaShulkerBox(id)) {
            return 5000L;
        }
        if ("minecraft:dirt".equals(itemId)) {
            return 2L;
        }
        if ("minecraft:grass_block".equals(itemId)) {
            return 5L;
        }
        if (category == ShopCategory.MINERALS) {
            return MINERAL_PRICE_BY_ID.getOrDefault(itemId, 60L);
        }
        long base = category.isModded() ? 25L : CatalogService.vanillaBasePrice(category);
        String path = id.getPath().toLowerCase();
        if (path.contains("log") || path.contains("planks") || path.contains("wood") || path.contains("bark")) {
            base = 20L;
        } else if (path.contains("cobble") || path.contains("stone") || path.contains("sand") || path.contains("gravel")) {
            base = 5L;
        } else if (path.contains("ore") || path.contains("ancient") || path.contains("crystal") || path.contains("geode")) {
            base = Math.round((float)base * 1.6f);
        } else if (path.contains("deepslate") || path.contains("polished") || path.contains("chiseled") || path.contains("cut")) {
            base = Math.round((float)base * 1.25f);
        } else if (path.contains("stairs") || path.contains("slab") || path.contains("wall") || path.contains("fence") || path.contains("gate")) {
            base = Math.round((float)base * 1.15f);
        }
        return Math.max(1L, base);
    }

    private ShopCategory detectCategory(Identifier id) {
        String namespace = id.getNamespace().toLowerCase();
        String path = id.getPath().toLowerCase();
        if (!"minecraft".equals(namespace)) {
            if ("openblocks".equals(namespace) && "elevator_block".equals(path)) {
                return ShopCategory.CRAFTED_ITEMS;
            }
            if (namespace.startsWith("letsdo-") || LETS_DO_NAMESPACES.contains(namespace)) {
                return ShopCategory.forMod("letsdo", "[lets do]");
            }
            return ShopCategory.forMod(namespace, this.modDisplayName(namespace));
        }
        if (path.contains("quartz")) {
            return ShopCategory.NETHER;
        }
        if (CatalogService.isVanillaMineral(id)) {
            return ShopCategory.MINERALS;
        }
        if (CatalogService.isVanillaShulkerBox(id)) {
            return ShopCategory.END;
        }
        if (CatalogService.isItemFrame(id)) {
            return ShopCategory.MISC;
        }
        if (CatalogService.isVanillaVegetation(id) || CatalogService.isVegetationPath(path)) {
            return ShopCategory.VEGETATION;
        }
        if (path.equals("tuff") || path.equals("gravel")) {
            return ShopCategory.STONE;
        }
        if (path.equals("anvil") || path.equals("decorated_pot") || path.equals("jukebox")) {
            return ShopCategory.CRAFTED_ITEMS;
        }
        if (CatalogService.isWoodPath(path)) {
            return ShopCategory.WOOD;
        }
        if (CatalogService.isStoneFamilyPath(path)) {
            return ShopCategory.STONE;
        }
        if (CatalogService.isFurniturePath(path)) {
            return ShopCategory.CRAFTED_ITEMS;
        }
        if (CatalogService.isCraftedStorageOrStationPath(path) || CatalogService.isStoveFurnaceOvenPath(path)) {
            return ShopCategory.CRAFTED_ITEMS;
        }
        if (path.contains("banner")) {
            return ShopCategory.COLOR_MATERIALS;
        }
        if (CatalogService.isNetherPath(namespace, path)) {
            return ShopCategory.NETHER;
        }
        if (CatalogService.isRedstoneUtilityPath(id.getPath().toLowerCase()) || CatalogService.isVanillaRedstoneItem(id)) {
            return ShopCategory.REDSTONE;
        }
        if (this.isExplicitCategoryMember(ShopCategory.LIGHTING, id)) {
            return ShopCategory.LIGHTING;
        }
        if (this.isExplicitCategoryMember(ShopCategory.STAIRS_SLABS, id)) {
            return ShopCategory.STAIRS_SLABS;
        }
        if (this.isExplicitCategoryMember(ShopCategory.COLOR_MATERIALS, id)) {
            return ShopCategory.COLOR_MATERIALS;
        }
        if (this.isExplicitCategoryMember(ShopCategory.SAND_GLASS, id)) {
            return ShopCategory.SAND_GLASS;
        }
        if (this.isExplicitCategoryMember(ShopCategory.OCEAN, id) || CatalogService.isOceanPath(path)) {
            return ShopCategory.OCEAN;
        }
        if (CatalogService.isVanillaMobDrop(id)) {
            return ShopCategory.MOB_DROPS;
        }
        if (path.contains("stone") || path.contains("deepslate") || path.contains("cobble") || path.contains("ore") || path.contains("brick") || path.contains("prismarine")) {
            return ShopCategory.STONE;
        }
        if (path.contains("dripstone")) {
            return ShopCategory.MISC;
        }
        if (path.equals("obsidian") || path.equals("crying_obsidian")) {
            return ShopCategory.NETHER;
        }
        if (namespace.contains("end") || path.contains("end") || path.contains("purpur") || path.contains("chorus")) {
            return ShopCategory.END;
        }
        if (path.contains("bone") || path.contains("mob") || path.contains("skull") || path.contains("head")) {
            return ShopCategory.MOB_DROPS;
        }
        if (path.contains("crafted") || path.contains("cut") || path.contains("chiseled") || path.contains("polished") || path.contains("stairs") || path.contains("slab")) {
            return ShopCategory.CRAFTED_ITEMS;
        }
        return ShopCategory.MISC;
    }

    private static boolean isFurniturePath(String path) {
        return path.contains("chair") || path.contains("table") || path.contains("desk") || path.contains("bench") || path.contains("stool") || path.contains("sofa") || path.contains("couch") || path.contains("cabinet") || path.contains("wardrobe") || path.contains("bookshelf") || path.contains("bookcase") || path.contains("nightstand") || path.contains("dresser") || path.contains("drawer") || path.contains("counter") || path.contains("shelf") || path.contains("lamp") || path.contains("lantern_decor");
    }

    private static long vanillaBasePrice(ShopCategory category) {
        if (category == ShopCategory.WOOD) return 20L;
        if (category == ShopCategory.STONE) return 5L;
        if (category == ShopCategory.REDSTONE) return 50L;
        if (category == ShopCategory.LIGHTING) return 15L;
        if (category == ShopCategory.STAIRS_SLABS) return 12L;
        if (category == ShopCategory.COLOR_MATERIALS) return 20L;
        if (category == ShopCategory.SAND_GLASS) return 12L;
        if (category == ShopCategory.VEGETATION) return 15L;
        if (category == ShopCategory.OCEAN) return 20L;
        if (category == ShopCategory.NETHER || category == ShopCategory.CRAFTED_ITEMS) return 25L;
        if (category == ShopCategory.END) return 30L;
        if (category == ShopCategory.MOB_DROPS) return 40L;
        return 12L;
    }

    private String modDisplayName(String namespace) {
        return FabricLoader.getInstance().getModContainer(namespace)
            .map(container -> container.getMetadata().getName())
            .orElse(namespace.replace('_', ' ').replace('-', ' '));
    }

    private static boolean isWoodPath(String path) {
        return path.contains("bamboo") || path.contains("log") || path.contains("plank") || path.contains("wood") || path.contains("stem") || path.contains("hyphae") || path.contains("acacia") || path.contains("birch") || path.contains("spruce") || path.contains("jungle") || path.contains("mangrove") || path.contains("cherry") || path.contains("dark_oak") || path.contains("oak_");
    }

    private static boolean isStoneFamilyPath(String path) {
        return path.contains("diorite") || path.contains("calcite") || path.contains("andesite") || path.contains("granite");
    }

    private static boolean isCraftedStorageOrStationPath(String path) {
        return path.contains("barrel") || path.contains("chest") || path.contains("bed") || path.contains("bookshelf") || path.contains("bookcase") || path.equals("crafting_table") || CatalogService.isVillagerWorkstation(path);
    }

    private static boolean isNetherPath(String namespace, String path) {
        return namespace.contains("nether") || path.contains("nether") || path.contains("crimson") || path.contains("warped") || path.contains("basalt") || path.equals("magma_block") || path.equals("soul_sand") || path.equals("soul_soil") || path.equals("obsidian") || path.equals("crying_obsidian");
    }

    private static boolean isLightingPath(String path) {
        return path.contains("torch") || path.contains("lantern") || path.contains("lamp") || path.contains("candle") || path.contains("sconce");
    }

    private static boolean isStairsOrSlabPath(String path) {
        return path.contains("stairs") || path.contains("slab");
    }

    private static boolean isColorMaterialsPath(String path) {
        return path.contains("concrete") || path.contains("terracotta") || path.contains("clay") || path.contains("wool") || path.contains("carpet");
    }

    private static boolean isSandGlassPath(String path) {
        return path.contains("sand") || path.contains("sandstone") || path.contains("glass") || path.contains("pane");
    }

    private static boolean isOceanPath(String path) {
        return path.contains("coral") || path.equals("conduit") || path.contains("sponge") || path.contains("sea_pickle") || path.contains("anemone") || path.contains("barnacle") || path.contains("starfish") || path.contains("urchin") || path.contains("seashell") || path.contains("sea_shell") || path.contains("tube_worm");
    }

    private static boolean isStoveFurnaceOvenPath(String path) {
        return path.contains("stove") || path.contains("furnace") || path.contains("oven") || path.contains("smoker") || path.contains("blast");
    }

    private static boolean isVillagerWorkstation(String path) {
        return path.equals("cartography_table") || path.equals("fletching_table") || path.equals("smithing_table") || path.equals("loom") || path.equals("lectern") || path.equals("grindstone") || path.equals("stonecutter") || path.equals("cauldron") || path.equals("brewing_stand") || path.equals("barrel") || path.equals("composter");
    }

    public Collection<ShopCategory> categories() {
        List<ShopCategory> categories = new ArrayList<>();
        ShopCategory.vanillaCategories().stream()
            .filter(category -> !this.getEntries(category).isEmpty())
            .forEach(categories::add);
        this.byCategory.keySet().stream()
            .filter(ShopCategory::isModded)
            .filter(category -> !this.getEntries(category).isEmpty())
            .sorted((left, right) -> left.getDisplayName().compareToIgnoreCase(right.getDisplayName()))
            .forEach(categories::add);
        return categories;
    }

    public List<ShopEntry> getEntries(ShopCategory category) {
        return this.byCategory.getOrDefault((Object)category, List.of());
    }

    public ShopEntry getEntry(Identifier id) {
        return this.byId.get(id);
    }

    public int getEntryCount() {
        return this.byId.size();
    }

    static {
        MINERAL_PRICE_BY_ID.put("minecraft:coal", 50L);
        MINERAL_PRICE_BY_ID.put("minecraft:coal_block", 450L);
        MINERAL_PRICE_BY_ID.put("minecraft:flint", 12L);
        MINERAL_PRICE_BY_ID.put("minecraft:redstone", 50L);
        MINERAL_PRICE_BY_ID.put("minecraft:redstone_block", 450L);
        MINERAL_PRICE_BY_ID.put("minecraft:redstone_ore", 75L);
        MINERAL_PRICE_BY_ID.put("minecraft:deepslate_redstone_ore", 90L);
        MINERAL_PRICE_BY_ID.put("minecraft:coal_ore", 80L);
        MINERAL_PRICE_BY_ID.put("minecraft:deepslate_coal_ore", 100L);
        MINERAL_PRICE_BY_ID.put("minecraft:raw_copper", 80L);
        MINERAL_PRICE_BY_ID.put("minecraft:copper_ingot", 120L);
        MINERAL_PRICE_BY_ID.put("minecraft:copper_block", 1080L);
        MINERAL_PRICE_BY_ID.put("minecraft:raw_copper_block", 720L);
        MINERAL_PRICE_BY_ID.put("minecraft:copper_ore", 120L);
        MINERAL_PRICE_BY_ID.put("minecraft:deepslate_copper_ore", 150L);
        MINERAL_PRICE_BY_ID.put("minecraft:raw_iron", 120L);
        MINERAL_PRICE_BY_ID.put("minecraft:iron_ingot", 150L);
        MINERAL_PRICE_BY_ID.put("minecraft:iron_block", 1350L);
        MINERAL_PRICE_BY_ID.put("minecraft:raw_iron_block", 1080L);
        MINERAL_PRICE_BY_ID.put("minecraft:iron_ore", 180L);
        MINERAL_PRICE_BY_ID.put("minecraft:deepslate_iron_ore", 220L);
        MINERAL_PRICE_BY_ID.put("minecraft:raw_gold", 220L);
        MINERAL_PRICE_BY_ID.put("minecraft:gold_ingot", 300L);
        MINERAL_PRICE_BY_ID.put("minecraft:gold_block", 2700L);
        MINERAL_PRICE_BY_ID.put("minecraft:raw_gold_block", 1980L);
        MINERAL_PRICE_BY_ID.put("minecraft:gold_ore", 300L);
        MINERAL_PRICE_BY_ID.put("minecraft:deepslate_gold_ore", 360L);
        MINERAL_PRICE_BY_ID.put("minecraft:nether_gold_ore", 260L);
        MINERAL_PRICE_BY_ID.put("minecraft:diamond", 400L);
        MINERAL_PRICE_BY_ID.put("minecraft:diamond_block", 3600L);
        MINERAL_PRICE_BY_ID.put("minecraft:diamond_ore", 500L);
        MINERAL_PRICE_BY_ID.put("minecraft:deepslate_diamond_ore", 600L);
        MINERAL_PRICE_BY_ID.put("minecraft:emerald", 500L);
        MINERAL_PRICE_BY_ID.put("minecraft:emerald_block", 4500L);
        MINERAL_PRICE_BY_ID.put("minecraft:emerald_ore", 650L);
        MINERAL_PRICE_BY_ID.put("minecraft:deepslate_emerald_ore", 750L);
        MINERAL_PRICE_BY_ID.put("minecraft:lapis_lazuli", 50L);
        MINERAL_PRICE_BY_ID.put("minecraft:lapis_block", 450L);
        MINERAL_PRICE_BY_ID.put("minecraft:lapis_ore", 120L);
        MINERAL_PRICE_BY_ID.put("minecraft:deepslate_lapis_ore", 150L);
        MINERAL_PRICE_BY_ID.put("minecraft:quartz", 50L);
        MINERAL_PRICE_BY_ID.put("minecraft:quartz_block", 450L);
        MINERAL_PRICE_BY_ID.put("minecraft:nether_quartz_ore", 80L);
        MINERAL_PRICE_BY_ID.put("minecraft:amethyst_shard", 100L);
        MINERAL_PRICE_BY_ID.put("minecraft:amethyst_block", 900L);
        MINERAL_PRICE_BY_ID.put("minecraft:netherite_scrap", 1250L);
        MINERAL_PRICE_BY_ID.put("minecraft:netherite_ingot", 20000L);
        MINERAL_PRICE_BY_ID.put("minecraft:netherite_block", 180000L);
    }
}

