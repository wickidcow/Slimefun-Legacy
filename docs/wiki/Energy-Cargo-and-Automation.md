# ⚡ Energy, Cargo & Automation

Factories are where Slimefun becomes much more than a crafting plugin. A reliable factory is built from small systems you can understand and diagnose.

## Electricity: think in three numbers

### Generation
How much energy your generators can provide over time.

### Capacity
How much the network/storage can buffer or make available.

### Demand
How much connected machinery consumes while active.

A machine saying it has insufficient power does not always mean “add one more generator.” Check whether the network is valid, storage/capacity is appropriate, the machine is recognized, chunks/regions are active and an addon has not changed the machine's behavior.

## Cargo: think in flows

For every automated item path, be able to answer:

1. **Where does the item originate?**
2. **What is allowed to move it?**
3. **Where is it supposed to go?**
4. **Can the destination currently accept it?**

Filters, direction, channels, full inventories and addon-specific storage rules can all make a healthy Cargo system appear “stuck.”

## Build factories in layers

A good progression is:

**manual machine → powered machine → buffered production → one Cargo route → filtered Cargo → multi-machine line → addon integration**

If you jump directly to a huge interconnected factory, one misconfigured node becomes difficult to find.

## Troubleshooting a machine line

Work from the machine outward:

1. Confirm the recipe in the current Guide.
2. Put inputs in manually.
3. Confirm output space.
4. Confirm power.
5. Confirm the machine works without Cargo.
6. Add Cargo back one route at a time.
7. Check filters/direction/channel configuration.
8. Check `/sf versions` if addon machinery is involved.
9. Ask an operator to review `/sf doctor runtime` and `/sf doctor compatibility`.

## Folia note

Legacy's Folia work is conservative. Cargo and energy operations must respect region ownership, and cross-region transactional behavior is intentionally limited. A factory that spans region boundaries may behave differently from the primary Paper target.

## Addon automation

Legacy provides addon-facing machine recipe and input-fill adapter APIs for richer Enhanced Guide and automation integration. This does **not** mean every historical addon automatically exposes perfect structured recipes; addon support still varies.

For exact block layouts, machine recipes and Cargo controls, use the in-game Guide.