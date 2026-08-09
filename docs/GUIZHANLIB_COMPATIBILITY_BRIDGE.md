# Slimefun Legacy 4.1.29 — GuizhanLib Compatibility Bridge

This release adds an experimental compatibility bridge for addons which expect `GuizhanLibPlugin`.

## What it does

- Declares `GuizhanLibPlugin` through Paper's `provides` alias so dependency resolution can continue when the external plugin is absent.
- Preserves Slimefun Legacy's existing **private, relocated** GuizhanLib usage.
- Adds a second, public GuizhanLib 2.5.0 compatibility surface to the final Slimefun JAR for older addons.
- Includes the common legacy `net.guizhanss.minecraft.guizhanlib.gugu.*` and compatibility utility classes used by older Gugu-era addons.
- Adds `/sf doctor guizhanlib` to show the external-plugin state, provider resolution, API readiness, and installed hard/soft dependents.
- Adds source-level and assembled-JAR verification so the bridge cannot silently disappear during Shadow packaging.
- Includes the upstream GuizhanLibPlugin GPL-3.0 license/attribution and the MIT license for Libby, which is bundled because GuizhanLib's public library-manager API references it.

## Deliberate safety boundary

Slimefun Legacy does **not** emulate or bundle the concrete GuizhanLibPlugin main `JavaPlugin` class. The bridge targets the GuizhanLib library API and common legacy helper packages.

An addon that performs a concrete cast such as `PluginManager#getPlugin("GuizhanLibPlugin")` to the GuizhanLibPlugin main class can still require the real external plugin. The Doctor command calls this out explicitly.

The old `net.guizhanss.minecraft.guizhanlib.gugu.localization.LocalizationLoader` shim is also deliberately excluded because its implementation calls the external GuizhanLibPlugin singleton directly. Other common legacy Gugu helpers that do not require that singleton remain available.

## First staging test

1. Build Slimefun Legacy normally in GitHub Actions.
2. Start a staging copy **with the existing GuizhanLibPlugin still installed** and run `/sf doctor guizhanlib` to inventory dependents and verify all bridge classes are present.
3. Stop the staging server normally.
4. Remove only `GuizhanLibPlugin.jar` from the staging plugin folder.
5. Start again and run `/sf doctor guizhanlib`.
6. Confirm it reports `Fallback mode: Active and ready`.
7. Test every addon listed under hard- and soft-depending addons, especially startup, guides, commands, machines, and updater-related code paths.

Do not use `/reload` for this transition.

## Packaging model

The normal Shadow task remains responsible for Slimefun Legacy's private dependencies and continues relocating `net.guizhanss.guizhanlib` into Slimefun's internal namespace. That Shadow output is moved to an intermediate build directory.

A final `guizhanLibBridgeJar` task then creates the production JAR by combining:

- the already-relocated Slimefun Shadow JAR, and
- the version-pinned public GuizhanLib compatibility classes needed by dependent addons.

The final artifact deliberately excludes the GuizhanLibPlugin main class, config manager, plugin-specific updater implementation, and the legacy `LocalizationLoader` shim that directly calls the external plugin singleton. GitHub Actions verifies these boundaries after the JAR is assembled.

## Upstream attribution

Compatibility classes are sourced at build time from `net.guizhanss:GuizhanLibPlugin:2.5.0` and the GuizhanLib 2.5.0 libraries distributed by the upstream GuizhanLibPlugin/GuizhanLib projects. These projects are GPL-3.0 licensed. The bridge is version-pinned so future upstream changes are reviewed rather than silently changing the exposed addon API.

Libby (`net.byteflux.libby`) is included only because GuizhanLib's public `BukkitLibraryManager` API depends on it. Libby is MIT licensed. License copies and attribution notices are packaged under `META-INF/LICENSES/`.
