# Resonance Beacon — Slimefun Legacy 4.1.30B

Slimefun Legacy 4.1.30B updates the Resonance Beacon field model so its visible area and runtime behavior match chunk boundaries cleanly.

The repository remains on the numeric `4.1.30` development line so the existing Legacy compatibility gates retain their historical three-part version contract. The published Resonance Beacon JAR is built with the maintenance version **4.1.30B**, so the server/plugin build itself reports the B release version.

## Full-height field powers

Resonance Beacon field powers use a chunk-aligned square footprint instead of a spherical three-dimensional radius.

- Every covered chunk column is affected from the world's minimum build height through its maximum build height.
- There is no vertical-distance limit inside a covered chunk column.
- Horizontal coverage remains derived from the beacon's effective range, including imported range overrides and Extra Range.
- Normal field powers only operate in chunks that are already loaded; they never load additional chunks by themselves.
- Existing bounded work limits for tile entities, crop sampling, and other machine/block effects remain in place.

## Show Effect Area

The Show Effect Area control now renders the exact chunk grid covered by the field.

- The old spherical/three-ring particle preview is removed.
- The preview is a flat square chunk grid rendered at each viewer's current Y level.
- Chunk boundary lines make the exact affected chunk columns visible.
- The preview is display-only and does not load chunks.

## Activator chunk loading

Activator remains the only Resonance Beacon power that intentionally keeps chunks loaded.

- Tier I: current chunk.
- Tier II: 3x3 chunks.
- Tier III: 5x5 chunks.
- Existing global safety caps remain 64 active Resonance Beacon loaders and 256 unique ticketed chunks.
- `/beacon enable`, `/beacon disable`, and `/beacon status` continue to control Activator chunk loading globally.

The full-height field model does not increase Activator coverage and does not turn ordinary beacon effects into chunk loaders.

## Validation

The cleaned source passed the Adventurer's Curios verifier, special-item correctness verifier, full Slimefun Legacy invariant suite, Gradle tests, and a `shadowJar` build without applying repository-wide formatting changes.
