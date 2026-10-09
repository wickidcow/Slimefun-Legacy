package io.github.thebusybiscuit.slimefun4.core.services.github;

import io.github.bakedlibs.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.api.lifecycle.CoreLifecycleService;
import io.github.thebusybiscuit.slimefun4.api.lifecycle.CoreLifecycleState;
import io.github.thebusybiscuit.slimefun4.core.services.scheduling.SlimefunScheduler;
import io.github.thebusybiscuit.slimefun4.core.services.scheduling.TaskHandle;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.HeadTexture;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;

/**
 * This Service is responsible for grabbing every {@link Contributor} to this project
 * from GitHub and holding data associated to the project repository, such
 * as open issues or pending pull requests.
 *
 * @author TheBusyBiscuit
 */
public class GitHubService {

    private static final String UPDATE_NOTIFICATION_PERMISSION = "slimefun.update-notifications";

    private final String repository;
    private final Set<GitHubConnector> connectors;
    private final ConcurrentMap<String, Contributor> contributors;
    private final ConcurrentMap<UUID, String> notifiedReleaseByPlayer = new ConcurrentHashMap<>();
    private final GitHubReleaseUpdateService releaseUpdateService;
    private final Set<OwnedTask> ownedTasks = new HashSet<>();

    // A core instance owns one terminal scheduler/thread-service lifetime. Keep its resources after stop so an
    // in-flight response can never rebind itself to the singleton of a replacement plugin.
    private volatile Context context;
    private volatile boolean stopped;

    private final Config uuidCache = new Config("plugins/Slimefun/cache/github/uuids.yml");
    private final Config texturesCache = new Config("plugins/Slimefun/cache/github/skins.yml");

    private boolean logging = false;
    private LocalDateTime lastUpdate = LocalDateTime.now();
    private int openIssues = 0;
    private int pendingPullRequests = 0;
    private int publicForks = 0;
    private int stargazers = 0;
    private volatile String latestReleaseTag;
    private volatile String latestReleaseUrl;
    private volatile String loggedReleaseTag;

    /**
     * This creates a new {@link GitHubService} for the given repository.
     *
     * @param repository The repository to create this {@link GitHubService} for
     */
    public GitHubService(@Nonnull String repository) {
        this.repository = repository;
        this.latestReleaseUrl = "https://github.com/" + repository + "/releases/latest";
        connectors = new HashSet<>();
        contributors = new ConcurrentHashMap<>();
        releaseUpdateService = new GitHubReleaseUpdateService(this, repository);
    }

    /** Starts the asynchronous GitHub refresh tasks. */
    public synchronized void start(@Nonnull Slimefun plugin) {
        Validate.notNull(plugin, "Plugin must not be null.");
        if (stopped || context != null || Slimefun.instance() != plugin || !plugin.isEnabled()) {
            return;
        }

        context = new Context(
                plugin,
                Slimefun.getSchedulerService(),
                Slimefun.getCoreLifecycleService(),
                Slimefun.getCfg(),
                plugin.getLogger(),
                plugin.getPluginMeta().getVersion());
        if (!isActive()) {
            return;
        }

        // Release checks are intentionally independent from contributor/issue refreshes so the
        // server owner can see an update notice during boot without waiting for the larger task.
        releaseUpdateService.start();

        loadConnectors(false);
        long period = TimeUnit.HOURS.toSeconds(1) * 20L;
        GitHubTask task = new GitHubTask(this);
        runAsyncAtFixedRate(task, 30L * 20L, period);
    }

    /** Stops this service permanently, discarding late results and cancelling its scheduled work. */
    public void shutdown() {
        List<TaskHandle> handles;
        synchronized (this) {
            if (stopped) {
                return;
            }
            stopped = true;
            handles = ownedTasks.stream()
                    .map(task -> task.handle)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            ownedTasks.clear();
        }

        // Cancellation need not wait for a running HTTP request. Its eventual result still has to pass the gate.
        IllegalStateException failure = null;
        for (TaskHandle handle : handles) {
            try {
                handle.cancel();
            } catch (RuntimeException | LinkageError error) {
                if (failure == null) {
                    failure = new IllegalStateException("Failed to cancel GitHub service work", error);
                } else {
                    failure.addSuppressed(error);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    boolean isActive() {
        Context current = context;
        if (stopped
                || current == null
                || Slimefun.instance() != current.plugin()
                || !current.plugin().isEnabled()
                || !current.scheduler().isAcceptingTasks()) {
            return false;
        }
        CoreLifecycleState state = current.lifecycle().getSnapshot().getState();
        return state == CoreLifecycleState.STARTING || state == CoreLifecycleState.RUNNING;
    }

    /** Serializes publication/cache commits with stop, never network or profile-future waits. */
    synchronized boolean runIfActive(Runnable action) {
        if (!isActive()) {
            return false;
        }
        action.run();
        return true;
    }

    Slimefun getOwner() {
        return context.plugin();
    }

    Config getConfig() {
        return context.config();
    }

    Logger getLogger() {
        return context.logger();
    }

    void runAsync(Runnable task) {
        schedule(task, false, owned -> context.scheduler().runAsync(owned));
    }

    void runAsyncLater(Runnable task, long delayTicks) {
        schedule(task, false, owned -> context.scheduler().runAsyncLater(owned, delayTicks));
    }

    void runAsyncAtFixedRate(Runnable task, long delayTicks, long periodTicks) {
        schedule(task, true, owned -> context.scheduler().runAsyncAtFixedRate(owned, delayTicks, periodTicks));
    }

    private void run(Runnable task) {
        schedule(task, false, owned -> context.scheduler().run(owned));
    }

    private void runFor(Player player, Runnable task) {
        schedule(task, false, owned -> context.scheduler().runFor(player, owned, owned::finish));
    }

    private void schedule(Runnable task, boolean repeating, Function<OwnedTask, TaskHandle> submit) {
        TaskHandle cancelledAfterSubmission = null;
        synchronized (this) {
            if (!isActive()) {
                return;
            }
            OwnedTask owned = new OwnedTask(task, repeating);
            ownedTasks.add(owned);
            try {
                owned.handle = submit.apply(owned);
                if (stopped) {
                    cancelledAfterSubmission = owned.handle;
                    ownedTasks.remove(owned);
                } else if (owned.finished || owned.handle.isCancelled()) {
                    ownedTasks.remove(owned);
                }
            } catch (IllegalPluginAccessException | RejectedExecutionException rejection) {
                ownedTasks.remove(owned);
                // Bukkit can disable the owner between our check and native scheduler submission.
                if (isActive()) {
                    throw rejection;
                }
            } catch (RuntimeException | Error failure) {
                ownedTasks.remove(owned);
                throw failure;
            }
        }
        if (cancelledAfterSubmission != null) {
            cancelledAfterSubmission.cancel();
        }
    }

    private final class OwnedTask implements Runnable {
        private final Runnable action;
        private final boolean repeating;
        private TaskHandle handle;
        private boolean finished;

        private OwnedTask(Runnable action, boolean repeating) {
            this.action = action;
            this.repeating = repeating;
        }

        @Override
        public void run() {
            try {
                if (isActive()) {
                    action.run();
                }
            } finally {
                if (!repeating) {
                    finish();
                }
            }
        }

        private void finish() {
            synchronized (GitHubService.this) {
                finished = true;
                ownedTasks.remove(this);
            }
        }
    }

    private record Context(
            Slimefun plugin,
            SlimefunScheduler scheduler,
            CoreLifecycleService lifecycle,
            Config config,
            Logger logger,
            String installedVersion) {}

    private void addDefaultContributors() {
        addContributor("wickidcow", "https://github.com/wickidcow", ContributorRole.DEVELOPER.getId(), 0);
        addContributor("Fuffles_", "&dArtist");
        addContributor("IMS_Art", "https://github.com/IAmSorryArt", "&dArtist", 0);
        addContributor("nahkd123", "&aWinner of the 2020 Addon Jam");

        try {
            TranslatorsReader translators = new TranslatorsReader(this);
            translators.load();
        } catch (Exception x) {
            getLogger().log(Level.SEVERE, "Failed to read 'translators.json'", x);
        }
    }

    private void addContributor(@Nonnull String name, @Nonnull String role) {
        Contributor contributor = new Contributor(name);
        contributor.setContributions(role, 0);
        contributor.setUniqueId(uuidCache.getUUID(name));
        contributors.put(name, contributor);
    }

    public @Nonnull Contributor addContributor(
            @Nonnull String minecraftName, @Nonnull String profileURL, @Nonnull String role, int commits) {
        Validate.notNull(minecraftName, "Minecraft username must not be null.");
        Validate.notNull(profileURL, "GitHub profile url must not be null.");
        Validate.notNull(role, "Role should not be null.");
        Validate.isTrue(commits >= 0, "Commit count cannot be negative.");

        String username = profileURL.substring(profileURL.lastIndexOf('/') + 1);
        Contributor contributor =
                contributors.computeIfAbsent(username, key -> new Contributor(minecraftName, profileURL));
        contributor.setContributions(role, commits);
        contributor.setUniqueId(uuidCache.getUUID(minecraftName));
        return contributor;
    }

    public @Nonnull Contributor addContributor(@Nonnull String username, @Nonnull String role, int commits) {
        Validate.notNull(username, "Username must not be null.");
        Validate.notNull(role, "Role should not be null.");
        Validate.isTrue(commits >= 0, "Commit count cannot be negative.");

        Contributor contributor = contributors.computeIfAbsent(username, key -> new Contributor(username));
        contributor.setContributions(role, commits);
        return contributor;
    }

    private void loadConnectors(boolean logging) {
        this.logging = logging;
        addDefaultContributors();

        connectors.add(new ContributionsConnector(this, "code", 1, repository, ContributorRole.DEVELOPER));
        connectors.add(new ContributionsConnector(this, "code2", 2, repository, ContributorRole.DEVELOPER));
        connectors.add(new ContributionsConnector(this, "code3", 3, repository, ContributorRole.DEVELOPER));
        connectors.add(new ContributionsConnector(this, "wiki", 1, "Slimefun/Wiki", ContributorRole.WIKI_EDITOR));
        connectors.add(new ContributionsConnector(
                this, "resourcepack", 1, "Slimefun/Resourcepack", ContributorRole.RESOURCEPACK_ARTIST));
        connectors.add(new GitHubIssuesConnector(this, repository, (issues, pullRequests) -> {
            this.openIssues = issues;
            this.pendingPullRequests = pullRequests;
        }));
        connectors.add(new GitHubActivityConnector(this, repository, (forks, stars, date) -> {
            this.publicForks = forks;
            this.stargazers = stars;
            this.lastUpdate = date;
        }));
    }

    protected @Nonnull Set<GitHubConnector> getConnectors() {
        return connectors;
    }

    protected boolean isLoggingEnabled() {
        return logging;
    }

    public @Nonnull ConcurrentMap<String, Contributor> getContributors() {
        return contributors;
    }

    public int getForks() {
        return publicForks;
    }

    public int getStars() {
        return stargazers;
    }

    public int getOpenIssues() {
        return openIssues;
    }

    public @Nonnull String getRepository() {
        return repository;
    }

    public int getPendingPullRequests() {
        return pendingPullRequests;
    }

    public @Nonnull LocalDateTime getLastUpdate() {
        return lastUpdate;
    }

    /** Returns the latest published GitHub Release tag when the service has fetched one. */
    public @Nonnull Optional<String> getLatestReleaseTag() {
        return Optional.ofNullable(latestReleaseTag);
    }

    /** Returns whether the latest published release is newer than the running Slimefun Legacy version. */
    public synchronized boolean isUpdateAvailable() {
        String latest = latestReleaseTag;
        return isActive() && latest != null && compareVersions(latest, context.installedVersion()) > 0;
    }

    /** Sends the configured update notice once per published tag to operators and authorized administrators. */
    public void notifyUpdateIfAvailable(@Nonnull Player player) {
        runIfActive(() -> {
            if (!canReceiveUpdateNotifications(player) || !isUpdateAvailable()) {
                return;
            }

            String tag = latestReleaseTag;
            if (tag == null || tag.equals(notifiedReleaseByPlayer.put(player.getUniqueId(), tag))) {
                return;
            }

            sendUpdateNotice(player, tag);
        });
    }

    private boolean canReceiveUpdateNotifications(@Nonnull Player player) {
        return player.isOp() || player.hasPermission(UPDATE_NOTIFICATION_PERMISSION);
    }

    void updateLatestRelease(@Nonnull String tag, @Nonnull String releaseUrl) {
        runIfActive(() -> publishLatestRelease(tag, releaseUrl));
    }

    private void publishLatestRelease(String tag, String releaseUrl) {
        boolean changed = !tag.equals(latestReleaseTag);
        latestReleaseTag = tag;

        String expectedPrefix = "https://github.com/" + repository + "/releases/";
        latestReleaseUrl = releaseUrl.startsWith(expectedPrefix)
                ? releaseUrl
                : "https://github.com/" + repository + "/releases/latest";

        if (!changed || !isUpdateAvailable()) {
            return;
        }

        if (!tag.equals(loggedReleaseTag)) {
            loggedReleaseTag = tag;
            sendUpdateNotice(Bukkit.getConsoleSender(), tag);
        }

        run(() -> runIfActive(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                // Permissions and delivery belong to the player's execution context, including on Folia.
                runFor(player, () -> notifyUpdateIfAvailable(player));
            }
        }));
    }

    private void sendUpdateNotice(@Nonnull CommandSender recipient, @Nonnull String latestTag) {
        recipient.sendMessage("§6[Slimefun Legacy] §eUpdate available: §f" + displayVersion(context.installedVersion())
                + " §7→ §a" + displayVersion(latestTag));
        recipient.sendMessage("§7https://github.com/" + repository + "/releases/latest");
    }

    private static String displayVersion(String version) {
        int[] parsed = parseVersion(version);
        if (parsed.length == 0) {
            return version;
        }

        StringBuilder display = new StringBuilder();
        for (int i = 0; i < parsed.length; i++) {
            if (i > 0) {
                display.append('.');
            }
            display.append(parsed[i]);
        }
        return display.toString();
    }

    private static int compareVersions(String left, String right) {
        int[] leftParts = parseVersion(left);
        int[] rightParts = parseVersion(right);
        if (leftParts.length == 0 || rightParts.length == 0) {
            return 0;
        }

        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            int l = i < leftParts.length ? leftParts[i] : 0;
            int r = i < rightParts.length ? rightParts[i] : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    private static int[] parseVersion(String value) {
        if (value == null) {
            return new int[0];
        }

        String normalized = value.trim();
        int start = 0;
        while (start < normalized.length() && !Character.isDigit(normalized.charAt(start))) {
            start++;
        }
        if (start >= normalized.length()) {
            return new int[0];
        }

        int end = start;
        while (end < normalized.length()) {
            char character = normalized.charAt(end);
            if (!Character.isDigit(character) && character != '.') {
                break;
            }
            end++;
        }

        normalized = normalized.substring(start, end);
        if (normalized.isEmpty() || normalized.endsWith(".")) {
            return new int[0];
        }

        String[] pieces = normalized.split("\\.");
        int[] result = new int[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            if (pieces[i].isEmpty()) {
                return new int[0];
            }
            try {
                result[i] = Integer.parseInt(pieces[i]);
            } catch (NumberFormatException ignored) {
                return new int[0];
            }
        }
        return result;
    }

    protected void saveCache() {
        runIfActive(this::writeContributorCache);
    }

    private void writeContributorCache() {
        for (Contributor contributor : contributors.values()) {
            Optional<UUID> uuid = contributor.getUniqueId();
            uuid.ifPresent(value -> uuidCache.setValue(contributor.getName(), value));

            if (contributor.hasTexture()) {
                String texture = contributor.getTexture(this);
                if (!texture.equals(HeadTexture.UNKNOWN.getTexture())) {
                    texturesCache.setValue(contributor.getName(), texture);
                }
            }
        }

        uuidCache.save();
        texturesCache.save();
    }

    protected @Nullable String getCachedTexture(@Nonnull String username) {
        return texturesCache.getString(username);
    }
}
