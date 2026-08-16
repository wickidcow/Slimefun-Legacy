# ⚡ Energy Networks

Slimefun's energy system powers electric machines, advanced processing, automation and much of the late-game technology tree.

A reliable power network is one of the biggest milestones in Slimefun progression.

## The four basic jobs

Think of an energy network as four roles:

1. **Generation** — produces energy.
2. **Storage** — buffers energy so machines do not starve when generation fluctuates.
3. **Distribution** — connects generators, storage and consumers.
4. **Consumption** — machines use the available energy to operate.

## Generators

Slimefun includes multiple generator types with different fuels, operating conditions and output patterns. Classic examples include solar, coal, lava, magnesium-based and reactor-based generation.

Do not choose a generator only because it has a large number in the Guide. Consider:

- fuel availability
- whether generation is continuous or conditional
- how many machines will run simultaneously
- how much storage exists between generation and consumers
- whether the generator introduces special risks

## Capacitors

Capacitors store energy and help smooth out uneven generation and machine demand.

A factory with enough theoretical generation can still behave poorly if it has almost no storage and several machines start at the same time.

A healthy network usually has enough capacitor capacity to absorb temporary spikes and keep important machines operating while generation changes.

## Regulators and connectors

Energy Regulators and Energy Connectors form the classic Slimefun energy-network structure.

Keep networks understandable. When diagnosing a large installation, being able to visually identify the regulator, major generators and storage is far more useful than hiding everything inside a giant wall of blocks.

## Machine power problems

If a machine reports insufficient power even though a generator exists nearby, check the whole network rather than only the generator:

- Is the machine actually connected to the intended network?
- Is the regulator/network loaded?
- Is generation currently active?
- Is another machine consuming the available energy first?
- Is capacitor storage empty?
- Is the machine's per-tick demand larger than the network can provide?
- Is an addon using a different or extended energy implementation?

For addon machines, run `/sf versions` and `/sf doctor compatibility <plugin>` when the problem looks version-specific.

## Reactors are not ordinary generators

Nuclear and other reactor systems can have additional fuel, cooling, byproduct or safety requirements. Treat them as a separate progression step rather than dropping them into a starter network without planning.

See **[Radiation & Reactors](Radiation-and-Reactors.md)**.

## Folia note

Slimefun Legacy's Folia support is experimental. Energy and Cargo behavior follows region-ownership safety boundaries rather than pretending cross-region transactions are always safe. Every addon participating in a network must also be Folia-safe.

## Server-owner advice

Large energy networks can contribute to tick cost because the server must discover and process connected components.

For busy servers:

- keep network size reasonable
- avoid pointless connector chains
- separate unrelated factories
- profile rather than guessing
- investigate addon machines individually when one network becomes expensive

See **[Server Performance](Server-Performance.md)**.

## Related pages

- **[Cargo Networks](Cargo-Networks.md)**
- **[Energy, Cargo & Automation](Energy-Cargo-and-Automation.md)**
- **[Radiation & Reactors](Radiation-and-Reactors.md)**
- **[Troubleshooting](Troubleshooting.md)**
