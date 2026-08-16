# 🛡️ Server Owner Guide

Running Slimefun is closer to operating a modpack than installing a small utility plugin. The core, addons, data, protection plugins and automation systems form one ecosystem.

## Recommended operating model

### 1. Maintain a staging server

Before a production upgrade, clone representative data and test the exact Paper, Java, Slimefun Legacy and addon versions you intend to deploy.

See **[Installation & Upgrades](Installation-and-Upgrades.md)** for the deployment sequence.

### 2. Keep a known-good plugin set

Archive the exact JARs from the last stable deployment. A backup is much more useful when you can reproduce the software that created it.

### 3. Version-control your configuration

Keep a copy of the configuration that produced each known-good deployment. When changing machine/network timing, research, radiation or stability settings, change one group at a time and document why.

See **[Server Configuration](Server-Configuration.md)**.

### 4. Treat addon changes as platform changes

An addon can register machines, recipes, storage types, schedulers and listeners. A core update can expose an addon bug, and an addon update can expose a core compatibility gap.

Use:

```text
/sf versions
/sf doctor compatibility
/sf doctor dependencies
```

Read **[Addon Ecosystem](Addon-Ecosystem.md)** before deploying a large addon stack.

### 5. Watch machine and storage health

Legacy includes safeguards for machine failures, storage/item repair, shutdown/write state and addon callback boundaries. Do not ignore repeated circuit-breaker or linkage warnings — they are usually evidence, not cosmetic noise.

### 6. Never use `/reload`

Use full restarts. Reloading a complex plugin ecosystem can leave tasks, inventories, listeners and addon state in unsafe combinations.

## Production checklist

- [ ] Full backup completed and verified.
- [ ] Old core JAR removed; only one provider remains.
- [ ] Java version matches the supported runtime.
- [ ] Paper/Purpur line matches the release target.
- [ ] Server starts without unresolved hard dependencies.
- [ ] `/sf doctor status` is healthy.
- [ ] `/sf doctor compatibility` reviewed.
- [ ] `/sf doctor dependencies` reviewed.
- [ ] Guide and search tested.
- [ ] Backpack/storage tested.
- [ ] Cargo and an electric machine tested.
- [ ] Energy generation/storage tested under load.
- [ ] Protection behavior tested as a non-OP player.
- [ ] High-value addon machines tested.
- [ ] A short performance profile shows no new pathological hotspot.

## Protection and claims

Do not assume Slimefun integration is correct because a machine works as an operator. Test normal players, claim boundaries, protected containers, Androids, special tools and entity interactions.

See **[Protection Plugins & Claims](Protection-Plugins-and-Claims.md)**.

## Performance practices

Slimefun performance problems are often **local** rather than global. When TPS or chunk performance changes, identify the world, chunk, machine class or addon involved instead of immediately removing the core.

Useful evidence includes:

- spark/Paper profiles;
- `/sf timings`;
- machine counts in the affected area;
- entity and armor-stand counts;
- Cargo complexity;
- addon ownership of failing items/machines;
- repeated stack traces;
- region/thread ownership on Folia.

The full workflow is in **[Server Performance](Server-Performance.md)**.

## Factory policy

Large public servers benefit from clear automation rules. Consider documenting expectations around:

- enormous Cargo/energy networks
- chunk loaders supplied by addons
- unattended reactors
- Android farms/mines
- high-speed machine arrays
- shared public factory infrastructure

The goal is not to ban automation; it is to prevent one uncontrolled build from becoming everyone else's performance problem.

See **[Factory Design Patterns](Factory-Design-Patterns.md)** for designs that are easier to operate and troubleshoot.

## Folia

Folia remains experimental. Legacy routes a growing set of location/entity/machine work through owning schedulers, but cross-region Cargo/Energy behavior is intentionally conservative. **Every addon must independently be Folia-safe.**

If you want the least surprising production environment, use the primary Paper target.

## Security and permissions

Keep cheat, debug, migration, repair, backpack-other and bypass permissions restricted to trusted operators. Review **[Commands & Permissions](Commands-and-Permissions.md)** before delegating administrative access.

When reporting a security-sensitive issue, remove tokens, passwords and private server information from logs before sharing them.

## Administrator quick links

**[Configuration](Server-Configuration.md)** · **[Performance](Server-Performance.md)** · **[Protection](Protection-Plugins-and-Claims.md)** · **[Addons](Addon-Ecosystem.md)** · **[Doctor](Doctor-and-Diagnostics.md)** · **[Troubleshooting](Troubleshooting.md)** · **[Bug Reporting](Bug-Reporting.md)**
