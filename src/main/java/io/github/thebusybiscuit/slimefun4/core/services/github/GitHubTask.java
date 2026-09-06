package io.github.thebusybiscuit.slimefun4.core.services.github;

import io.github.bakedlibs.dough.skins.CustomGameProfile;
import io.github.bakedlibs.dough.skins.PlayerSkin;
import io.github.bakedlibs.dough.skins.UUIDLookup;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;

/**
 * This {@link GitHubTask} represents a {@link Runnable} that is run every X minutes.
 * It retrieves every {@link Contributor} of this project from GitHub.
 *
 * @author TheBusyBiscuit
 *
 * @see GitHubService
 * @see Contributor
 *
 */
class GitHubTask implements Runnable {

    private static final int MAX_REQUESTS_PER_MINUTE = 16;
    private static final int DEFAULT_LOOKUP_TIMEOUT_SECONDS = 8;
    private static final int MAX_LOOKUP_TIMEOUT_SECONDS = 30;
    private static final String RESOLVE_ONLINE_PATH = "guide.contributor-heads.resolve-online";
    private static final String LOOKUP_TIMEOUT_PATH = "guide.contributor-heads.lookup-timeout-seconds";
    private static final String BLOCKED_NAMES_PATH = "guide.contributor-heads.blocked-names";

    private final GitHubService gitHubService;

    GitHubTask(@Nonnull GitHubService github) {
        gitHubService = github;
    }

    @Override
    public void run() {

        if (Bukkit.isPrimaryThread()) {
            Slimefun.logger().log(Level.SEVERE, "The contributors task may never run on the main Thread!");
            return;
        }

        connectAndCache();
        grabTextures();
    }

    private void connectAndCache() {
        gitHubService.getConnectors().forEach(GitHubConnector::download);
    }

    /**
     * This method will pull the skin textures for every {@link Contributor} and store
     * the {@link UUID} and received skin inside a local cache {@link File}.
     */
    private void grabTextures() {
        /**
         * Store all queried usernames to prevent 429 responses for pinging
         * the same URL twice in one run.
         */
        Map<String, String> skins = new HashMap<>();
        int requests = 0;

        for (Contributor contributor : gitHubService.getContributors().values()) {
            int newRequests = requestTexture(contributor, skins);

            requests += newRequests;

            if (newRequests < 0 || requests >= MAX_REQUESTS_PER_MINUTE) {
                break;
            }
        }

        if (requests >= MAX_REQUESTS_PER_MINUTE
                && Slimefun.instance() != null
                && Slimefun.instance().isEnabled()) {
            // Slow down API requests and wait a minute after more than x requests were made
            Slimefun.getSchedulerService().runAsyncLater(this::grabTextures, 2L * 60L * 20L);
        }

        for (GitHubConnector connector : gitHubService.getConnectors()) {
            if (connector instanceof ContributionsConnector contributionsConnector
                    && !contributionsConnector.hasFinished()) {
                return;
            }
        }

        /**
         * We only wanna save this if all Connectors finished already.
         * This will run multiple times but thats okay, this way we get as much
         * data as possible stored.
         */
        gitHubService.saveCache();
    }

    private int requestTexture(@Nonnull Contributor contributor, @Nonnull Map<String, String> skins) {
        if (!contributor.hasTexture()) {
            if (!shouldResolveOnline(contributor)) {
                return 0;
            }

            try {
                if (skins.containsKey(contributor.getMinecraftName())) {
                    contributor.setTexture(skins.get(contributor.getMinecraftName()));
                } else {
                    contributor.setTexture(pullTexture(contributor, skins));
                    return contributor.getUniqueId().isPresent() ? 1 : 2;
                }
            } catch (IllegalArgumentException x) {
                // There cannot be a texture found because it is not a valid MC username
                contributor.setTexture(null);
            } catch (InterruptedException x) {
                Slimefun.logger().log(Level.WARNING, "The contributors thread was interrupted!");
                Thread.currentThread().interrupt();
            } catch (Exception x) {
                // Too many requests or an unavailable profile service. Contributor heads are cosmetic,
                // so never let this affect the server's main gameplay loop.
                Slimefun.logger()
                        .log(
                                Level.WARNING,
                                "Attempted to refresh skin cache, got this response: {0}: {1}",
                                new Object[] {x.getClass().getSimpleName(), x.getMessage()});

                String msg = x.getMessage();

                // Retry after 5 minutes if it was just rate-limiting
                if (msg != null && msg.contains("429")) {
                    Slimefun.getSchedulerService().runAsyncLater(this::grabTextures, 5L * 60L * 20L);
                }

                return -1;
            }
        }

        return 0;
    }

    private boolean shouldResolveOnline(@Nonnull Contributor contributor) {
        if (!Slimefun.getCfg().getBoolean(RESOLVE_ONLINE_PATH)) {
            return false;
        }

        String githubName = contributor.getName().toLowerCase(Locale.ROOT);
        String minecraftName = contributor.getMinecraftName().toLowerCase(Locale.ROOT);

        for (String configuredName : Slimefun.getCfg().getStringList(BLOCKED_NAMES_PATH)) {
            String blockedName = configuredName.trim().toLowerCase(Locale.ROOT);
            if (!blockedName.isEmpty() && (blockedName.equals(githubName) || blockedName.equals(minecraftName))) {
                return false;
            }
        }

        return true;
    }

    private int getLookupTimeoutSeconds() {
        int configured = Slimefun.getCfg().getInt(LOOKUP_TIMEOUT_PATH);
        if (configured <= 0) {
            return DEFAULT_LOOKUP_TIMEOUT_SECONDS;
        }

        return Math.min(configured, MAX_LOOKUP_TIMEOUT_SECONDS);
    }

    private @Nullable String pullTexture(@Nonnull Contributor contributor, @Nonnull Map<String, String> skins)
            throws InterruptedException, ExecutionException, TimeoutException {
        Optional<UUID> uuid = contributor.getUniqueId();
        int timeoutSeconds = getLookupTimeoutSeconds();

        if (!uuid.isPresent()) {
            CompletableFuture<UUID> future =
                    UUIDLookup.getUuidFromUsername(Slimefun.instance(), contributor.getMinecraftName());

            uuid = Optional.ofNullable(future.get(timeoutSeconds, TimeUnit.SECONDS));
            uuid.ifPresent(contributor::setUniqueId);
        }

        if (uuid.isPresent()) {
            CompletableFuture<PlayerSkin> future = PlayerSkin.fromPlayerUUID(Slimefun.instance(), uuid.get());
            Optional<String> skin = Optional.ofNullable(
                    CustomGameProfile.getBase64Texture(future.get(timeoutSeconds, TimeUnit.SECONDS).getProfile()));
            skins.put(contributor.getMinecraftName(), skin.orElse(""));
            return skin.orElse(null);
        } else {
            return null;
        }
    }
}
