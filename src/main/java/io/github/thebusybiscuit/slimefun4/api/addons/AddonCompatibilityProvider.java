package io.github.thebusybiscuit.slimefun4.api.addons;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import javax.annotation.Nonnull;

/** Optional interface an addon can implement to publish its compatibility declaration. */
@SlimefunAPI
public interface AddonCompatibilityProvider {

    @Nonnull
    AddonCompatibilityDeclaration getAddonCompatibilityDeclaration();
}
