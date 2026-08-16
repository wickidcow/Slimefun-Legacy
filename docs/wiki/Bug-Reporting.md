# 🐛 Bug Reporting

A good bug report can turn hours of guessing into a reproducible fix.

Slimefun Legacy runs alongside Paper, protection plugins, databases and a large addon ecosystem, so reports should include enough information to separate a core bug from an addon, dependency or configuration problem.

## Before reporting

Please check:

1. You are running a currently supported Slimefun Legacy build.
2. Only one Slimefun core provider is installed.
3. The server has been fully restarted since the last plugin/configuration change.
4. The problem still occurs without `/reload` having been used.
5. The first relevant exception in the console has been saved.
6. The issue can be reproduced consistently if possible.

## Include these versions

Always include:

- Minecraft version
- Paper/Purpur/Folia build
- Java version
- Slimefun Legacy version and, when available, commit SHA
- exact addon version/JAR when an addon is involved
- exact versions of required dependency plugins

## Run diagnostics

For general core problems:

```text
/sf doctor status
/sf doctor core
/sf doctor runtime
```

For addon problems:

```text
/sf versions
/sf doctor compatibility
/sf doctor compatibility <plugin>
/sf doctor dependencies <plugin>
```

For external integrations:

```text
/sf doctor integrations
```

Include the relevant output with the report.

## Include the first exception

A log often contains many follow-up errors caused by the first failure.

Include the **first complete exception**, including:

- exception type
- message
- `Caused by:` sections
- complete stack trace
- surrounding plugin startup/runtime messages

Do not submit only the final line such as `Plugin disabled` or `Could not pass event`.

## Reproduction steps

Write the smallest sequence that triggers the problem.

Good example:

1. Start Paper with Slimefun Legacy and Addon X.
2. Join as a normal player.
3. Place Machine Y.
4. Insert Item Z.
5. Connect it to an energy network.
6. Machine stops and console throws the attached exception.

Avoid descriptions such as "machines are broken" when the failure can be narrowed further.

## Data-loss or inventory issues

For backpacks, storage, Cargo or machine-inventory problems, also explain:

- what items existed before the failure
- where they were stored
- whether the chunk unloaded/reloaded
- whether the server restarted or crashed
- whether another player had the inventory open
- whether the item/block came from an older Slimefun fork

Do not repeatedly retry destructive actions on the only copy of affected data. Make a backup first.

## Performance reports

For lag, include a profiler capture taken **while the problem is happening**.

Useful evidence includes:

- spark profiler link/report
- `/sf timings`
- affected world/chunk
- approximate machine/network size
- addon ownership of expensive machines
- whether performance returns to normal when a specific machine/network is removed on staging

See **[Server Performance](Server-Performance.md)**.

## Test on staging when possible

A clean or copied staging server can answer important questions:

- Does the issue happen with only Slimefun Legacy?
- Does it begin when one addon is added?
- Does it only happen with existing world data?
- Is it a regression from the previous stable release?

Never delete production data merely to make a bug easier to reproduce.

## Security issues

Do not publicly post secrets, database credentials, private tokens or other exploitable server information.

If a report contains a security-sensitive exploit, remove credentials and unnecessary private data before sharing logs.

## What makes a great report?

The best reports contain four things:

**exact environment + exact steps + exact failure + exact evidence**

That is enough for a maintainer to reproduce the problem instead of guessing.

## Related pages

- **[Troubleshooting](Troubleshooting.md)**
- **[Doctor & Diagnostics](Doctor-and-Diagnostics.md)**
- **[Compatibility & Addons](Compatibility-and-Addons.md)**
- **[Server Performance](Server-Performance.md)**
