# Mandatory WorldEdit runtime and item-preservation integration

## Scope

This candidate extends coordinated PR #292 at `7c8320828f7d0f98e80246f2766e7814a8a15eca`. It retains all 45 maintained addon members, the existing core production source and the concurrent persisted-data corrections. Bundle revision 97 is a candidate source-set identifier, not a stable release or plugin version bump. Core remains 4.1.62. No master/main merge or automatic data migration is implied.

Only two addon source pins change from revision 96: FNAmplifications to `2eb07e7bca82d4d7621224e6f9e4942481e7e8f7` (PR #6, stacked on component PR #5), and LiteXpansion to `4dc69e1ba835b897e2fa38b9eb54bcbcee644abf` (Cargo Configurator component PR #5). These preserve newer source changes rather than applying obsolete saved patches over them. The retained `maintained-addon-test-ledger.json` explicitly describes the prior revision-96 evidence; it must not be misrepresented as final revision-97 validation.

## WorldEdit requirement

The full-stack runner previously allowed WorldEditSlimefun to be excluded when WorldEdit was absent. Each supported lane must now stage an official, verified Bukkit WorldEdit provider, require WorldEditSlimefun in the enabled-addon list, and verify both provider/addon enable evidence on each boot. The external provider is not another addon member in the release ZIP.

Selection requires the exact Minecraft version in published metadata, approved project/source identity, Bukkit-compatible loader, a unique primary runtime artifact, published size/SHA-512, restricted download transport, archive integrity and plugin main/name checks. Applicable class files must fit Java 21 for 1.21.11 or Java 25 for newer lanes; preview bytecode is rejected. Prerelease selection is explicit only in the 26.3 candidate lane. The recorded version ID can be supplied for exact replay.

A real validation run exposed a newer provider that declared the floor Minecraft version but required Java 25. The selector now checks up to twelve exact-version candidates, recording each Java-incompatible rejection. It never falls back on checksum, identity, archive or network failure, and an explicit version pin never silently selects another artifact. The floor's Java requirement is not weakened.

`worldedit-runtime.json` records provider identity, release channel, filename, published support, exact hashes and rejected candidates. The staged JAR is rechecked before addon classification and after each boot. Configuration/linkage failure checks and exact matching-core/bundle provenance remain in force. Missing or unsuitable WorldEdit fails a lane rather than improving its apparent enable count.

## Actual pre-integration evidence

[WorldEdit validation run 36810221723](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/36810221723) passed all 76 offline dependency/provenance/wiring tests and downloaded/verified actual provider JARs for all three lanes. Its artifact `11139013736` SHA-256 is `60fb3da842afa3111b4b792a3a3cd22ee9b554d585fc12e1b213ebc1d1093380`; the six promoted source blob hashes were checked against that artifact.

| Lane | Verified provider | Channel / Java |
| --- | --- | --- |
| 1.21.11 | WorldEdit 7.4.2+7450-eb8e82c, version ID p8T2aZ8U | Release / Java 21 |
| 26.2 | WorldEdit 7.4.5+7590-b8dc4c1, version ID F5ea2ov3 | Release / Java 25 |
| 26.3 | WorldEdit 7.4.6-beta-02+2c90a77a1, version ID J1eeOh6C | Beta / Java 25 |

These are dependency-validation results, not completed server boots. The candidate's new normal full-stack run must independently boot with the exact built core and revision-97 bundle. No earlier successful bundle is substituted. The initial run `36810015614` correctly failed at the Java ceiling; its 67 then-existing regression tests had passed.

FNAmplifications normal run `36809172646` passed both Legacy and United API builds, 39 tests per verified lane with no failures/errors/skips. The 22 new operation tests use real component code with explicit ItemMeta/PDC doubles; they are not native CraftItemStack/player-inventory certification. Valid random failures retain the original whole-stack cost and `nextInt(100) <= chance` rule; invalid data and rejected metadata updates do not consume a tool. LiteXpansion's normal PR run `36806029740` passed independently. Its saved cargo JSON/LX2 and Doctor fingerprint formats remain unchanged by the component work.

## Release boundaries

A source/build/smoke success does not prove all old worlds, every addon machine, concurrent Folia ownership or physical crash behavior. Real native-item tests and exact candidate artifacts remain separately attributable. Do not label this a stable release merely because dependency setup or the previous revision passed.

Preserve original item/research IDs, typed data, owners, inventories, recipes, production/energy/transfer behavior and legacy readers. United-to-Legacy upgrades remain relevant; reverse conversion to United is not reinstated as a release gate. The established primary Paper/Purpur and secondary Leaf/Folia priorities remain unchanged. Rebar/Pylon and excluded/integrated addons are not added.
