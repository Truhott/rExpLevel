package ru.truhot.rexplevel.database;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.DataSourceConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.truhot.rexplevel.database.model.PlayerBottleModeRecord;
import ru.truhot.rexplevel.database.model.PlayerSettingsRecord;
import ru.truhot.rexplevel.util.logger.Logger;

import java.nio.file.Files;
import java.nio.file.Path;

@RequiredArgsConstructor
public final class DatabaseManager implements AutoCloseable {

    private static final String DATABASE_PATH = "data/data.db";

    private final @NotNull Plugin plugin;

    private @Nullable HikariDataSource dataSource;
    private @Nullable ConnectionSource connectionSource;
    private @Nullable Dao<PlayerSettingsRecord, Integer> playerSettingsDao;
    private @Nullable Dao<PlayerBottleModeRecord, Integer> playerBottleModeDao;

    public boolean connect() {
        try {
            Path dataDirectory = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
            Path databasePath = dataDirectory.resolve(DATABASE_PATH).normalize();
            if (!databasePath.startsWith(dataDirectory)) {
                Logger.error("Файл базы данных должен находиться в папке плагина");
                return false;
            }
            Path parent = databasePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            String jdbcUrl = "jdbc:sqlite:" + databasePath;
            HikariConfig hikari = new HikariConfig();
            hikari.setPoolName("rExpLevel-SQLite");
            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setJdbcUrl(jdbcUrl);
            hikari.setMaximumPoolSize(2);
            hikari.setMinimumIdle(1);
            hikari.setConnectionTimeout(10_000);
            hikari.setConnectionTestQuery("SELECT 1");
            hikari.setConnectionInitSql(
                    "PRAGMA busy_timeout = 5000; PRAGMA journal_mode = DELETE; PRAGMA synchronous = NORMAL;"
            );

            dataSource = new HikariDataSource(hikari);
            connectionSource = new DataSourceConnectionSource(dataSource, jdbcUrl);
            TableUtils.createTableIfNotExists(connectionSource, PlayerSettingsRecord.class);
            TableUtils.createTableIfNotExists(connectionSource, PlayerBottleModeRecord.class);
            playerSettingsDao = DaoManager.createDao(connectionSource, PlayerSettingsRecord.class);
            playerBottleModeDao = DaoManager.createDao(connectionSource, PlayerBottleModeRecord.class);
            Logger.info("SQLite подключена");
            return true;
        } catch (Exception exception) {
            Logger.error("Не удалось подключить SQLite", exception);
            close();
            return false;
        }
    }

    public @Nullable Dao<PlayerSettingsRecord, Integer> playerSettingsDao() {
        if (playerSettingsDao == null) {
            Logger.error("База данных не подключена");
        }
        return playerSettingsDao;
    }

    public @Nullable Dao<PlayerBottleModeRecord, Integer> playerBottleModeDao() {
        if (playerBottleModeDao == null) {
            Logger.error("База данных не подключена");
        }
        return playerBottleModeDao;
    }

    @Override
    public void close() {
        if (connectionSource != null) {
            try {
                connectionSource.close();
            } catch (Exception exception) {
                Logger.error("Не удалось закрыть ORMLite", exception);
            }
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
