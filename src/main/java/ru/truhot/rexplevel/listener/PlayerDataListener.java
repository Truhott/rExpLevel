package ru.truhot.rexplevel.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import ru.truhot.rexplevel.database.repository.PlayerSettingsRepository;

@RequiredArgsConstructor
public final class PlayerDataListener implements Listener {

    private final @NotNull PlayerSettingsRepository repository;

    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {
        repository.loadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        repository.unloadPlayer(event.getPlayer().getUniqueId());
    }
}
