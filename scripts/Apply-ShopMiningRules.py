"""Generate explicit shop restrictions from measured installed block properties."""

import argparse
import importlib.util
from pathlib import Path
import re
import sys


spec = importlib.util.spec_from_file_location(
    "shop_mining_prices", Path(__file__).with_name("Balance-ShopPrices.py"),
)
shop = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = shop
spec.loader.exec_module(shop)

MATERIAL = re.compile(
    r"(?:^|_)(?:copper|iron|gold|diamond|emerald|netherite)_(?:block|ore|bars)(?:_|$)"
    r"|(?:^|_)copper$|ancient_debris"
)


def chipped_removals(prices: dict, resources: dict, properties: dict) -> list[str]:
    candidates = set(prices["items"]) | set(properties)
    selected = set()
    tags = resources["tags"]

    def expand(tag: str, parents: tuple[str, ...] = ()) -> set[str]:
        if tag in parents:
            raise ValueError(f"Cyclic Chipped material tag: {tag}")
        items = set()
        for value in tags.get(tag, []):
            item = value if isinstance(value, str) else value["id"]
            if item.startswith("#"):
                items.update(expand(item[1:], (*parents, tag)))
            else:
                items.add(item)
        return items

    for tag in tags:
        if tag.startswith("chipped:") and MATERIAL.search(tag.split(":")[1]):
            selected.update(item for item in expand(tag) if item.startswith("chipped:"))
    selected.update(item for item in candidates if item.startswith("chipped:")
                    and MATERIAL.search(item.split(":")[1]))
    return sorted(selected)


def mining_caps(prices: dict, properties: dict, denied: set[str]) -> dict[str, int]:
    caps = {}
    for item, entry in properties.items():
        ticks = entry["ticks"]
        if not isinstance(ticks, int) or ticks < 1:
            raise ValueError(f"Invalid measured mining ticks: {item}")
        price = prices["items"].get(item)
        if price and price["buy"] > 0 and price["sell"] > 0 and ticks <= 10:
            if item not in denied and not item.startswith("galosphere:"):
                caps[item] = 1
    for item in ("minecraft:grass_block", "minecraft:netherrack", "minecraft:stone"):
        if item not in caps:
            raise ValueError(f"Required near-instant example was not measured/selected: {item}")
    return dict(sorted(caps.items()))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--prices", type=Path, required=True)
    parser.add_argument("--overrides", type=Path, required=True)
    parser.add_argument("--resources", type=Path, required=True)
    parser.add_argument("--properties", type=Path, required=True)
    parser.add_argument("--output-overrides", type=Path, required=True)
    parser.add_argument("--output-caps", type=Path, required=True)
    args = parser.parse_args()
    prices = shop.read_json(args.prices)
    overrides = shop.read_json(args.overrides)
    properties = shop.read_json(args.properties)
    removals = chipped_removals(prices, shop.read_json(args.resources), properties)
    if not removals:
        raise ValueError("No Chipped material variants matched installed resources")
    for item in removals:
        overrides["denyItems"][item] = True
    denied = {item for item, value in overrides["denyItems"].items() if value}
    caps = mining_caps(prices, properties, denied)
    if overrides.get("globalSellMultiplier", 1.0) != 1.0 or any(
        value != 1.0 for value in overrides.get("categoryMultipliers", {}).values()
    ):
        raise ValueError("Literal $1 overrides require neutral global/category multipliers")
    # Stored prices bypass multipliers, but item overrides do not. The shop's
    # default modded multiplier is 2, which would otherwise turn $1 into $2.
    overrides["moddedBlocksSellMultiplier"] = 1.0
    for item in caps:
        overrides["itemSellOverrides"][item] = 1
    shop.write_json(args.output_overrides, overrides)
    shop.write_json(args.output_caps, {
        "rule": "diamond tools, no enchantments/effects, default block state, <=10 ticks",
        "items": caps,
        "chippedRemovals": removals,
        "measurements": {item: properties[item] for item in caps},
    })
    print(f"Chipped material removals: {len(removals)}; near-instant SELL caps: {len(caps)}")


if __name__ == "__main__":
    main()
