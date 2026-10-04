# Resource-pack Doctor migration validation

The Doctor probe exercises actual install/uninstall commands against disposable
Paper and Purpur servers. It starts from a fresh directory and runs three normal
boots, including the restart checkpoint before changing existing items.

## Verified native matrix

The expanded fixture passed locally against core source
`d9cbfb10df075cbc08b4a1e9b140b0dbd9f9237f`, with core JAR SHA-256
`a58799aa12b1e21d17c1ad6c4670ae41d049766ebd4289e8338247ccbb694973`.
This change adds test tooling and evidence, without changing production Java.

| Runtime | Java | Normal boots | Probe checkpoints |
| --- | --- | --- | --- |
| Paper 1.21.11 build 132 (`c5eb079`) | Temurin 21.0.12.1 | 3 passed | 7 passed |
| Purpur 1.21.11 build 2568 (`f57bd86`) | Temurin 21.0.12.1 | 3 passed | 7 passed |
| Paper 26.2 build 129 (`9240f58`) | Temurin 25.0.4.1 | 3 passed | 7 passed |
| Purpur 26.2 build 2633 (`3f5d9c0`) | Temurin 25.0.4.1 | 3 passed | 7 passed |
| Paper 26.3 build 145 (`6bc56ab`) | Temurin 25.0.4.1 | 3 passed | 7 passed |

The exact runtime downloads and SHA-256 hashes are pinned in
[`resource-pack-doctor-runtimes.json`](../compatibility/resource-pack-doctor-runtimes.json).
The local phase results, runtime Java versions and artifact hashes are retained in
[`resource-pack-doctor-evidence.json`](../compatibility/resource-pack-doctor-evidence.json).

## What the probe checks

- Preview leaves stored items, mapping configuration and authorization untouched.
- Confirmed install saves the restart checkpoint and disables delivery before
  changing runtime templates. Cleanup resumes after a normal restart.
- Cleanup preserves exact quantities, names, lore, charge, backpack/owner
  identities, opaque ItemsAdder-style PDC and unrelated model-component lanes.
- Loaded chests, nested bundles/shulkers, drops, persisted backpacks and unloaded
  block/universal inventory rows retain the expected items.
- Native binary and Base64 text payloads work alongside the retained Bukkit
  object-stream envelope. The original object-stream database payload is backed
  up byte-for-byte before replacement.
- A distant vanilla chest is repaired when its previously unloaded chunk loads.
- Uninstall leaves the cleaned contents intact and repeated cleanup makes zero
  repairs. A third normal boot verifies durable item/database contents and keeps
  a later external pack-ownership choice.

The object-stream fixture is generated using the retained historical encoding on
each tested server. It is not a captured item from an old production world.

## Reproduction and CI

`Resource Pack Doctor Migration` builds one core JAR for the exact checked-out
source and runs all five pinned runtimes. Each lane verifies the server checksum,
compiles the fixture with Java 25 targeting Java 21 bytecode, and runs the server
on the matrix's specified JVM. Logs, phase markers and JSON evidence are retained.

For a local run, use a new disposable directory:

```sh
python3 scripts/resource_pack_doctor_probe.py \
  --server-jar /path/to/pinned-server.jar \
  --core /path/to/Slimefun.jar \
  --java-home /path/to/jdk25 \
  --runtime-java-home /path/to/runtime-jdk \
  --work-dir /path/to/new-disposable-directory
```

`--server-template` can replace `--server-jar` when cached server code dependencies
are available. The template contributes only its server JAR, libraries, cache and
patched versions; it never contributes an existing world. `--server-cache` can
provide a Paperclip download cache when using `--server-jar`. Paperclip validates
its original-server payload before applying patches. The harness refuses to
reuse an existing work directory. Never install the fixture plugin on production.

## Remaining release evidence

This matrix does not validate client rendering, a real combined ItemsAdder pack,
captured historical production-world data, addon-private storage, or the Doctor's
Folia region-ownership paths. Ordinary Folia startup checks are separate evidence.
Those limits remain explicit; passing this fixture does not certify every stored
item or every external pack installation.
