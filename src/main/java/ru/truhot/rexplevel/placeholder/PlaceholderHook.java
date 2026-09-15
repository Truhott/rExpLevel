package ru.truhot.rexplevel.placeholder;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.truhot.rexplevel.manager.AutoConvertManager;
import ru.truhot.rexplevel.manager.ConfigManager;
import ru.truhot.rexplevel.util.logger.Logger;

public final class PlaceholderHook {

    private @Nullable RExpExpansion expansion;

    public void register(
            @NotNull JavaPlugin plugin,
            @NotNull AutoConvertManager autoConvert,
            @NotNull ConfigManager config
    ) {
        unregister();
        Plugin placeholderApi = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (placeholderApi == null || !placeholderApi.isEnabled()) {
            return;
        }
        expansion = new RExpExpansion(plugin.getPluginMeta().getVersion(), autoConvert, config);
        if (expansion.register()) {
            Logger.info("PlaceholderAPI подключён: rexp_auto, rexp_mode");
        }
    }

    public void unregister() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
    }
}
