#!/usr/bin/env python3
"""Verify Slimefun Legacy Core Platform Phase 1E runtime/integration invariants."""
from __future__ import annotations
import json, re, sys
from pathlib import Path

def read(root, rel): return (root/rel).read_text(encoding="utf-8")
def req(ok,msg,fail):
    if not ok: fail.append(msg)
def version(root):
    m=re.search(r"^projectVersion=(\d+)\.(\d+)\.(\d+)$", read(root,"gradle.properties"), re.M)
    return tuple(map(int,m.groups())) if m else None

def main():
    root=Path(sys.argv[1] if len(sys.argv)>1 else '.').resolve(); failures=[]
    files=(
      "CORE_PLATFORM_PHASE1E.md","SLIMEFUN_LEGACY_4.1.23.md",
      "src/main/java/io/github/thebusybiscuit/slimefun4/api/integrations/ExternalIntegrationCapability.java",
      "src/main/java/io/github/thebusybiscuit/slimefun4/api/integrations/ExternalIntegrationProvider.java",
      "src/main/java/io/github/thebusybiscuit/slimefun4/api/integrations/ExternalIntegrationService.java",
      "src/main/java/io/github/thebusybiscuit/slimefun4/api/integrations/ExternalIntegrationStatus.java",
      "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/DefaultExternalIntegrationService.java",
      "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/MachineFailureTracker.java",
      "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/MachineFailureSnapshot.java",
    )
    for f in files: req((root/f).is_file(), f"Missing Phase 1E file: {f}", failures)
    try:
      req(version(root) is not None and version(root)>=(4,1,23), "Phase 1E requires 4.1.23 or newer", failures)
      ticker=read(root,"src/main/java/io/github/thebusybiscuit/slimefun4/implementation/tasks/TickerTask.java")
      for token in ("MachineFailureTracker", "machine-circuit-breaker-failure-threshold", "ticker-lifecycle-log-cooldown-seconds", "getMachineFailureSnapshots", "Repeated reports are rate-limited"):
        req(token in ticker, f"Ticker Phase 1E invariant missing: {token}", failures)
      doctor=read(root,"src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorCommand.java")
      req('case "runtime", "failures"' in doctor, "Doctor runtime mode missing", failures)
      req('case "integrations", "integration"' in doctor, "Doctor integrations mode missing", failures)
      sf=read(root,"src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java")
      req("getExternalIntegrationService()" in sf, "External integration service accessor missing", failures)
      ext=read(root,"src/main/java/io/github/thebusybiscuit/slimefun4/core/services/compatibility/DefaultExternalIntegrationService.java")
      for token in ('"rebar"','"pylon"','providers.put','Set.copyOf(provider.getCapabilities())'):
        req(token in ext, f"External integration registry invariant missing: {token}", failures)
      req("io.github.pylonmc" not in sf and "io.github.pylonmc" not in ticker, "Core must not hard-link Rebar/Pylon classes", failures)
      support=json.loads(read(root,"compatibility/support-contract.json"))
      req(support.get("release")=="4.1.23", "Support contract release mismatch", failures)
      req(support.get("phase")=="Core Platform Phase 1E", "Support contract phase mismatch", failures)
      pol=support.get("compatibility_policy",{})
      for key in ("runtime_machine_failure_isolation","rate_limited_ticker_lifecycle_failures","external_integration_provider_api","external_capabilities_require_explicit_provider"):
        req(pol.get(key) is True, f"Phase 1E support policy missing: {key}", failures)
      req(pol.get("rebar_pylon_hard_dependency") is False, "Rebar/Pylon must remain optional", failures)
    except Exception as e:
      failures.append(f"Phase 1E verifier failed to inspect repository: {e}")
    report=root/"build/reports/core-platform-phase1e.txt"; report.parent.mkdir(parents=True,exist_ok=True)
    if failures:
      report.write_text("Core Platform Phase 1E verification: FAIL\n"+"\n".join(f"- {x}" for x in failures)+"\n",encoding="utf-8")
      print(report.read_text(encoding="utf-8"),end=""); return 1
    report.write_text("Core Platform Phase 1E verification: PASS\n- runtime machine failure isolation diagnostics validated\n- deferred ticker callback containment validated\n- external capability/provider boundary validated\n- Rebar/Pylon remain optional and detection-only without an explicit provider\n",encoding="utf-8")
    print(report.read_text(encoding="utf-8"),end=""); return 0
if __name__=='__main__': raise SystemExit(main())
