#!/usr/bin/env python3
"""Require deliberate authorization before touching existing release assets."""
import os


def require_authorization(event: str, ref: str, confirmed: str) -> None:
    if event != "workflow_dispatch":
        raise ValueError("Release cleanup requires an explicit workflow_dispatch")
    if ref != "refs/heads/master":
        raise ValueError("Release cleanup is restricted to master")
    if confirmed != "true":
        raise ValueError("Release cleanup requires an explicit true confirmation")


def main() -> None:
    try:
        require_authorization(
            os.environ.get("GITHUB_EVENT_NAME", ""),
            os.environ.get("GITHUB_REF", ""),
            os.environ.get("CONFIRM_RELEASE_CLEANUP", ""),
        )
    except ValueError as error:
        raise SystemExit(str(error)) from error
    print("Explicit release cleanup authorization verified; preservation preflight is still required.")


if __name__ == "__main__":
    main()
