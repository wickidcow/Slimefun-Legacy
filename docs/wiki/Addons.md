# Addons for Slimefun Legacy

Slimefun has a large addon ecosystem. Slimefun Legacy aims to preserve the established Slimefun 4 addon API and saved data wherever practical, but no core fork can guarantee every historical addon build.

## Before installing an addon

Check all of the following:

- the addon is intended for Slimefun 4 or a compatible API;
- required library plugins are installed;
- the addon supports your Minecraft/Paper version;
- the addon does not require a different Slimefun core implementation;
- the addon is actively maintained or has been tested on your staging server.

Use `/sf versions`, `/sf doctor compatibility`, and `/sf doctor dependencies` to inspect the installed stack.

## Legacy-maintained and tested addons

See [Addon Ecosystem](Addon-Ecosystem.md) for the current Legacy-oriented addon guidance and [Compatibility & Addons](Compatibility-and-Addons.md) for compatibility expectations.

## Existing worlds

When replacing an addon build, back up first and verify item IDs, block data and machine behavior on a staging copy. Do not assume that similarly named forks use identical persistent data.

## Addon developers

See the [Developer Guide](Developer-Guide.md) for API and compatibility guidance.
