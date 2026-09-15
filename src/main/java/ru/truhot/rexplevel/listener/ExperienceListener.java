package ru.truhot.rexplevel.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.truhot.rexplevel.manager.AutoConvertManager;
import ru.truhot.rexplevel.manager.ConfigManager;
import ru.truhot.rexplevel.model.AutoSettings.ConvertResult;
import ru.truhot.rexplevel.model.AutoSettings.Trigger;
import ru.truhot.rexplevel.model.BottleSettings.BottleMode;
import ru.truhot.rexplevel.util.MessageUtil;
import ru.truhot.rexplevel.util.SchedulerUtil;
import ru.truhot.rexplevel.util.SchedulerUtil.Task;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public final class ExperienceListener implements Listener {

    private final @NotNull AutoConvertManager autoConvert;
    private final @NotNull ConfigManager config;
    private final @NotNull SchedulerUtil scheduler;
    private final @NotNull Map<UUID, Task> pending = new ConcurrentHashMap<>();
    private volatile @Nullable Task periodicTask;

    public void start() {
        restart();
    }

    public void restart() {
        stopPeriodic();
        cancelPending();
        if (config.auto().trigger() != Trigger.PERIODIC) {
            return;
        }
        long periodTicks = Math.max(1L, config.auto().intervalSeconds() * 20L);
        periodicTask = scheduler.runGlobalAtFixedRate(this::tickPeriodic, periodTicks);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExperience(@NotNull PlayerExpChangeEvent event) {
        if (config.auto().trigger() != Trigger.EVENT) {
            return;
        }
        Player player = event.getPlayer();
        if (event.getAmount() <= 0
                || autoConvert.isConverting(player.getUniqueId())
                || !player.hasPermission("rexp.auto")
                || !autoConvert.isEnabled(player)) {
            return;
        }
        queue(player);
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId());
    }

    public void stop() {
        stopPeriodic();
        cancelPending();
    }

    private void tickPeriodic() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (autoConvert.isConverting(player.getUniqueId())
                    || !player.hasPermission("rexp.auto")
                    || !autoConvert.isEnabled(player)) {
                continue;
            }
            queue(player);
        }
    }

    private void queue(@NotNull Player player) {
        UUID playerUuid = player.getUniqueId();
        if (pending.containsKey(playerUuid)) {
            return;
        }
        Task[] holder = new Task[1];
        holder[0] = scheduler.runForLater(player, () -> {
            pending.remove(playerUuid, holder[0]);
            ConvertResult result = autoConvert.convert(player);
            if (result.disabledNoGlass()) {
                player.sendMessage(MessageUtil.parseText(
                        config.formatCommandMessage("auto.disabled-no-glass", Map.of())
                ));
                return;
            }
            if (result.bottles() > 0) {
                String key = result.mode() == BottleMode.BOTTLE
                        ? "auto.packed-bottle"
                        : "auto.packed-experience";
                player.sendMessage(MessageUtil.parseText(
                        config.formatCommandMessage(key, Map.of("amount", result.bottles()))
                ));
            }
            if (config.auto().trigger() == Trigger.EVENT
                    && result.bottles() > 0
                    && autoConvert.hasConvertibleSurplus(player)) {
                queue(player);
            }
        }, 1L);
        if (pending.putIfAbsent(playerUuid, holder[0]) != null) {
            holder[0].cancel();
        }
    }

    private void cancel(@NotNull UUID playerUuid) {
        Task task = pending.remove(playerUuid);
        if (task != null) {
            task.cancel();
        }
    }

    private void cancelPending() {
        for (Task task : pending.values()) {
            task.cancel();
        }
        pending.clear();
    }

    private void stopPeriodic() {
        Task task = periodicTask;
        periodicTask = null;
        if (task != null) {
            task.cancel();
        }
    }
}
