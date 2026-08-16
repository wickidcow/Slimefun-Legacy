# 🧯 Troubleshooting

When Slimefun breaks, avoid changing five things at once. Capture evidence, identify the layer and test one hypothesis at a time.

## 60-second triage

```text
/sf versions
/sf doctor status
/sf doctor compatibility
/sf doctor dependencies
/sf doctor runtime
```

Then save the **full exception**, not only its last line.

## Plugin/addon will not enable

Check:

- exact Java version;
- exact Paper/Minecraft build;
- duplicate Slimefun core JARs;
- missing hard dependencies;
- addon startup exception;
- `/sf doctor dependencies <plugin>`;
- `/sf doctor compatibility <plugin>`.

If hard dependencies are satisfied but the addon still disables itself, inspect its own startup log/configuration. Doctor intentionally does not guess at arbitrary plugin failures.

## Recipe will not craft

1. Search the item in the current Guide.
2. Verify the correct machine/crafting type.
3. Verify research/permissions.
4. Verify every ingredient is the exact expected item.
5. Check item disable configuration.
6. If addon-owned, confirm the addon is enabled and compatible.

## Electric machine does nothing

Test the machine manually before Cargo. Verify input, output space, power/network, recipe and addon status. Then check `/sf doctor runtime` for isolated machine failures.

## Cargo does not move items

Check source inventory, destination capacity, filters, direction/channel settings, chunk/region state and whether the destination is a special addon inventory requiring integration support.

## Old/translated item names or lore

Do not blindly replace item metadata. Use the Storage & Item Doctor dry-run path:

```text
/sf doctor scan
```

Review results before `/sf doctor repair confirm`.

## Backpack problems

Because backpacks hold valuable player data, reproduce the issue with a test backpack when possible. Capture the exact open/close sequence, any console exception, player UUID and whether another plugin is manipulating inventories.

Do not repeatedly force-open or migrate a failing production backpack without a backup.

## Server starts but one chunk/player freezes

TPS can remain healthy while one client experiences a pathological chunk. Check entity counts, armor stands/displays, addon structures, model entities and client-render-heavy content in the affected area.

Use a profiler and compare the bad chunk with nearby normal chunks before blaming Slimefun globally.

## Folia-only issue

Reproduce on the primary Paper target if possible. If it works on Paper but fails on Folia, record region boundaries and whether the failing addon explicitly supports Folia.

## What to include in a bug report

- Slimefun Legacy release and commit if known;
- Paper/Minecraft version;
- Java version;
- addon versions;
- complete stack trace;
- startup log when relevant;
- reproduction steps;
- expected vs actual behavior;
- Doctor/versions output;
- whether it reproduces on a clean staging server.

## Avoid these “fixes”

- `/reload`;
- deleting data without a backup;
- installing multiple Slimefun cores simultaneously;
- randomly downgrading only one JAR against newer mutated data;
- ignoring missing dependency errors;
- repeatedly retrying a crashing integration without reading the exception.