# Slimefun Legacy 4.1.18 — Guide & Runtime Stability, Phase 1A

## Included

This first 4.1.18 drop-in adds a central runtime boundary around public Slimefun Guide operations.

- Blocks the same guide operation from recursively reopening itself.
- Limits nested guide calls to 12 levels.
- Catches addon `RuntimeException`, `LinkageError`, and guide-specific `StackOverflowError` failures.
- Closes a broken menu instead of leaving the player trapped in it.
- Reports the guide mode, item-group key, group class, and owning addon in the console.
- Warns when a guide operation takes at least one second.
- Rate-limits repeated diagnostics to one message per minute for the same failure.
- Does not catch fatal JVM failures such as `OutOfMemoryError`.

## Scope

This phase protects the public `SlimefunGuide` entry points used by commands, guide history, addons, and custom category navigation.

The next 4.1.18 phase should move the same guard directly into classic and enhanced item-group rendering so every built-in category click and every `FlexItemGroup` call is protected at the final execution boundary.
