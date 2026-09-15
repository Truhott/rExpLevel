package ru.truhot.rexplevel.database.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.truhot.rexplevel.model.BottleSettings.BottleMode;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@DatabaseTable(tableName = "rexplevel_player_bottle_modes")
public final class PlayerBottleModeRecord {

    @DatabaseField(columnName = "id", generatedId = true)
    private int id;

    @DatabaseField(columnName = "player_uuid", canBeNull = false, uniqueIndex = true)
    private @Nullable String playerUuid;

    @DatabaseField(columnName = "bottle_mode", canBeNull = false)
    private @Nullable String bottleMode;

    public PlayerBottleModeRecord(@NotNull UUID playerUuid, @NotNull BottleMode bottleMode) {
        this.playerUuid = playerUuid.toString();
        this.bottleMode = bottleMode.name();
    }
}
