# Optional resource-pack support

Slimefun Legacy supports custom item model IDs independently from resource-pack delivery.

## Default behavior

Resource-pack delivery is **disabled by default**. Slimefun Legacy does not upload, host, download or force a resource pack unless a server owner explicitly enables the external sender in `plugins/Slimefun/config.yml`.

This is intentional for servers that already use ItemsAdder, Oraxen, a proxy-level pack, or their own server resource-pack workflow.

```yaml
resource-pack:
  enabled: false
  url: ''
  sha1: ''
  required: false
  prompt: 'Slimefun Legacy resource pack'
```

When `enabled` is `false`, Slimefun Legacy sends no pack request at all.

## External pack delivery

To let Slimefun Legacy add an externally hosted pack on player join:

1. Host the completed resource-pack ZIP on an HTTP(S) endpoint reachable by players. HTTPS is recommended.
2. Set `resource-pack.enabled` to `true`.
3. Set `resource-pack.url` to the direct ZIP URL.
4. Set `resource-pack.sha1` to the 40-character SHA-1 of that exact ZIP when possible.
5. Leave `required: false` unless the server should reject players who decline the pack.
6. Restart the server or reload the Slimefun configuration through the supported server workflow.

Slimefun Legacy uses Minecraft's additive resource-pack API so an explicitly enabled Slimefun pack can coexist with another server pack rather than replacing it. The implementation targets the modern API available on Minecraft 1.21.11+ / current Paper server lines.

## ItemsAdder servers

If ItemsAdder already builds and sends the server's combined pack, leave this feature disabled:

```yaml
resource-pack:
  enabled: false
```

The Slimefun item-model mappings can still be used. ItemsAdder can include the matching models/textures in its generated pack while Slimefun Legacy supplies the configured `CustomModelData` values on the items.

## Paxel model compatibility

The established Slimefun resource-pack mapping uses model ID `2201302` for the FluffyMachines Paxel. Slimefun Legacy intentionally maps both IDs to that same model:

```yaml
PAXEL: 2201302
ADVENTURERS_PAXEL: 2201302
```

The IDs remain distinct to avoid a Slimefun registration collision when FluffyMachines and Adventurer's Curios are both installed/enabled.

## AdvanceTexture

The optional sender and model-mapping workflow were designed with the same server-owner use case addressed by the community AdvanceTexture project (`m1919810/AdvanceTexture`), but Slimefun Legacy does not bundle or require that plugin. The Legacy implementation uses its own existing custom-texture service and current Paper APIs so the feature can remain optional and dependency-free.
