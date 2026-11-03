"""Balance Gang Shop integer prices against the installed recipe resources.

Use --crafted-premium for ingredient BUY cost +20%, preserving SELL payouts.
Then use --sell-percent with the BUY report to cap resale against ingredients.
The original sell-balancing model remains available for preparing a baseline.
Requires scipy==1.17.1. Input files are read-only.
"""

from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import dataclass
from fractions import Fraction
from io import BytesIO
import json
from math import gcd
from pathlib import Path
import re
import zipfile


RESOURCE = re.compile(r"^data/([^/]+)/(recipes|tags/items)/(.+)\.json$")


@dataclass(frozen=True)
class Recipe:
    id: str
    inputs: tuple[tuple[tuple[str, ...], int], ...]
    outputs: tuple[tuple[str, int], ...]


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def targets_from_file(path: Path) -> dict[str, int]:
    data = read_json(path)
    if data["minimumBuy"] != 2 or data["buyToSellRatio"] != 2:
        raise ValueError("This balancer requires a $2 buy minimum and 2:1 buy/sell ratio")
    targets = {f"minecraft:{key}": value for key, value in data["minecraft"].items()}
    targets.update({f"mythicmetals:{key}": value for key, value in data["mythicmetals"].items()})
    for metal, (raw, ingot, deepslate) in data["metals"].items():
        for item, value in {
            f"raw_{metal}": raw,
            f"{metal}_ingot": ingot,
            f"{metal}_ore": ingot,
            f"deepslate_{metal}_ore": deepslate,
        }.items():
            targets[f"mythicmetals:{item}"] = value
    for wood, (log, plank, group) in data["wood"].items():
        nether = wood in {"crimson", "warped"}
        log_form, wood_form = ("stem", "hyphae") if nether else ("log", "wood")
        targets[f"minecraft:{wood}_{log_form}"] = log
        targets[f"minecraft:{wood}_planks"] = plank
        forms = [
            f"stripped_{wood}_{log_form}", f"{wood}_{wood_form}",
            f"stripped_{wood}_{wood_form}", f"{wood}_slab", f"{wood}_stairs",
            f"{wood}_fence", f"{wood}_fence_gate", f"{wood}_door",
            f"{wood}_trapdoor", f"{wood}_pressure_plate", f"{wood}_button",
            f"{wood}_sign", f"{wood}_hanging_sign", f"{wood}_boat",
            f"{wood}_chest_boat",
            f"{wood}_fungus" if nether else (
                "mangrove_propagule" if wood == "mangrove" else f"{wood}_sapling"
            ),
            f"{wood}_leaves",
        ]
        for item, value in zip(forms, data["woodForms"][group], strict=True):
            if value is not None:
                targets[f"minecraft:{item}"] = value
    return targets


def collect_resources(
    minecraft: Path, inventory: Path, archives: list[Path], mod_dirs: list[Path],
    datapacks: Path, extra_resources: Path | None = None,
) -> tuple[dict, dict, list[str], list[str], list[str]]:
    recipes: dict[str, dict] = {}
    tags: dict[str, list] = {}
    ignored: list[str] = []
    namespaces = {"minecraft"}
    mod_ids = {"minecraft", "fabric"}

    def resource(name: str, raw: bytes) -> None:
        match = RESOURCE.fullmatch(name)
        if not match:
            return
        namespace, kind, key = match.groups()
        if namespace == "galosphere":
            return
        data = json.loads(raw)
        item_id = f"{namespace}:{key}"
        if kind == "recipes":
            recipes[item_id] = data
        else:
            if data.get("replace", False):
                tags[item_id] = []
            tags.setdefault(item_id, []).extend(data.get("values", []))

    def scan(jar: zipfile.ZipFile) -> int:
        count = 0
        if "fabric.mod.json" in jar.namelist():
            metadata = json.loads(jar.read("fabric.mod.json"), strict=False)
            mod_ids.add(metadata["id"])
            mod_ids.update(metadata.get("provides", []))
        for entry in jar.infolist():
            namespace = re.match(r"^(?:assets|data)/([^/]+)/", entry.filename)
            if namespace:
                namespaces.add(namespace[1])
            if RESOURCE.fullmatch(entry.filename):
                resource(entry.filename, jar.read(entry))
                count += 1
        for entry in jar.infolist():
            if entry.filename.startswith("META-INF/jars/") and entry.filename.endswith(".jar"):
                with zipfile.ZipFile(BytesIO(jar.read(entry))) as nested:
                    count += scan(nested)
        return count

    with zipfile.ZipFile(minecraft) as jar:
        scan(jar)
    installed = read_json(inventory)
    wanted = {item["name"]: item["size"] for item in installed}
    found: dict[str, tuple[Path, str | None]] = {}
    for directory in mod_dirs:
        for path in directory.rglob("*.jar"):
            if path.name in wanted and path.stat().st_size == wanted[path.name]:
                found[path.name] = (path, None)
    for archive in archives:
        with zipfile.ZipFile(archive) as pack:
            for entry in pack.infolist():
                name = entry.filename.rsplit("/", 1)[-1]
                if name in wanted and entry.file_size == wanted[name] and name not in found:
                    found[name] = (archive, entry.filename)
    extra = {}
    if extra_resources:
        extra = {entry["name"]: entry for entry in read_json(extra_resources)}
        for name, entry in extra.items():
            if name not in wanted or wanted[name] != entry["size"]:
                raise ValueError(f"Live resource inventory mismatch: {name}")
    missing = sorted(wanted.keys() - found.keys() - extra.keys())
    if missing:
        raise ValueError("Missing exact-name/size active jars: " + json.dumps(missing))
    for name in sorted(found):
        if "galosphere" in name.lower():
            ignored.append(name)
            continue
        path, entry = found[name]
        if entry is None:
            with zipfile.ZipFile(path) as jar:
                scan(jar)
        else:
            with zipfile.ZipFile(path) as pack:
                with zipfile.ZipFile(BytesIO(pack.read(entry))) as jar:
                    scan(jar)
    for entry in extra.values():
        for name, data in entry["resources"].items():
            resource(name, json.dumps(data).encode())
    for pack in sorted(datapacks.iterdir()):
        if pack.is_dir():
            for path in sorted(pack.rglob("*.json")):
                name = path.relative_to(pack).as_posix()
                if RESOURCE.fullmatch(name):
                    resource(name, path.read_bytes())
        elif pack.suffix == ".zip":
            with zipfile.ZipFile(pack) as jar:
                scan(jar)
    return recipes, tags, ignored, sorted(namespaces), sorted(mod_ids)


def parse_recipes(resources: dict) -> tuple[list[Recipe], list[dict]]:
    tags = resources["tags"]
    mod_ids = set(resources["modIds"])
    cache: dict[str, tuple[str, ...]] = {}
    skipped: list[dict] = []
    parsed: list[Recipe] = []

    def tag_items(tag: str, parents: tuple[str, ...] = ()) -> tuple[str, ...]:
        if tag in parents:
            raise ValueError(f"Cyclic tag: {parents} -> {tag}")
        if tag not in cache:
            values = set()
            for entry in tags.get(tag, []):
                item = entry if isinstance(entry, str) else entry["id"]
                if item.startswith("#"):
                    values.update(tag_items(item[1:], (*parents, tag)))
                elif not item.startswith("galosphere:"):
                    values.add(item)
            cache[tag] = tuple(sorted(values))
        return cache[tag]

    def ingredient(value: dict | list | str) -> tuple[tuple[str, ...], int]:
        if isinstance(value, str):
            return ((value,), 1)
        if isinstance(value, list):
            choices = set()
            for choice in value:
                resolved, count = ingredient(choice)
                if count != 1:
                    raise ValueError("Counted ingredient alternatives")
                choices.update(resolved)
            return (tuple(sorted(choices)), 1)
        count = value.get("count", 1)
        if value.get("fabric:type") == "fabric:any":
            return ingredient(value["ingredients"])
        if value.get("fabric:type") == "fabric:nbt":
            return ingredient(value["base"])
        if value.get("fabric:type") == "fabric:difference":
            base, count = ingredient(value["base"])
            excluded, _ = ingredient(value["subtracted"])
            return tuple(item for item in base if item not in excluded), count
        if "item" in value:
            return ((value["item"],), count)
        if "tag" in value:
            return (tag_items(value["tag"]), count)
        raise ValueError(f"Unknown ingredient: {value}")

    def output(value: dict | str, count: int = 1) -> tuple[str, int]:
        if isinstance(value, str):
            return value, count
        if "item" in value or "id" in value:
            return value.get("item", value.get("id")), value.get("count", count)
        if "priority" in value:
            choices = value["priority"]
            defaults = tag_items(value["default"]) if "default" in value else ()
            for choice in choices:
                if choice in defaults:
                    return choice, value.get("count", count)
            if defaults:
                return defaults[0], value.get("count", count)
        raise ValueError(f"Unknown result: {value}")

    def condition(value: dict) -> bool:
        kind = value.get("condition", value.get("type"))
        if kind in resources.get("conditions", {}):
            return resources["conditions"][kind]
        if kind in {"fabric:all_mods_loaded", "fabric:any_mod_loaded"}:
            checks = [mod in mod_ids for mod in value["values"]]
            return all(checks) if kind == "fabric:all_mods_loaded" else any(checks)
        if kind == "fabric:tags_populated":
            return all(tag_items(tag) for tag in value["values"])
        if kind in {"forge:mod_loaded", "meadow:mod_loaded"}:
            return value["modid"] in mod_ids
        if kind in {"forge:not", "fabric:not"}:
            return not condition(value["value"])
        if kind in {"forge:and", "forge:or"}:
            checks = [condition(entry) for entry in value["values"]]
            return all(checks) if kind == "forge:and" else any(checks)
        if kind == "forge:tag_empty":
            return not tag_items(value["tag"])
        raise ValueError(f"Unknown load condition: {value}")

    def parse(recipe_id: str, data: dict) -> None:
        load_conditions = data.get("fabric:load_conditions", [])
        if not all(condition(value) for value in load_conditions):
            skipped.append({"id": recipe_id, "reason": "inactive load condition"})
            return
        kind = data["type"]
        if kind in {"meadow:conditional", "doapi:conditional"}:
            if all(condition(value) for value in data["conditions"]):
                parse(recipe_id, data["recipe"])
            else:
                skipped.append({"id": recipe_id, "reason": "inactive conditional wrapper"})
            return
        if kind.startswith("chipped:") and "tags" in data:
            for tag in data["tags"]:
                choices = tag_items(tag)
                for item in choices:
                    parsed.append(Recipe(
                        f"{recipe_id}/{item}", ((choices, 1),), ((item, 1),),
                    ))
            return
        if "pattern" in data and "key" in data:
            counts = Counter("".join(data["pattern"]).replace(" ", ""))
            inputs = []
            for symbol, count in counts.items():
                choices, quantity = ingredient(data["key"][symbol])
                inputs.append((choices, count * quantity))
            outputs = [output(data["result"])]
        elif kind == "alloy_forgery:forging":
            inputs = [ingredient(value) for value in data["inputs"]]
            item, count = output(data["output"])
            count = max([count] + [
                override.get("count", count) for override in data.get("overrides", {}).values()
            ])
            outputs = [(item, count)]
        elif kind == "betterend:infusion":
            inputs = [ingredient(data["input"])]
            inputs.extend(ingredient(value) for value in data["catalysts"].values())
            outputs = [output(data["result"])]
        elif "base" in data and "addition" in data and "result" in data:
            inputs = [ingredient(data["base"]), ingredient(data["addition"])]
            if "template" in data:
                inputs.append(ingredient(data["template"]))
            outputs = [output(data["result"])]
        elif kind == "meadow:woodcutting":
            inputs = [ingredient(data["inputItem"])]
            outputs = [output(data["outputItem"], data["outputAmount"])]
        elif (
            ("ingredients" in data or "ingredient" in data)
            and ("result" in data or "results" in data or "item" in data)
            and not any(key in data for key in ("juice", "fluid", "fluid_input"))
        ):
            values = data.get("ingredients", [data.get("ingredient")])
            inputs = [ingredient(value) for value in values]
            result = data.get("result", data.get("results", data.get("item")))
            results = result if isinstance(result, list) else [result]
            outputs = [
                output(value, data.get("count", 1)) for value in results
                if not isinstance(value, dict) or value.get("chance", 1) >= 1
            ]
        else:
            raise ValueError(f"Unsupported serializer/schema: {kind}")
        if not outputs:
            raise ValueError("No guaranteed item output")
        if any(not choices or count < 1 for choices, count in inputs):
            raise ValueError("Empty ingredient tag or invalid ingredient count")
        if any(count < 1 for _, count in outputs):
            raise ValueError("Invalid output count")
        parsed.append(Recipe(recipe_id, tuple(inputs), tuple(outputs)))

    for recipe_id, data in resources["recipes"].items():
        try:
            parse(recipe_id, data)
        except ValueError as error:
            skipped.append({"id": recipe_id, "reason": str(error)})

    # These are survival transformations implemented in code, not recipe JSON.
    for wood in ("oak", "spruce", "birch", "acacia", "dark_oak", "jungle",
                 "mangrove", "cherry", "crimson", "warped"):
        forms = ("stem", "hyphae") if wood in {"crimson", "warped"} else ("log", "wood")
        for form in forms:
            source = f"minecraft:{wood}_{form}"
            parsed.append(Recipe(
                f"stripping/{wood}_{form}", (((source,), 1),),
                ((f"minecraft:stripped_{wood}_{form}", 1),),
            ))
    parsed.append(Recipe(
        "stripping/bamboo_block", ((("minecraft:bamboo_block",), 1),),
        (("minecraft:stripped_bamboo_block", 1),),
    ))
    return parsed, skipped


def balance(
    prices: dict, targets: dict[str, int], resources: dict,
    sell_caps: dict[str, int] | None = None,
    allow_batch_floor: bool = False,
) -> tuple[dict, dict]:
    import numpy as np
    from scipy.optimize import Bounds, LinearConstraint, milp
    from scipy.sparse import coo_matrix

    recipes, skipped = parse_recipes(resources)
    crafted = {item for recipe in recipes for item, _ in recipe.outputs}
    namespaces = set(resources["namespaces"]) - {"galosphere"}
    original = prices["items"]
    absent_targets = sorted(targets.keys() - original.keys())
    targets = {key: value for key, value in targets.items() if key in original}
    seeds = sell_caps if sell_caps is not None else {
        key: max(1, targets.get(key, value["sell"]))
        for key, value in original.items()
        if key.split(":")[0] in namespaces and (value["sell"] > 0 or key in targets)
    }
    known = set(seeds)
    while sell_caps is None:
        before = len(known)
        for recipe in recipes:
            if all(any(item in known for item in choices) for choices, _ in recipe.inputs):
                known.update(item for item, _ in recipe.outputs)
        if before == len(known):
            break
    ids = sorted(known)
    columns = {item: index for index, item in enumerate(ids)}
    rows: list[dict[int, float]] = []
    lower: list[float] = []
    upper: list[float] = []
    modeled: list[Recipe] = []
    unknown: list[dict] = []
    alternatives: dict[tuple[str, ...], int] = {}
    floor_variables: list[int] = []
    nvars = len(ids)

    def add_row(values: dict[int, float], lo: float = -np.inf, hi: float = 0) -> None:
        rows.append(values)
        lower.append(lo)
        upper.append(hi)

    for recipe in recipes:
        if not any(item in known for item, _ in recipe.outputs):
            continue
        if not all(item in known for item, _ in recipe.outputs):
            unknown.append({"id": recipe.id, "reason": "unpriced output",
                            "inputs": recipe.inputs, "outputs": recipe.outputs})
            continue
        inputs = [(tuple(item for item in choices if item in known), count)
                  for choices, count in recipe.inputs]
        if any(not choices for choices, _ in inputs):
            unknown.append({"id": recipe.id, "reason": "unpriced ingredient",
                            "inputs": recipe.inputs, "outputs": recipe.outputs})
            continue
        values: Counter = Counter()
        for item, count in recipe.outputs:
            values[columns[item]] += count
        for choices, count in inputs:
            if len(choices) == 1:
                column = columns[choices[0]]
            else:
                if choices not in alternatives:
                    alternatives[choices] = nvars
                    nvars += 1
                    for item in choices:
                        add_row({alternatives[choices]: 1, columns[item]: -1})
                column = alternatives[choices]
            values[column] -= count
        output_count = sum(count for _, count in recipe.outputs)
        input_count = sum(count for _, count in inputs)
        if allow_batch_floor and sell_caps is not None and output_count > input_count:
            flag = nvars
            nvars += 1
            floor_variables.append(flag)
            maximum = sum(sell_caps[item] * count for item, count in recipe.outputs)
            add_row({**values, flag: -maximum})
            floor_row: Counter = Counter()
            for item, count in recipe.outputs:
                floor_row[columns[item]] += count
            floor_row[flag] = maximum
            add_row(dict(floor_row), hi=output_count + maximum)
        else:
            add_row(dict(values))
        modeled.append(recipe)

    integer_variables = nvars
    objective = [0.0] * nvars
    increase_objective = [0.0] * nvars
    for item, target in seeds.items():
        increase, decrease = nvars, nvars + 1
        nvars += 2
        weight = 1.0 if sell_caps is not None else 100.0 if item in targets else (
            1.0 if item not in crafted or item.startswith("minecraft:") else 0.001
        )
        objective.extend([0.0, weight])
        increase_objective.extend([1.0, 0.0])
        add_row({columns[item]: 1, increase: -1}, hi=target)
        add_row({columns[item]: -1, decrease: -1}, hi=-target)
    row_indices, col_indices, coefficients = [], [], []
    for row_index, values in enumerate(rows):
        for col_index, value in values.items():
            if value:
                row_indices.append(row_index)
                col_indices.append(col_index)
                coefficients.append(value)
    matrix = coo_matrix((coefficients, (row_indices, col_indices)),
                        shape=(len(rows), nvars)).tocsc()
    ceilings = np.full(nvars, np.inf)
    floors = np.array([1.0] * integer_variables + [0.0] * (nvars - integer_variables))
    for column in floor_variables:
        floors[column] = 0
        ceilings[column] = 1
    if sell_caps is not None:
        for item, column in columns.items():
            ceilings[column] = sell_caps[item]
    bounds = Bounds(
        floors,
        ceilings,
    )
    integrality = np.array([1] * integer_variables + [0] * (nvars - integer_variables))
    # First allow only the ingredient increases required by the $2 buy minimum.
    # Then keep targets close without raising ingredients to prop up old markups.
    constraints = [LinearConstraint(matrix, lower, upper)]
    if sell_caps is None:
        floor_result = milp(
            np.array(increase_objective), integrality=integrality, bounds=bounds,
            constraints=constraints,
            options={"time_limit": 60, "mip_rel_gap": 0.0},
        )
        if not floor_result.success:
            raise ValueError(f"Minimum-price feasibility solve failed: {floor_result.message}")
        constraints.append(
            LinearConstraint(np.array(increase_objective), -np.inf, floor_result.fun + 1e-6),
        )
    result = milp(
        np.array(objective), integrality=integrality, bounds=bounds,
        constraints=constraints,
        options={"time_limit": 60, "mip_rel_gap": 0.0 if sell_caps is not None else 0.01},
    )
    if sell_caps is not None and not result.success:
        raise ValueError(f"SELL price optimization failed: {result.message}")
    if not result.success and result.x is None:
        raise ValueError(f"Integer price solver failed: {result.message}")
    if result.x is None or any(
        abs(result.x[column] - round(result.x[column])) > 1e-5 for column in columns.values()
    ):
        raise ValueError("Solver did not return integer item prices")
    values = {item: int(round(result.x[column])) for item, column in columns.items()}
    if sell_caps is not None and any(
        not 1 <= value <= sell_caps[item] for item, value in values.items()
    ):
        raise ValueError("SELL solution violates an integer percentage ceiling")
    # Check actual cheapest alternatives, not the solver's auxiliary estimates.
    violations = []
    floor_exceptions = []
    for recipe in modeled:
        cost = sum(min(values[item] for item in choices if item in values) * count
                   for choices, count in recipe.inputs)
        value = sum(values[item] * count for item, count in recipe.outputs)
        if value > cost:
            entry = {"id": recipe.id, "cost": cost, "outputValue": value}
            if allow_batch_floor and all(values[item] == 1 for item, _ in recipe.outputs):
                floor_exceptions.append(entry)
            else:
                violations.append(entry)
    if violations:
        raise ValueError(f"Recipe-cost violations: {violations[:10]}")
    output_prices = json.loads(json.dumps(prices))
    changed = {}
    for item, price in values.items():
        if item not in original:
            continue
        if original[item]["sell"] == 0 and item not in targets and not item.startswith("mythicmetals:"):
            continue
        after = {"sell": price, "buy": original[item]["buy"] if sell_caps is not None else price * 2}
        output_prices["items"][item] = after
        if original[item] != after:
            changed[item] = {"before": original[item], "after": after}
    report = {
        "recipeCount": len(recipes), "modeledRecipes": len(modeled),
        "priceCount": len(original), "changedCount": len(changed),
        "targetCount": len(targets), "requestedButAbsent": absent_targets,
        "targetAdjustments": {
            item: {"requestedSell": target, "actualSell": values[item]}
            for item, target in targets.items() if target != values[item]
        },
        "changes": changed, "skipped": skipped, "unpricedRecipes": unknown,
        "referencePrices": {item: price for item, price in values.items() if item not in original},
        "violations": violations, "objective": result.fun,
        "batchFloorExceptions": floor_exceptions,
        "optimization": {
            "status": result.message, "withinOnePercent": bool(result.success),
            "gap": float(result.mip_gap),
        },
    }
    return output_prices, report


def balance_sell_prices(
    prices: dict, resources: dict, reference_buys: dict[str, int], percent: int,
    item_caps: dict[str, int] | None = None,
    previous_reference_sells: dict[str, int] | None = None,
    allow_batch_floor: bool = False,
) -> tuple[dict, dict]:
    if not 1 <= percent < 100:
        raise ValueError("SELL percentage must be between 1 and 99")
    buys = {
        item: value["buy"] for item, value in prices["items"].items()
        if value["buy"] > 0 and not item.startswith("galosphere:")
    }
    buys.update({
        item: value for item, value in reference_buys.items()
        if item not in prices["items"] and value > 0 and not item.startswith("galosphere:")
    })
    caps = {item: max(1, buy * percent // 100) for item, buy in buys.items()}
    if item_caps is not None:
        missing = item_caps.keys() - caps.keys()
        if missing:
            raise ValueError(f"SELL caps contain unpriced/disabled items: {sorted(missing)}")
        if any(not isinstance(value, int) or value < 1 for value in item_caps.values()):
            raise ValueError("SELL caps must be positive integers")
        for item, value in prices["items"].items():
            if item in caps:
                caps[item] = min(caps[item], value["sell"])
        for item, value in (previous_reference_sells or {}).items():
            if item in caps:
                caps[item] = min(caps[item], value)
        for item, value in item_caps.items():
            caps[item] = min(caps[item], value)
    output, report = balance(
        prices, {item: cap for item, cap in caps.items() if item in prices["items"]},
        resources, sell_caps=caps, allow_batch_floor=allow_batch_floor,
    )
    for item, before in prices["items"].items():
        after = output["items"][item]
        if after["buy"] != before["buy"]:
            raise ValueError(f"SELL balancing changed BUY price: {item}")
        if item in caps and after["sell"] > caps[item]:
            raise ValueError(f"SELL price exceeds percentage cap: {item}")
        if (before["buy"] == 0 or item.startswith("galosphere:")) and after != before:
            raise ValueError(f"SELL balancing changed a disabled/removed item: {item}")
        if item_caps is not None and after["sell"] > before["sell"]:
            raise ValueError(f"SELL cap propagation increased a payout: {item}")
    report.update({
        "policy": f"maximize SELL toward {percent}% of BUY, rounded down, capped by ingredient SELL values",
        "sellPercent": percent, "buyPolicy": "unchanged",
        "explicitSellCaps": item_caps or {},
        "allowBatchFloor": allow_batch_floor,
    })
    return output, report


def premium_buy_prices(
    prices: dict, resources: dict, reference_sells: dict[str, int],
) -> tuple[dict, dict]:
    """Price a baseline once; reversible components share a material-unit price."""
    from scipy.sparse import coo_matrix
    from scipy.sparse.csgraph import connected_components

    recipes, skipped = parse_recipes(resources)
    original = prices["items"]
    baseline = {
        item: value["buy"] for item, value in original.items()
        if value["buy"] > 0 and not item.startswith("galosphere:")
    }
    for item, sell in reference_sells.items():
        if item not in original and sell > 0 and not item.startswith("galosphere:"):
            baseline[item] = sell * 2
    ids = sorted(baseline)
    if not ids:
        raise ValueError("No positive baseline BUY prices")
    columns = {item: index for index, item in enumerate(ids)}
    modeled = []
    unpriced = []
    for recipe in recipes:
        if len(recipe.outputs) != 1:
            unpriced.append({"id": recipe.id, "reason": "multiple guaranteed outputs"})
            continue
        item, _ = recipe.outputs[0]
        if item not in baseline:
            continue
        inputs = tuple((tuple(i for i in choices if i in baseline), count)
                       for choices, count in recipe.inputs)
        if not inputs or any(not choices for choices, _ in inputs):
            unpriced.append({"id": recipe.id, "reason": "unpriced ingredient"})
            continue
        recipe = Recipe(recipe.id, inputs, recipe.outputs)
        modeled.append(recipe)
    conversions = {
        (source, target, Fraction(quantity, count))
        for recipe in modeled if len(recipe.inputs) == 1
        for choices, quantity in recipe.inputs for source in choices
        for target, count in recipe.outputs
    }
    neutral = set()
    edges = set()
    for recipe in modeled:
        if len(recipe.inputs) != 1:
            continue
        (choices, quantity), = recipe.inputs
        (target, count), = recipe.outputs
        if all(
            (target, source, Fraction(count, quantity)) in conversions
            and baseline[source] * quantity == baseline[target] * count
            for source in choices
        ):
            neutral.add(recipe.id)
            edges.update((columns[source], columns[target]) for source in choices)
    rows = [edge[0] for edge in edges]
    cols = [edge[1] for edge in edges]
    graph = coo_matrix(([1] * len(edges), (rows, cols)),
                       shape=(len(ids), len(ids))).tocsr()
    component_count, labels = connected_components(graph, connection="strong")
    groups = [[] for _ in range(component_count)]
    for item, label in zip(ids, labels):
        groups[int(label)].append(item)
    components = {item: int(labels[columns[item]]) for item in ids}
    neutral = {
        recipe.id for recipe in modeled if recipe.id in neutral
        and all(components[source] == components[recipe.outputs[0][0]]
                for choices, _ in recipe.inputs for source in choices)
    }
    incoming = [[] for _ in groups]
    for recipe in modeled:
        item, _ = recipe.outputs[0]
        group = components[item]
        if recipe.id not in neutral:
            incoming[group].append(recipe)
    units = []
    weights = {}
    for members in groups:
        unit = 0
        for item in members:
            unit = gcd(unit, baseline[item])
        units.append(unit)
        weights.update({item: baseline[item] // unit for item in members})

    anchored = {i for i, entries in enumerate(incoming) if not entries}
    material_prices = {i: units[i] for i in anchored}
    unrooted = []

    def candidate(recipe: Recipe):
        item, count = recipe.outputs[0]
        cost = 0
        for choices, quantity in recipe.inputs:
            available = [material_prices[components[i]] * weights[i]
                         for i in choices if components[i] in material_prices]
            if not available:
                return None
            cost += min(available) * quantity
        divisor = 5 * count * weights[item]
        required_unit = (6 * cost + divisor - 1) // divisor
        return (max(units[components[item]], required_unit), recipe.id, cost, item, count)

    # Relax from unknown, not from cheap crafted prices. Otherwise recycling
    # can become its own fictitious cheap supply of a material.
    while True:
        for iteration in range(10000):
            changed = False
            for group, entries in enumerate(incoming):
                if group in anchored:
                    continue
                candidates = [value for recipe in entries
                              if (value := candidate(recipe)) is not None]
                if not candidates:
                    continue
                unit = min(candidates)[0]
                if group not in material_prices or unit < material_prices[group]:
                    material_prices[group] = unit
                    changed = True
            if not changed:
                break
        else:
            raise ValueError("Crafted BUY prices did not converge")
        unknown = set(range(component_count)) - material_prices.keys()
        if not unknown:
            break
        # A closed crafting cycle with no obtainable entry is a primary market
        # source, not a way to manufacture its own starting item for free.
        missing_ids = sorted(unknown)
        missing_columns = {group: index for index, group in enumerate(missing_ids)}
        missing_edges = set()
        for target in missing_ids:
            for recipe in incoming[target]:
                for choices, _ in recipe.inputs:
                    missing_edges.update(
                        (missing_columns[components[i]], missing_columns[target])
                        for i in choices if components[i] in unknown
                    )
        missing_graph = coo_matrix(
            ([1] * len(missing_edges),
             ([a for a, _ in missing_edges], [b for _, b in missing_edges])),
            shape=(len(missing_ids), len(missing_ids)),
        ).tocsr()
        count, missing_labels = connected_components(missing_graph, connection="strong")
        roots = set(range(count))
        for source, target in missing_edges:
            if missing_labels[source] != missing_labels[target]:
                roots.discard(int(missing_labels[target]))
        for group, label in zip(missing_ids, missing_labels):
            if int(label) in roots:
                anchored.add(group)
                material_prices[group] = units[group]
                unrooted.append(groups[group])
    values = {item: material_prices[components[item]] * weights[item] for item in ids}
    decisions = []
    for group, entries in enumerate(incoming):
        if group in anchored:
            continue
        chosen = min(candidate(recipe) for recipe in entries)
        unit, recipe_id, cost, item, count = chosen
        if unit != material_prices[group]:
            raise ValueError(f"Unstable recipe price: {item}")
        decisions.append({
            "item": item, "recipe": recipe_id, "ingredientBuyCost": cost,
            "outputCount": count, "buy": values[item],
            "materialUnit": unit, "baselineUnit": units[group],
        })
    cycle_groups = [
        {"items": members, "baselineUnit": units[group],
         "materialUnit": material_prices[group]}
        for group, members in enumerate(groups)
        if len(members) > 1
    ]

    # Verify the selected cheapest entry route and exact integer rounding.
    for decision in decisions:
        divisor = 5 * decision["outputCount"] * (
            baseline[decision["item"]] // decision["baselineUnit"]
        )
        expected = max(decision["baselineUnit"],
                       (6 * decision["ingredientBuyCost"] + divisor - 1) // divisor)
        if decision["materialUnit"] != expected:
            raise ValueError(f"Incorrect crafted premium: {decision}")
    output = json.loads(json.dumps(prices))
    changes = {}
    for item, before in original.items():
        if item not in values:
            continue
        after = {**before, "buy": values[item]}
        if after["buy"] < before["buy"]:
            raise ValueError(f"BUY price decreased: {item}")
        if after["buy"] > 9_223_372_036_854_775_807:
            raise ValueError(f"BUY price exceeds the shop integer limit: {item}")
        output["items"][item] = after
        if before != after:
            changes[item] = {"before": before, "after": after}
    return output, {
        "policy": "cheapest ingredient BUY cost +20%, rounded up; no BUY decreases",
        "cyclePolicy": "preserve baseline material ratios; premium on cheapest external entry only",
        "sellPolicy": "unchanged",
        "recipeCount": len(recipes), "modeledRecipes": len(modeled),
        "changedCount": len(changes), "changes": changes,
        "pricedEntryComponents": decisions, "reversibleComponents": cycle_groups,
        "cycleRecipeExceptions": sorted(neutral), "skipped": skipped,
        "unrootedMarketSources": unrooted,
        "unpricedRecipes": unpriced,
        "referenceBuyPrices": {item: value for item, value in values.items()
                               if item not in original},
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--minecraft", type=Path)
    parser.add_argument("--inventory", type=Path)
    parser.add_argument("--archive", type=Path, action="append", default=[])
    parser.add_argument("--mods", type=Path, action="append", default=[])
    parser.add_argument("--datapacks", type=Path)
    parser.add_argument("--resources", type=Path, required=True)
    parser.add_argument("--extra-resources", type=Path)
    parser.add_argument("--prices", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--report", type=Path)
    parser.add_argument("--targets", type=Path, default=Path(__file__).with_name("shop-price-targets.json"))
    parser.add_argument("--conditions", type=Path)
    parser.add_argument("--crafted-premium", action="store_true")
    parser.add_argument("--baseline-report", type=Path)
    parser.add_argument("--sell-percent", type=int)
    parser.add_argument("--buy-reference-report", type=Path)
    parser.add_argument("--sell-caps", type=Path)
    parser.add_argument("--previous-sell-report", type=Path)
    parser.add_argument("--allow-batch-floor", action="store_true")
    args = parser.parse_args()
    if args.minecraft and (args.inventory is None or args.datapacks is None):
        parser.error("--minecraft requires --inventory and --datapacks")
    if args.prices and (args.output is None or args.report is None):
        parser.error("--prices requires --output and --report")
    if not args.minecraft and not args.prices:
        parser.error("Provide --minecraft to extract resources or --prices to balance")
    if args.crafted_premium and not args.baseline_report:
        parser.error("--crafted-premium requires --baseline-report for unlisted ingredient values")
    if args.sell_percent is not None and (args.crafted_premium or not args.buy_reference_report):
        parser.error("--sell-percent requires --buy-reference-report and cannot use --crafted-premium")
    if (args.sell_caps or args.previous_sell_report or args.allow_batch_floor) and args.sell_percent is None:
        parser.error("SELL caps and batch rounding require --sell-percent")
    if args.minecraft:
        recipes, tags, ignored, namespaces, mod_ids = collect_resources(
            args.minecraft, args.inventory, args.archive, args.mods, args.datapacks,
            args.extra_resources,
        )
        write_json(args.resources, {
            "recipes": recipes, "tags": tags, "ignored": ignored,
            "namespaces": namespaces, "modIds": mod_ids,
        })
        print(json.dumps({"recipes": len(recipes), "tags": len(tags)}, indent=2))
    if args.prices:
        resources = read_json(args.resources)
        if args.conditions:
            resources["conditions"] = read_json(args.conditions)
        if args.sell_percent is not None:
            output_prices, report = balance_sell_prices(
                read_json(args.prices), resources,
                read_json(args.buy_reference_report)["referenceBuyPrices"], args.sell_percent,
                item_caps=read_json(args.sell_caps)["items"] if args.sell_caps else None,
                previous_reference_sells=read_json(args.previous_sell_report)["referencePrices"]
                if args.previous_sell_report else None,
                allow_batch_floor=args.allow_batch_floor,
            )
        elif args.crafted_premium:
            output_prices, report = premium_buy_prices(
                read_json(args.prices), resources,
                read_json(args.baseline_report)["referencePrices"],
            )
        else:
            output_prices, report = balance(
                read_json(args.prices), targets_from_file(args.targets), resources,
            )
        write_json(args.output, output_prices)
        write_json(args.report, report)
        print(json.dumps({key: report[key] for key in (
            "recipeCount", "modeledRecipes", "changedCount", "targetCount", "requestedButAbsent",
            "optimization",
        ) if key in report}, indent=2))


if __name__ == "__main__":
    main()
