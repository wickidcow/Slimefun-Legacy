#!/usr/bin/env python3
"""Run every Slimefun Legacy source invariant from one command."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

PHASE1B_MARKER = "Slimefun Legacy 4.1.18 Phase 1B internal guide guards."
RELEASE_MARKER = "Slimefun Legacy 4.1.18 Item Doctor failure isolation."


def run_script(script: Path, root: Path, label: str | None = None) -> int:
    print(f"\n==> {label or script.name}", flush=True)
    result = subprocess.run([sys.executable, str(script), str(root)], cwd=root, check=False)
    return result.returncode


def ensure_guide_runtime_phase1b(root: Path) -> int:
    updater = root / "scripts" / "apply_guide_runtime_phase1b.py"
    verifier = root / "scripts" / "verify_guide_runtime_phase1b.py"
    classic = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/SurvivalSlimefunGuide.java"
    enhanced = root / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/EnhancedSurvivalSlimefunGuide.java"
    if not updater.is_file() or not verifier.is_file():
        return 0
    classic_applied = classic.is_file() and PHASE1B_MARKER in classic.read_text(encoding="utf-8")
    enhanced_applied = enhanced.is_file() and PHASE1B_MARKER in enhanced.read_text(encoding="utf-8")
    if classic_applied and enhanced_applied:
        return 0
    return run_script(updater, root, "apply_guide_runtime_phase1b.py")


def ensure_release_4_1_18(root: Path) -> int:
    updater = root / "scripts" / "apply_release_4_1_18.py"
    doctor = root / "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/ItemPresentationDoctor.java"
    changelog = root / "EVERYTHING_THAT_CHANGED.md"
    if not updater.is_file():
        return 0
    doctor_ready = doctor.is_file() and RELEASE_MARKER in doctor.read_text(encoding="utf-8")
    changelog_ready = changelog.is_file() and "# Slimefun Legacy 4.1.18" in changelog.read_text(encoding="utf-8")
    if doctor_ready and changelog_ready:
        return 0
    return run_script(updater, root, "apply_release_4_1_18.py")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    result = ensure_guide_runtime_phase1b(root)
    if result != 0:
        return result
    result = ensure_release_4_1_18(root)
    if result != 0:
        return result

    scripts = (
        "verify_english.py",
        "verify_chunk_load_threading.py",
        "check_api_annotations.py",
        "verify_api_compatibility.py",
        "verify_part2.py",
        "verify_part3.py",
        "verify_part4.py",
        "verify_folia_phase1.py",
        "verify_enhanced_guide.py",
        "verify_enhanced_guide_phase2.py",
        "verify_enhanced_guide_phase3.py",
        "verify_enhanced_guide_phase3_1.py",
        "verify_enhanced_guide_phase4.py",
        "verify_enhanced_guide_phase4_1a.py",
        "verify_enhanced_guide_phase4_1b.py",
        "verify_enhanced_guide_phase4_1c.py",
        "verify_enhanced_guide_phase4_1d.py",
        "verify_guide_runtime_phase1b.py",
        "verify_release_4_1_18.py",
        "verify_core_platform_phase1a.py",
        "verify_core_platform_phase1b.py",
        "verify_core_platform_phase1c.py",
        "verify_core_platform_phase1d.py",
        "verify_core_platform_phase1e.py",
        "verify_core_platform_phase1f.py",
        "verify_core_platform_phase1g.py",
        "verify_core_platform_phase1h.py",
        "verify_core_platform_phase1i.py",
        "verify_documentation_consolidation.py",
        "verify_upstream_health_gate.py",
        "verify_gugu_sync.py",
        "verify_paper_purpur_compat.py",
        "verify_core_correctness.py",
        "verify_compatibility_round2.py",
        "verify_compatibility_foundation.py",
        "check_dependency_boundaries.py",
    )
    for script_name in scripts:
        script = root / "scripts" / script_name
        result = run_script(script, root)
        if result != 0:
            return result

    print("\nAll Slimefun Legacy verification checks passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
