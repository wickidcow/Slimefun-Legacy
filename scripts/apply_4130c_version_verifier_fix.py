#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FILES = [
    "scripts/verify_phase1k_release_readiness.py",
    "scripts/verify_core_platform_phase1l.py",
    "scripts/verify_phase1l_release_artifact.py",
    "scripts/verify_core_platform_phase1l_part3.py",
    "scripts/verify_core_platform_phase1l_part4.py",
    "scripts/verify_release_artifact.py",
]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel: str, text: str) -> None:
    (ROOT / rel).write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old in text:
        return text.replace(old, new, 1)
    if new in text:
        return text
    raise SystemExit(f"Could not locate {label}")


# All release verifiers must read maintenance suffixes such as 4.1.30C.
for rel in FILES:
    text = read(rel)
    text = text.replace(
        're.search(r"^projectVersion=(\\d+\\.\\d+\\.\\d+)$", read(root, "gradle.properties"), re.M)',
        're.search(r"^projectVersion=(\\d+\\.\\d+\\.\\d+[A-Z]?)$", read(root, "gradle.properties"), re.M)',
    )
    write(rel, text)

# Phase 1K: compare compatibility registries to the numeric release line while
# preserving the full maintenance version for the built plugin.
rel = "scripts/verify_phase1k_release_readiness.py"
text = read(rel)
text = replace_once(
    text,
    '''def version_tuple(version: str) -> tuple[int, int, int]:
    return tuple(map(int, version.split(".")))
''',
    '''def release_line(version: str) -> str:
    match = re.match(r"^(\\d+\\.\\d+\\.\\d+)", version)
    return match.group(1) if match else ""


def version_tuple(version: str) -> tuple[int, int, int]:
    return tuple(map(int, release_line(version).split(".")))
''',
    "Phase 1K version helpers",
)
text = text.replace(
    'require(candidate.get("version") == version, "Baseline candidate must match projectVersion", failures)',
    'require(candidate.get("version") == release_line(version), "Baseline candidate must match the project release line", failures)',
)
text = text.replace(
    'require(data.get("release") == version, f"{relative} release must match projectVersion", failures)',
    'require(data.get("release") == release_line(version), f"{relative} release must match the project release line", failures)',
)
write(rel, text)

# Phase 1L core verifier: 4.1.30C is a maintenance build on the 4.1.30 release line.
rel = "scripts/verify_core_platform_phase1l.py"
text = read(rel)
text = replace_once(
    text,
    '''def main() -> int:
''',
    '''def release_line(version: str) -> str:
    match = re.match(r"^(\\d+\\.\\d+\\.\\d+)", version)
    return match.group(1) if match else ""


def main() -> int:
''',
    "Phase 1L release-line helper",
)
text = text.replace(
    'require(version == CURRENT_VERSION, f"Phase 1L projectVersion must be {CURRENT_VERSION}, got {version or \'<missing>\'}", failures)',
    'require(release_line(version) == CURRENT_VERSION, f"Phase 1L projectVersion must be on {CURRENT_VERSION}, got {version or \'<missing>\'}", failures)',
)
write(rel, text)

# Part 2 reproducible artifact verifier.
rel = "scripts/verify_phase1l_release_artifact.py"
text = read(rel)
text = replace_once(
    text,
    '''def main() -> int:
''',
    '''def release_line(version: str) -> str:
    match = re.match(r"^(\\d+\\.\\d+\\.\\d+)", version)
    return match.group(1) if match else ""


def main() -> int:
''',
    "Phase 1L Part 2 release-line helper",
)
text = text.replace(
    'require(version == CURRENT_VERSION, f"Part 2 requires projectVersion {CURRENT_VERSION}", failures)',
    'require(release_line(version) == CURRENT_VERSION, f"Part 2 requires the {CURRENT_VERSION} release line", failures)',
)
write(rel, text)

# Part 3 upgrade diagnostics verifier.
rel = "scripts/verify_core_platform_phase1l_part3.py"
text = read(rel)
text = replace_once(
    text,
    '''def main() -> int:
''',
    '''def release_line(version: str) -> str:
    match = re.match(r"^(\\d+\\.\\d+\\.\\d+)", version)
    return match.group(1) if match else ""


def main() -> int:
''',
    "Phase 1L Part 3 release-line helper",
)
text = text.replace(
    'require(project_version(root) == CURRENT_VERSION, "Part 3 requires projectVersion 4.1.30", failures)',
    'require(release_line(project_version(root)) == CURRENT_VERSION, "Part 3 requires the 4.1.30 release line", failures)',
)
write(rel, text)

# Part 4 Paper smoke verifier.
rel = "scripts/verify_core_platform_phase1l_part4.py"
text = read(rel)
text = replace_once(
    text,
    '''def main() -> int:
''',
    '''def release_line(version: str) -> str:
    match = re.match(r"^(\\d+\\.\\d+\\.\\d+)", version)
    return match.group(1) if match else ""


def main() -> int:
''',
    "Phase 1L Part 4 release-line helper",
)
text = text.replace(
    'require(project_version(root) == CURRENT_VERSION, "Part 4 requires projectVersion 4.1.30", failures)',
    'require(release_line(project_version(root)) == CURRENT_VERSION, "Part 4 requires the 4.1.30 release line", failures)',
)
write(rel, text)

# Built artifact verifier: plugin.yml and git.build.version must use full
# 4.1.30C, while support-contract and release-baseline metadata stay 4.1.30.
rel = "scripts/verify_release_artifact.py"
text = read(rel)
text = replace_once(
    text,
    '''def sha256(path: Path) -> str:
''',
    '''def release_line(version: str) -> str:
    match = re.match(r"^(\\d+\\.\\d+\\.\\d+)", version)
    return match.group(1) if match else ""


def sha256(path: Path) -> str:
''',
    "release artifact release-line helper",
)
text = text.replace(
    'if support.get("release") != version:\n        failures.append("Support contract release does not match projectVersion")',
    'if support.get("release") != release_line(version):\n        failures.append("Support contract release does not match the project release line")',
)
text = text.replace(
    'if baselines.get("candidate", {}).get("version") != version:\n        failures.append("Release baseline candidate does not match projectVersion")',
    'if baselines.get("candidate", {}).get("version") != release_line(version):\n        failures.append("Release baseline candidate does not match the project release line")',
)
write(rel, text)

# Guard the exact maintenance behavior we intend.
for rel in FILES:
    text = read(rel)
    if '[A-Z]?' not in text:
        raise SystemExit(f"Maintenance suffix regex missing from {rel}")

for rel in (
    "scripts/verify_core_platform_phase1l.py",
    "scripts/verify_phase1l_release_artifact.py",
    "scripts/verify_core_platform_phase1l_part3.py",
    "scripts/verify_core_platform_phase1l_part4.py",
    "scripts/verify_release_artifact.py",
):
    if "def release_line(" not in read(rel):
        raise SystemExit(f"release_line helper missing from {rel}")

print("4.1.30C maintenance-version verifier support applied")
