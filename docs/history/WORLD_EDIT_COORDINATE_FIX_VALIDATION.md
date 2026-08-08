# WorldEdit Coordinate Accessor Fix

The prior maintenance build changed `getBlockX/Y/Z()` to `getX/Y/Z()`, but the current
WorldEdit `BlockVector3` API marks both getter families for removal.

This correction uses the record component accessors:

- `x()`
- `y()`
- `z()`

The compatibility verifier now rejects both deprecated getter families and requires
the record accessors.

Only WorldEdit coordinate access and related documentation/verification changed.
