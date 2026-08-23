# Common Issues

This page is the quick entry point for Slimefun Legacy problems. For a full diagnostic flow, use [Troubleshooting](Troubleshooting.md).

## An item or machine does not work

1. Confirm the item is enabled in the in-game Guide.
2. Confirm the player has completed any required research.
3. Check that the machine is built exactly as shown in the Guide.
4. For electric machines, verify generation, storage, regulator placement and network connectivity.
5. Check `/sf doctor runtime` for isolated machine failures.

## An addon will not start

Use `/sf versions`, `/sf doctor dependencies`, and `/sf doctor compatibility <plugin>`. Missing hard dependencies, provider aliases and binary/API incompatibilities are different problems and should be diagnosed separately.

## Items have old or translated names

Use the Storage and Item Doctor workflow described in [Doctor & Diagnostics](Doctor-and-Diagnostics.md). Always run a scan before confirming a repair.

## Cargo behaves unexpectedly

See [Cargo Networks](Cargo-Networks.md). Confirm channel configuration and connected inventories before assuming the network is broken.

## The server has performance problems

See [Server Performance](Server-Performance.md). Capture timings or profiling evidence and identify the exact machine/addon behavior before changing random settings.

## Still stuck?

Read [Bug Reporting](Bug-Reporting.md) before opening an issue. Include the full startup log, server version, Java version, exact Slimefun Legacy build, addon versions, complete exception and reproduction steps.
