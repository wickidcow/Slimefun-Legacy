#!/usr/bin/env python3
"""Validate the assembled Slimefun Legacy GuizhanLib compatibility bridge JAR."""

from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path


REQUIRED_PUBLIC_CLASSES = (
    "net/guizhanss/guizhanlib/common/Cooldown.class",
    "net/guizhanss/guizhanlib/minecraft/utils/ItemUtil.class",
    "net/guizhanss/guizhanlib/slimefun/addon/AbstractAddon.class",
    "net/guizhanss/guizhanlib/slimefun/machines/MenuBlock.class",
    "net/guizhanss/guizhanlib/updater/GuizhanBuildsUpdater.class",
    "net/guizhanss/minecraft/guizhanlib/gugu/minecraft/ChatColors.class",
    "net/guizhanss/minecraft/guizhanlib/utils/NamespacedKeyUtils.class",
    "net/guizhanss/minecraft/guizhanlib/updater/GuizhanUpdater.class",
    "net/guizhanss/guizhanlibplugin/updater/GuizhanUpdater.class",
)

REQUIRED_ISOLATED_CLASSES = (
    "io/github/thebusybiscuit/slimefun4/libraries/guizhanlib/minecraft/utils/ItemUtil.class",
    "io/github/thebusybiscuit/slimefun4/libraries/guizhanlib/updater/GuizhanBuildsUpdater.class",
)

REQUIRED_RESOURCES = (
    "META-INF/LICENSES/GuizhanLibPlugin-LICENSE.txt",
    "META-INF/LICENSES/GuizhanLibPlugin-NOTICE.txt",
    "META-INF/LICENSES/Libby-LICENSE.txt",
)

FORBIDDEN_LINKAGE_TOKENS = (
    b"net/guizhanss/minecraft/guizhanlib/GuizhanLib",
    b"net/guizhanss/minecraft/guizhanlib/config/ConfigManager",
)

FORBIDDEN_CLASSES = (
    # The bridge is intentionally not a fake GuizhanLibPlugin JavaPlugin implementation.
    "net/guizhanss/minecraft/guizhanlib/GuizhanLib.class",
    "net/guizhanss/minecraft/guizhanlib/config/ConfigManager.class",
    "net/guizhanss/minecraft/guizhanlib/gugu/localization/LocalizationLoader.class",
)


def fail(message: str) -> None:
    print(f"FAIL - {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("jar", type=Path, help="assembled Slimefun plugin JAR")
    args = parser.parse_args()

    jar = args.jar
    if not jar.is_file():
        fail(f"JAR does not exist: {jar}")

    try:
        with zipfile.ZipFile(jar) as archive:
            names = set(archive.namelist())

            for entry in REQUIRED_PUBLIC_CLASSES:
                if entry not in names:
                    fail(f"missing public GuizhanLib bridge class: {entry}")

            for entry in REQUIRED_ISOLATED_CLASSES:
                if entry not in names:
                    fail(f"missing Slimefun-private relocated GuizhanLib class: {entry}")

            for entry in REQUIRED_RESOURCES:
                if entry not in names:
                    fail(f"missing GuizhanLib attribution/license resource: {entry}")

            for entry in FORBIDDEN_CLASSES:
                if entry in names:
                    fail(f"forbidden GuizhanLibPlugin implementation class was bundled: {entry}")

            bridge_classes = sorted(
                name
                for name in names
                if name.endswith(".class")
                and (
                    name.startswith("net/guizhanss/guizhanlib/")
                    or name.startswith("net/guizhanss/minecraft/guizhanlib/gugu/")
                    or name.startswith("net/guizhanss/minecraft/guizhanlib/utils/")
                    or name == "net/guizhanss/minecraft/guizhanlib/updater/GuizhanUpdater.class"
                    or name == "net/guizhanss/guizhanlibplugin/updater/GuizhanUpdater.class"
                )
            )
            for entry in bridge_classes:
                data = archive.read(entry)
                for token in FORBIDDEN_LINKAGE_TOKENS:
                    if token in data:
                        fail(
                            "bridge class links to excluded GuizhanLibPlugin singleton/config implementation: "
                            f"{entry}"
                        )

            plugin_yml = archive.read("plugin.yml").decode("utf-8")
            if "provides:" not in plugin_yml or "- GuizhanLibPlugin" not in plugin_yml:
                fail("plugin.yml does not provide the GuizhanLibPlugin compatibility alias")

            public_count = sum(name.startswith("net/guizhanss/guizhanlib/") and name.endswith(".class") for name in names)
            legacy_count = sum(
                (
                    name.startswith("net/guizhanss/minecraft/guizhanlib/gugu/")
                    or name.startswith("net/guizhanss/minecraft/guizhanlib/utils/")
                    or name == "net/guizhanss/minecraft/guizhanlib/updater/GuizhanUpdater.class"
                    or name == "net/guizhanss/guizhanlibplugin/updater/GuizhanUpdater.class"
                )
                and name.endswith(".class")
                for name in names
            )
            isolated_count = sum(
                name.startswith("io/github/thebusybiscuit/slimefun4/libraries/guizhanlib/")
                and name.endswith(".class")
                for name in names
            )

            if public_count < 20:
                fail(f"public GuizhanLib bridge is unexpectedly small ({public_count} classes)")
            if isolated_count < 2:
                fail(f"private relocated GuizhanLib copy is unexpectedly small ({isolated_count} classes)")

    except zipfile.BadZipFile as exc:
        fail(f"not a valid JAR/ZIP: {exc}")

    print("GuizhanLib compatibility bridge JAR verification passed.")
    print(f"Public GuizhanLib classes: {public_count}")
    print(f"Legacy helper shim classes: {legacy_count}")
    print(f"Slimefun-private relocated GuizhanLib classes: {isolated_count}")
    print("GuizhanLibPlugin concrete JavaPlugin implementation: excluded")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
