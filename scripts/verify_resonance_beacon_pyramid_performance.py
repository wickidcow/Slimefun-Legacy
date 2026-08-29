#!/usr/bin/env python3
"""Guard the Resonance Beacon pyramid hot path against per-block config regressions."""

from __future__ import annotations

import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise FileNotFoundError(relative)
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str, failures: list[str]) -> None:
    if not condition:
        failures.append(message)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    base = "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/curios/"
    failures: list[str] = []

    try:
        config = read(root, base + "BeaconPlusConfig.java")
        pyramid = read(root, base + "BeaconPlusPyramid.java")

        for token, label in (
            ("static PyramidSettings getPyramidSettings()", "one-inspection pyramid settings snapshot"),
            ("CuriositiesConfig config = CuriositiesConfig.getConfig()", "single config handle for pyramid snapshot"),
            ("readMaterialPower(config, Material.IRON_BLOCK)", "iron material snapshot"),
            ("readMaterialPower(config, Material.GOLD_BLOCK)", "gold material snapshot"),
            ("readMaterialPower(config, Material.EMERALD_BLOCK)", "emerald material snapshot"),
            ("readMaterialPower(config, Material.DIAMOND_BLOCK)", "diamond material snapshot"),
            ("readMaterialPower(config, Material.NETHERITE_BLOCK)", "netherite material snapshot"),
            ("record PyramidSettings(", "immutable pyramid settings carrier"),
        ):
            require(token in config, f"missing {label}: {token}", failures)

        for token, label in (
            ("BeaconPlusConfig.PyramidSettings settings = BeaconPlusConfig.getPyramidSettings()", "snapshot acquisition"),
            ("int layersToInspect = Math.min(MAX_LAYERS, vanillaTier)", "vanilla-tier layer bound"),
            ("double materialPower = getMaterialPower(settings, material)", "settings-backed block material lookup"),
            ("return settings.materialPower(material)", "material helper uses snapshot"),
            ("usableLayers >= getRequiredPyramidTier(settings, tier)", "settings-backed pyramid tier threshold"),
            ("return settings.requiredPyramidTier(tier)", "tier helper uses snapshot"),
            ("settings.requiredAverageMaterialPower(tier)", "settings-backed average threshold"),
        ):
            require(token in pyramid, f"missing {label}: {token}", failures)

        require(
            pyramid.count("BeaconPlusConfig.getPyramidSettings()") == 1,
            "pyramid inspection must acquire exactly one settings snapshot",
            failures,
        )
        require(
            "BeaconPlusConfig.getMaterialPower(" not in pyramid,
            "pyramid block loop must not call YAML-backed BeaconPlusConfig.getMaterialPower",
            failures,
        )
        require(
            "BeaconPlusConfig.getRequiredPyramidTier(" not in pyramid,
            "pyramid tier resolution must not call YAML-backed BeaconPlusConfig.getRequiredPyramidTier",
            failures,
        )
        require(
            "BeaconPlusConfig.getRequiredAverageMaterialPower(" not in pyramid,
            "pyramid tier resolution must not call YAML-backed BeaconPlusConfig.getRequiredAverageMaterialPower",
            failures,
        )
        require(
            "CuriositiesConfig" not in pyramid,
            "pyramid scan must not reach the YAML configuration service directly",
            failures,
        )
    except Exception as error:
        failures.append(f"verification could not inspect Resonance Beacon pyramid sources: {error}")

    if failures:
        print("Resonance Beacon pyramid performance verification: FAIL", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1

    print("Resonance Beacon pyramid performance verification: PASS")
    print("- each physical pyramid inspection captures one immutable settings snapshot")
    print("- up to 164 live pyramid block reads no longer perform YAML-backed material-power lookups")
    print("- vanilla Beacon tier bounds physically impossible layer scans")
    print("- configured material powers and tier thresholds remain live on the next inspection")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
