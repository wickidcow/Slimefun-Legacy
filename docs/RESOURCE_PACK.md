# Optional resource-pack support

Slimefun Legacy supports custom item model IDs independently from resource-pack delivery.

## Default behavior

Resource-pack delivery is **disabled by default**. Slimefun Legacy does not upload, host, download or force a resource pack unless a server owner explicitly enables the external sender in `plugins/Slimefun/config.yml`.

This is intentional for servers that already use ItemsAdder, Oraxen, a proxy-level pack, or their own server resource-pack workflow.

```yaml
resource-pack:
  enabled: false
  url: 'https://github.com/wickidcow/SFL_RP_Official/releases/latest/download/SlimefunLegacyRP.zip'
  sha1: ''
  required: false
  prompt: 'Slimefun Legacy resource pack'
```

When `enabled` is `false`, Slimefun Legacy sends no pack request at all. The default URL points to the latest Slimefun Legacy resource-pack release at `https://github.com/wickidcow/SFL_RP_Official/releases/latest/download/SlimefunLegacyRP.zip`.

Minecraft changed the item-model resource-pack format substantially in the modern 1.21.x line. A pack built for an older item-model layout can load successfully while still showing vanilla, missing, or misplaced guide/item visuals, so this hosted pack must remain synchronized with Slimefun Legacy's `item-models.yml`.

## Client pack vs. server model map

These are two separate files that must stay synchronized:

- **Player/client download:** `https://github.com/wickidcow/SFL_RP_Official/releases/latest/download/SlimefunLegacyRP.zip`
- **Server mapping:** `plugins/Slimefun/item-models.yml`

Minecraft clients receive only the ZIP. The YAML is never sent as the resource pack; Slimefun reads it on the server and applies the matching model IDs to item stacks.

Slimefun Legacy bundles the verified non-zero mapping for the hosted pack. On a clean install those values are written into `plugins/Slimefun/item-models.yml`, while registered IDs without a texture mapping are added as `0`. On upgrade, a one-time migration replaces old saved `0` placeholders with the bundled hosted-pack value, but preserves every existing non-zero server customization.

## External pack delivery

To let Slimefun Legacy add an externally hosted pack on player join:

1. Make sure the hosted pack supports the Minecraft client generation used by your players.
2. Make sure the pack's Slimefun model IDs match `plugins/Slimefun/item-models.yml`.
3. Set `resource-pack.enabled` to `true`.
4. Leave `resource-pack.url` at the included Slimefun Legacy GitHub release URL, or replace it with another direct HTTP(S) ZIP URL.
5. Restart the server or reload the Slimefun configuration through the supported server workflow.
6. Set `resource-pack.sha1` to the 40-character SHA-1 of that exact ZIP when possible.
6. Leave `required: false` unless the server should reject players who decline the pack.\n7. Restart the server or reload the Slimefun configuration through the supported server workflow.

Slimefun Legacy uses Minecraft's additive resource-pack API so an explicitly enabled Slimefun pack can coexist with another server pack rather than replacing it. The implementation targets the modern API available on Minecraft 1.21.11+ / current Paper server lines.

## ItemsAdder servers

If ItemsAdder already builds and sends the server's combined pack, leave this feature disabled:

```yaml
resource-pack:
  enabled: false
```

The Slimefun item-model mappings can still be used. ItemsAdder can include the matching models/textures in its generated pack while Slimefun Legacy supplies the configured model value on the item's modern custom-model-data component.

## Modern CustomModelData compatibility

Slimefun's existing `item-models.yml` format intentionally remains numeric for addon compatibility. On modern Paper/Minecraft, Slimefun Legacy stores that number as the **first float** in the custom-model-data component, which is the platform-defined equivalent of the old integer CustomModelData value.

This preserves existing Slimefun/addon numeric mappings while avoiding the deprecated integer item-meta API. Additional component floats, flags, strings, and colors supplied by other integrations are preserved.

## Guide and UI textures

The guide is not a separate rendering engine. Guide buttons are ordinary item stacks with Slimefun IDs such as:

- `SLIMEFUN_GUIDE`
- `_UI_BACKGROUND`
- `_UI_BACK`
- `_UI_MENU`
- `_UI_SEARCH`
- `_UI_WIKI`
- `_UI_PREVIOUS_ACTIVE` / `_UI_PREVIOUS_INACTIVE`
- `_UI_NEXT_ACTIVE` / `_UI_NEXT_INACTIVE`

If the selected resource pack expects custom models for these IDs but Slimefun's `item-models.yml` leaves them at `0`, the pack cannot apply those guide textures. Likewise, copying model IDs from a different pack can make the wrong model appear in a guide slot. Use the mapping supplied for the exact pack you deploy.

## Paxel model compatibility

The established Slimefun resource-pack mapping uses model ID `2201302` for the FluffyMachines Paxel. Slimefun Legacy intentionally maps both IDs to that same model:

```yaml
PAXEL: 2201302
ADVENTURERS_PAXEL: 2201302
```

The IDs remain distinct to avoid a Slimefun registration collision when FluffyMachines and Adventurer's Curios are both installed/enabled.

## AdvanceTexture

The optional sender and model-mapping workflow were designed with the same server-owner use case addressed by the community AdvanceTexture project (`m1919810/AdvanceTexture`), but Slimefun Legacy does not bundle or require that plugin. The Legacy implementation uses its own existing custom-texture service and current Paper APIs so the feature can remain optional and dependency-free.
