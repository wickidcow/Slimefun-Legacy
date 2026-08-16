from pathlib import Path

patch_path = Path("scripts/one_shot_update_beacon_area_verifier_docs.py")
source = patch_path.read_text(encoding="utf-8")

start_marker = '''verifier = replace_once(
    verifier,
    ''' + "'''" + '''        "- event-driven bonuses require a recently successful paid energy pulse'''
end_marker = '''verifier_path.write_text(verifier, encoding='utf-8')'''

start = source.find(start_marker)
end = source.find(end_marker, start if start >= 0 else 0)

if start < 0:
    raise SystemExit("Could not find the optional verifier PASS-report wording block")
if end < 0:
    raise SystemExit("Could not find verifier write marker after optional PASS-report wording block")

# The verifier's actual protected invariants are all patched before this optional
# human-readable PASS-report wording block. Remove only that brittle replacement.
patched_source = source[:start] + source[end:]

temp = Path("/tmp/one_shot_update_beacon_area_verifier_docs_fixed.py")
temp.write_text(patched_source, encoding="utf-8")

namespace = {"__name__": "__main__", "__file__": str(temp)}
exec(compile(patched_source, str(temp), "exec"), namespace, namespace)
