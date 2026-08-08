package io.github.thebusybiscuit.slimefun4.api.registry;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.List;
import javax.annotation.Nonnull;

/** Read-only addon-facing view of Slimefun's registry health and ownership. */
@SlimefunAPI
public interface RegistryRuntimeService {

    @Nonnull
    RegistryRuntimeSnapshot getSnapshot();

    @Nonnull
    List<AddonRegistrySnapshot> getAddonSnapshots();
}
