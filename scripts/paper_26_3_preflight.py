#!/usr/bin/env python3
"""Advisory source scan for likely Paper/Minecraft 26.3 compatibility breakpoints.

This scanner is deliberately heuristic. A hit is a review target, not a failure. It is
usable against Slimefun Legacy itself or an addon checkout so the same preflight lane
can cover the maintained SF_ fork collection before a 26.3 API/runtime is available.
"""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


@dataclass(frozen=True)
class Rule:
    key: str
    title: str
    pattern: re.Pattern[str]


RULES = (
    Rule(
        "materials-entities",
        "Material / entity enum assumptions",
        re.compile(r"\b(?:Material|EntityType|Biome|TreeType)\s*\.\s*[A-Z0-9_]+"),
    ),
    Rule(
        "deprecated-api",
        "Deprecated or legacy API surface",
        re.compile(r"@Deprecated\b|\bcom\.destroystokyo\.paper\b|\borg\.bukkit\.material\b"),
    ),
    Rule(
        "paper-internals",
        "Paper / CraftBukkit / Minecraft internals",
        re.compile(
            r"\b(?:org\.bukkit\.craftbukkit|net\.minecraft|io\.papermc\.paper\.(?:adventure|util\.ObfHelper|configuration\.legacy))\b"
        ),
    ),
    Rule(
        "reflection",
        "Reflection and runtime member lookup",
        re.compile(
            r"\b(?:Class\.forName|getDeclaredMethod|getDeclaredField|getMethod|getField|MethodHandles|java\.lang\.reflect)\b"
        ),
    ),
    Rule(
        "recipes",
        "Recipe API / serialization assumptions",
        re.compile(
            r"\b(?:RecipeChoice|ShapedRecipe|ShapelessRecipe|SmithingRecipe|StonecuttingRecipe|FurnaceRecipe|CookingRecipe|RecipeIterator|Bukkit\.addRecipe|removeRecipe)\b"
        ),
    ),
    Rule(
        "worldgen",
        "World generation / structure / chunk hooks",
        re.compile(
            r"\b(?:ChunkGenerator|BlockPopulator|BiomeProvider|WorldInfo|WorldCreator|StructureType|Structure\.class|LimitedRegion)\b"
        ),
    ),
    Rule(
        "growth",
        "Trees / crops / saplings / growth behavior",
        re.compile(
            r"\b(?:StructureGrowEvent|BlockGrowEvent|BlockFertilizeEvent|TreeType|Ageable|Sapling|Crops|Bonemeal|BoneMeal|PALE_OAK|CHERRY_SAPLING|MANGROVE_PROPAGULE)\b",
            re.IGNORECASE,
        ),
    ),
    Rule(
        "serialization",
        "Persistent or serialized Bukkit data",
        re.compile(
            r"\b(?:ConfigurationSerializable|PersistentDataContainer|PersistentDataType|BukkitObjectOutputStream|BukkitObjectInputStream|serializeAsBytes|deserializeBytes|ItemStack\.deserialize|ObjectInputStream|ObjectOutputStream)\b"
        ),
    ),
    Rule(
        "version-checks",
        "Hard-coded Minecraft / Paper version checks",
        re.compile(
            r"\b(?:getMinecraftVersion|getBukkitVersion|getVersion\(\)|minecraftVersion|serverVersion|paperVersion)\b|(?:1\.21\.11|26\.2|v1_21|MC_VERSION)",
            re.IGNORECASE,
        ),
    ),
    Rule(
        "scheduler-threading",
        "Scheduler / ownership assumptions",
        re.compile(
            r"\b(?:BukkitScheduler|runTask(?:Later|Timer|Asynchronously)?|isOwnedByCurrentRegion|RegionScheduler|GlobalRegionScheduler|AsyncScheduler|Folia)\b"
        ),
    ),
)

TEXT_SUFFIXES = {".java", ".kt", ".kts", ".yml", ".yaml", ".json", ".properties"}
SKIP_PARTS = {".git", ".gradle", "build", "target", "out", "node_modules", ".idea"}


def source_files(root: Path) -> Iterable[Path]:
    for path in root.rglob("*"):
        if not path.is_file() or path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        relative = path.relative_to(root)
        if any(part in SKIP_PARTS for part in relative.parts):
            continue
        yield path


def scan(root: Path, max_examples: int) -> dict:
    findings: dict[str, list[dict[str, object]]] = {rule.key: [] for rule in RULES}
    files_scanned = 0

    for path in source_files(root):
        files_scanned += 1
        try:
            lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
        except OSError:
            continue

        relative = path.relative_to(root).as_posix()
        for line_number, line in enumerate(lines, start=1):
            for rule in RULES:
                if len(findings[rule.key]) >= max_examples:
                    continue
                match = rule.pattern.search(line)
                if match:
                    findings[rule.key].append(
                        {
                            "path": relative,
                            "line": line_number,
                            "match": match.group(0)[:120],
                        }
                    )

    categories = []
    for rule in RULES:
        examples = findings[rule.key]
        categories.append(
            {
                "key": rule.key,
                "title": rule.title,
                "examples": examples,
                "example_count": len(examples),
            }
        )

    return {
        "schema": 1,
        "target": root.name,
        "files_scanned": files_scanned,
        "categories": categories,
    }


def write_markdown(report: dict, destination: Path) -> None:
    lines = [
        f"# Paper 26.3 preflight source scan — {report['target']}",
        "",
        "> Advisory only: a match is a review target, not a confirmed 26.3 incompatibility.",
        "",
        f"Scanned **{report['files_scanned']}** source/configuration files.",
        "",
    ]

    for category in report["categories"]:
        examples = category["examples"]
        lines.append(f"## {category['title']} ({len(examples)} sampled)")
        lines.append("")
        if not examples:
            lines.append("No sampled matches.")
            lines.append("")
            continue

        for example in examples:
            lines.append(
                f"- `{example['path']}:{example['line']}` — `{str(example['match']).replace('`', '')}`"
            )
        lines.append("")

    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", nargs="?", default=".")
    parser.add_argument("--json", dest="json_path")
    parser.add_argument("--markdown", dest="markdown_path")
    parser.add_argument("--max-examples", type=int, default=25)
    args = parser.parse_args()

    root = Path(args.root).resolve()
    if not root.is_dir():
        raise SystemExit(f"Paper 26.3 preflight failed: not a directory: {root}")

    report = scan(root, max(1, args.max_examples))

    if args.json_path:
        output = Path(args.json_path)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    if args.markdown_path:
        write_markdown(report, Path(args.markdown_path))

    sampled = sum(category["example_count"] for category in report["categories"])
    print(f"Paper 26.3 advisory preflight scanned {report['files_scanned']} files; {sampled} sampled review targets.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
