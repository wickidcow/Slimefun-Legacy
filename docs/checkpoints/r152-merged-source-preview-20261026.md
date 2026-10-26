# Slimefun Legacy 4.1.78 — r152 merged-source diagnostic

This branch is a **diagnostic candidate**, not a public release and not an eligible distribution ZIP. It is layered on Slimefun Legacy draft #483 (combined core chunk protections and 45-addon r151 preview).

The same 45 source-locked addon repositories are retained. Revision 152 advances only the exact source identities for:

| Maintained addon | Previous PR source | Final merged source identity |
| --- | --- | --- |
| SF_NetworksExp 1.0.62 | \`288640e6dbf907a6ba238d98af206aab2d56f92b\` | \`79e4c0116cc4b4683c668a4920e67692f925b83f\` |
| SF_JustEnoughGuide 2.1.74 | \`9107d23c2eaa34e6477f561fb1f42d93c7e50195\` | \`a05fda6c5ce3310fa339ebe0f2f00801197e6d9b\` |
| SF_MagicExpansion 1.1.19 | \`c9be11af6b32f3e0d517803b9362c7c88312b150\` | \`b28c304f2909f15b43da53910ce7650886cc0e6f\` |

Both the Networks/Fluffy conservation probe and MagicExpansion four-JVM Black Market probe now **reject** obsolete PR-source identities for r152. All previous r135–r151 exact-source fixtures stay intact. Static verifier success is not proof of native gameplay acceptance.

## Outstanding 4.1.78 gates

- Check actual **published immutable** standalone JARs and SHA-256 for Networks, IE2, HotbarPets, JEG, Magic and BuildingStaff. A merged source or queued publisher is not a release.
- Review BuildingStaff 1.0.36 native projection geometry/protection/cleanup on Paper/Purpur; no claim of Folia multi-region compatibility.
- Integrate separately tested Fluid Pump repair #486 and confirm existing machine data and rollback contracts.
- Repin published storage-pair receipts only after verified public artifact digests exist; do not disable these release gates or substitute CI-only unreleased preview files.
- Confirm 45-addon release eligible bundle on **final master commit**, native four-JVM Black Market receipts and Networks/Fluffy/IE2 storage, all Paper/Purpur/Leaf/Folia supported lanes, public API 4.1.77 baseline and two byte-identical core builds.
- Preserve existing Java resource-pack bytes and previous immutable releases. Do not add Bedrock or Geyser functionality.
- Official 4.1.78 release publication requires the maintainers' explicit approved GitHub Actions dispatch (publish=true), and must never occur from this branch.

