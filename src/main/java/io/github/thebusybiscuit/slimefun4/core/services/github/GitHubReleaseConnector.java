package io.github.thebusybiscuit.slimefun4.core.services.github;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import javax.annotation.Nonnull;

/** Reads the latest published non-prerelease GitHub Release for Slimefun Legacy. */
final class GitHubReleaseConnector extends GitHubConnector {

    GitHubReleaseConnector(@Nonnull GitHubService github, @Nonnull String repository) {
        super(github, repository);
    }

    @Override
    public @Nonnull String getFileName() {
        return "latest-release";
    }

    @Override
    public @Nonnull String getEndpoint() {
        return "/releases/latest";
    }

    @Override
    public @Nonnull Map<String, Object> getParameters() {
        return Map.of();
    }

    @Override
    public void onSuccess(@Nonnull JsonElement response) {
        if (!response.isJsonObject()) {
            return;
        }

        JsonObject object = response.getAsJsonObject();
        JsonElement tag = object.get("tag_name");
        JsonElement url = object.get("html_url");
        if (tag == null || !tag.isJsonPrimitive()) {
            return;
        }

        String releaseTag = tag.getAsString();
        String releaseUrl = url != null && url.isJsonPrimitive()
                ? url.getAsString()
                : "https://github.com/" + github.getRepository() + "/releases";
        github.updateLatestRelease(releaseTag, releaseUrl);
    }
}
