# ❓ Frequently Asked Questions

## Is Slimefun Legacy a mod?

No. It is a server plugin. Players connect with a normal Minecraft Java client unless the server separately requires something else.

## Do players need a resource pack?

Not for Slimefun Legacy itself. A server may choose to provide a resource pack for its own content or addons.

## What server software should I use?

The primary target is Paper 26.2 / Minecraft 26.2 on Java 25. Purpur based on that Paper line is supported. Folia is experimental.

## Can I use Spigot?

Slimefun Legacy does not list Spigot/CraftBukkit as a supported runtime target. Use the supported Paper line for production.

## Can I use old Slimefun addons?

Sometimes. Legacy intentionally preserves historical API/data compatibility where practical, but the addon ecosystem is too large to guarantee every old JAR. Check `/sf versions` and `/sf doctor compatibility`.

## Does CI-tested mean guaranteed compatible?

No. CI coverage is compatibility evidence for known source/build combinations. Your exact addon release, dependency set and configuration can still differ.

## Can I move an existing Slimefun world to Legacy?

Legacy is designed to preserve established data formats where practical, but any core migration can be destructive if done carelessly. Make a complete backup and stage the upgrade first.

## Why does `plugin.yml` say `api-version: 1.16` if Legacy targets modern Minecraft?

That descriptor value is retained for historical Bukkit/material/addon behavior. It is not the supported Minecraft-version floor.

## Why are some item names still translated after I switch to English?

Existing item stacks can store their old display metadata. Use the Storage & Item Doctor dry-run workflow rather than manually rewriting items.

## Can I use Slimefun Translate?

Legacy's normal English-first experience does not require it. If you deliberately run translation tooling, test the exact setup carefully because existing items can retain metadata.

## What should I do before reporting a bug?

Run `/sf versions`, Doctor status/compatibility/dependency checks, save the full exception and reproduce on staging if possible. See [Troubleshooting](Troubleshooting.md).

## Can I disable items?

Slimefun traditionally supports server-side item disabling/configuration. Exact availability should be verified in your current Legacy configuration and in-game Guide.

## Is Slimefun Legacy officially affiliated with Mojang or Microsoft?

No. It is an independent community project and is not approved by or associated with Mojang or Microsoft.

## Can I donate?

If the project maintainer provides an official donation link, donations should be treated as optional support for development/maintenance, not as payment for ownership, guaranteed features or special rights.