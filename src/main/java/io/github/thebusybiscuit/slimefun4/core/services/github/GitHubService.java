package io.github.thebusybiscuit.slimefun4.core.services.github;

import io.github.bakedlibs.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.HeadTexture;
import java.io.File;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * This Service is responsible for grabbing every {@link Contributor} to this project
 * from GitHub and holding data associated to the project repository, such
 * as open issues or pending pull requests.
 *
 * @author TheBusyBiscuit
 */
public class GitHubService {

    private static final String UPDATE_SEPARATOR = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

    private final String repository;
    private final Set<GitHubConnector> connectors;
    private final ConcurrentMap<String, Contributor> contributors;
    private final ConcurrentMap<UUID, String> notifiedReleaseByPlayer = new ConcurrentHashMap<>();

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
    }

    /** Starts the asynchronous GitHub refresh task. */
    public void start(@Nonnull Slimefun plugin) {
        loadConnectors(false);
        long period = TimeUnit.HOURS.toSeconds(1) * 20L;
        GitHubTask task = new GitHubTask(this);
        Slimefun.getSchedulerService().runAsyncAtFixedRate(task, 30L * 20L, period);
    }

    private void addDefaultContributors() {
        addContributor("Fuffles_", "&dArtist");
        addContributor("IMS_Art", "https://github.com/IAmSorryArt", "&dArtist", 0);
        addContributor("nahkd123", "&aWinner of the 2020 Addon Jam");

        try {
            TranslatorsReader translators = new TranslatorsReader(this);
            translators.load();
        } catch (Exception x) {
            Slimefun.logger().log(Level.SEVERE, "Failed to read 'translators.json'", x);
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
        Contributor contributor = contributors.computeIfAbsent(username, key -> new Contributor(minecraftName, profileURL));
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
        connectors.add(new GitHubReleaseConnector(this, repository));
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
    public boolean isUpdateAvailable() {
        String latest = latestReleaseTag;
        return latest != null && compareVersions(latest, Slimefun.getVersion()) > 0;
    }

    /** Sends the configured update notice once per published tag to online operators only. */
    public void notifyUpdateIfAvailable(@Nonnull Player player) {
        if (!player.isOp() || !isUpdateAvailable()) {
            return;
        }

        String tag = latestReleaseTag;
        if (tag == null || tag.equals(notifiedReleaseByPlayer.put(player.getUniqueId(), tag))) {
            return;
        }

        sendUpdateNotice(player, tag);
    }

    void updateLatestRelease(@Nonnull String tag, @Nonnull String releaseUrl) {
        boolean changed = !tag.equals(latestReleaseTag);
        latestReleaseTag = tag;
        latestReleaseUrl = "https://github.com/" + repository + "/releases/latest";
        if (!changed || !isUpdateAvailable()) {
            return;
        }

        if (!tag.equals(loggedReleaseTag)) {
            loggedReleaseTag = tag;
            sendUpdateNotice(Bukkit.getConsoleSender(), tag);
        }

        if (Slimefun.instance() == null || !Slimefun.instance().isEnabled()) {
            return;
        }

        Slimefun.getSchedulerService().run(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isOp()) {
                    Slimefun.getSchedulerService().runFor(player, () -> notifyUpdateIfAvailable(player), () -> {});
                }
            }
        });
    }

    private void sendUpdateNotice(@Nonnull CommandSender recipient, @Nonnull String latestTag) {
        recipient.sendMessage(UPDATE_SEPARATOR);
        recipient.sendMessage("Slimefun Legacy Update Available");
        recipient.sendMessage(UPDATE_SEPARATOR);
        recipient.sendMessage("Installed: " + displayVersion(Slimefun.getVersion()));
        recipient.sendMessage("Latest:    " + displayVersion(latestTag));
        recipient.sendMessage("A newer version of Slimefun Legacy is available.");
        recipient.sendMessage("Update to receive the latest fixes and improvements.");
        recipient.sendMessage("Download:");
        recipient.sendMessage(latestReleaseUrl);
        recipient.sendMessage(UPDATE_SEPARATOR);
    }

    private static String displayVersion(String version) {
        if (version != null && version.length() > 1 && (version.charAt(0) == 'v' || version.charAt(0) == 'V')) {
            return version.substring(1);
        }
        return version;
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
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        int suffix = normalized.indexOf('-');
        if (suffix < 0) {
            suffix = normalized.indexOf('+');
        }
        if (suffix >= 0) {
            normalized = normalized.substring(0, suffix);
        }

        String[] pieces = normalized.split("\\.");
        int[] result = new int[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            int end = 0;
            while (end < pieces[i].length() && Character.isDigit(pieces[i].charAt(end))) {
                end++;
            }
            if (end == 0) {
                return new int[0];
            }
            try {
                result[i] = Integer.parseInt(pieces[i].substring(0, end));
            } catch (NumberFormatException ignored) {
                return new int[0];
            }
        }
        return result;
    }

    protected void saveCache() {
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
