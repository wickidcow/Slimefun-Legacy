package io.github.thebusybiscuit.slimefun4.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import javax.annotation.Nonnull;

/**
 * Some helper methods for dealing with Json data.
 *
 * @author TheBusyBiscuit
 *
 */
public final class JsonUtils {

    /**
     * Do not instantiate this class.
     */
    private JsonUtils() {}

    /**
     * Parses the supplied JSON using Gson's supported static parser API.
     *
     * @param json
     *            The {@link String} to parse
     *
     * @return The parsed {@link JsonElement}
     */
    public static @Nonnull JsonElement parseString(@Nonnull String json) {
        return JsonParser.parseString(json);
    }
}
