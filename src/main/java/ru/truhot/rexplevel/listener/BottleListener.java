package ru.truhot.rexplevel.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import ru.truhot.rexplevel.manager.BottleManager;
import ru.truhot.rexplevel.manager.ConfigManager;
import ru.truhot.rexplevel.model.BottleSettings.RedeemMode;
import ru.truhot.rexplevel.util.MessageUtil;

import java.util.Map;

@RequiredArgsConstructor
public final class BottleListener implements Listener {

    private final @NotNull BottleManager bottles;
    private final @NotNull ConfigManager config;

    @EventHandler(ignoreCancelled = true)
    public void onUse(@NotNull PlayerInteractEvent event) {
        if (config.bottle().redeem() != RedeemMode.DRINK) {
            return;
        }
        EquipmentSlot hand = event.getHand();
        if (hand == null || !event.getAction().isRightClick()) {
            return;
        }

        ItemStack main = event.getPlayer().getInventory().getItemInMainHand();
        ItemStack off = event.getPlayer().getInventory().getItemInOffHand();
        boolean mainBottle = bottles.isBottle(main);
        boolean offBottle = bottles.isBottle(off);

        if (!mainBottle && !offBottle) {
            return;
        }

        if (hand == EquipmentSlot.OFF_HAND && mainBottle) {
            return;
        }

        // Main hand throws vanilla bottle while off-hand holds custom: drink off once on MAIN event.
        if (!mainBottle && offBottle && main.getType() == Material.EXPERIENCE_BOTTLE) {
            if (hand == EquipmentSlot.OFF_HAND) {
                return;
            }
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
            drink(event, EquipmentSlot.OFF_HAND);
            return;
        }

        if (!bottles.isBottle(event.getItem())) {
            return;
        }

        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
        drink(event, hand);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDispense(@NotNull BlockDispenseEvent event) {
        if (!bottles.isBottle(event.getItem())) {
            return;
        }
        if (config.bottle().redeem() != RedeemMode.THROW) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(@NotNull ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.ThrownExpBottle bottle)
                || !bottles.isBottle(bottle.getItem())) {
            return;
        }
        if (config.bottle().redeem() != RedeemMode.THROW) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(@NotNull ExpBottleEvent event) {
        ItemStack item = event.getEntity().getItem();
        int points = bottles.points(item);
        if (points <= 0) {
            return;
        }
        event.setExperience(points);
    }

    private void drink(@NotNull PlayerInteractEvent event, @NotNull EquipmentSlot hand) {
        int points = bottles.drink(event.getPlayer(), hand);
        if (points < 0) {
            send(event.getPlayer(), "drink-full");
            return;
        }
        if (points > 0) {
            send(event.getPlayer(), "drink", Map.of("points", points));
        }
    }

    private void send(
            @NotNull org.bukkit.command.CommandSender sender,
            @NotNull String key
    ) {
        send(sender, key, Map.of());
    }

    private void send(
            @NotNull org.bukkit.command.CommandSender sender,
            @NotNull String key,
            @NotNull Map<String, ?> placeholders
    ) {
        sender.sendMessage(MessageUtil.parseText(config.formatCommandMessage(key, placeholders)));
    }
}
