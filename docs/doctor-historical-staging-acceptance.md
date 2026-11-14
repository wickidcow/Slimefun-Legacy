# AlbionMC historical-item Doctor repair: staged acceptance

This document describes the **only real-item acceptance still missing** for draft [Slimefun Legacy PR #607](https://github.com/wickidcow/Slimefun-Legacy/pull/607). CI fixtures with constructed item metadata, even when run through a real connected Paper/Folia player and restart, are **not** proof that all historical player items are eligible.

## Before installing any candidate

- **Do not install the PR artifact on live AlbionMC.** Shut down a disposable clone of the server and make an independent full backup, including `world*/`, `playerdata/`, plugin databases and the Slimefun/Fluffy storage folders. Test in that clone only.
- Install the exact-head **test-only core JAR** from [PR #607's successful build](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/43594960301/artifacts/14113544864); remove the previous Slimefun core JAR from the clone first so only one is loaded. The candidate still declares version `4.1.80`; its artifact is **not** an official v4.1.80 release or a production replacement. Record the commit SHA in addition to the version.
- Keep the clone's addon versions, real historical storage and Paper build aligned with AlbionMC. Do not let production players connect to the clone. Have one original historical item in accessible storage and a freshly generated item of the same Slimefun ID; do not synthesize the old item from the new template.
- Try a genuine historical `CARBONADO`, `SYNTHETIC_SAPPHIRE` or `SMALL_URANIUM` pair. Test each independently. If the old item already stacks with the new one, there is **nothing to repair**; a full 64/64 stack cannot accept an additional item.

## Phase A: detection without changing items

1. On the isolated clone, place the **old original stack in the main hand** and **one current reference item in the offhand**. Capture each quantity and item's registered ID. Do not swap hand orientation.
2. Run `/sf doctor identity compare`. Record `Actual stack compatibility`, name/lore, model, PDC keys, `Non-presentation Bukkit metadata`, and the registered-template comparison. Note that equal **PDC keys alone** do not establish equal typed PDC values.
3. Run `/sf doctor identity repair scan`. This is read-only and must not create an item backup or modify either hand. The server should either offer `confirm` for a presentation-only candidate or refuse. **If it refuses, stop.** Never force a conversion.
4. Check both item amounts and both serialized items remain unchanged. If a real old sample refuses despite showing a cosmetic candidate, retain the failure details for investigation; do not weaken the PDC/model/profile checks.

## Phase B: reversible held-stack repair

Only on the disposable clone, and only after a successful Phase A preview:

1. Run `/sf doctor identity repair confirm` **within five minutes** without moving either item. Verify the old **main-hand stack retains its original amount**, the offhand is unchanged, and the updated main-hand item now has strict stacking identity with the reference.
2. Verify there is **exactly one new original-byte backup** under `plugins/Slimefun/recovery/identity/` and that it is not empty. Keep that file; do not post its raw contents publicly because item PDC may contain private server data.
3. Before changing or combining the repaired stack, run `/sf doctor identity repair rollback confirm` within five minutes. Verify original name/lore and other item fields and exact original quantity return, while the reference item remains untouched.
4. Repeat the scan and confirm with a second copy of the old item obtained by restoring the disposable server snapshot. Leave sufficient space in the destination stack: e.g. a repaired old stack of 63 and a genuine current stack of 1 must merge to exactly 64; a full 64/64 old stack is not a valid merge test.
5. Shut down and restart the clone; verify all counts, typed PDC, skull/profile, custom-model components, owner fields, actual Slimefun IDs, and stacking identity persist. Check no duplicate/lost item, no storage record rewrite, and the original backup remains intact.

## Evidence and release boundary

Record: plugin candidate commit, Paper/Purpur/Folia version, addon versions, item IDs/materials, original and current stack quantities, scan verdict, success/refusal, backup/rollback outcome, exact merge amount and independent-restart result. A sanitized screenshot or report is sufficient initially; **do not share raw PDC blobs, tokens, player UUIDs or server backup archives**.

If a real old-world sample shows non-presentation metadata differences, keep issue #578 open and investigate that item type separately. Issue #581 also stays open until each real producer and storage route has native evidence. Even a successful held-item repair is **not** permission to rewrite Fluffy Barrels, backpacks, Cargo, machine inventories or offline saved items.

Do not merge PR #607 or publish a new Slimefun Legacy version merely because 16/16 synthetic/CI workflows are green. Real-item staging acceptance and independent source review are separate gates.
