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
* profile registration events remain controller-owned and fire only once;
* reverse recipe lookup must stay off the normal guide-render hot path and yield across ticks;
* published reverse-usage lists must be sorted/frozen once and reused on cached reopens.
"""

from __future__ import annotations

import re
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


def method_body(text: str, method_name: str) -> str:
    match = re.search(rf"\b{re.escape(method_name)}\s*\([^)]*\)\s*\{{", text)
    if not match:
        raise SystemExit(f"Guide runtime correctness failed: missing method {method_name}")

    start = match.end() - 1
    depth = 0
    for index in range(start, len(text)):
        char = text[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return text[start + 1 : index]

    raise SystemExit(f"Guide runtime correctness failed: unterminated method {method_name}")


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
        profile,
        "pendingProfileCallbacks.computeIfAbsent(uuid, ignored -> new CopyOnWriteArrayList<>()).add(callback);",
        "profile callback queuing",
    )
    require_before(
        profile,
        "pendingProfileCallbacks.computeIfAbsent(uuid, ignored -> new CopyOnWriteArrayList<>()).add(callback);",
        "if (loadingProfiles.add(uuid))",
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

    bootstrap = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyGuideBootstrap.java",
    )
    require(bootstrap, "LegacyRecipeUsageBrowser.initialize(plugin);", "recipe-usage browser initialization")
    require(
        bootstrap,
        "new RecipeUsageIndexedEnhancedSurvivalSlimefunGuide()",
        "recipe-usage enhanced guide registration",
    )

    usage_guide = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/RecipeUsageIndexedEnhancedSurvivalSlimefunGuide.java",
    )
    require(
        usage_guide,
        "extends IndexedEnhancedSurvivalSlimefunGuide",
        "cached indexed guide inheritance",
    )
    require_before(
        usage_guide,
        "super.displayItem(profile, item, addToHistory);",
        "LegacyRecipeUsageBrowser.get().decorateItemPage(player, profile, this, item);",
        "existing guide render before constant-time usage decoration",
    )

    usage_browser = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyRecipeUsageBrowser.java",
    )
    decorate = method_body(usage_browser, "decorateItemPage")
    click = method_body(usage_browser, "onInventoryClick")
    batch = method_body(usage_browser, "runBuildBatch")
    request = method_body(usage_browser, "requestIndex")
    cached_open = method_body(usage_browser, "openCachedUsages")
    freeze = method_body(usage_browser, "freezeUsageIndex")

    require(usage_browser, "private final Map<UUID, UsageIndex> indexes = new ConcurrentHashMap<>()", "per-world recipe-usage cache")
    require(usage_browser, "private final Map<UUID, IndexBuildState> builds = new ConcurrentHashMap<>()", "single in-flight build registry")
    require(decorate, "UsageIndex cached = indexes.get(worldId)", "constant-time cached usage count")
    reject(decorate, "getEnabledSlimefunItems", "registry scan during ordinary item-page decoration")
    reject(decorate, "MachineRecipeProviderRegistry.getProviders", "provider scan during ordinary item-page decoration")
    reject(decorate, "updateInventory(", "forced full inventory sync during ordinary item-page decoration")
    require(click, "requestIndex(player, context, targetKey)", "explicit-click index start")
    reject(click, "for (SlimefunItem", "synchronous item scan in usage click handler")
    reject(usage_browser, "getOrBuildIndex(", "legacy synchronous reverse-index builder")
    require(request, "builds.putIfAbsent(worldId, created)", "one shared in-flight build per world")
    require(request, "Slimefun.getRegistry().getEnabledSlimefunItems()", "bounded live item registry view")
    reject(request, "new ArrayList<>(Slimefun.getRegistry().getEnabledSlimefunItems())", "full item-list copy during usage click")
    require(usage_browser, "private final int itemLimit;", "fixed starting item-count bound")
    require(usage_browser, "this.itemLimit = items.size();", "captured item-count bound")
    require(usage_browser, "runLater(() -> runBuildBatch(state), 1L)", "next-tick incremental indexing")
    require(batch, "state.nextItem < state.itemLimit", "fixed item-count batch boundary")
    require(batch, "processed < state.maxItemsPerTick", "per-tick item budget")
    require(batch, "System.nanoTime() - batchStarted >= state.batchBudgetNanos", "per-tick elapsed-time budget")
    require(batch, "UsageIndex completed = freezeUsageIndex(state.usages);", "one-time usage-index publication")
    require(usage_browser, "runFor(", "entity-owned completion delivery")
    require(cached_open, "index.usages().getOrDefault(targetKey, List.of())", "direct cached usage-list lookup")
    reject(cached_open, "new ArrayList<>(", "cached usage-list copy")
    reject(usage_browser, "sortedUsages(", "per-open usage-list sorting")
    require(freeze, "sorted.sort(USAGE_ORDER)", "one-time usage-list sorting")
    require(freeze, "List.copyOf(sorted)", "frozen usage lists")
    require(freeze, "Collections.unmodifiableMap(frozen)", "published completed index")

    settings = read(
        root,
        "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/LegacyGuideSettings.java",
    )
    require(settings, 'config.getInt("features.recipe-usages.index-items-per-tick", 6)', "conservative item batch default")
    require(settings, 'config.getInt("features.recipe-usages.index-budget-micros", 1500)', "conservative time budget default")

    print("Guide/profile runtime correctness verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
