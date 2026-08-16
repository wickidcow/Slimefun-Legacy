# 📦 Cargo Networks

Cargo is Slimefun's classic item-transport and factory-automation system. It can automatically move items between inventories and machines, allowing a collection of individual machines to become a real production line.

## Core idea

A Cargo network is built around a **Cargo Manager** and Cargo nodes attached to inventories or machines.

The classic components are:

| Component | Purpose |
| --- | --- |
| **Cargo Manager** | Controls and reports the network |
| **Input Node** | Pulls items from an attached inventory into the network |
| **Output Node** | Sends matching items from the network into an attached inventory |
| **Advanced Output Node** | Provides more selective output behavior |
| **Connector Node** | Extends network reach without attaching to an inventory |
| **Cargo Motor** | Crafting component used by Cargo equipment |

## Channels

Cargo uses color-coded channels so several logical item routes can coexist inside one network. Classic Slimefun provides the familiar wool-color channel system.

A simple design might use:

- one channel for raw materials
- one for machine intermediates
- one for finished products
- one for fuel or special materials

Keeping channels purposeful makes large factories much easier to troubleshoot.

## A simple first network

Start small:

1. Place a Cargo Manager.
2. Attach an input node to a source chest.
3. Attach an output node to a destination chest or compatible machine.
4. Put both nodes on the same channel.
5. Configure filters only after confirming basic movement works.

Do not begin by connecting an entire base. Prove one transfer path first.

## Cargo and machines

Machine automation is more complex than chest-to-chest movement because different machines expose different input and output inventories.

Slimefun Legacy preserves historical Cargo behavior while also providing modern machine-input and recipe-provider integration points for compatible addons.

If an addon machine will not accept Cargo:

- confirm that the addon supports Cargo automation
- confirm the correct side/inventory is targeted
- check filters and channel selection
- verify the machine is actually loaded and active
- run `/sf doctor compatibility <plugin>` for addon diagnostics

## Performance-friendly Cargo design

Cargo can become expensive when players build enormous interconnected networks.

For production servers:

- prefer several purposeful networks over one giant universal network
- avoid unnecessary connector chains
- do not repeatedly move items back and forth between the same inventories
- keep production and bulk storage layouts understandable
- profile heavy factories with `/sf timings` and a server profiler such as spark
- use the network limits and Cargo timing settings appropriate for your server

See **[Server Performance](Server-Performance.md)** for administrator guidance.

## Legacy safety behavior

Slimefun Legacy includes chunk-safety and transactional correctness work around Cargo. The goal is to avoid silently losing items when a destination cannot accept the full transfer or when network members are not safely available.

That does not make every addon implementation automatically safe. Addon-specific Cargo behavior should still be tested on a staging server.

## Troubleshooting checklist

When Cargo stops moving items:

1. Confirm the Cargo Manager is part of the network.
2. Check node direction — input versus output.
3. Check channel colors.
4. Remove filters temporarily.
5. Confirm destination inventory space.
6. Confirm the target machine is operational.
7. Check protection-plugin access.
8. Check chunk/loading conditions.
9. Use `/sf timings`, `/sf versions` and Doctor diagnostics if the problem persists.

## Related pages

- **[Energy Networks](Energy-Networks.md)**
- **[Energy, Cargo & Automation](Energy-Cargo-and-Automation.md)**
- **[Server Performance](Server-Performance.md)**
- **[Troubleshooting](Troubleshooting.md)**
