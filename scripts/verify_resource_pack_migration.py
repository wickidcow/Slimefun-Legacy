#!/usr/bin/env python3
"""Verify resource-pack recommendation and retired-URL migration invariants."""

from __future__ import annotations

import sys
from pathlib import Path


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Resource-pack migration verification failed: missing {label}: {needle}")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    config_source = (root / "src/main/java/io/github/thebusybiscuit/slimefun4/core/config/CuriositiesConfig.java").read_text(
        encoding="utf-8"
    )
    sender = (root / "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/ExternalResourcePackService.java").read_text(
        encoding="utf-8"
    )
    ownership = (root / "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/ResourcePackOwnershipMode.java").read_text(
        encoding="utf-8"
    )
    config_yaml = (root / "src/main/resources/configSFLAddons.yml").read_text(encoding="utf-8")
    docs = (root / "docs/RESOURCE_PACK.md").read_text(encoding="utf-8")
    tests = (root / "src/test/java/io/github/thebusybiscuit/slimefun4/core/services/TestExternalResourcePackService.java").read_text(
        encoding="utf-8"
    )

    official = "https://github.com/wickidcow/SFL_RP_Official/releases/latest/download/SlimefunLegacyRP.zip"
    retired_modrinth = "https://cdn.modrinth.com/data/TznkVJky/versions/nwij66MR/Slimefun-ResourcePack.zip"

    require(config_source, official, "official GitHub config URL")
    require(config_source, retired_modrinth, "retired Modrinth config URL")
    require(config_source, "migrateRetiredResourcePackUrl();", "startup retired-URL migration")
    require(
        config_source,
        'setValue(LEGACY_RESOURCE_PACK_ROOT + ".url", DEFAULT_RESOURCE_PACK_URL);',
        "persistent official URL rewrite",
    )
    require(
        config_source,
        'setValue(LEGACY_RESOURCE_PACK_ROOT + ".sha1", "");',
        "stale SHA-1 clearing on retired URL migration",
    )

    require(sender, retired_modrinth, "runtime retired Modrinth fallback")
    require(sender, "boolean normalizedLegacyUrl = !configuredUrl.equals(url);", "runtime legacy URL detection")
    require(
        sender,
        'String configuredHash = normalizedLegacyUrl ? "" : trim(config.getString(CONFIG_ROOT + "sha1"));',
        "runtime stale SHA-1 suppression",
    )

    require(config_source, "CURRENT_CONFIG_VERSION = 2", "versioned addon config schema")
    require(config_source, "migrateConfigVersion();", "one-time addon config migration")
    require(config_source, "writeResourcePackSafetyGuide", "resource-pack operator note injection")
    require(config_source, "writeResourcePackOwnershipGuide", "resource-pack ownership note injection")
    require(config_source, "existingVersion >= CURRENT_CONFIG_VERSION", "no per-startup config rewrite")
    require(config_source, "migrateConfigVersion();\n        migrateRetiredResourcePackUrl();\n        ensureResourcePackDefaults();", "schema migration must run before normal default persistence")
    require(config_source, '"+ "\\n  ownership-mode: auto" +"', "text-preserving ownership key insertion")
    require(config_source, 'Pattern.compile("(?m)^\\\\s{2}ownership-mode\\\\s*:")', "ownership-key duplicate guard")
    require(config_yaml, "config-version: 2", "bundled addon config version")
    require(config_yaml, "Slimefun Legacy resource-pack safety guide (config-version 1)", "versioned pack safety guide")
    require(config_yaml, "/sf doctor item-models enable-pack scan", "pack enable scan instruction")
    require(config_yaml, "/sf doctor item-models enable-pack confirm", "pack enable confirm instruction")
    require(config_yaml, "The sender toggle NEVER rewrites item-models.yml or stored ItemStacks.", "safe pack disable boundary")
    require(config_yaml, "/sf doctor item-models remove-resourcepack-texture-ids", "clear model cleanup instruction")

    require(config_yaml, "ownership-mode: auto", "backwards-compatible ownership default")
    require(config_yaml, "external = ItemsAdder/Oraxen/proxy/server pack sends a combined pack.", "external ownership guidance")
    require(config_yaml, "EXTERNAL is valid with Legacy sender OFF and Slimefun model mappings ON.", "combined-pack safety boundary")
    require(config_source, 'setDefaultValue(LEGACY_RESOURCE_PACK_ROOT + ".ownership-mode", ResourcePackOwnershipMode.AUTO.configValue())', "ownership default registration")
    require(config_source, "setResourcePackOwnershipAndSender", "atomic ownership/sender persistence")
    require(ownership, "AUTO", "AUTO ownership mode")
    require(ownership, "LEGACY", "LEGACY ownership mode")
    require(ownership, "EXTERNAL", "EXTERNAL ownership mode")
    require(ownership, "NONE", "NONE ownership mode")
    require(sender, "getOwnershipMode()", "ownership mode accessor")
    require(sender, "hasOwnershipContradiction()", "ownership contradiction detection")
    require(sender, "mode != ResourcePackOwnershipMode.EXTERNAL && mode != ResourcePackOwnershipMode.NONE", "external/none sender suppression")
    require(sender, "case EXTERNAL, NONE -> false;", "explicit external/none ownership disables Legacy sender")
    require(sender, "This never changes item-model mappings or stored Slimefun items.", "ownership mutation safety boundary")

    require(config_yaml, "RECOMMENDED (GitHub)", "recommended GitHub config comment")
    require(config_yaml, official, "recommended config URL")
    require(config_yaml, "retired Modrinth/default Legacy URLs are migrated here automatically", "config migration note")

    require(docs, "recommended", "resource-pack recommendation documentation")
    require(docs, "retired Modrinth", "retired Modrinth migration documentation")
    require(docs, "ownership-mode", "resource-pack ownership documentation")
    require(docs, "external", "external/combined pack ownership documentation")
    require(docs, "Legacy sender OFF + Slimefun mappings ON", "combined-pack mapping preservation documentation")
    require(tests, retired_modrinth, "retired Modrinth normalization regression test")
    require(tests, "testCustomResourcePackUrlIsPreserved", "custom URL preservation regression test")

    print("Resource-pack recommendation, ownership and retired-URL migration verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
