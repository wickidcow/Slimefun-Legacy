# Slimefun Legacy 4.1.18 — Guide Runtime Stability Phase 1B

Phase 1B moves the Phase 1A runtime guard into the guide's internal navigation paths. This prevents addon menus from bypassing protection simply because they were opened from a click handler, nested category, bookmark, search page, or history entry instead of the public `SlimefunGuide` utility.

## Runtime behavior

- Guards direct `FlexItemGroup#open` calls in both classic and enhanced guides.
- Routes classic and enhanced category pagination back through the guarded public guide entry points.
- Guards guide history restoration and back-button dispatch for main menus, item groups, Slimefun items, vanilla recipe pages, and searches.
- Guards `NestedItemGroup` root opening and page navigation.
- Isolates nested subgroup visibility and icon failures so one malformed addon category can be skipped instead of terminating the whole menu.
- Adds barrier fallback icons and names when an addon category cannot render its icon.
- Guards the enhanced guide's private bookmark and smart-search pages.
- Routes item and recipe ingredient clicks through guarded display entry points.

## Diagnostics

A failure report now includes:

- player name and UUID;
- guide mode;
- operation being performed;
- current nesting depth;
- active guide call chain;
- item-group key and implementation class;
- owning addon name;
- exception type;
- elapsed time for slow guide calls.

Warnings remain rate-limited to prevent a broken category from flooding the console.

## Compatibility boundary

The existing public guide method descriptors remain unchanged. Phase 1B adds internal routing and defensive rendering without relocating guide APIs or changing addon registration contracts.

## Source updater

The classic and enhanced guide implementations are large, actively changing files. This package therefore includes an idempotent source updater and a GitHub Actions workflow rather than a patch file. The updater recognizes the current 4.1.17/4.1.18 guide structure, refuses an unexpected source layout, and marks successfully transformed files so rerunning it is safe.
