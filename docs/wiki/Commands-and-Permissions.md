# ⌨️ Commands & Permissions

Slimefun accepts both `/slimefun` and `/sf`. This page focuses on the commands most useful to players and operators. `/sf help` on your installed build is the final authority for available syntax.

## Player-facing commands

| Command | Purpose | Permission |
| --- | --- | --- |
| `/sf help` | Show Slimefun command help | — |
| `/sf guide` | Obtain the Slimefun Guide | `slimefun.command.guide` |
| `/sf search <query>` | Search Slimefun items | `slimefun.command.search` |
| `/sf stats` | View your Slimefun progress/statistics | Usually available to players |
| `/sf open_guide` | Open the Guide without the book | `slimefun.command.open_guide` |

`guide` and `search` are enabled for players by default in the plugin descriptor. `open_guide` defaults to operators unless your permissions setup changes it.

## Operator / diagnostic commands

| Command | Purpose | Permission |
| --- | --- | --- |
| `/sf versions` | Show core/addon versions and Legacy compatibility markers | `slimefun.command.versions` |
| `/sf timings` | Slimefun timings tools | `slimefun.command.timings` |
| `/sf backpack` | Administrative backpack access | `slimefun.command.backpack` |
| `/sf itemid` | Inspect item identity | `slimefun.command.id` |
| `/sf debug` | Debug utilities | `slimefun.command.debug` |
| `/sf migrate` | Migration tools | `slimefun.command.migrate` |
| `/sf stability ...` | Stability/health recovery tools | `slimefun.command.stability` |
| `/sf doctor ...` | Legacy diagnostics and repair workflows | `slimefun.command.doctor` |
| `/sf blockdata` | Inspect Slimefun block data | `slimefun.command.blockdata` |
| `/sf banitem` / `/sf unbanitem` | Item restriction administration | corresponding command permission |

## Doctor quick reference

```text
/sf doctor status
/sf doctor core
/sf doctor compatibility
/sf doctor dependencies
/sf doctor runtime
/sf doctor integrations
/sf doctor scan
/sf doctor repair confirm
```

See [Doctor & Diagnostics](Doctor-and-Diagnostics.md) before running repair operations.

## Important bypass permissions

These default to operators and should be delegated carefully:

- `slimefun.android.bypass`
- `slimefun.cargo.bypass`
- `slimefun.inventory.bypass`
- `slimefun.gps.bypass`
- `slimefun.debugging`

Other sensitive permissions include:

- `slimefun.cheat.items`
- `slimefun.cheat.researches`
- `slimefun.command.backpack.other`
- `slimefun.android.upload-script`

## Permission-management advice

Grant the smallest permission set a role needs. Avoid broad wildcard grants for staff who only need Guide/search or read-only version information.