import importlib.util
from pathlib import Path
import sys
import unittest


spec = importlib.util.spec_from_file_location(
    "shop_prices", Path(__file__).with_name("Balance-ShopPrices.py"),
)
shop = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = shop
spec.loader.exec_module(shop)

mining_spec = importlib.util.spec_from_file_location(
    "shop_mining_rules", Path(__file__).with_name("Apply-ShopMiningRules.py"),
)
mining = importlib.util.module_from_spec(mining_spec)
mining_spec.loader.exec_module(mining)


def resources(recipes, tags=None):
    return {
        "recipes": recipes, "tags": tags or {},
        "namespaces": ["minecraft", "mythicmetals"], "modIds": ["minecraft"],
    }


def prices(**values):
    return {"version": 2, "items": {
        f"minecraft:{key}": {"sell": value, "buy": value * 2}
        for key, value in values.items()
    }}


class ShopPriceTests(unittest.TestCase):
    def test_persisted_mining_rules_remove_materials_and_keep_literal_dollar_caps(self):
        root = Path(__file__).resolve().parents[1]
        overrides = shop.read_json(root / "config" / "gangshop" / "overrides.json")
        catalog = shop.read_json(root / "config" / "gangshop" / "prices.json")
        for item in mining.chipped_removals(catalog, resources({}), {}):
            self.assertTrue(overrides["denyItems"][item], item)
        self.assertFalse(overrides["denyItems"].get("chipped:diamond_oak_planks", False))
        self.assertEqual(overrides["moddedBlocksSellMultiplier"], 1.0)
        for item, cap in overrides["itemSellOverrides"].items():
            self.assertEqual(cap, 1, item)
            self.assertEqual(catalog["items"][item]["sell"], 1, item)
        for item in ("minecraft:grass_block", "minecraft:netherrack", "minecraft:stone",
                     "minecraft:cobblestone", "minecraft:oak_log"):
            self.assertEqual(overrides["itemSellOverrides"][item], 1)

    def test_chipped_removal_covers_material_families_without_vanilla_bans(self):
        source = resources({}, {
            "chipped:raw_copper_block": ["minecraft:raw_copper_block", "chipped:odd_variant"],
            "chipped:diamond_block": ["chipped:ancient_diamond_block"],
            "chipped:oak_planks": ["chipped:ordinary_oak_planks"],
        })
        original = {"items": {
            item: {"buy": 100, "sell": 60} for item in (
                "chipped:iron_bars", "chipped:raw_gold_block", "chipped:netherite_block",
                "chipped:ancient_debris", "chipped:waxed_oxidized_copper",
                "chipped:emerald_block", "chipped:ordinary_oak_planks",
                "chipped:diamond_oak_planks", "chipped:large_diamond_leaded_glass",
                "minecraft:iron_ingot", "minecraft:raw_copper_block",
            )
        }}
        removed = mining.chipped_removals(original, source, {})
        self.assertIn("chipped:odd_variant", removed)
        self.assertIn("chipped:ancient_diamond_block", removed)
        self.assertNotIn("chipped:ordinary_oak_planks", removed)
        self.assertNotIn("chipped:diamond_oak_planks", removed)
        self.assertNotIn("chipped:large_diamond_leaded_glass", removed)
        self.assertTrue(all(item.startswith("chipped:") for item in removed))
        self.assertEqual(len(removed), 8)

    def test_mining_selection_uses_measured_tick_cutoff_and_preserves_disabled(self):
        original = prices(
            grass_block=2, netherrack=4, stone=6, slow_block=6,
            boundary_block=3, denied_block=4, disabled_block=0,
        )
        properties = {
            item: {"ticks": 10, "hardness": 2.5, "tool": "minecraft:diamond_pickaxe"}
            for item in original["items"]
        }
        properties["minecraft:slow_block"]["ticks"] = 11
        caps = mining.mining_caps(original, properties, {"minecraft:denied_block"})
        self.assertEqual(caps, {
            "minecraft:boundary_block": 1, "minecraft:grass_block": 1,
            "minecraft:netherrack": 1, "minecraft:stone": 1,
        })
        properties["minecraft:stone"]["ticks"] = 0
        with self.assertRaisesRegex(ValueError, "Invalid measured"):
            mining.mining_caps(original, properties, set())

    def test_batch_floor_allows_only_minimum_outputs_without_raising_inputs(self):
        source = resources({
            "slabs": {
                "type": "minecraft:stonecutting",
                "ingredient": {"item": "minecraft:stone"},
                "result": "minecraft:stone_slab", "count": 2,
            },
            "polished": {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [{"item": "minecraft:stone"}],
                "result": {"item": "minecraft:polished_stone"},
            },
        })
        original = prices(stone=6, stone_slab=3, polished_stone=6)
        output, report = shop.balance_sell_prices(
            original, source, {}, 60, {"minecraft:stone": 1},
            allow_batch_floor=True,
        )
        self.assertEqual(output["items"]["minecraft:stone"], {"buy": 12, "sell": 1})
        self.assertEqual(output["items"]["minecraft:stone_slab"], {"buy": 6, "sell": 1})
        self.assertEqual(output["items"]["minecraft:polished_stone"]["sell"], 1)
        self.assertEqual(report["batchFloorExceptions"],
                         [{"id": "slabs", "cost": 1, "outputValue": 2}])
        self.assertEqual(report["violations"], [])

    def test_batch_floor_does_not_relax_valuable_batch_outputs(self):
        source = resources({
            "slabs": {
                "type": "minecraft:stonecutting",
                "ingredient": {"item": "minecraft:stone"},
                "result": "minecraft:stone_slab", "count": 2,
            },
        })
        output, report = shop.balance_sell_prices(
            prices(stone=6, stone_slab=6), source, {}, 60, {},
            allow_batch_floor=True,
        )
        self.assertEqual(output["items"]["minecraft:stone_slab"]["sell"], 3)
        self.assertEqual(report["batchFloorExceptions"], [])

    def test_explicit_sell_caps_cannot_raise_other_items(self):
        original = prices(stone=6, diamond=100)
        output, _ = shop.balance_sell_prices(
            original, resources({}), {}, 60, {"minecraft:stone": 1},
        )
        self.assertEqual(output["items"]["minecraft:diamond"], original["items"]["minecraft:diamond"])
        with self.assertRaisesRegex(ValueError, "unpriced/disabled"):
            shop.balance_sell_prices(original, resources({}), {}, 60, {"minecraft:missing": 1})
        with self.assertRaisesRegex(ValueError, "positive integers"):
            shop.balance_sell_prices(original, resources({}), {}, 60, {"minecraft:stone": 0})

    def test_sell_targets_sixty_percent_rounds_down_and_keeps_buy(self):
        original = {"version": 2, "items": {
            "minecraft:stone": {"buy": 7, "sell": 2},
        }}
        output, report = shop.balance_sell_prices(original, resources({}), {}, 60)
        self.assertEqual(output["items"]["minecraft:stone"], {"buy": 7, "sell": 4})
        self.assertEqual(report["violations"], [])

    def test_sell_anvil_is_capped_by_ingredients_and_reversible_nuggets(self):
        source = resources({
            "anvil": {
                "type": "minecraft:crafting_shaped", "pattern": ["BBB", " I ", "III"],
                "key": {"B": {"item": "minecraft:iron_block"},
                        "I": {"item": "minecraft:iron_ingot"}},
                "result": {"item": "minecraft:anvil"},
            },
            "block": {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [{"item": "minecraft:iron_ingot", "count": 9}],
                "result": {"item": "minecraft:iron_block"},
            },
            "ingots": {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [{"item": "minecraft:iron_block"}],
                "result": {"item": "minecraft:iron_ingot", "count": 9},
            },
            "nuggets": {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [{"item": "minecraft:iron_ingot"}],
                "result": {"item": "minecraft:iron_nugget", "count": 9},
            },
            "from_nuggets": {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [{"item": "minecraft:iron_nugget", "count": 9}],
                "result": {"item": "minecraft:iron_ingot"},
            },
        })
        original = {"version": 2, "items": {
            "minecraft:anvil": {"buy": 1005, "sell": 12},
            "minecraft:iron_block": {"buy": 243, "sell": 81},
            "minecraft:iron_ingot": {"buy": 27, "sell": 9},
        }}
        output, report = shop.balance_sell_prices(
            original, source, {"minecraft:iron_nugget": 3}, 60,
        )
        self.assertEqual(output["items"]["minecraft:anvil"], {"buy": 1005, "sell": 279})
        self.assertEqual(output["items"]["minecraft:iron_ingot"]["sell"], 9)
        self.assertEqual(output["items"]["minecraft:iron_block"]["sell"], 81)
        self.assertEqual(report["violations"], [])

    def test_sell_checks_cheapest_tags_batch_yields_and_combined_outputs(self):
        source = resources({
            "slab": {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [{"tag": "minecraft:planks", "count": 3}],
                "result": {"item": "minecraft:oak_slab", "count": 6},
            },
            "two_outputs": {
                "type": "farm_and_charm:mincer",
                "ingredient": {"item": "minecraft:oak_planks"},
                "results": [{"item": "minecraft:stick"}, {"item": "minecraft:oak_button"}],
            },
        }, {"minecraft:planks": ["minecraft:oak_planks", "minecraft:spruce_planks"]})
        output, _ = shop.balance_sell_prices(
            prices(oak_planks=10, spruce_planks=20, oak_slab=10,
                   stick=10, oak_button=10), source, {}, 60,
        )
        items = output["items"]
        self.assertEqual(items["minecraft:oak_slab"]["sell"], 6)
        self.assertLessEqual(items["minecraft:stick"]["sell"]
                             + items["minecraft:oak_button"]["sell"],
                             items["minecraft:oak_planks"]["sell"])

    def test_sell_keeps_disabled_and_removed_entries(self):
        original = prices(apple=0, stone=4)
        original["items"]["galosphere:allurite_shard"] = {"buy": 160, "sell": 80}
        output, _ = shop.balance_sell_prices(original, resources({}), {}, 60)
        self.assertEqual(output["items"]["minecraft:apple"], {"buy": 0, "sell": 0})
        self.assertEqual(output["items"]["galosphere:allurite_shard"],
                         original["items"]["galosphere:allurite_shard"])

    def test_sell_refuses_an_active_multiplication_cycle(self):
        source = resources({
            "mincer": {
                "type": "farm_and_charm:mincer",
                "ingredient": {"item": "minecraft:stone"},
                "result": {"item": "minecraft:cobblestone", "count": 2},
            },
            "smelt": {
                "type": "minecraft:smelting",
                "ingredient": {"item": "minecraft:cobblestone"},
                "result": "minecraft:stone",
            },
        })
        with self.assertRaisesRegex(ValueError, "SELL price optimization failed"):
            shop.balance_sell_prices(prices(stone=6, cobblestone=6), source, {}, 60)

    def test_sell_rejects_an_invalid_percentage(self):
        for percent in (0, 100):
            with self.subTest(percent=percent):
                with self.assertRaisesRegex(ValueError, "between 1 and 99"):
                    shop.balance_sell_prices(prices(stone=4), resources({}), {}, percent)

    def test_premium_anvil_uses_ingredient_buys_and_preserves_sells(self):
        source = resources({
            "anvil": {
                "type": "minecraft:crafting_shaped", "pattern": ["BBB", " I ", "III"],
                "key": {"B": {"item": "minecraft:iron_block"},
                        "I": {"item": "minecraft:iron_ingot"}},
                "result": {"item": "minecraft:anvil"},
            },
        })
        original = prices(iron_block=81, iron_ingot=9, anvil=12)
        output, _ = shop.premium_buy_prices(original, source, {})
        self.assertEqual(output["items"]["minecraft:anvil"], {"buy": 670, "sell": 12})
        self.assertEqual(original["items"]["minecraft:anvil"]["buy"], 24)

    def test_premium_uses_cheapest_route_tags_and_batch_output(self):
        source = resources({
            "buttons": {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [{"tag": "minecraft:planks"}],
                "result": {"item": "minecraft:oak_button", "count": 2},
            },
            "expensive_buttons": {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [{"item": "minecraft:diamond"}],
                "result": {"item": "minecraft:oak_button"},
            },
        }, {"minecraft:planks": ["minecraft:oak_planks", "minecraft:spruce_planks"]})
        output, _ = shop.premium_buy_prices(
            prices(oak_planks=2, spruce_planks=3, diamond=100, oak_button=1), source, {},
        )
        self.assertEqual(output["items"]["minecraft:oak_button"]["buy"], 3)

    def test_premium_reversible_materials_have_no_compounding_markup(self):
        source = resources({
            "block": {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [{"item": "minecraft:iron_ingot", "count": 9}],
                "result": {"item": "minecraft:iron_block"},
            },
            "ingots": {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [{"item": "minecraft:iron_block"}],
                "result": {"item": "minecraft:iron_ingot", "count": 9},
            },
        })
        original = prices(iron_block=81, iron_ingot=9)
        output, report = shop.premium_buy_prices(original, source, {})
        self.assertEqual(output, original)
        self.assertEqual(len(report["cycleRecipeExceptions"]), 2)

    def test_premium_reversible_variants_share_cheapest_external_entry(self):
        source = resources({
            "planks": {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [{"item": "minecraft:oak_log"}],
                "result": {"item": "minecraft:oak_planks", "count": 4},
            },
            "chipped:carpenters_table": {
                "type": "chipped:carpenters_table", "tags": ["chipped:oak_planks"],
            },
        }, {"chipped:oak_planks": ["minecraft:oak_planks", "minecraft:variant"]})
        output, _ = shop.premium_buy_prices(
            prices(oak_log=10, oak_planks=2, variant=2), source, {},
        )
        self.assertEqual(output["items"]["minecraft:oak_planks"]["buy"], 6)
        self.assertEqual(output["items"]["minecraft:variant"]["buy"], 6)

    def test_premium_propagates_through_multiple_crafting_steps(self):
        source = resources({
            "planks": {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [{"item": "minecraft:oak_log"}],
                "result": {"item": "minecraft:oak_planks", "count": 4},
            },
            "button": {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [{"item": "minecraft:oak_planks"}],
                "result": {"item": "minecraft:oak_button"},
            },
        })
        output, _ = shop.premium_buy_prices(
            prices(oak_log=10, oak_planks=2, oak_button=1), source, {},
        )
        self.assertEqual(output["items"]["minecraft:oak_button"]["buy"], 8)

    def test_premium_preserves_disabled_and_removed_items_and_uses_references(self):
        source = resources({
            "block": {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [{"item": "minecraft:bone_meal", "count": 9}],
                "result": {"item": "minecraft:bone_block"},
            },
        })
        original = prices(apple=0, bone_block=9)
        original["items"]["galosphere:allurite_shard"] = {"buy": 160, "sell": 80}
        output, _ = shop.premium_buy_prices(original, source, {"minecraft:bone_meal": 1})
        self.assertEqual(output["items"]["minecraft:bone_block"]["buy"], 22)
        self.assertEqual(output["items"]["minecraft:apple"], {"buy": 0, "sell": 0})
        self.assertEqual(output["items"]["galosphere:allurite_shard"],
                         original["items"]["galosphere:allurite_shard"])
        self.assertNotIn("minecraft:bone_meal", output["items"])

    def test_premium_reports_missing_costs_without_changing_item(self):
        source = resources({
            "anvil": {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [{"item": "minecraft:missing"}],
                "result": {"item": "minecraft:anvil"},
            },
        })
        original = prices(anvil=12)
        output, report = shop.premium_buy_prices(original, source, {})
        self.assertEqual(output, original)
        self.assertEqual(report["unpricedRecipes"],
                         [{"id": "anvil", "reason": "unpriced ingredient"}])

    def test_premium_does_not_exempt_tool_recycling_from_markup(self):
        source = resources({
            "ingot": {
                "type": "minecraft:smelting",
                "ingredient": {"item": "minecraft:raw_iron"},
                "result": "minecraft:iron_ingot",
            },
            "tool": {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [{"item": "minecraft:iron_ingot", "count": 3},
                                {"item": "minecraft:stick", "count": 2}],
                "result": {"item": "minecraft:iron_pickaxe"},
            },
            "recycling": {
                "type": "minecraft:smelting",
                "ingredient": {"item": "minecraft:iron_pickaxe"},
                "result": "minecraft:iron_nugget",
            },
            "nuggets": {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [{"item": "minecraft:iron_ingot"}],
                "result": {"item": "minecraft:iron_nugget", "count": 9},
            },
            "from_nuggets": {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [{"item": "minecraft:iron_nugget", "count": 9}],
                "result": {"item": "minecraft:iron_ingot"},
            },
        })
        output, report = shop.premium_buy_prices(
            prices(raw_iron=9, iron_ingot=9, iron_pickaxe=12, stick=1),
            source, {"minecraft:iron_nugget": 1},
        )
        self.assertEqual(output["items"]["minecraft:iron_ingot"]["buy"], 27)
        self.assertEqual(output["items"]["minecraft:iron_pickaxe"]["buy"], 102)
        self.assertNotIn("tool", report["cycleRecipeExceptions"])
        self.assertNotIn("recycling", report["cycleRecipeExceptions"])

    def test_premium_self_requiring_recipe_is_not_a_free_material_source(self):
        source = resources({
            "duplication": {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [{"item": "minecraft:template"},
                                {"item": "minecraft:diamond", "count": 7}],
                "result": {"item": "minecraft:template", "count": 2},
            },
        })
        original = prices(template=20, diamond=100)
        output, report = shop.premium_buy_prices(original, source, {})
        self.assertEqual(output, original)
        self.assertEqual(report["unrootedMarketSources"], [["minecraft:template"]])

    def test_real_vanilla_recipe_output_counts(self):
        source = resources({
            "button": {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [{"item": "minecraft:oak_planks"}],
                "result": {"item": "minecraft:oak_button"},
            },
            "slab": {
                "type": "minecraft:crafting_shaped", "pattern": ["PPP"],
                "key": {"P": {"item": "minecraft:oak_planks"}},
                "result": {"item": "minecraft:oak_slab", "count": 6},
            },
            "door": {
                "type": "minecraft:crafting_shaped", "pattern": ["PP", "PP", "PP"],
                "key": {"P": {"item": "minecraft:oak_planks"}},
                "result": {"item": "minecraft:oak_door", "count": 3},
            },
        })
        parsed, skipped = shop.parse_recipes(source)
        indexed = {recipe.id: recipe for recipe in parsed}
        self.assertEqual(indexed["button"].outputs, (("minecraft:oak_button", 1),))
        self.assertEqual(indexed["slab"].inputs, ((("minecraft:oak_planks",), 3),))
        self.assertEqual(indexed["slab"].outputs, (("minecraft:oak_slab", 6),))
        self.assertEqual(indexed["door"].inputs, ((("minecraft:oak_planks",), 6),))
        self.assertEqual(indexed["door"].outputs, (("minecraft:oak_door", 3),))
        self.assertEqual(skipped, [])

    def test_cheapest_alternative_and_multi_ingredient(self):
        source = resources({
            "piston": {
                "type": "minecraft:crafting_shaped", "pattern": ["PPP", "CIC", "CRC"],
                "key": {
                    "P": {"tag": "minecraft:planks"},
                    "C": {"item": "minecraft:cobblestone"},
                    "I": {"item": "minecraft:iron_ingot"},
                    "R": {"item": "minecraft:redstone"},
                },
                "result": {"item": "minecraft:piston"},
            },
        }, {"minecraft:planks": ["minecraft:oak_planks", "minecraft:spruce_planks"]})
        original = prices(
            oak_planks=2, spruce_planks=3, cobblestone=2, iron_ingot=48,
            redstone=12, piston=100,
        )
        output, report = shop.balance(original, {"minecraft:piston": 70}, source)
        items = output["items"]
        cost = (3 * min(items["minecraft:oak_planks"]["sell"],
                        items["minecraft:spruce_planks"]["sell"])
                + 4 * items["minecraft:cobblestone"]["sell"]
                + items["minecraft:iron_ingot"]["sell"]
                + items["minecraft:redstone"]["sell"])
        self.assertLessEqual(items["minecraft:piston"]["sell"], cost)
        self.assertEqual(report["violations"], [])

    def test_minimum_raises_ingredients_instead_of_free_outputs(self):
        source = resources({
            "slab": {
                "type": "minecraft:stonecutting",
                "ingredient": {"item": "minecraft:stone"},
                "result": "minecraft:stone_slab", "count": 2,
            },
        })
        output, _ = shop.balance(prices(stone=1, stone_slab=1), {}, source)
        self.assertEqual(output["items"]["minecraft:stone"], {"sell": 2, "buy": 4})
        self.assertEqual(output["items"]["minecraft:stone_slab"], {"sell": 1, "buy": 2})

    def test_existing_markups_do_not_raise_ingredients(self):
        output, _ = shop.balance(
            prices(oak_log=8, stripped_oak_log=9),
            {"minecraft:oak_log": 8, "minecraft:stripped_oak_log": 9},
            resources({}),
        )
        self.assertEqual(output["items"]["minecraft:oak_log"]["sell"], 8)
        self.assertEqual(output["items"]["minecraft:stripped_oak_log"]["sell"], 8)

    def test_reversible_bone_block_is_exactly_nine_bonemeal(self):
        source = resources({
            "bone_block": {
                "type": "minecraft:crafting_shaped", "pattern": ["BBB", "BBB", "BBB"],
                "key": {"B": {"item": "minecraft:bone_meal"}},
                "result": {"item": "minecraft:bone_block"},
            },
            "bone_meal": {
                "type": "minecraft:crafting_shapeless",
                "ingredients": [{"item": "minecraft:bone_block"}],
                "result": {"item": "minecraft:bone_meal", "count": 9},
            },
        })
        output, _ = shop.balance(prices(bone_block=100, bone_meal=4), {}, source)
        self.assertEqual(output["items"]["minecraft:bone_block"]["buy"],
                         output["items"]["minecraft:bone_meal"]["buy"] * 9)

    def test_forge_uses_highest_tier_yield_and_id_field(self):
        source = resources({
            "forge": {
                "type": "alloy_forgery:forging",
                "inputs": [{"item": "minecraft:raw_copper", "count": 2}],
                "output": {"id": "minecraft:copper_ingot", "count": 3},
                "overrides": {"2+": {"id": "minecraft:copper_ingot", "count": 4}},
            },
        })
        parsed, _ = shop.parse_recipes(source)
        self.assertEqual(parsed[0].inputs, ((("minecraft:raw_copper",), 2),))
        self.assertEqual(parsed[0].outputs, (("minecraft:copper_ingot", 4),))

    def test_chipped_variants_cannot_bypass_base_price(self):
        source = resources({
            "chipped:carpenters_table": {
                "type": "chipped:carpenters_table", "tags": ["chipped:oak_planks"],
            },
        }, {"chipped:oak_planks": ["minecraft:oak_planks", "minecraft:variant"]})
        output, _ = shop.balance(prices(oak_planks=2, variant=50), {}, source)
        self.assertEqual(output["items"]["minecraft:variant"]["sell"],
                         output["items"]["minecraft:oak_planks"]["sell"])

    def test_zero_food_and_removed_mod_are_unchanged(self):
        original = prices(apple=0, stone=4)
        original["items"]["galosphere:allurite_shard"] = {"sell": 80, "buy": 160}
        output, _ = shop.balance(original, {}, resources({}))
        self.assertEqual(output, original)

    def test_disabled_copper_reversal_is_inactive(self):
        root = Path(__file__).resolve().parents[1]
        folder = root / "config" / "paxi" / "datapacks" / "gangs_fixes" / "data"
        disabled = shop.read_json(
            folder / "farm_and_charm" / "recipes" / "mincer" /
            "raw_copper_from_copper_ingot.json",
        )
        parsed, skipped = shop.parse_recipes(resources({"copper": disabled}))
        self.assertNotIn("copper", [recipe.id for recipe in parsed])
        self.assertEqual(skipped, [{"id": "copper", "reason": "inactive load condition"}])
        stone = shop.read_json(
            folder / "farm_and_charm" / "recipes" / "mincer" / "cobblestone.json",
        )
        limestone = shop.read_json(
            folder / "conditional" / "recipes" / "farm_and_charm" / "mincer" /
            "cobbled_limestone_from_mincer.json",
        )
        self.assertEqual(stone["result"]["count"], 1)
        self.assertEqual(limestone["recipe"]["result"]["count"], 1)

    def test_multiplication_loops_are_rejected_not_silently_mispriced(self):
        for multiplier in (2, 4):
            with self.subTest(multiplier=multiplier):
                source = resources({
                    "mincing": {
                        "type": "farm_and_charm:mincer",
                        "ingredient": {"item": "minecraft:stone"},
                        "result": {"item": "minecraft:cobblestone", "count": multiplier},
                    },
                    "smelting": {
                        "type": "minecraft:smelting",
                        "ingredient": {"item": "minecraft:cobblestone"},
                        "result": "minecraft:stone",
                    },
                })
                with self.assertRaisesRegex(ValueError, "feasibility"):
                    shop.balance(prices(stone=6, cobblestone=6), {}, source)
                source["recipes"]["mincing"]["result"]["count"] = 1
                _, report = shop.balance(prices(stone=6, cobblestone=6), {}, source)
                self.assertEqual(report["violations"], [])

    def test_screenshot_transcription(self):
        targets = shop.targets_from_file(Path(__file__).with_name("shop-price-targets.json"))
        self.assertEqual(targets["minecraft:jungle_log"], 10)
        self.assertEqual(targets["minecraft:jungle_planks"], 3)
        self.assertEqual(targets["minecraft:oak_button"], 2)
        self.assertEqual(targets["minecraft:crimson_fungus"], 10)
        self.assertEqual(targets["mythicmetals:deepslate_stormyx_ore"], 510)
        self.assertFalse(any(key.startswith("galosphere:") for key in targets))

    def test_arbitrage_restrictions_preserve_storage_and_trades(self):
        root = Path(__file__).resolve().parents[1]
        config = shop.read_json(root / "config" / "gangshop" / "overrides.json")
        denied = config["denyItems"]
        for material in ("coal", "copper", "diamond", "emerald", "gold", "iron",
                         "lapis", "redstone"):
            for prefix in ("", "deepslate_"):
                self.assertTrue(denied[f"minecraft:{prefix}{material}_ore"])
        for item in ("nether_gold_ore", "nether_quartz_ore", "amethyst_cluster",
                     "sea_lantern", "ender_chest", "gravel", "sunflower",
                     "lilac", "rose_bush", "peony"):
            self.assertTrue(denied[f"minecraft:{item}"])
        for material in ("iron", "copper", "gold"):
            self.assertFalse(denied.get(f"minecraft:raw_{material}_block", False))
        for item in ("emerald", "iron_ingot", "stone", "cobblestone"):
            self.assertFalse(denied.get(f"minecraft:{item}", False))


if __name__ == "__main__":
    unittest.main()
