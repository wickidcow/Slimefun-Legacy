# 🚀 Server Performance

Slimefun is a large gameplay platform. Machines, Cargo, energy networks and addons can create a lot of scheduled work, so performance should be managed with measurements rather than guesses.

Slimefun Legacy is maintained primarily for **Paper 26.2 / Minecraft 26.2 with Java 25**.

## 1. Profile first

Before changing configuration, determine what is actually expensive.

Useful tools include:

- `/sf timings` for Slimefun-focused timing information
- **spark** for server-wide profiling
- Paper's own diagnostics
- `/sf doctor runtime` for Legacy runtime health
- `/sf versions` for installed Slimefun addons and compatibility markers

A red startup task does not automatically mean the server has an ongoing tick problem. Look for work that repeats while players are online.

## 2. Never use `/reload`

Do not use Bukkit/Spigot-style `/reload` to install, update or repair Slimefun.

Slimefun and many of its addons maintain inventories, schedulers, listeners, databases and machine state that are not designed to survive arbitrary plugin reloads.

**Stop and restart the server normally.**

## 3. Design networks intentionally

Large Cargo and energy networks can be much more expensive than several smaller purpose-built networks.

Prefer:

- short, understandable paths
- separate factories for unrelated production chains
- local storage buffers
- fewer unnecessary connector nodes
- reasonable network limits

See **[Cargo Networks](Cargo-Networks.md)** and **[Energy Networks](Energy-Networks.md)**.

## 4. Watch addon behavior

A server with twenty addons is not simply "Slimefun plus more items." Every addon can introduce its own:

- ticking machines
- database work
- entity scanning
- chunk access
- Cargo hooks
- energy logic
- GUIs
- external dependencies

If a profile points at an addon, test that addon separately before changing the core.

Use:

```text
/sf versions
/sf doctor compatibility
/sf doctor dependencies
/sf doctor runtime
```

## 5. Be careful with chunk-heavy automation

Factories spread across many chunks are harder to reason about and can interact poorly with chunk loading.

For production builds:

- keep tightly coupled machines near each other
- avoid intentionally forcing huge areas to stay active unless necessary
- do not assume an unloaded destination can safely participate in a transaction
- investigate chunk loaders provided by addons separately

Slimefun Legacy includes chunk-safety work intended to avoid unsafe Cargo, energy and machine behavior around unloaded locations.

## 6. Tune only after measuring

Slimefun exposes settings related to machine/network timing and network size. Lower-frequency work can improve performance, but excessive delays make factories feel broken.

Change one thing at a time and compare profiles before and after.

Keep a copy of the previous configuration so the change can be reversed.

## 7. Use staging for upgrades

For a large server, a production upgrade should look like this:

1. Back up worlds, Slimefun data, databases and addon data.
2. Copy the server or representative data to staging.
3. Install the candidate Slimefun Legacy build.
4. Review startup logs.
5. Run Doctor diagnostics.
6. Test representative machines, Cargo, energy, backpacks and addons.
7. Profile the staging server under load when possible.
8. Only then update production.

## 8. Keep failed machines visible

Slimefun Legacy can isolate repeatedly failing machine callbacks rather than allowing one bad ticker to continuously hammer the server.

Use runtime Doctor commands to inspect and retry isolated machine behavior after the underlying problem is understood.

## 9. Folia is not a free performance switch

Folia changes scheduler and region-ownership rules. It should not be installed merely because a server is experiencing lag.

Slimefun Legacy's Folia support remains experimental, and **every installed addon also needs to be Folia-safe**.

## Practical checklist

When TPS or MSPT suddenly worsens:

1. Capture a spark profile during the problem.
2. Check `/sf timings`.
3. Check recently added or updated addons.
4. Look for oversized Cargo/energy networks.
5. Look for a single pathological machine or chunk.
6. Review `/sf doctor runtime` and `/sf versions`.
7. Compare against the last known-good build/configuration.
8. Reproduce on staging before making destructive changes.

## Related pages

- **[Server Owner Guide](Server-Owner-Guide.md)**
- **[Doctor & Diagnostics](Doctor-and-Diagnostics.md)**
- **[Cargo Networks](Cargo-Networks.md)**
- **[Energy Networks](Energy-Networks.md)**
- **[Troubleshooting](Troubleshooting.md)**
