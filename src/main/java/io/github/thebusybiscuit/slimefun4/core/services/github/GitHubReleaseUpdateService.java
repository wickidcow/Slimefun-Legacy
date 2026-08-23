package io.github.thebusybiscuit.slimefun4.core.services.github;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

/**
 * Dedicated update-notification service for Slimefun Legacy.
 *
 * <p>This service only checks the latest published GitHub Release for the configured repository. It never downloads,
 * replaces, or modifies the running plugin JAR. The first check runs asynchronously during startup, and a lightweight
 * follow-up check runs once per hour so long-running servers can also notice newly published releases.</p>
 */
final class GitHubReleaseUpdateService {

    private static final long HOURLY_TICKS = TimeUnit.HOURS.toSeconds(1) * 20L;

    private final GitHubService github;
    private final String repository;

    GitHubReleaseUpdateService(@Nonnull GitHubService github, @Nonnull String repository) {
        this.github = github;
        this.repository = repository;
    }

    /** Starts the release-only updater without blocking server startup. */
    void start() {
        Slimefun.getSchedulerService().runAsync(this::checkLatestRelease);
        Slimefun.getSchedulerService().runAsyncAtFixedRate(this::checkLatestRelease, HOURLY_TICKS, HOURLY_TICKS);
    }

    private void checkLatestRelease() {
        if (Slimefun.instance() == null || !Slimefun.instance().isEnabled()) {
            return;
        }

        new GitHubReleaseConnector(github, repository).download();
    }
}
