#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import shutil
from pathlib import Path

root = Path(__file__).resolve().parents[1]
libs = root / "build" / "libs"
candidates = [
    path
    for path in libs.glob("*.jar")
    if not any(token in path.name.lower() for token in ("sources", "javadoc", "plain", "dev"))
]
if not candidates:
    raise SystemExit("No release JAR was found in build/libs")
source = max(candidates, key=lambda path: path.stat().st_size)
release = root / "release"
release.mkdir(exist_ok=True)
target = release / "Slimefun-Legacy-4.1.18.jar"
shutil.copy2(source, target)
digest = hashlib.sha256(target.read_bytes()).hexdigest()
(release / "Slimefun-Legacy-4.1.18-SHA256.txt").write_text(
    f"{digest}  {target.name}\n", encoding="utf-8"
)
print(f"Packaged {target.relative_to(root)}")
