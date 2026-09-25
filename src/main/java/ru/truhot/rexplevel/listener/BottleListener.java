package ru.truhot.rexplevel.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(@NotNull PlayerInteractEvent event) {
        if (config.bottle().redeem() != RedeemMode.DRINK) {
            return;
        }
        if (event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        EquipmentSlot hand = event.getHand();
        if (hand == null || !event.getAction().isRightClick()) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        boolean mainBottle = bottles.isBottle(main);
        boolean offBottle = bottles.isBottle(off);
        if (!mainBottle && !offBottle) {
            return;
        }
        if (hand == EquipmentSlot.OFF_HAND && mainBottle) {
            return;
        }

        if (!mainBottle && main.getType() == Material.EXPERIENCE_BOTTLE) {
            if (hand == EquipmentSlot.OFF_HAND) {
                return;
            }
            deny(event);
            drink(player, EquipmentSlot.OFF_HAND);
            return;
        }

        if (!bottles.isBottle(event.getItem())) {
            return;
        }
        deny(event);
        drink(player, hand);
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
        if (!(event.getEntity() instanceof ThrownExpBottle bottle) || !bottles.isBottle(bottle.getItem())) {
            return;
        }
        if (config.bottle().redeem() != RedeemMode.THROW) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(@NotNull ExpBottleEvent event) {
        int points = bottles.points(event.getEntity().getItem());
        if (points <= 0) {
            return;
        }
        event.setExperience(points);
    }

    private void deny(@NotNull PlayerInteractEvent event) {
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
    }

    private void drink(@NotNull Player player, @NotNull EquipmentSlot hand) {
        int points = bottles.drink(player, hand);
        if (points < 0) {
            send(player, "drink-full", Map.of());
            return;
        }
        if (points > 0) {
            send(player, "drink", Map.of("points", points));
        }
    }

    private void send(
            @NotNull CommandSender sender,
            @NotNull String key,
            @NotNull Map<String, ?> placeholders
    ) {
        sender.sendMessage(MessageUtil.parseText(config.formatCommandMessage(key, placeholders)));
    }
}
