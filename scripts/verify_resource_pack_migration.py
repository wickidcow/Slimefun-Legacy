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

    require(config_source, "CURRENT_CONFIG_VERSION = 1", "versioned addon config schema")
    require(config_source, "migrateConfigVersion();", "one-time addon config migration")
    require(config_source, "writeResourcePackSafetyGuide", "resource-pack operator note injection")
    require(config_source, "existingVersion >= CURRENT_CONFIG_VERSION", "no per-startup config rewrite")
    require(config_yaml, "config-version: 1", "bundled addon config version")
    require(config_yaml, "Slimefun Legacy resource-pack safety guide (config-version 1)", "versioned pack safety guide")
    require(config_yaml, "/sf doctor item-models enable-pack scan", "pack enable scan instruction")
    require(config_yaml, "/sf doctor item-models enable-pack confirm", "pack enable confirm instruction")
    require(config_yaml, "The sender toggle NEVER rewrites item-models.yml or stored ItemStacks.", "safe pack disable boundary")
    require(config_yaml, "/sf doctor item-models rollback-v52", "v4.1.52 recovery instruction")

    require(config_yaml, "RECOMMENDED (GitHub)", "recommended GitHub config comment")
    require(config_yaml, official, "recommended config URL")
    require(config_yaml, "retired Modrinth/default Legacy URLs are migrated here automatically", "config migration note")

    require(docs, "recommended", "resource-pack recommendation documentation")
    require(docs, "retired Modrinth", "retired Modrinth migration documentation")
    require(tests, retired_modrinth, "retired Modrinth normalization regression test")
    require(tests, "testCustomResourcePackUrlIsPreserved", "custom URL preservation regression test")

    print("Resource-pack recommendation and retired-URL migration verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
