# FinalTECH shared-lore follow-through in the pinned addon bundle

Manifest revision 95 advances only FinalTECH from `6a6adbe073a54c792653aca669362cf364ec853e` to `7392ee61f4af3cbd779ee3c5267131d721311b67`. The other 44 pins, all 45 inclusions, exclusions, naming conventions and platform policy are unchanged from revision 94. The source manifest is `8567837a25900a1bdd7959340d95aa43dc230b90`. Core production code and release versions do not change.

FinalTECH's shared String-facing lore edit methods now retain unrelated native components and decode only requested new lines. All thirteen public method descriptors and original append/remove/padding/null rules remain. Its serialization, item comparison, transfers, storage, energy, ticker and earlier English-locale recovery code are untouched. The helper is package-private and adds no dependency or warning suppression.

## Pre-promotion evidence

Exact-core validation `36801120276` built coordinated Legacy source `9e8de71adf70e9a361a74c21afe5e7006c85faf6` and passed the real Maven verify build on Paper 1.21.11. All **34 tests passed**, with no failures/errors/skips: **14 new helper/descriptor tests**, 12 existing Bukkit YAML tests and eight ticker tests. The new suite includes **4,500 deterministic comparisons** against the old String-list rules. All retained English, source, storage and API guards passed unchanged, Java21 output was verified, and no test dependencies were shipped.

These new cases directly test the actual component helper and reflectively verify public descriptors without server-dependent utility initialization. They are not claimed live ItemStack-carrier tests, full machine simulation or captures from historical worlds. The narrow production delegation diff preserves unrelated methods. No compiler warning lines were emitted under the existing fail-on-warning settings; other addon warnings are not hidden by that result.

Downloaded evidence `11135622265` matched SHA-256 `eb6e48c6235f3c24eedbc8d5df1a4f0ee074a9a614bce2ca4affb4da7a339568`; actual XML counts and all three source hashes matched reviewed files. The addon commit includes these files and its scope note, not the temporary validation workflow or compressed patch.

Together with the Networks, IE2 and Supreme batch, this continuation adds **42 regression cases** and **5,250 old-behavior comparisons**. The four exact-core suites executed **154 passing tests** in total. See `docs/addon-component-preservation-checkpoint.md` for the other three source revisions and their precise test scopes. No server TPS improvement is inferred from these tests.

## Prior complete bundle and next gate

Revision 94 at `d3e620de1ea0d842414283848b3c94f525d56679` completed the 45-addon build in `36800283342`. Its downloaded archive was independently checked for 45 exact source pins, unique JAR entries, every JAR checksum/CRC and Java21 base-class headers. Inner archive SHA-256: `6339f0153723cb8610afd4cdce88aa6fa773f63e394308e435a57801209b4941`. The full-stack run `36800283465` completed two boot cycles on Paper 1.21.11, 26.2 and 26.3. That prior archive does not contain this new FinalTECH source and is not substituted for revision 95 validation.

The new head must build all 45 sources again and pass the exact-head full-stack matrix. Only its successful matching archive should be distributed as this candidate. A missing or failed candidate must not fall back to an older release. This checkpoint is not a version bump, merge, stable publication, live installation or proof that every maintained addon is completely deprecation-free. IE2/Supreme still have documented warning work, and real historical-world fixtures, broader gameplay/performance and appropriate Folia concurrency checks remain separate requirements.
