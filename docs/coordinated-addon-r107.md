# Coordinated addon revision 107 — October 2, 2026

## Source selection and preserved release boundary

Continue from the exact 4.1.66 core source `22ae22c4e32fd3b086565922f4427206d0f4b065`. The revision-106 core release is independently validated/published; this addon-only selection must not change that release's core JAR or tag.

The complete 45-repository inventory found two already published addon versions newer than the selected bundle sources:

| Addon | Previous bundle | Selected release | Exact source |
| --- | --- | --- | --- |
| HotbarPets | 1.0.2 | 1.0.3 | `ac1e2213686a83acd52d82c01434ab12b48e64f3` |
| SimpleMaterialGenerators | 1.0.4 | 1.0.5 | `014a7e55a832255e6dd7fc4677b05480624a0fd7` |

All 43 other source selections, all 45 members and every exclusion are unchanged. Core source, version values, storage schemas, item/research IDs, recipes, capacities, production rates, transfer priorities and migration defaults are unchanged by this selection update. Existing standalone releases are not republished or overwritten.

The HotbarPets source difference includes the supported crafting-menu API in WorkbenchPet while retaining forced access and sound, plus build/test/version maintenance. The SMG difference has no production Java changes; it adds generator behavior tests and build/version maintenance. Neither update introduces a data conversion.

## Actual exact-source validation

Run `37063708114` checked out both exact sources and built against the checksum-verified 4.1.66 canonical core artifact `11250818006`. Each addon compiled on the 1.21.11, 26.2 and 26.3 API lines and then ran its full baseline Maven `clean verify` suite:

- HotbarPets: 13 tests, no failures/errors/skips. Current/legacy entity markers and owner data retain their names, types, precedence and refusal behavior.
- SMG: 11 tests, no failures/errors/skips. Generator cadence/output, full-target handling, independent ownership of output templates, and block/chunk/world/global cleanup behavior are checked, including 1,000 generator ticks.

A second independent clean baseline build was byte-identical for each addon. Both rebuilt JARs also match the actual previously published standalone release bytes:

- `SF_HotbarPets1.0.3.jar`: `d0598baad3e8b5d9f759f7b7e860291c9ef301df833174eeaedab6501cee8d64`.
- `SF_SMG1.0.5.jar`: `0d6971ecaaf690139cc96a305337d00748ae07a7f195e42ce7b3e9d96be4cd91`.

Actual publication downloads were checked against release asset metadata/digests, and the rebuilt JARs passed CRC, descriptor/version, all-base-class Java 21 and no-test-library checks. Evidence artifact `11252010720` SHA-256 is `6725f7f499f7930d3a6d1bc22f1d6fc463f8da60146360394b6f4cab8c2befb7`. Downloaded XML, both reviewed metadata blobs and standalone JAR bytes were independently inspected.

Detailed per-addon evidence is retained in `compatibility/addon-validation-r107.json`. The older aggregate test ledger remains historically attributed, rather than silently relabeling its old revisions or counts. These 24 project-test entries are not one new full-suite run of all 45 addons. API compilation does not certify live-player workbench opening, populated customer worlds, Folia region safety, crash recovery or every future API.

## Remaining promotion checks

The clean revision-107 selection requires its own normal canonical 45-addon build and full-stack checks. Before replacing the 4.1.66 aggregate ZIP, also test that exact archive with the exact published 4.1.66 core binary on disposable supported-version servers, with all 45 addons required and clean restart confirmation. Keep real WorldEdit and WorldEditSlimefun mandatory.

An eventual addon-only publication must back up the prior ZIP/metadata, verify the new uploaded bytes and retain the original core asset and release tag unchanged. Do not publish an untested source archive as a plugin, remove an addon to hide a failure, or arbitrarily increment otherwise unchanged standalone versions. At this checkpoint the selection is prepared, not a claim of a completed bundle replacement.
