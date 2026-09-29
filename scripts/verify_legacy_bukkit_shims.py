#!/usr/bin/env python3
"""Verify that deprecated Bukkit compatibility shims remain narrow and behavior-preserving."""

from __future__ import annotations

import sys
from pathlib import Path


def require(condition: bool, failures: list[str], message: str) -> None:
    if not condition:
        failures.append(message)


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise FileNotFoundError(relative)
    return path.read_text(encoding="utf-8")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []

    bridge = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/utils/compatibility/LegacyBukkitCompatibility.java",
    )
    require(
        "return material.isInteractable();" in bridge,
        failures,
        "LegacyBukkitCompatibility must preserve Bukkit's historical Material#isInteractable behavior",
    )
    require(
        "return choice.getItemStack();" in bridge,
        failures,
        "LegacyBukkitCompatibility must preserve generic RecipeChoice representative-stack behavior",
    )

    for relative in (
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/food/HeavyCream.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/magical/KnowledgeFlask.java",
    ):
        source = read(root, relative)
        require(
            "LegacyBukkitCompatibility.isInteractable(" in source,
            failures,
            f"{relative} must route interactability checks through the compatibility bridge",
        )
        require(
            ".isInteractable()" not in source,
            failures,
            f"{relative} must not call deprecated Material#isInteractable directly",
        )

    for relative in (
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/autocrafters/VanillaRecipe.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/autocrafters/AbstractAutoCrafter.java",
    ):
        source = read(root, relative)
        require(
            "LegacyBukkitCompatibility.getRecipeChoiceRepresentative(" in source,
            failures,
            f"{relative} must route RecipeChoice preview access through the compatibility bridge",
        )
        require(
            ".getItemStack()" not in source,
            failures,
            f"{relative} must not call deprecated RecipeChoice#getItemStack directly",
        )

    slimefun_stack = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/items/SlimefunItemStack.java",
    )
    require(
        "public void setType(Material type)" in slimefun_stack
        and "validate();\n        super.setType(type);" in slimefun_stack,
        failures,
        "SlimefunItemStack must retain its setType validation override",
    )

    wrapper = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/utils/itemstack/ItemStackWrapper.java",
    )
    require(
        "public void setType(Material type)" in wrapper
        and "throw new UnsupportedOperationException(ERROR_MESSAGE);" in wrapper,
        failures,
        "ItemStackWrapper must retain its immutable setType override",
    )

    codec = read(
        root,
        "src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/util/ItemStackDataCodec.java",
    )
    require(
        "new BukkitObjectInputStream(stream)" in codec,
        failures,
        "Historical Bukkit object-stream item data must remain readable",
    )
    require(
        "deserializeLegacyWithCompatibility" in codec,
        failures,
        "Legacy item deserialization compatibility path must remain present",
    )

    if failures:
        print("Legacy Bukkit compatibility shim verification: FAIL")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("Legacy Bukkit compatibility shim verification: PASS")
    print("Deprecated Bukkit usage remains confined to documented compatibility boundaries.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
