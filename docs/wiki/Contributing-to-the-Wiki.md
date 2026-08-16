# ✍️ Contributing to the Wiki

Good documentation is a compatibility feature. It reduces bad upgrades, prevents data loss and helps players understand systems without relying on outdated forum posts.

## What makes a good page?

- Explain **one clear topic**.
- Put the safest/common path first.
- Distinguish player instructions from operator-only actions.
- Use exact commands in code blocks.
- Call out destructive operations with warnings.
- Prefer the in-game Guide for exact recipes.
- State when behavior is addon-specific or version-specific.
- Link to deeper pages instead of repeating whole sections.

## Style

Use short sections, tables for comparisons, and GitHub callouts:

```markdown
> [!WARNING]
> Back up your server before this operation.
```

Do not turn every paragraph into a warning. Reserve callouts for information people can easily miss and regret.

## Screenshots

Screenshots are especially useful for:

- Guide navigation;
- multiblock orientation;
- Cargo configuration;
- Doctor output examples;
- before/after item repair;
- addon ownership/compatibility markers.

Crop out unrelated player data and secrets. Use stable repository-hosted images rather than temporary image hosts.

## Version-sensitive claims

Avoid hardcoding a release number when a link to Releases is better. If a page must describe behavior added in a specific release, say so explicitly.

## Respect upstream documentation

Use other Slimefun wikis for concepts and coverage ideas, but rewrite explanations in your own words and verify them against Legacy. Link to upstream sources when they are the best historical reference.

## Suggested future pages

The foundation wiki intentionally starts with concepts and operations. Great next expansions include:

- individual multiblock tutorials;
- Energy machine reference;
- Cargo node walkthroughs with screenshots;
- Programmable Android tutorial;
- GPS tutorial;
- radiation/hazmat guide;
- backpack/storage guide;
- addon-by-addon Legacy compatibility pages;
- performance case studies;
- developer migration notes for popular APIs.