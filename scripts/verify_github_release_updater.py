#!/usr/bin/env python3
"""Verify Slimefun Legacy's release-only update notification service."""

from __future__ import annotations

import sys
from pathlib import Path


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"GitHub release updater verification failed: {message}")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

    service = (root / "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/github/GitHubReleaseUpdateService.java").read_text(encoding="utf-8")
    github = (root / "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/github/GitHubService.java").read_text(encoding="utf-8")
    connector = (root / "src/main/java/io/github/thebusybiscuit/slimefun4/core/services/github/GitHubReleaseConnector.java").read_text(encoding="utf-8")
    config = (root / "src/main/java/io/github/thebusybiscuit/slimefun4/core/config/SlimefunConfigManager.java").read_text(encoding="utf-8")

    require('return "/releases/latest";' in connector, "release discovery must use GitHub /releases/latest")
    require("runAsync(this::checkLatestRelease)" in service, "startup release check must run asynchronously")
    require("runAsyncAtFixedRate(this::checkLatestRelease" in service, "long-running servers must receive periodic release checks")
    require("new GitHubReleaseConnector(github, repository).download();" in service, "dedicated updater must use only the release connector")

    require("releaseUpdateService = new GitHubReleaseUpdateService(this, repository);" in github, "GitHubService must own the dedicated release updater")
    require("releaseUpdateService.start();" in github, "release updater must start during Slimefun GitHub service startup")
    require("A newer published GitHub release of Slimefun Legacy is available." in github, "console notice must identify a published GitHub release")
    require('recipient.sendMessage("Installed: "' in github, "notice must include installed version")
    require('recipient.sendMessage("Latest:    "' in github, "notice must include latest release version")
    require('recipient.sendMessage("Download:")' in github, "notice must include the release download destination")

    # The broad contributor/issues refresh must not become a second update source.
    load_connectors = github.split("private void loadConnectors(boolean logging)", 1)[1].split("protected @Nonnull Set<GitHubConnector>", 1)[0]
    require("new GitHubReleaseConnector" not in load_connectors, "release checks must remain isolated from general GitHub refresh connectors")

    # Release tags historically used both v4.x.y and SlimefunLegacy4.x.y forms.
    require("while (start < normalized.length() && !Character.isDigit(normalized.charAt(start)))" in github,
            "version parser must accept prefixed release tags")

    # The old community auto-downloader must be unreachable even if an old config enables it.
    require("autoUpdate = false;" in config, "legacy community auto-updater must remain disabled")
    require("Published GitHub Releases are checked for notifications only." in config,
            "legacy auto-update setting must explain release-only notification behavior")

    print("GitHub release-only update notification verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
