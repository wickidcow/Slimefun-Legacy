# r155: correct Cultivation and HotbarPets versions omitted from v4.1.80

The **published v4.1.80 release and its assets remain unchanged**. Its r154 ZIP was
successfully built but pinned Cultivation 1.1.23 and HotbarPets 1.0.4 even though
standalone 1.1.24 and 1.0.6 had already been released.

The r155 source matrix now targets two **published, merged release commits**:

- Cultivation **1.1.24** `ab97e9d1a89a57bfca3801fce7cef75020fd9bf0` from
  https://github.com/wickidcow/SF_CultivationLegacy/releases/tag/v1.1.24
  (published JAR `SF_Cultivation1.1.24.jar`;
  SHA-256 `f6b395fe4ca8d50cfbae6d51432e330e02e7b33cfedbb9a4636853d82b573ccd`).
- HotbarPets **1.0.6** `54c802438a6819e6f3b0eea411e675a3eb0855dc` from
  https://github.com/wickidcow/SF_HotbarPets/releases/tag/v1.0.6
  (published JAR `SF_HotbarPets1.0.6.jar`;
  SHA-256 `c873c2fb2eeec6ffa3c8248320025ae330271e7e53d09ecfac3fda9d53bb6258`).

Retain the 44-addon roster, exclusions, filenames and all other 42 r154
source selections, notably FluffyMachines 26.2.18, Networks 1.0.64, IE2 2.0.18,
Supreme 1.0.21 and MagicExpansion 1.1.19. Historical native test evidence stays
bound to r154; r155 mappings explicitly reuse the same storage and Magic
binary/source pairs.

This manifest/fixture update is only a **new-bundle source candidate**.
Before publishing it through a new distinct release, execute exact-source
44-addon build, ZIP manifest/SHA checks, core-compatibility and full-stack/
native conservation and restart tests. No existing released tag, JAR or ZIP
may be overwritten. Standalone plugin binaries remain independently available.
