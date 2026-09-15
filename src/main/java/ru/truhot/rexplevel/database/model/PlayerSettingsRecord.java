package ru.truhot.rexplevel.database.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@DatabaseTable(tableName = "rexplevel_player_settings")
public final class PlayerSettingsRecord {

    @DatabaseField(columnName = "id", generatedId = true)
    private int id;

    @DatabaseField(columnName = "player_uuid", canBeNull = false, uniqueIndex = true)
    private @Nullable String playerUuid;

    @DatabaseField(columnName = "auto_enabled", canBeNull = false)
    private boolean autoEnabled;

    public PlayerSettingsRecord(@NotNull UUID playerUuid, boolean autoEnabled) {
        this.playerUuid = playerUuid.toString();
        this.autoEnabled = autoEnabled;
    }
}
