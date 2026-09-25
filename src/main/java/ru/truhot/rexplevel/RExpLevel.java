package ru.truhot.rexplevel;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.NamespacedKey;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.truhot.rexplevel.command.ExpCommand;
import ru.truhot.rexplevel.command.sub.AutoSubCommand;
import ru.truhot.rexplevel.command.sub.GiveSubCommand;
import ru.truhot.rexplevel.command.sub.HelpSubCommand;
import ru.truhot.rexplevel.command.sub.PackSubCommand;
import ru.truhot.rexplevel.command.sub.ReloadSubCommand;
import ru.truhot.rexplevel.listener.BottleListener;
import ru.truhot.rexplevel.listener.ExperienceListener;
import ru.truhot.rexplevel.manager.AutoConvertManager;
import ru.truhot.rexplevel.manager.BottleManager;
import ru.truhot.rexplevel.manager.ConfigManager;
import ru.truhot.rexplevel.manager.ExperienceBottleManager;
import ru.truhot.rexplevel.manager.PlayerSettingsManager;
import ru.truhot.rexplevel.placeholder.PlaceholderHook;
import ru.truhot.rexplevel.util.Metrics;
import ru.truhot.rexplevel.util.SchedulerUtil;
import ru.truhot.rexplevel.util.logger.Logger;

import java.util.List;

public final class RExpLevel extends JavaPlugin {

    private static final int BSTATS_ID = 34005;
    private static final List<String> PERMISSIONS = List.of(
            "rexp.use",
            "rexp.pack",
            "rexp.auto",
            "rexp.give",
            "rexp.reload"
    );

    private volatile boolean shuttingDown;
    private volatile @Nullable SchedulerUtil scheduler;
    private volatile @Nullable PlayerSettingsManager playerSettings;
    private volatile @Nullable ExperienceListener experienceListener;
    private volatile @Nullable AutoConvertManager autoConvert;
    private volatile @Nullable Metrics metrics;
    private final @NotNull PlaceholderHook placeholders = new PlaceholderHook();

    @Override
    public void onEnable() {
        Logger.setup(this);
        SchedulerUtil schedulerUtil = new SchedulerUtil(this);
        scheduler = schedulerUtil;
        ConfigManager config = new ConfigManager(this);
        try {
            config.loadForStartup();
        } catch (Exception exception) {
            Logger.error("Не удалось загрузить команды из config.yml", exception);
        }
        if (config.isMetrics()) {
            metrics = new Metrics(this, BSTATS_ID);
            Logger.info("bStats успешно инициализирован!");
        }
        ExpCommand command = new ExpCommand(config);
        PlayerSettingsManager settings = new PlayerSettingsManager(this, schedulerUtil);
        playerSettings = settings;
        registerPermissions();
        registerCommands(command, config);
        schedulerUtil.runAsync(() -> initialize(config, command, settings, schedulerUtil));
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
        placeholders.unregister();
        Metrics currentMetrics = metrics;
        if (currentMetrics != null) {
            currentMetrics.shutdown();
        }
        ExperienceListener listener = experienceListener;
        if (listener != null) {
            listener.stop();
        }
        AutoConvertManager convertManager = autoConvert;
        if (convertManager != null) {
            convertManager.stop();
        }
        SchedulerUtil currentScheduler = scheduler;
        if (currentScheduler != null) {
            currentScheduler.cancelAll();
        }
        PlayerSettingsManager settings = playerSettings;
        if (settings != null) {
            settings.shutdown();
        }
        unregisterPermissions();
    }

    private void initialize(
            @NotNull ConfigManager config,
            @NotNull ExpCommand command,
            @NotNull PlayerSettingsManager settings,
            @NotNull SchedulerUtil schedulerUtil
    ) {
        try {
            config.reload();
            Logger.setDebugEnabled(config.isDebug());
            settings.load();
        } catch (Exception exception) {
            Logger.error("Не удалось запустить rExpLevel", exception);
            disable(schedulerUtil);
            return;
        }
        if (shuttingDown) {
            return;
        }
        schedulerUtil.runGlobal(() -> registerRuntime(config, command, settings, schedulerUtil));
    }

    private void registerRuntime(
            @NotNull ConfigManager config,
            @NotNull ExpCommand command,
            @NotNull PlayerSettingsManager settings,
            @NotNull SchedulerUtil schedulerUtil
    ) {
        if (shuttingDown) {
            return;
        }

        BottleManager bottles = new BottleManager(
                new NamespacedKey(this, "experience_points"),
                new NamespacedKey(this, "display_levels"),
                config
        );
        ExperienceBottleManager experienceBottles = new ExperienceBottleManager(config);
        AutoConvertManager convertManager = new AutoConvertManager(
                settings,
                config,
                bottles,
                experienceBottles
        );
        ExperienceListener experience = new ExperienceListener(
                convertManager,
                config,
                schedulerUtil
        );

        autoConvert = convertManager;
        experienceListener = experience;

        command.install(List.of(
                new PackSubCommand(bottles, experienceBottles, config, schedulerUtil),
                new AutoSubCommand(convertManager, config, schedulerUtil),
                new GiveSubCommand(this, bottles, config, schedulerUtil),
                new ReloadSubCommand(config, schedulerUtil, experience),
                new HelpSubCommand(config)
        ));

        PluginManager plugins = getServer().getPluginManager();
        plugins.registerEvents(new BottleListener(bottles, config), this);
        plugins.registerEvents(experience, this);

        experience.start();
        placeholders.register(this, convertManager, config);
        Logger.info("rExpLevel включён");
    }

    @SuppressWarnings("UnstableApiUsage")
    private void registerCommands(@NotNull ExpCommand command, @NotNull ConfigManager config) {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var settings = config.commands();
            event.registrar().register(settings.main(), command);
            for (String alias : settings.aliases()) {
                event.registrar().register(alias, command);
            }
        });
    }

    private void registerPermissions() {
        PluginManager plugins = getServer().getPluginManager();
        for (String name : PERMISSIONS) {
            if (plugins.getPermission(name) != null) {
                continue;
            }
            PermissionDefault defaultValue = name.equals("rexp.give") || name.equals("rexp.reload")
                    ? PermissionDefault.OP
                    : PermissionDefault.TRUE;
            plugins.addPermission(new Permission(name, defaultValue));
        }
    }

    private void unregisterPermissions() {
        PluginManager plugins = getServer().getPluginManager();
        for (String name : PERMISSIONS) {
            plugins.removePermission(name);
        }
    }

    private void disable(@NotNull SchedulerUtil schedulerUtil) {
        if (!shuttingDown) {
            schedulerUtil.runGlobal(() -> getServer().getPluginManager().disablePlugin(this));
        }
    }
}
