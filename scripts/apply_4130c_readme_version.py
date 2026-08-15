#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
path = root / "README.md"
text = path.read_text(encoding="utf-8")
replacements = (
    (
        "Current development release: **4.1.30 — Core Platform Phase 1L (Release Lifecycle & Upgrade Safety)**.",
        "Current development release: **4.1.30C — Core Platform Phase 1L (Release Lifecycle & Upgrade Safety)**.",
    ),
    (
        "Slimefun Legacy 4.1.30 is tested primarily against **Paper 26.2 / Minecraft 26.2 on Java 25**.",
        "Slimefun Legacy 4.1.30C is tested primarily against **Paper 26.2 / Minecraft 26.2 on Java 25**.",
    ),
)
for old, new in replacements:
    if old in text:
        text = text.replace(old, new, 1)
    elif new not in text:
        raise SystemExit(f"README version marker was not found: {old}")
path.write_text(text, encoding="utf-8")
print("README development and tested-version metadata set to 4.1.30C")
