package ru.truhot.rexplevel.database.repository;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.truhot.rexplevel.database.model.PlayerBottleModeRecord;
import ru.truhot.rexplevel.database.model.PlayerSettingsRecord;
import ru.truhot.rexplevel.model.BottleSettings.BottleMode;
import ru.truhot.rexplevel.util.SchedulerUtil;
import ru.truhot.rexplevel.util.logger.Logger;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@RequiredArgsConstructor
public final class PlayerSettingsRepository {

    private static final String UUID_COLUMN = "player_uuid";

    private final @NotNull Dao<PlayerSettingsRecord, Integer> settingsDao;
    private final @NotNull Dao<PlayerBottleModeRecord, Integer> bottleModeDao;
    private final @NotNull SchedulerUtil scheduler;
    private final @NotNull ConcurrentMap<UUID, PlayerSettingsRecord> settingsCache =
            new ConcurrentHashMap<>();
    private final @NotNull ConcurrentMap<UUID, BottleMode> bottleModeCache =
            new ConcurrentHashMap<>();
    private final @NotNull ConcurrentMap<UUID, Long> writeGeneration = new ConcurrentHashMap<>();
    private final @NotNull Set<UUID> loaded = ConcurrentHashMap.newKeySet();

    public boolean isLoaded(@NotNull UUID playerUuid) {
        return loaded.contains(playerUuid);
    }

    public void loadPlayer(@NotNull UUID playerUuid) {
        long generation = writeGeneration.getOrDefault(playerUuid, 0L);
        scheduler.runAsync(() -> {
            try {
                PlayerSettingsRecord settings = querySettings(playerUuid);
                BottleMode mode = queryBottleMode(playerUuid);
                if (writeGeneration.getOrDefault(playerUuid, 0L) != generation) {
                    loaded.add(playerUuid);
                    return;
                }
                if (settings != null) {
                    settingsCache.put(playerUuid, settings);
                }
                if (mode != null) {
                    bottleModeCache.put(playerUuid, mode);
                }
                loaded.add(playerUuid);
            } catch (SQLException exception) {
                Logger.error("Не удалось загрузить настройки игрока " + playerUuid, exception);
                loaded.add(playerUuid);
            }
        });
    }

    public void unloadPlayer(@NotNull UUID playerUuid) {
        PlayerSettingsRecord settings = settingsCache.remove(playerUuid);
        BottleMode mode = bottleModeCache.remove(playerUuid);
        loaded.remove(playerUuid);
        writeGeneration.remove(playerUuid);
        if (settings != null) {
            saveSync(playerUuid, settings.isAutoEnabled());
        }
        if (mode != null) {
            saveBottleModeSync(playerUuid, mode);
        }
    }

    public void flush() {
        for (Map.Entry<UUID, PlayerSettingsRecord> entry : settingsCache.entrySet()) {
            saveSync(entry.getKey(), entry.getValue().isAutoEnabled());
        }
        for (Map.Entry<UUID, BottleMode> entry : bottleModeCache.entrySet()) {
            saveBottleModeSync(entry.getKey(), entry.getValue());
        }
        settingsCache.clear();
        bottleModeCache.clear();
        loaded.clear();
        writeGeneration.clear();
    }

    public boolean isAutoEnabled(@NotNull UUID playerUuid, boolean fallback) {
        if (!loaded.contains(playerUuid)) {
            return false;
        }
        PlayerSettingsRecord record = settingsCache.get(playerUuid);
        return record == null ? fallback : record.isAutoEnabled();
    }

    public void setAutoEnabled(@NotNull UUID playerUuid, boolean enabled) {
        writeGeneration.merge(playerUuid, 1L, Long::sum);
        long generation = writeGeneration.getOrDefault(playerUuid, 1L);
        settingsCache.compute(playerUuid, (uuid, record) -> {
            PlayerSettingsRecord settings = record == null
                    ? new PlayerSettingsRecord(uuid, enabled)
                    : record;
            settings.setAutoEnabled(enabled);
            return settings;
        });
        scheduler.runAsync(() -> {
            if (writeGeneration.getOrDefault(playerUuid, 0L) != generation) {
                return;
            }
            PlayerSettingsRecord current = settingsCache.get(playerUuid);
            if (current == null) {
                return;
            }
            saveSync(playerUuid, current.isAutoEnabled());
        });
    }

    public @NotNull BottleMode getBottleMode(
            @NotNull UUID playerUuid,
            @NotNull BottleMode fallback
    ) {
        return bottleModeCache.getOrDefault(playerUuid, fallback);
    }

    public void setBottleMode(@NotNull UUID playerUuid, @NotNull BottleMode mode) {
        writeGeneration.merge(playerUuid, 1L, Long::sum);
        long generation = writeGeneration.getOrDefault(playerUuid, 1L);
        bottleModeCache.put(playerUuid, mode);
        scheduler.runAsync(() -> {
            if (writeGeneration.getOrDefault(playerUuid, 0L) != generation) {
                return;
            }
            BottleMode current = bottleModeCache.get(playerUuid);
            if (current == null) {
                return;
            }
            saveBottleModeSync(playerUuid, current);
        });
    }

    private @Nullable PlayerSettingsRecord querySettings(@NotNull UUID playerUuid)
            throws SQLException {
        QueryBuilder<PlayerSettingsRecord, Integer> query = settingsDao.queryBuilder();
        query.where().eq(UUID_COLUMN, playerUuid.toString());
        return settingsDao.queryForFirst(query.prepare());
    }

    private @Nullable BottleMode queryBottleMode(@NotNull UUID playerUuid) throws SQLException {
        QueryBuilder<PlayerBottleModeRecord, Integer> query = bottleModeDao.queryBuilder();
        query.where().eq(UUID_COLUMN, playerUuid.toString());
        PlayerBottleModeRecord record = bottleModeDao.queryForFirst(query.prepare());
        return record == null ? null : BottleMode.find(record.getBottleMode());
    }

    private void saveSync(@NotNull UUID playerUuid, boolean enabled) {
        try {
            PlayerSettingsRecord record = querySettings(playerUuid);
            if (record == null) {
                try {
                    settingsDao.create(new PlayerSettingsRecord(playerUuid, enabled));
                } catch (SQLException createException) {
                    PlayerSettingsRecord existing = querySettings(playerUuid);
                    if (existing == null) {
                        throw createException;
                    }
                    existing.setAutoEnabled(enabled);
                    settingsDao.update(existing);
                }
                return;
            }
            record.setAutoEnabled(enabled);
            settingsDao.update(record);
        } catch (SQLException exception) {
            Logger.error("Не удалось сохранить настройки игрока " + playerUuid, exception);
        }
    }

    private void saveBottleModeSync(@NotNull UUID playerUuid, @NotNull BottleMode mode) {
        try {
            QueryBuilder<PlayerBottleModeRecord, Integer> query = bottleModeDao.queryBuilder();
            query.where().eq(UUID_COLUMN, playerUuid.toString());
            PlayerBottleModeRecord record = bottleModeDao.queryForFirst(query.prepare());
            if (record == null) {
                try {
                    bottleModeDao.create(new PlayerBottleModeRecord(playerUuid, mode));
                } catch (SQLException createException) {
                    PlayerBottleModeRecord existing = bottleModeDao.queryForFirst(query.prepare());
                    if (existing == null) {
                        throw createException;
                    }
                    existing.setBottleMode(mode.name());
                    bottleModeDao.update(existing);
                }
                return;
            }
            record.setBottleMode(mode.name());
            bottleModeDao.update(record);
        } catch (SQLException exception) {
            Logger.error("Не удалось сохранить режим игрока " + playerUuid, exception);
        }
    }
}
