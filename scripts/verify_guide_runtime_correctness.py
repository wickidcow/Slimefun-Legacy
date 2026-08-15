#!/usr/bin/env python3
"""Verify Slimefun Legacy guide/profile runtime correctness invariants.

These checks intentionally preserve the stronger core-native behavior already present in
Slimefun Legacy while incorporating the useful lessons from JustEnoughGuide's
470c736fdee429b8d49789775716774eafb3d978 update:

* guide failures must be observable rather than silently swallowed;
* GuideHistory must exist before consumers can observe a PlayerProfile;
* core must not depend on addon-style reflective GuideHistory replacement;
* public guide entry points must stay behind the runtime guard;
* concurrent PlayerProfile requests must coalesce without dropping callbacks;
* profile registration events remain controller-owned and fire only once.
"""

from __future__ import annotations

import sys
from pathlib import Path


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"Guide runtime correctness failed: missing file {relative}")
    return path.read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"Guide runtime correctness failed: missing {label}: {needle}")


def reject(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"Guide runtime correctness failed: found forbidden {label}: {needle}")


def require_before(text: str, first: str, second: str, label: str) -> None:
    first_at = text.find(first)
    second_at = text.find(second)
    if first_at < 0 or second_at < 0 or first_at >= second_at:
        raise SystemExit(
            f"Guide runtime correctness failed: ordering violation for {label}: expected {first!r} before {second!r}"
        )


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    guard = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/GuideRuntimeGuard.java",
    )
    require(guard, "RuntimeException | LinkageError | StackOverflowError", "guide failure boundary")
    require(guard, "Slimefun.logger().log(Level.SEVERE", "full guide exception logging")
    require(guard, "describeContext(", "guide failure context")
    require(guard, "addon=", "addon ownership diagnostics")
    require(guard, "activeChain=", "nested guide call diagnostics")

    guide = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/core/guide/SlimefunGuide.java",
    )
    for operation in (
        "restore guide history",
        "open main menu page ",
        "open item group page ",
        "open guide search",
        "display item stack",
        "display Slimefun item ",
    ):
        require(guide, f'"{operation}', f"guarded guide operation {operation!r}")
    require(guide, "GuideRuntimeGuard.run(", "guide runtime guard routing")

    profile = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/api/player/PlayerProfile.java",
    )
    profile_compact = "".join(profile.split())
    require(
        profile,
        "private final GuideHistory guideHistory = new GuideHistory(this);",
        "eager core-owned GuideHistory initialization",
    )
    require(profile, "public @Nonnull GuideHistory getGuideHistory()", "nonnull GuideHistory API")
    reject(profile, 'ReflectionUtil.setValue', "reflective PlayerProfile mutation")
    reject(profile, '"guideHistory"', "string-based GuideHistory field mutation")

    require(
        profile,
        "private static final Set<UUID> loadingProfiles = ConcurrentHashMap.newKeySet();",
        "dedicated profile-load coordination",
    )
    require(
        profile,
        "private static final Map<UUID, CopyOnWriteArrayList<Consumer<PlayerProfile>>> pendingProfileCallbacks",
        "per-player callback queue",
    )
    require(
        profile_compact,
        "pendingProfileCallbacks.computeIfAbsent(uuid,ignored->newCopyOnWriteArrayList<>()).add(callback);",
        "profile callback queuing",
    )
    require_before(
        profile_compact,
        "pendingProfileCallbacks.computeIfAbsent(uuid,ignored->newCopyOnWriteArrayList<>()).add(callback);",
        "if(loadingProfiles.add(uuid))",
        "callback queued before profile-load ownership",
    )
    require(
        profile,
        "controller.getOrCreateProfileAsync(p).whenComplete((profile, error) -> {",
        "controller-owned load/create lifecycle",
    )
    require_before(
        profile,
        "loadingProfiles.remove(uuid);",
        "CopyOnWriteArrayList<Consumer<PlayerProfile>> callbacks = pendingProfileCallbacks.remove(uuid);",
        "load marker released before callback drain",
    )
    require(profile, "for (Consumer<PlayerProfile> callback : callbacks)", "all queued callbacks invoked")
    require(profile, "catch (RuntimeException x)", "profile callback failure isolation")
    reject(profile, "if (processProfiles.containsKey(uuid))", "legacy callback-dropping load guard")
    reject(profile, "new AsyncProfileLoadEvent(pf)", "duplicate PlayerProfile load event")
    reject(profile, "controller.getProfileAsync(p, new IAsyncReadCallback", "legacy callback-wrapper load path")

    controller = read(
        root,
        "src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/ProfileDataController.java",
    )
    require(
        controller,
        "AsyncProfileLoadEvent event = new AsyncProfileLoadEvent(profile);",
        "single controller-owned profile-load event",
    )
    require(
        controller,
        "Slimefun.getRegistry().getPlayerProfiles().put(uid, event.getProfile());",
        "profile registration after load event",
    )

    enhanced = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/EnhancedSurvivalSlimefunGuide.java",
    )
    require(enhanced, "GuideRuntimeGuard.run(", "enhanced guide page guard")
    require(enhanced, "catch (Exception | LinkageError exception)", "enhanced item-open failure boundary")
    require(enhanced, "item.error(", "enhanced item-open exception reporting")

    print("Guide/profile runtime correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
