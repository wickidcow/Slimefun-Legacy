#!/usr/bin/env python3
"""Verify Paper/Purpur API modernization while retaining addon bridges."""

from __future__ import annotations

import sys
from pathlib import Path


def require(condition: bool, message: str, failures: list[str]) -> None:
    if not condition:
        failures.append(message)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []

    sources = root / "src/main/java"
    all_java = "\n".join(path.read_text(encoding="utf-8") for path in sources.rglob("*.java"))

    require(
        "new EntityDamageByEntityEvent" not in all_java,
        "Production code still constructs Paper's internal EntityDamageByEntityEvent directly.",
        failures,
    )

    worldedit = (sources / "io/github/thebusybiscuit/slimefun4/integrations/WorldEditIntegration.java").read_text(
        encoding="utf-8"
    )
    require("pos.getBlockX()" not in worldedit, "WorldEdit integration still uses getBlockX().", failures)
    require("pos.getBlockY()" not in worldedit, "WorldEdit integration still uses getBlockY().", failures)
    require("pos.getBlockZ()" not in worldedit, "WorldEdit integration still uses getBlockZ().", failures)
    require("pos.getX()" in worldedit, "WorldEdit integration is missing modern coordinate accessors.", failures)

    legacy_config = (
        sources / "me/mrCookieSlime/CSCoreLibPlugin/Configuration/Config.java"
    ).read_text(encoding="utf-8")
    require(
        "@Deprecated(forRemoval = true)" not in legacy_config,
        "Legacy Config is still marked for removal even though addon bridges are retained.",
        failures,
    )

    block_ticker = (
        sources / "me/mrCookieSlime/Slimefun/Objects/handlers/BlockTicker.java"
    ).read_text(encoding="utf-8")
    require("Config data" in block_ticker, "BlockTicker Config bridge was removed.", failures)
    require("ASlimefunDataContainer data" in block_ticker, "BlockTicker modern overload is missing.", failures)

    energy_provider = (
        sources / "io/github/thebusybiscuit/slimefun4/core/attributes/EnergyNetProvider.java"
    ).read_text(encoding="utf-8")
    require("Config data" in energy_provider, "EnergyNetProvider Config bridges were removed.", failures)
    require(
        "ASlimefunDataContainer data" in energy_provider,
        "EnergyNetProvider modern overloads are missing.",
        failures,
    )

    sql_constants = (
        sources / "com/xzavier0722/mc/plugin/slimefun4/storage/adapter/sqlcommon/SqlConstants.java"
    ).read_text(encoding="utf-8")
    require(
        "@Deprecated\n    String TABLE_NAME_TABLE_INFORMATION" in sql_constants,
        "Deprecated SQL table constant is missing its annotation.",
        failures,
    )
    require(
        "@Deprecated\n    String FIELD_TABLE_VERSION" in sql_constants,
        "Deprecated SQL field constant is missing its annotation.",
        failures,
    )

    gradle = (root / "build.gradle.kts").read_text(encoding="utf-8")
    require(
        '--enable-native-access=ALL-UNNAMED' in gradle,
        "Gradle storage tests are missing Java 25 native-access configuration.",
        failures,
    )

    if failures:
        print("Compatibility Maintenance Round 2 verification failed:")
        for failure in failures:
            print(f"  - {failure}")
        return 1

    print("Compatibility Maintenance Round 2 verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
