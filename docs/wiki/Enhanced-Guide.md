# 📖 Enhanced Guide

Slimefun Legacy keeps the familiar Slimefun Guide while extending it into a more useful navigation and recipe tool for large addon-heavy servers.

## Core uses

The Guide is where players discover categories, inspect recipes, see research locks and understand machine/multiblock requirements.

Useful commands:

```text
/sf guide
/sf open_guide
/sf search <query>
```

`/sf open_guide` is permission-controlled and may not be available to normal players on every server.

## Legacy enhancements

Slimefun Legacy's native Enhanced Guide includes features such as:

- **Smart search** for finding items in very large installations.
- **Bookmarks** for keeping track of progression goals.
- **Safe recipe preparation** workflows.
- **Universal machine recipe browsing** when machines/addons expose structured recipe information.
- **Addon ownership diagnostics** to help identify which addon contributed a problematic menu or item.
- **Slow-menu/runtime isolation** safeguards so one bad addon menu is less likely to damage the whole guide experience.

## Recipes are server-specific

The in-game Guide should be treated as the authoritative recipe reference for your server because:

- items can be disabled;
- research settings can differ;
- addons can add categories and recipes;
- addon versions can change recipes;
- server owners may run custom content.

A web wiki is best for **concepts, progression, troubleshooting and administration**. The Guide is best for **the exact item in front of you**.

## When a recipe does not work

Check these in order:

1. Is the item enabled?
2. Is the recipe shown in your current Guide?
3. Are you using the correct crafting/machine type?
4. Does the item require research or a permission?
5. Is the output inventory full?
6. For powered machines, is enough energy available?
7. Is the recipe contributed by an addon that failed to enable?
8. Run `/sf versions` and ask an admin to check `/sf doctor compatibility` if an addon is involved.

See [Troubleshooting](Troubleshooting.md) for the full diagnostic flow.