"""Keep source-compile evidence distinct from runtime and saved-world support."""

NOTICE_261 = (
    "- 26.1.2: advisory source compilation only; "
    "no runtime or world-migration/rollback certification"
)


def validate_scope(manifest: dict) -> None:
    versions = manifest["compatibility"]["minecraft_versions"]
    supported = versions["supported"]
    if versions["minimum"] != "1.21.11" or versions["primary"] != "26.3":
        raise ValueError("The reviewed minimum and primary version lines changed")
    if not all(v in supported for v in ("1.21.11", "26.2", "26.3")):
        raise ValueError("A reviewed runtime version line is missing")
    if any(v == "26.1" or v.startswith("26.1.") for v in supported):
        raise ValueError("26.1 source compilation must not be advertised as runtime support")
    advisory = versions["advisory"]["26.1.2"]
    if advisory.get("validation") != "source-compile-only":
        raise ValueError("26.1.2 must remain an advisory source-compile lane")
    for field in ("runtime_support", "world_migration_or_rollback_verified"):
        if advisory.get(field) is not False:
            raise ValueError(f"26.1.2 does not establish {field}")


def validate_notice(text: str) -> None:
    if NOTICE_261 not in text:
        raise ValueError("Generated compatibility text must retain the explicit 26.1.2 advisory")
    if "26.1.x: supported" in text or "26.1.2: supported" in text:
        raise ValueError("Generated text must not restore the historical broad 26.1 support claim")
