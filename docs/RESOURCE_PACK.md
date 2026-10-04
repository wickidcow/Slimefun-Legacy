# Optional resource-pack support

Slimefun Legacy supports custom item model IDs independently from resource-pack delivery. Legacy-specific delivery settings live in `configSFLAddons.yml`.

## Default behavior

Resource-pack delivery is **disabled by default**. Slimefun Legacy does not upload, host, download or force a resource pack unless a server owner explicitly enables the external sender in `plugins/Slimefun/configSFLAddons.yml`. The resource-pack section is independent of the top-level Curiosities/additions `enabled` switch.

This is intentional for servers that already use ItemsAdder, Oraxen, a proxy-level pack, or their own server resource-pack workflow.

```yaml
resource-pack:
  ownership-mode: auto
  enabled: false
  # RECOMMENDED (GitHub): https://github.com/wickidcow/SFL_RP_Official/releases/latest/download/SlimefunLegacyRP.zip
  url: 'https://github.com/wickidcow/SFL_RP_Official/releases/latest/download/SlimefunLegacyRP.zip'
  sha1: ''
  required: false
  prompt: 'Slimefun Legacy resource pack'
```

When `enabled` is `false`, Slimefun Legacy sends no pack request at all. The **recommended** URL is the latest Slimefun Legacy resource-pack release at `https://github.com/wickidcow/SFL_RP_Official/releases/latest/download/SlimefunLegacyRP.zip`.

The retired Modrinth default is no longer recommended. On upgrade, known retired built-in URLs (including the old Modrinth URL) are rewritten in `configSFLAddons.yml` to the recommended GitHub release URL. Any SHA-1 associated with a retired pack is cleared during that rewrite so Minecraft does not validate the replacement ZIP against an obsolete checksum. Custom HTTP(S) pack URLs are preserved.

Minecraft changed the item-model resource-pack format substantially in the modern 1.21.x line. A pack built for an older item-model layout can load successfully while still showing vanilla, missing, or misplaced guide/item visuals. Packs that select numeric models must match the server's configured `item-models.yml` values.

## Client pack vs. server model map

For packs that select numeric models, these two separate files must stay synchronized:

- **Player/client download:** `https://github.com/wickidcow/SFL_RP_Official/releases/latest/download/SlimefunLegacyRP.zip`
- **Server mapping:** `plugins/Slimefun/item-models.yml`

Minecraft clients receive only the ZIP. The YAML is never sent as the resource pack; Slimefun reads it on the server and applies the matching model IDs to item stacks.

Slimefun Legacy retains the bundled non-zero mapping as a reference for explicit adoption and historical recovery. **All missing server mappings default to `0`**, including on a clean install and when an existing file lacks an entry. Existing saved zero and non-zero values are preserved. Pack delivery does not opt items into model metadata, even when the sender is enabled or a server uses ItemsAdder.

Resource packs can instead match an item's existing `slimefun:slimefun_item` identity using modern client component predicates. Such a pack does not require model-number adoption or a saved-item conversion. This requires a compatible pack and client; it does not retroactively fix mismatched metadata already on items. The companion pack preview must pass its own client testing before a stable release.

Only when deliberately adopting a pack that requires the bundled numeric model values, use `/sf doctor item-models enable-pack scan` followed by the separate confirmed adoption workflow. That workflow changes metadata and does not cover offline player inventories, unloaded physical containers or arbitrary addon-owned storage. Do not describe a clean reachable scan as a complete world conversion.

## Versioned config safety notes

`configSFLAddons.yml` now has its own independent `config-version`. This is a configuration-schema version, not the Slimefun Legacy plugin version.

Normal plugin updates do **not** rewrite the file on every start. When the config schema itself changes, Legacy performs a one-time migration, advances `config-version`, and can inject new operator notes without repeatedly replacing the server owner's file.

Config version 1 added the resource-pack safety guide directly above the `resource-pack:` section. Config version 2 adds an explicit `resource-pack.ownership-mode` so Doctor can distinguish who is expected to deliver Slimefun textures without guessing from the sender toggle.

The supported ownership modes are:

- `auto` — backwards-compatible behavior; infer delivery from `resource-pack.enabled`.
- `legacy` — Slimefun Legacy owns delivery of the configured official/custom ZIP.
- `external` — ItemsAdder, Oraxen, a proxy, server-level pack system, or another combined-pack manager owns delivery.
- `none` — the server explicitly declares that no Slimefun-textured pack is intended.

Changing ownership never rewrites `item-models.yml` or stored ItemStacks. `external` deliberately supports **Legacy sender OFF + Slimefun mappings ON**. `none` may cause Doctor to recommend reviewing unused bundled mappings, but cleanup remains a separate confirmed Doctor action.

Disabling `resource-pack.enabled` only disables Legacy's sender. It does not rewrite `item-models.yml` and does not strip CustomModelData from stored items. This is important for servers that stop using Legacy's sender but continue serving the matching models through ItemsAdder, Oraxen, a proxy pack, or another combined-pack manager.

## External pack delivery

To let Slimefun Legacy add an externally hosted pack on player join:

1. Make sure the hosted pack supports the Minecraft client generation used by your players.
2. For numeric packs, make sure the Slimefun model IDs match `plugins/Slimefun/item-models.yml`. A compatible identity-based pack can read existing Slimefun IDs with mappings left at zero.
3. Set `resource-pack.enabled` to `true`.
4. Leave `resource-pack.url` at the included Slimefun Legacy GitHub release URL, or replace it with another direct HTTP(S) ZIP URL.
5. Set `resource-pack.sha1` to the 40-character SHA-1 of that exact ZIP when possible.
6. Leave `required: false` unless the server should reject players who decline the pack.
7. Restart the server or reload the Slimefun configuration through the supported server workflow.

Slimefun Legacy uses Minecraft's additive resource-pack API so an explicitly enabled Slimefun pack can coexist with another server pack rather than replacing it. The implementation targets the modern API available on Minecraft 1.21.11+ / current Paper server lines.

## ItemsAdder servers

If ItemsAdder already builds and sends the server's combined pack, declare external ownership:

```yaml
resource-pack:
  ownership-mode: external
  enabled: false
```

The Slimefun item-model mappings can still be used. ItemsAdder can include the matching models/textures in its generated pack while Slimefun Legacy supplies the configured model value on the item's modern custom-model-data component. Alternatively, merge compatible identity-based item definitions into the combined pack without adopting numeric mappings. Multiple packs that override the same vanilla item definition still require a deliberate merge.

## Modern CustomModelData compatibility

Slimefun's existing `item-models.yml` format intentionally remains numeric for addon compatibility. On modern Paper/Minecraft, Slimefun Legacy stores that number as the **first float** in the custom-model-data component, which is the platform-defined equivalent of the old integer CustomModelData value.

This preserves existing Slimefun/addon numeric mappings while avoiding the deprecated integer item-meta API. Additional component floats, flags, strings, and colors supplied by other integrations are preserved.

## Guide and UI textures

The guide is not a separate rendering engine. Guide buttons are ordinary item stacks with Slimefun IDs such as:

- `_UI_BACKGROUND`
- `_UI_BACK`
- `_UI_MENU`
- `_UI_SEARCH`
- `_UI_WIKI`
- `_UI_PREVIOUS_ACTIVE` / `_UI_PREVIOUS_INACTIVE`
- `_UI_NEXT_ACTIVE` / `_UI_NEXT_INACTIVE`

Guide books use the existing `slimefun:slimefun_guide_mode` persistent key; `SLIMEFUN_GUIDE` is their numeric texture-mapping name. An identity-based pack should recognize both `SURVIVAL_MODE` and `CHEAT_MODE` using that key.

If the selected pack relies only on numeric models, zero mappings do not select those guide textures. A compatible identity-based pack can select them without numeric model data. Copying numbers from a different pack can make the wrong model appear in a guide slot; preserve an existing working mapping unless deliberately changing pack ownership.

## Paxel model compatibility

The established Slimefun resource-pack mapping uses model ID `2201302` for the FluffyMachines Paxel. Slimefun Legacy intentionally maps both IDs to that same model:

```yaml
PAXEL: 2201302
ADVENTURERS_PAXEL: 2201302
```

The IDs remain distinct to avoid a Slimefun registration collision when FluffyMachines and Adventurer's Curios are both installed/enabled.

## AdvanceTexture

The optional sender and model-mapping workflow were designed with the same server-owner use case addressed by the community AdvanceTexture project (`m1919810/AdvanceTexture`), but Slimefun Legacy does not bundle or require that plugin. The Legacy implementation uses its own existing custom-texture service and current Paper APIs so the feature can remain optional and dependency-free.


## Repairing stale model data after disabling mappings

If a server previously ran with the bundled Slimefun Legacy model map and later returns affected IDs to `0` in
`plugins/Slimefun/item-models.yml`, items created while the mapping was active can still carry that old model
value. That can make otherwise identical Slimefun items fail normal stacking or strict addon item comparisons.

Use the guarded Doctor workflow:

```text
/sf doctor item-models scan
/sf doctor item-models repair confirm
```

The repair only considers currently registered Slimefun IDs whose current configured mapping is `0`, and only
when the stack's first CustomModelData float exactly matches the bundled Slimefun Legacy value for that same ID.
It removes only that bundled float, preserving any additional floats, flags, strings, or colors from other
integrations. If the bundled float was the only custom-model-data content, the component is removed completely.

This repair is never automatic. Run the scan first, make an offline backup, and use the explicit repair command
only after reviewing the candidate counts.

## Model-map synchronization

The official client pack is published separately at:

`https://github.com/wickidcow/SFL_RP_Official/releases/latest/download/SlimefunLegacyRP.zip`

For numeric packs, the client ZIP and the server-side `plugins/Slimefun/item-models.yml` mapping must stay synchronized. Slimefun Legacy bundles the matching non-zero model IDs, but existing zero mappings are preserved unless the owner explicitly adopts the pack through the guarded `enable-pack` Doctor workflow. Existing non-zero server customizations are preserved. The ID-pack preview described below uses existing item identities with mappings at zero.

## Slimefun Legacy release mirror

Each Slimefun Legacy GitHub release also publishes the pinned stable official pack as a **separate** `SlimefunLegacyRP.zip` asset beside the core JAR and the maintained-addon ZIP. The release workflow never rebuilds or modifies that ZIP: it downloads the exact approved release from `wickidcow/SFL_RP_Official`, verifies the pinned GitHub asset metadata, byte size, SHA-256, ZIP integrity, `pack.mcmeta`, `pack.png`, and the presence of resource namespaces, then uploads those exact bytes.

The source selection is recorded in `compatibility/resource-pack-release.json`. Stable Slimefun releases deliberately ignore resource-pack prereleases unless that pin is reviewed and advanced. This mirror is for convenient versioned downloads; the default automatic sender can continue using the dedicated resource-pack repository's stable `releases/latest/download/SlimefunLegacyRP.zip` URL so the pack can be maintained independently of core releases.

On modern Paper/Minecraft, Slimefun Legacy stores the historical numeric model ID as the first float in Minecraft's CustomModelData component. Additional component floats, flags, strings, and colors supplied by other integrations are preserved.

## Configuration migration

On upgrade, an existing `resource-pack:` section in `plugins/Slimefun/config.yml` is copied into `configSFLAddons.yml` without overwriting values already configured there. The old core-config section is removed only after the addon config has been saved successfully.

After that migration, known retired Slimefun Legacy pack URLs are normalized to the recommended GitHub release URL and persisted back to `configSFLAddons.yml`. This includes the retired Modrinth URL. A saved SHA-1 is cleared only when one of those known retired URLs is replaced; custom URLs and their hashes are left alone.


### v4.1.52 storage compatibility recovery

v4.1.52 briefly upgraded existing zero item-model placeholders to the bundled hosted-pack map. That behavior has been removed. Affected servers can audit with `/sf doctor item-models remove-resourcepack-texture-ids` (the historical `rollback-v52` alias still works); after an explicit confirmed rollback and clean restart, use `/sf doctor item-models scan` followed by `/sf doctor item-models repair confirm` to normalize reachable stored ItemStacks. Custom non-matching model values are preserved.

## Doctor install and uninstall using existing item IDs

The ID pack reads existing Slimefun identity data. These commands reset exact bundled numeric mappings to `0`
and remove matching stale first model floats, preserving custom mappings and the remaining item metadata.
They never replace items with fresh templates.

```text
/sf doctor resource-pack install scan
/sf doctor resource-pack install confirm
/sf doctor resource-pack uninstall scan
/sf doctor resource-pack uninstall confirm
/sf doctor resource-pack status
/sf doctor resource-pack resume
```

Omitting the mode starts a read-only scan. `confirm` saves authorization for initial cleanup, restart continuation
and future join/load cleanup. Ordinary resource-pack delivery toggles do not authorize item changes.
Use a maintenance window and a full server backup before confirming.

Install pins the official GitHub `v4.1.0-id-preview.1` ZIP with SHA-1
`0d667fe74db4a9456aa5a603d71921bfc5a0d6fe`. That preview still needs client and combined ItemsAdder testing.
An existing `external` pack owner remains external with Legacy delivery disabled; merge the ID definitions through
that manager. Confirming either operation also resets matching Legacy mappings used by a numeric combined pack.
Uninstall removes only Legacy's resource-pack UUID and its eligible numeric metadata.

If runtime item or guide templates still contain old bundled values, Doctor pauses and saves an `awaiting-restart`
checkpoint. Stop normally and restart. The authorized cleanup resumes after registration with fresh templates.
Later restarts and `resume` retry cleanup without overwriting subsequent sender, ownership or URL choices.

| Storage | Coverage |
| --- | --- |
| Online players | Inventories including armor/offhand, ender chests, cursor and open backpacks |
| Loaded containers | World containers, inventory-holder entities and dropped items |
| Slimefun backpacks | All persisted profiles, cache ownership guard and acknowledged saves |
| Slimefun machines/storage | Loaded block/universal menus and guarded rewrites of unloaded inventory rows |
| Nested items | Bundles and container item metadata, up to four nested levels |
| Offline players | Deferred until join; player files are not rewritten |
| Unloaded vanilla containers | Deferred until chunk load; generated chunks are not force-loaded |
| Addon-private storage | Requires its owning addon's adapter; arbitrary private databases are untouched |

Unavailable IDs and unreadable records remain intact. Unfinished/pending-removal inventories are preserved and
reported for a later resume. On Folia, gameplay-owned cached backpacks require an online owner's open inventory;
ownerless mutation requires an exclusive maintenance guard. Loaded universal and virtual menus without a safe
owner are deferred on Folia. Unloaded universal rows remain eligible for the guarded database pass.
Item-frame/display/equipment-only entities and deeper nesting are outside this inventory traversal.

Each changed item is backed up before mutation in
`plugins/Slimefun/backups/resource-pack-doctor/<operation-id>/items.tsv`. Exact binary/text database before-images
are journaled in `rows.tsv`; configuration snapshots share that directory. The durable checkpoint is
`resource-pack-doctor.yml`. Backup failure prevents the affected change. Stale, loaded or missing database rows
are skipped and reported. These journals support manual recovery and do not replace a complete world backup.

### Native verification

`scripts/resource_pack_doctor_probe.py` creates a disposable server directory from a cached Paper installation's
code dependencies and runs synthetic fixtures across three normal boots. It refuses to replace an existing
directory. The fixture plugin is for this probe only and must not be installed on a production server.

The final candidate passed on Paper `26.3-143-ff3655a` with Java `25.0.4.1`: read-only preview, restart checkpoint,
install, nested containers, backpack and unloaded SQLite inventory rewrites, deferred chunk cleanup, uninstall,
and preservation of later external ownership. Native item equality checks preserve opaque ItemsAdder-style PDC,
charge, identity, quantities and unrelated model-component lanes. This does not validate client rendering, a real
ItemsAdder installation, historical production-world data, or the Folia/Purpur/minimum-version runtime matrix.
