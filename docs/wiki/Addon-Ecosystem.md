# 🔌 Addon Ecosystem

One of Slimefun's greatest strengths is its addon ecosystem. Addons can introduce entire new technology trees, machines, resources, storage systems, magic, automation and endgame progression without changing the client.

Slimefun Legacy aims to preserve the established Slimefun addon API wherever practical, but **an addon being a Slimefun addon does not automatically mean every historical build is compatible with every modern core**.

## Compatibility levels

Slimefun Legacy distinguishes several kinds of compatibility evidence.

### ✅ Release-blocking Legacy probes

These are representative Legacy-targeted addons used as strong compatibility gates. A new core regression against these targets is intended to block a release.

Current matrix examples include:

- **FastMachines Legacy** — `wickidcow/SF_FastMachines`
- **Networks Expansion Legacy** — `wickidcow/SF_NetworksExp`
- **SlimeTinker IE2 Legacy** — `wickidcow/SF_SlimeTinkerIE2`
- **BetterChests Legacy** — `wickidcow/SF_BetterChests`

### ⚠️ Advisory ecosystem probes

Slimefun Legacy also checks representative upstream/community addons as compatibility signals. Advisory failures are reported but do not automatically mean the core release is broken.

The current matrix includes representatives such as:

- Networks
- Infinity Expansion 2
- DynaTech
- Supreme
- Magic Expansion
- FluffyMachines
- FastMachines upstream
- SlimeTinker upstream
- FoxyMachines
- FlowerPower
- IDreamOfEasy
- Gastronomicon
- Bump
- SlimeCustomizer
- EMCTech

The live source of truth is `compatibility/addon-compatibility-matrix.json` in the repository.

## What a CI pass means

A compatibility pass is useful evidence, not a guarantee that every feature of an addon has been manually played through.

Legacy's compatibility harness can compare:

- source builds against a previous stable core and the candidate core
- binary linkage against protected Slimefun APIs
- declared compatibility metadata
- required and optional plugin dependencies

Runtime behavior can still depend on configuration, external plugins, server software and world data.

## Compatibility declarations

Addon developers can optionally declare tested core variants, platform requirements, minimum versions and dependencies.

An addon with no declaration is not automatically disabled. Legacy reports it as undeclared/unknown rather than pretending it has been verified.

See the repository's `docs/ADDON_COMPATIBILITY.md` for the developer-facing declaration format.

## Checking installed addons

Server owners should use:

```text
/sf versions
/sf doctor compatibility
/sf doctor dependencies
```

For a specific addon:

```text
/sf doctor compatibility <plugin>
/sf doctor dependencies <plugin>
```

These commands help distinguish:

- missing hard dependencies
- provider aliases
- declared compatibility warnings
- runtime/linkage failures observed inside Slimefun boundaries
- addons whose compatibility is simply unknown

## Before installing an addon

Check:

1. Does it target a Slimefun core compatible with Legacy?
2. Does it require another plugin or library?
3. Is the addon still maintained or is there a Legacy fork?
4. Does it store persistent world/player data?
5. Does it add ticking machines, chunk loaders or large networks?
6. Does it claim Folia support if you are using Folia?

Install it on staging first.

## When an addon breaks

Do not immediately downgrade or delete world data.

Collect:

- Paper/Minecraft version
- Java version
- Slimefun Legacy version/commit
- exact addon JAR/version
- required dependency versions
- complete startup error
- first runtime exception
- `/sf versions`
- `/sf doctor compatibility <plugin>`
- `/sf doctor dependencies <plugin>`
- reproduction steps

Then follow **[Bug Reporting](Bug-Reporting.md)**.

## Related pages

- **[Compatibility & Addons](Compatibility-and-Addons.md)**
- **[Developer Guide](Developer-Guide.md)**
- **[Doctor & Diagnostics](Doctor-and-Diagnostics.md)**
- **[Bug Reporting](Bug-Reporting.md)**
