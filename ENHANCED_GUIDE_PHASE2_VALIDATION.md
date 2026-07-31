# Enhanced Guide Phase 2 validation

## Static checks completed

- Phase 1 menu, search and bookmark files remain present.
- Recipe-page decoration occurs only after the native Slimefun recipe page opens.
- The fill button uses a Slimefun-owned persistent-data key and a short-lived inventory-bound context.
- Only Enhanced Crafting Table, Magic Workbench and Armor Forge recipe types are accepted.
- Target resolution validates each machine's physical structure.
- Protection and Folia ownership checks occur before inventory snapshots or writes.
- Player and dispenser inventory changes are planned on clones and committed only after success; a failed commit attempts to restore both original snapshots.
- Existing incompatible recipe-slot contents abort instead of being overwritten.
- Actual player item metadata is retained during transfer, with machine-specific matching and backpack compatibility aligned to the core crafting implementations.
- Dragging, shift-transfer, double-click collection, expired sessions and disconnects cannot extract or overwrite the protected guide button.
- No automatic crafting, nearby-container access or recursive recipe execution is present.

## Required build and staging checks

The full repository must still run through GitHub Actions with its normal formatting, compilation and test workflows. On a copied server, test vanilla and Slimefun ingredients, repeated ingredients, stack amounts, backpacks, full dispensers, protected claims, Folia region boundaries and shift-click filling before production use.
