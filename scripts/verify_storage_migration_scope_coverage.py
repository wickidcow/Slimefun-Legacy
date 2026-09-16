#!/usr/bin/env python3
"""Guard Doctor coverage for structured persisted Slimefun identities and ItemStacks."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parents[1]).resolve()
ERRORS: list[str] = []

IDENTITY_SCOPES = {"BLOCK_RECORD", "UNIVERSAL_RECORD"}
INVENTORY_SCOPES = {"BACKPACK_INVENTORY", "BLOCK_INVENTORY", "UNIVERSAL_INVENTORY"}
OPAQUE_DATA_SCOPES = {"BLOCK_DATA", "CHUNK_DATA", "UNIVERSAL_DATA"}


def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)


def reject(condition: bool, message: str) -> None:
    if condition:
        ERRORS.append(message)


def read(relative: str) -> str:
    path = ROOT / relative
    require(path.is_file(), f"missing required file: {relative}")
    return path.read_text(encoding="utf-8") if path.is_file() else ""


def method_bodies(source: str) -> list[tuple[str, str]]:
    """Return Java create*Table method bodies without depending on formatting."""
    methods: list[tuple[str, str]] = []
    pattern = re.compile(r"\b(?:private|protected|public)\s+void\s+(create\w*Table)\s*\([^)]*\)\s*\{")
    for match in pattern.finditer(source):
        depth = 1
        index = match.end()
        while index < len(source) and depth:
            char = source[index]
            if char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
            index += 1
        require(depth == 0, f"could not parse Java method body for {match.group(1)}")
        if depth == 0:
            methods.append((match.group(1), source[match.end(): index - 1]))
    return methods


def table_variable_scopes(source: str) -> dict[str, str]:
    """Resolve MySQL/PostgreSQL cached table-name variables back to their DataScope."""
    return {
        variable: scope
        for variable, scope in re.findall(
            r"\b([A-Za-z][A-Za-z0-9_]*)\s*=\s*SqlUtils\.mapTable\(DataScope\.([A-Z_]+)", source
        )
    }


def primary_table_scope(body: str, table_variables: dict[str, str]) -> str | None:
    local = re.search(r"\bvar\s+table\s*=\s*SqlUtils\.mapTable\(DataScope\.([A-Z_]+)\)", body)
    if local:
        return local.group(1)

    direct = re.search(r"SqlUtils\.mapTable\(DataScope\.([A-Z_]+)\)", body)
    if direct:
        return direct.group(1)

    # Prefix-capable adapters cache mapped table names in fields during initStorage(). The primary table name is
    # the first such variable used by a create*Table method; later occurrences may be foreign-key references.
    matches = [(body.find(variable), scope) for variable, scope in table_variables.items() if variable in body]
    matches = [match for match in matches if match[0] >= 0]
    return min(matches, default=(0, None), key=lambda match: match[0])[1]


def scopes_using_field(source: str, field_constant: str, adapter: str) -> set[str]:
    result: set[str] = set()
    table_variables = table_variable_scopes(source)
    for method, body in method_bodies(source):
        if field_constant not in body:
            continue
        scope = primary_table_scope(body, table_variables)
        require(scope is not None, f"{adapter}: could not resolve table scope for {method} using {field_constant}")
        if scope is not None:
            result.add(scope)
    return result


data_scope = read("src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/common/DataScope.java")
field_key = read("src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/common/FieldKey.java")
block_ids = read("src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/BlockIdStorageMaintenance.java")
stored_items = read("src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/PersistedItemStorageMaintenance.java")
backpack_items = read("src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/PersistedBackpackItemStorageMaintenance.java")

adapters = {
    "SQLite": read("src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/adapter/sqlite/SqliteAdapter.java"),
    "MySQL": read("src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/adapter/mysql/MysqlAdapter.java"),
    "PostgreSQL": read("src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/adapter/postgresql/PostgreSqlAdapter.java"),
}

# The schema has one structured Slimefun identity field and one serialized ItemStack field.
# Opaque DATA_VALUE text can contain addon-owned state and must never be interpreted as an Item ID by core.
require("SLIMEFUN_ID" in field_key, "FieldKey.SLIMEFUN_ID is missing; update migration coverage deliberately")
require("INVENTORY_ITEM" in field_key, "FieldKey.INVENTORY_ITEM is missing; update migration coverage deliberately")
require("DATA_VALUE" in field_key, "FieldKey.DATA_VALUE is missing; review opaque data migration assumptions")

for scope in IDENTITY_SCOPES | INVENTORY_SCOPES | OPAQUE_DATA_SCOPES:
    require(scope in data_scope, f"DataScope.{scope} is missing; update storage migration coverage deliberately")

for adapter, source in adapters.items():
    identity_scopes = scopes_using_field(source, "FIELD_SLIMEFUN_ID", adapter)
    inventory_scopes = scopes_using_field(source, "FIELD_INVENTORY_ITEM", adapter)
    opaque_scopes = scopes_using_field(source, "FIELD_DATA_VALUE", adapter)

    require(
        identity_scopes == IDENTITY_SCOPES,
        f"{adapter}: structured Slimefun-ID scopes changed: expected {sorted(IDENTITY_SCOPES)}, got {sorted(identity_scopes)}",
    )
    require(
        inventory_scopes == INVENTORY_SCOPES,
        f"{adapter}: serialized ItemStack scopes changed: expected {sorted(INVENTORY_SCOPES)}, got {sorted(inventory_scopes)}",
    )
    require(
        opaque_scopes == OPAQUE_DATA_SCOPES,
        f"{adapter}: opaque DATA_VALUE scopes changed: expected {sorted(OPAQUE_DATA_SCOPES)}, got {sorted(opaque_scopes)}",
    )

# Structured identity migration coverage: both tables that persist sf_id are handled together.
for scope in IDENTITY_SCOPES:
    require(
        f"DataScope.{scope}" in block_ids,
        f"BlockIdStorageMaintenance must explicitly cover DataScope.{scope}",
    )
require("FieldKey.SLIMEFUN_ID" in block_ids, "persisted identity maintenance must read/write FieldKey.SLIMEFUN_ID")

# Serialized machine/universal ItemStacks are handled by the unloaded persisted-item lane.
for scope in {"BLOCK_INVENTORY", "UNIVERSAL_INVENTORY"}:
    require(
        f"DataScope.{scope}" in stored_items,
        f"PersistedItemStorageMaintenance must explicitly cover DataScope.{scope}",
    )
require("FieldKey.INVENTORY_ITEM" in stored_items, "persisted machine-item maintenance must cover INVENTORY_ITEM")

# Backpack ItemStacks require their separate cache-aware profile-storage lane.
require(
    "DataScope.BACKPACK_INVENTORY" in backpack_items,
    "PersistedBackpackItemStorageMaintenance must explicitly cover BACKPACK_INVENTORY",
)
require("FieldKey.INVENTORY_ITEM" in backpack_items, "persisted backpack maintenance must cover INVENTORY_ITEM")

# Generic core migration must never reinterpret arbitrary addon KV text as Slimefun identity/item data.
for name, source in {
    "BlockIdStorageMaintenance": block_ids,
    "PersistedItemStorageMaintenance": stored_items,
    "PersistedBackpackItemStorageMaintenance": backpack_items,
}.items():
    reject("FieldKey.DATA_VALUE" in source, f"{name} must not rewrite opaque DATA_VALUE payloads generically")
    for scope in OPAQUE_DATA_SCOPES:
        reject(f"DataScope.{scope}" in source, f"{name} must not treat opaque DataScope.{scope} as Item-ID storage")

if ERRORS:
    print("Structured storage migration coverage verification failed:")
    for error in ERRORS:
        print(" -", error)
    raise SystemExit(1)

print("Structured storage migration coverage verification passed.")
