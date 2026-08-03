# Contributing

This fork tracks `SlimefunGuguProject/Slimefun4` while maintaining an English-only
player experience. Changes should preserve both goals.

## Development setup

Use Java 25 and the included Gradle wrapper. Production classes must continue to target Java 21 bytecode.

```bash
chmod +x gradlew
./gradlew spotlessApply
python3 scripts/verify_legacy.py .
./gradlew clean build --no-daemon
```

Use four-space indentation and run Spotless before opening a pull request.

## Player-facing text

Do not add hard-coded Chinese text to Java string literals or the default configuration
files. The English verification script scans these locations and fails the build when
CJK text is found.

Optional translation packs may remain in `src/main/resources/languages/`; they are
excluded from the English-only scan because per-player translations are disabled in
this fork.

## Commit messages

Conventional Commit prefixes are preferred, for example:

```text
fix(cargo): preserve output items when transfer is rejected
trans(items): correct English talisman lore
chore(upstream): sync Gugu changes
```

## Upstream changes

Do not copy the English fork over a new upstream tree manually. Use
`scripts/sync_upstream.sh` or the scheduled GitHub workflow so the maintained patch is
applied and verified consistently.

When the patch no longer applies:

1. Start with a clean checkout of the newest Gugu source.
2. Reapply the English and Albion changes manually.
3. Run the verifier and full Gradle build.
4. Regenerate `patches/albion-english.patch` with
   `scripts/regenerate_patch.sh /path/to/clean/Slimefun4`.
5. Review the resulting patch before committing it.

## Compatibility Foundation

Pull requests must not casually remove released public JVM signatures, increase sensitive direct dependency imports, introduce CraftBukkit/NMS imports, or raise the Java bytecode target. Run the complete verifier before opening a pull request.

When a compatibility boundary must change, update the relevant report or baseline deliberately and explain the migration impact in the pull request. Do not hide a breaking API change by weakening a verifier.
