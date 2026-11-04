# Slimefun Legacy crafting restrictions

Slimefun Legacy provides a separate `plugins/Slimefun/CraftingRestrictions.yml` for blocking
**crafting** without disabling a registered Slimefun item. Items remain registered, usable,
visible in the guide, tradeable and awardable via quests/crates/commands. The existing
`Items.yml` enabled flag still controls the entire item; do not change it for this purpose.

```yaml
enabled: true
default-message: "&cCrafting is disabled for %item%!"
items:
  FLIGHT_GEM:
    crafting-enabled: false
    message: "&6Flight Gems can only be earned through server rewards!"
  SOME_ADDON_ITEM:
    crafting-enabled: false
    message: "&cGet this item from a server event instead."
  AN_ALLOWED_ITEM:
    crafting-enabled: true
```

Use the item's **registered ID**, not its display name. IDs from addons use the same
global registry as core items, so a compatible addon item can be configured without
editing the addon. `FLIGHT_GEM` is an example ID and must be replaced if the actual
installed addon has named its item differently. Entries not explicitly blocked remain
craftable. `%item%` is the registered item name and `%id%` is its ID. Strings support
legacy ampersand colour codes. Blank per-item messages use the default message.

### Protected production paths

- Player crafting via the vanilla crafting table, including shift-click crafting.
- Vanilla Crafter block production of registered Slimefun outputs (no player message).
- Slimefun multiblock craft events, including core crafting table, armor forge and magic workbench.
- Ancient Altar: blocks new rituals before consuming catalysts, plus a final event guard
  for policies reloaded during an active ritual (the altar refunds when its completion is cancelled).
- Built-in Slimefun autocrafters.
- Core `AContainer` machines and addon machines that use its recipe finder; a prohibited
  recipe is skipped **before** ingredients are consumed.

Reload with the existing Slimefun configuration reload command, or restart the server.
Malformed YAML retains the previous valid policy and reports an error in the console.
In-progress machine operations that already reserved/consumed inputs before a reload
are allowed to complete, preventing loss of paid ingredients.

### Custom addon crafting

Some addons implement **their own** crafting inventories, tickers and output logic, bypassing
the core hooks above. Such handlers must check the policy **before** consuming inputs and
must not delete completed items from an output inventory as a workaround. For a player
craft, the addon can use:

```java
if (Slimefun.getCraftingRestrictionService().denyIfBlocked(player, output)) {
    return;
}
```

For autonomous machines, use `isBlocked(output)` or `isBlockedAny(outputs)` to skip
new operations without removing existing item stacks. A producer in an addon that never
uses these APIs or shared core events is **not yet guaranteed** to be covered: audit it
separately rather than assuming that a general inventory transfer ban is safe.

**Acceptance test:** Keep an existing flight gem and confirm that it works, attempt a new
craft in every enabled recipe path, check that the inputs remain and the configured message
appears when a player crafts. Test a permitted addon item and a disabled one, then edit
and reload the YAML. Confirm that player gifts and existing item data are unchanged.
