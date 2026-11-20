#!/usr/bin/env python3
"""Verify the pinned official QuickShop-Hikari binary exposes Doctor's public API.

Strict static ABI gate. This does not install a plugin or claim a real shop,
database, transaction, server restart or authenticated historical item test.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import subprocess
from zipfile import ZipFile


# The Doctor uses getMethod on Shop for inherited public API methods.
# Verify both the Shop parent interfaces and their binary method descriptors.
CONTRACT = {
    "com.ghostchu.quickshop.api.QuickShopAPI": (
        ("getInstance", "()Lcom/ghostchu/quickshop/api/QuickShopAPI;"),
        ("getShopManager", "()Lcom/ghostchu/quickshop/api/shop/ShopManager;"),
    ),
    "com.ghostchu.quickshop.api.shop.ShopManager": (
        ("getShop", "(Lorg/bukkit/Location;)Lcom/ghostchu/quickshop/api/shop/Shop;"),
    ),
    "com.ghostchu.quickshop.api.shop.Shop": (),
    "com.ghostchu.quickshop.api.shop.meta.ShopMeta": (
        ("getItem", "()Lorg/bukkit/inventory/ItemStack;"),
        ("getPrice", "()D"),
        ("getOwner", "()Lcom/ghostchu/quickshop/api/obj/QUser;"),
    ),
    "com.ghostchu.quickshop.api.shop.inventory.ShopInventory": (
        ("matches", "(Lorg/bukkit/inventory/ItemStack;)Z"),
    ),
    "com.ghostchu.quickshop.api.shop.Locatable": (
        ("bukkitLocation", "()Lorg/bukkit/Location;"),
    ),
    "com.ghostchu.quickshop.api.obj.QUser": (
        ("serialize", "()Ljava/lang/String;"),
    ),
}
INHERITED = (
    "com.ghostchu.quickshop.api.shop.inventory.ShopInventory",
    "com.ghostchu.quickshop.api.shop.meta.ShopMeta",
    "com.ghostchu.quickshop.api.shop.Locatable",
)


def check_binary(jar: Path, javap: Path | str = "javap") -> None:
    if not jar.is_file():
        raise ValueError("QuickShop-Hikari binary is missing")
    with ZipFile(jar) as archive:
        manifest = archive.read("plugin.yml").decode("utf-8")
        if not re.search(r"(?m)^name:\s*QuickShop-Hikari\s*$", manifest):
            raise ValueError("Binary is not named QuickShop-Hikari in plugin.yml")
        for api in CONTRACT:
            filename = api.replace(".", "/") + ".class"
            if filename not in archive.namelist():
                raise ValueError("Missing public API class: " + api)

    for klass, required in CONTRACT.items():
        command = [str(javap), "-s", "-public", "-classpath", str(jar), klass]
        result = subprocess.run(command, check=True, capture_output=True, text=True, timeout=30)
        output = result.stdout
        if klass.endswith(".Shop"):
            # The inspection calls methods inherited from these interfaces.
            if any(parent not in output for parent in INHERITED):
                raise ValueError("QuickShop Shop public inheritance contract changed")
        for method, descriptor in required:
            # Check the public method declaration followed by its *own*
            # descriptor, not an unrelated overload on the same API.
            pattern = (r"(?m)^\s*public\s+[^\n;]+\b" + re.escape(method)
                       + r"\s*\([^\n]*\)\s*;\s*\n\s*descriptor:\s*" + re.escape(descriptor) + r"\s*$")
            if not re.search(pattern, output):
                raise ValueError(f"QuickShop API signature mismatch: {klass}.{method} {descriptor}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--javap", default="javap")
    args = parser.parse_args()
    check_binary(args.jar, args.javap)
    # Artifact identity is pinned by the caller's SHA-256 check; never
    # describe a different installed QuickShop build as independently tested.
    version = re.fullmatch(r"QuickShop-Hikari-(6\\.3\\.0\\.[34])\\.jar", args.jar.name)
    if version is None:
        raise ValueError("Unrecognized QuickShop-Hikari release filename")
    print("SFL_QUICKSHOP_HIKARI_" + version.group(1).replace(".", "_") + "_PUBLIC_ABI_PASS", flush=True)


if __name__ == "__main__":
    main()
