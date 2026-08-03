## Summary

<!-- Explain what this changes and why. -->

## Testing

- [ ] `python3 scripts/verify_legacy.py .`
- [ ] `./gradlew spotlessCheck clean build --no-daemon`
- [ ] Public API compatibility and dependency-boundary reports were reviewed
- [ ] Tested on a compatible Paper server, when applicable

## Player-facing text

- [ ] New or changed player-facing text is English
- [ ] Existing item IDs and persistent metadata remain compatible

## Related issues

<!-- Example: Fixes #123 -->

## Compatibility impact

- [ ] No public JVM signature was removed without an approved allowlist entry
- [ ] No new direct CraftBukkit or NMS import was added
- [ ] Java 21 bytecode targeting remains intact
- [ ] Database and saved-data formats remain compatible, or migration is documented and tested
