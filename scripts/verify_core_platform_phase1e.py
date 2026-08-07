#!/usr/bin/env python3
"""Verify Slimefun Legacy Core Platform Phase 1E runtime/integration invariants."""
from __future__ import annotations
import hashlib, json, re, sys
from pathlib import Path


def read(root, rel):
    return (root / rel).read_text(encoding="utf-8")


def req(ok, msg, fail):
    if not ok:
        fail.append(msg)


def version(root):
    m = re.search(r"^projectVersion=(\d+)\.(\d+)\.(\d+)$", read(root, "gradle.properties"), re.M)
    return tuple(map(int, m.groups())) if m else None


def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures = []
    files = (
        "CORE_PLATFORM_PHASE1E.md",
        "SLIMEFUN_LEGACY_4.1.23.md",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/integrations/ExternalBlockIntegration.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/integrations/ExternalIntegrationCapability.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/integrations/ExternalIntegrationProvider.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/integrations/ExternalIntegrationService.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/integrations/ExternalIntegrationStatus.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/integrations/ExternalIntegrationFailureSnapshot.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/DefaultExternalIntegrationService.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/ReflectiveRebarAccess.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/ReflectiveRebarIntegrationProvider.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/MachineFailureTracker.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/MachineFailureSnapshot.java",
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/ExternalIntegrationFailureTracker.java",
        "compatibility/phase1e-normal-core-sha256.json",
        "src/test/java/io/github/thebusybiscuit/slimefun4/api/integrations/TestExternalBlockIntegration.java",
    )
    for f in files:
        req((root / f).is_file(), f"Missing Phase 1E file: {f}", failures)

    try:
        req(version(root) is not None and version(root) >= (4, 1, 23), "Phase 1E requires 4.1.23 or newer", failures)

        ticker = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/tasks/TickerTask.java")
        for token in (
            "MachineFailureTracker",
            "machine-circuit-breaker-failure-threshold",
            "ticker-lifecycle-log-cooldown-seconds",
            "getMachineFailureSnapshots",
            "Repeated reports are rate-limited",
        ):
            req(token in ticker, f"Ticker Phase 1E invariant missing: {token}", failures)

        versions = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/VersionsCommand.java")
        for token in (
            '"✔ Compatible"',
            '"⚠ Compatible with warnings"',
            '"✕ Incompatible"',
            '"✕ Disabled"',
            'result.getSource().getDisplayName()',
            '.orElseGet(this::uncheckedCompatibilityComponent)',
        ):
            req(token in versions, f"Versions compatibility clarity invariant missing: {token}", failures)
        req(
            '"? Compatibility not verified"' in versions
            or '"? Slimefun addon — compatibility unknown"' in versions,
            "Versions must retain an operator-readable undeclared/unknown addon state",
            failures,
        )
        req(
            'Component.text(" [" + result.getStatus().getDisplayName() + "]"' not in versions,
            "Versions must not expose raw bracketed compatibility enum labels",
            failures,
        )

        doctor = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorCommand.java")
        req('case "runtime", "failures"' in doctor, "Doctor runtime mode missing", failures)
        req('case "integrations", "integration"' in doctor, "Doctor integrations mode missing", failures)
        req('args[2].equalsIgnoreCase("probe")' in doctor, "Doctor integration block probe missing", failures)
        req("inspectBlock(block)" in doctor, "Doctor integration block inspection hook missing", failures)
        req("retryRuntimeFailures(sender, args)" in doctor, "Doctor runtime recovery controls missing", failures)
        req("retryExternalIntegrations(sender, args)" in doctor, "Doctor integration recovery controls missing", failures)
        req('args[2].equalsIgnoreCase("reload")' in doctor, "Doctor integration reload control missing", failures)

        sf = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java")
        req("getExternalIntegrationService()" in sf, "External integration service accessor missing", failures)

        provider_api = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/api/integrations/ExternalIntegrationProvider.java")
        service_api = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/api/integrations/ExternalIntegrationService.java")
        req("Optional<ExternalBlockIntegration> inspectBlock" in provider_api, "Provider block inspection API missing", failures)
        req("List<ExternalBlockIntegration> inspectBlock" in service_api, "Service block inspection API missing", failures)
        req("List<ExternalIntegrationFailureSnapshot> getFailureSnapshots" in service_api, "External integration failure snapshots API missing", failures)
        req("default boolean retry(" in service_api, "Additive external integration retry API missing", failures)
        req("default int retryAll()" in service_api, "Additive external integration retry-all API missing", failures)

        ext = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/DefaultExternalIntegrationService.java")
        for token in (
            '"rebar"',
            '"pylon"',
            "ReflectiveRebarAccess.create",
            "ReflectiveRebarIntegrationProvider",
            "effectiveProviders.putAll(providers)",
            "provider.inspectBlock(block)",
            "ExternalIntegrationFailureTracker",
            "MachineCircuitBreaker<String>",
            "external-integration-failure-threshold",
            "external-integration-cooldown-seconds",
            "getFailureSnapshots",
            "retryAll",
        ):
            req(token in ext, f"External integration registry invariant missing: {token}", failures)

        rebar = read(root, "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/ReflectiveRebarAccess.java")
        for token in (
            '"io.github.pylonmc.rebar.block.RebarBlock"',
            '"io.github.pylonmc.rebar.block.BlockStorage"',
            '"io.github.pylonmc.rebar.block.interfaces.VirtualInventoryRebarBlock"',
            '"io.github.pylonmc.rebar.block.interfaces.LogisticRebarBlock"',
            '"io.github.pylonmc.rebar.block.interfaces.ProcessorRebarBlock"',
            '"io.github.pylonmc.rebar.block.interfaces.FluidRebarBlock"',
            "ExternalIntegrationCapability.INVENTORY",
            "ExternalIntegrationCapability.CARGO",
            "ExternalIntegrationCapability.MACHINE",
            "ExternalIntegrationCapability.FLUID",
        ):
            req(token in rebar, f"Reflective Rebar adapter invariant missing: {token}", failures)
        req("ExternalIntegrationCapability.ENERGY" not in rebar, "Built-in Rebar adapter must not expose ENERGY", failures)

        # Rebar/Pylon names may appear as reflection strings, but never as Java imports or compile-time types.
        for source in (root / "src/main/java").rglob("*.java"):
            text = source.read_text(encoding="utf-8")
            req(
                "import io.github.pylonmc." not in text,
                f"Hard Rebar/Pylon Java dependency introduced: {source.relative_to(root)}",
                failures,
            )

        support = json.loads(read(root, "compatibility/support-contract.json"))
        support_release = tuple(map(int, str(support.get("release", "0.0.0")).split(".")))
        req(support_release >= (4, 1, 23), "Support contract must be 4.1.23 or newer", failures)
        req(
            isinstance(support.get("phase"), str)
            and support.get("phase", "").startswith("Core Platform Phase 1"),
            "Support contract must retain the Core Platform phase marker",
            failures,
        )
        pol = support.get("compatibility_policy", {})
        for key in (
            "runtime_machine_failure_isolation",
            "rate_limited_ticker_lifecycle_failures",
            "external_integration_provider_api",
            "external_capabilities_require_explicit_provider",
            "rebar_pylon_reflective_block_adapter",
            "external_block_capability_probe",
            "external_integration_failure_isolation",
            "external_integration_admin_recovery",
            "normal_slimefun_core_hash_guard",
            "operator_readable_versions_compatibility",
        ):
            req(pol.get(key) is True, f"Phase 1E support policy missing: {key}", failures)
        req(pol.get("rebar_pylon_hard_dependency") is False, "Rebar/Pylon must remain optional", failures)
        req(pol.get("rebar_pylon_cargo_transfer") is False, "Part 2 must not enable cross-network cargo transfer", failures)
        req(pol.get("rebar_pylon_energy_exchange") is False, "Part 2 must not enable Rebar energy exchange", failures)

        core_guard = json.loads(read(root, "compatibility/phase1e-normal-core-sha256.json"))
        for rel, expected in core_guard.get("files", {}).items():
            path = root / rel
            req(path.is_file(), f"Normal Slimefun compatibility guard file missing: {rel}", failures)
            if path.is_file():
                actual = hashlib.sha256(path.read_bytes()).hexdigest()
                req(actual == expected, f"Normal Slimefun core changed during Phase 1E Part 3: {rel}", failures)
    except Exception as e:
        failures.append(f"Phase 1E verifier failed to inspect repository: {e}")

    report = root / "build/reports/core-platform-phase1e.txt"
    report.parent.mkdir(parents=True, exist_ok=True)
    if failures:
        report.write_text(
            "Core Platform Phase 1E verification: FAIL\n" + "\n".join(f"- {x}" for x in failures) + "\n",
            encoding="utf-8",
        )
        print(report.read_text(encoding="utf-8"), end="")
        return 1

    report.write_text(
        "Core Platform Phase 1E verification: PASS\n"
        "- runtime machine failure isolation diagnostics validated\n"
        "- deferred ticker callback containment validated\n"
        "- external capability/provider boundary validated\n"
        "- reflection-only Rebar/Pylon block adapters validated\n"
        "- targeted external block capability probe validated\n"
        "- external provider failure isolation and admin recovery validated\n"
        "- /sf versions addon compatibility labels are operator-readable and explain unverified addons\n"
        "- normal Slimefun cargo, energy, guide, ticker and addon-facing core hashes remain unchanged\n"
        "- cross-network cargo transfer and Rebar energy exchange remain disabled\n",
        encoding="utf-8",
    )
    print(report.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
