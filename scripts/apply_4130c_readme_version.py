#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
path = root / "README.md"
text = path.read_text(encoding="utf-8")
old = "Current development release: **4.1.30 — Core Platform Phase 1L (Release Lifecycle & Upgrade Safety)**."
new = "Current development release: **4.1.30C — Core Platform Phase 1L (Release Lifecycle & Upgrade Safety)**."
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit("README current development version line was not found")
path.write_text(text, encoding="utf-8")
print("README development version set to 4.1.30C")
