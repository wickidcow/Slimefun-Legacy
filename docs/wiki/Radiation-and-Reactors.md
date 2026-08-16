# ☢️ Radiation & Reactors

Slimefun's nuclear progression introduces radioactive materials and powerful reactor technology. It is deliberately more dangerous than ordinary crafting.

## Radiation

Certain Slimefun materials can apply severe negative effects while carried in a player's inventory.

Classic radioactive materials include uranium-family resources and other late-game nuclear ingredients. Exact item behavior can vary between builds and addons, so check the item's current Guide entry and lore before handling it.

## Hazmat protection

A complete Hazmat Suit is the traditional protection against Slimefun radiation.

For reliable protection, treat the suit as a **full set**. Do not assume one piece is enough unless your server specifically changes that behavior.

Before transporting radioactive materials:

1. Equip the full protective set.
2. Confirm every armor slot contains the intended piece.
3. Keep radioactive material out of ordinary player storage when it is not needed.
4. Have a safe destination prepared before picking it up.

## Radioactive storage

Design nuclear areas separately from general crafting and public storage.

Good practices include:

- dedicated labeled storage
- limited player access
- clear separation from beginner areas
- backup power/storage where appropriate
- keeping fuel, coolant and byproducts organized

## Reactors

Reactors are advanced generators with additional operating requirements. Depending on the reactor, those requirements can include fuel, coolant, access ports, byproducts or environmental conditions.

Do not treat a reactor as a larger coal generator.

Before activation:

- read the current Guide recipe and structure
- understand its fuel cycle
- understand its cooling requirements
- know where byproducts go
- verify the energy network has somewhere to send/store output
- test the setup before leaving it unattended

## Why reactor automation needs care

Automating reactor inputs and outputs with Cargo is convenient, but it also adds more failure points.

A robust reactor setup should avoid consuming valuable fuel unless its outputs and byproducts can be handled safely.

Slimefun Legacy includes correctness work around generator and reactor transactions so failed output handling is less likely to silently destroy resources, but addon reactors may implement their own behavior.

## Server-owner considerations

Nuclear systems are a good candidate for server-specific rules because they can create both gameplay and performance consequences.

Consider documenting:

- whether reactors are allowed inside towns/claims
- whether unattended reactors are allowed
- whether radioactive materials can be traded freely
- whether addon reactor systems have additional restrictions

## When nuclear behavior looks wrong

If radiation or reactor behavior changes unexpectedly after an update:

1. Confirm the exact Slimefun Legacy version.
2. Confirm whether the item/reactor belongs to core Slimefun or an addon.
3. Check `/sf versions`.
4. Run `/sf doctor compatibility <plugin>` for addon-owned systems.
5. Capture the first console exception.
6. Reproduce on staging before changing live-world data.

## Related pages

- **[Energy Networks](Energy-Networks.md)**
- **[Cargo Networks](Cargo-Networks.md)**
- **[Resources, Dusts & Alloys](Resources-Dusts-and-Alloys.md)**
- **[Troubleshooting](Troubleshooting.md)**
