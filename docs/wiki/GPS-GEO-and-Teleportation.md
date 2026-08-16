# 🛰️ GPS, GEO & Teleportation

Slimefun's GPS category combines satellite-style positioning, location markers, GEO resource scanning and teleportation technology.

It is one of the clearest examples of Slimefun growing beyond simple custom crafting into a full server-side technology system.

## GPS network concepts

Classic GPS technology includes components such as:

- **GPS Transmitters**
- **GPS Control Panel**
- **GPS Marker Tool**
- **GPS Emergency Transmitter**
- **GPS Geo-Scanner**
- **Portable Geo-Scanner**
- **GEO Miner**
- **Oil Pump**
- **GPS Teleporter Pylons**
- **GPS Teleporter Matrix**
- **GPS Activation Devices**
- **Elevator Plates**

The exact recipes, signal behavior and enabled devices should be checked in the current in-game Guide.

## Building a GPS network

GPS systems are easier to understand when built in stages:

1. Establish transmitter coverage.
2. Open the control panel and verify the network sees what you expect.
3. Learn markers and location tools.
4. Experiment with GEO scanning.
5. Build GEO extraction only after understanding what resources are available.
6. Move into teleportation once the supporting infrastructure is stable.

## GEO scanning

GEO resources are tied to locations and worlds rather than behaving exactly like ordinary ores.

A scanner helps determine what resources are available in an area before investing in extraction equipment.

This makes GEO technology especially useful for servers where late-game resource acquisition should remain location-dependent rather than becoming a purely menu-based process.

## Teleportation

Classic Slimefun teleporters use GPS infrastructure rather than acting like an unrestricted vanilla-style `/tp` command.

When a teleporter refuses to activate, check:

- transmitter/network availability
- the teleporter structure
- destination registration
- power requirements
- protection-plugin access
- world or server restrictions
- whether the destination chunks can be handled safely

## Protection and server rules

Teleportation is one of the features most likely to interact with other server systems.

Server owners should test it with:

- claims/protection plugins
- world-border rules
- restricted worlds
- combat restrictions
- Towny or region permissions
- portals and other teleport plugins

Do not assume operator testing represents normal player behavior.

## GEO resources and addons

Addons can introduce their own resource types, scanners or extraction systems. When a GEO-related addon does not behave as expected, check **[Compatibility & Addons](Compatibility-and-Addons.md)** and the addon's own documentation.

## Related pages

- **[Resources, Dusts & Alloys](Resources-Dusts-and-Alloys.md)**
- **[Energy Networks](Energy-Networks.md)**
- **[Server Owner Guide](Server-Owner-Guide.md)**
- **[Troubleshooting](Troubleshooting.md)**
