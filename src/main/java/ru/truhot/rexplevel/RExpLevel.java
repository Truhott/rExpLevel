package ru.truhot.rexplevel;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
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
import ru.truhot.rexplevel.command.sub.UpdateSubCommand;
import ru.truhot.rexplevel.database.DatabaseManager;
import ru.truhot.rexplevel.database.repository.PlayerSettingsRepository;
import ru.truhot.rexplevel.listener.BottleListener;
import ru.truhot.rexplevel.listener.ExperienceListener;
import ru.truhot.rexplevel.listener.PlayerDataListener;
import ru.truhot.rexplevel.manager.AutoConvertManager;
import ru.truhot.rexplevel.manager.BottleManager;
import ru.truhot.rexplevel.manager.ConfigManager;
import ru.truhot.rexplevel.manager.ExperienceBottleManager;
import ru.truhot.rexplevel.placeholder.PlaceholderHook;
import ru.truhot.rexplevel.util.Metrics;
import ru.truhot.rexplevel.util.SchedulerUtil;
import ru.truhot.rexplevel.util.UpdateUtil;
import ru.truhot.rexplevel.util.logger.Logger;

import java.util.List;

public final class RExpLevel extends JavaPlugin {

    private volatile boolean shuttingDown;
    private @Nullable SchedulerUtil scheduler;
    private @Nullable DatabaseManager database;
    private @Nullable PlayerSettingsRepository repository;
    private @Nullable ExperienceListener experienceListener;
    private @Nullable AutoConvertManager autoConvert;
    private @Nullable Metrics metrics;
    private final @NotNull PlaceholderHook placeholders = new PlaceholderHook();

    @Override
    public void onEnable() {
        Logger.setup(this);
        SchedulerUtil schedulerUtil = new SchedulerUtil(this);
        ConfigManager config = new ConfigManager(this);
        try {
            config.loadForStartup();
        } catch (Exception exception) {
            Logger.error("Не удалось загрузить команды из config.yml", exception);
        }
        if (config.isMetrics()) {
            int pluginId = 34005;
            metrics = new Metrics(this, pluginId);
            Logger.info("bStats успешно инициализирован!");
        }
        ExpCommand command = new ExpCommand(config);
        scheduler = schedulerUtil;
        registerPermissions();
        registerCommands(command, config);
        schedulerUtil.runAsync(() -> initialize(config, command, schedulerUtil));
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
        placeholders.unregister();
        if (metrics != null) {
            metrics.shutdown();
        }
        if (experienceListener != null) {
            experienceListener.stop();
        }
        if (autoConvert != null) {
            autoConvert.stop();
        }
        if (repository != null) {
            repository.flush();
        }
        if (scheduler != null) {
            scheduler.cancelAll();
        }
        if (database != null) {
            database.close();
        }
    }

    private void initialize(
            @NotNull ConfigManager config,
            @NotNull ExpCommand command,
            @NotNull SchedulerUtil schedulerUtil
    ) {
        DatabaseManager databaseManager = new DatabaseManager(this);
        try {
            config.reload();
            Logger.setDebugEnabled(config.isDebug());
            if (!databaseManager.connect()) {
                disable(schedulerUtil);
                return;
            }

            var settingsDao = databaseManager.playerSettingsDao();
            var bottleModeDao = databaseManager.playerBottleModeDao();
            if (settingsDao == null || bottleModeDao == null) {
                databaseManager.close();
                disable(schedulerUtil);
                return;
            }

            PlayerSettingsRepository settingsRepository = new PlayerSettingsRepository(
                    settingsDao,
                    bottleModeDao,
                    schedulerUtil
            );
            if (shuttingDown) {
                databaseManager.close();
                return;
            }

            database = databaseManager;
            repository = settingsRepository;
            schedulerUtil.runGlobal(() -> registerRuntime(
                    config,
                    command,
                    settingsRepository,
                    schedulerUtil
            ));
        } catch (Exception exception) {
            databaseManager.close();
            Logger.error("Не удалось запустить rExpLevel", exception);
            disable(schedulerUtil);
        }
    }

    private void registerRuntime(
            @NotNull ConfigManager config,
            @NotNull ExpCommand command,
            @NotNull PlayerSettingsRepository settingsRepository,
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
                settingsRepository,
                config,
                bottles,
                experienceBottles
        );
        ExperienceListener experience = new ExperienceListener(
                convertManager,
                config,
                schedulerUtil
        );
        UpdateUtil updates = new UpdateUtil(this, schedulerUtil, config);

        autoConvert = convertManager;
        experienceListener = experience;

        command.install(List.of(
                new PackSubCommand(bottles, experienceBottles, config, schedulerUtil),
                new AutoSubCommand(convertManager, config, schedulerUtil),
                new GiveSubCommand(this, bottles, config, schedulerUtil),
                new ReloadSubCommand(config, schedulerUtil, experience),
                new UpdateSubCommand(updates, config),
                new HelpSubCommand(config)
        ));

        PluginManager plugins = getServer().getPluginManager();
        plugins.registerEvents(new BottleListener(bottles, config), this);
        plugins.registerEvents(experience, this);
        plugins.registerEvents(new PlayerDataListener(settingsRepository), this);

        experience.start();

        for (Player player : getServer().getOnlinePlayers()) {
            settingsRepository.loadPlayer(player.getUniqueId());
        }
        placeholders.register(this, convertManager, config);
        Logger.info("rExpLevel включён");
        updates.check();
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
        registerPermission(plugins, "rexp.use", PermissionDefault.TRUE);
        registerPermission(plugins, "rexp.pack", PermissionDefault.TRUE);
        registerPermission(plugins, "rexp.auto", PermissionDefault.TRUE);
        registerPermission(plugins, "rexp.give", PermissionDefault.OP);
        registerPermission(plugins, "rexp.reload", PermissionDefault.OP);
        registerPermission(plugins, "rexp.update", PermissionDefault.OP);
    }

    private void registerPermission(
            @NotNull PluginManager plugins,
            @NotNull String name,
            @NotNull PermissionDefault defaultValue
    ) {
        if (plugins.getPermission(name) == null) {
            plugins.addPermission(new Permission(name, defaultValue));
        }
    }

    private void disable(@NotNull SchedulerUtil schedulerUtil) {
        if (!shuttingDown) {
            schedulerUtil.runGlobal(() -> getServer().getPluginManager().disablePlugin(this));
        }
    }
}
