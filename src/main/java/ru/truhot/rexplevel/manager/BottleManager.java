package ru.truhot.rexplevel.manager;

import lombok.RequiredArgsConstructor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.truhot.rexplevel.model.BottleSettings;
import ru.truhot.rexplevel.model.BottleSettings.PackStatus;
import ru.truhot.rexplevel.util.ExperienceUtil;
import ru.truhot.rexplevel.util.MessageUtil;

import java.util.Map;

@RequiredArgsConstructor
public final class BottleManager {

    private final @NotNull NamespacedKey pointsKey;
    private final @NotNull NamespacedKey levelsKey;
    private final @NotNull ConfigManager config;

    public @NotNull ItemStack create(int levels, int amount) {
        BottleSettings settings = config.bottle();
        int points = ExperienceUtil.pointsAtLevel(levels);
        Map<String, Object> placeholders = Map.of(
                "levels", levels,
                "points", points
        );
        ItemStack item = new ItemStack(settings.material(), amount);
        item.editMeta(meta -> {
            meta.displayName(MessageUtil.parseText(config.format(settings.name(), placeholders)));
            meta.lore(MessageUtil.parseText(settings.lore().stream()
                    .map(line -> config.format(line, placeholders))
                    .toList()));
            meta.getPersistentDataContainer().set(pointsKey, PersistentDataType.INTEGER, points);
            meta.getPersistentDataContainer().set(levelsKey, PersistentDataType.INTEGER, levels);
            if (settings.customModelData() > 0) {
                meta.setCustomModelData(settings.customModelData());
            }
            meta.setEnchantmentGlintOverride(settings.glint());
        });
        return item;
    }

    public boolean isBottle(@Nullable ItemStack item) {
        return points(item) > 0;
    }

    public int points(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Integer points = item.getItemMeta().getPersistentDataContainer()
                .get(pointsKey, PersistentDataType.INTEGER);
        return points == null ? 0 : Math.max(0, points);
    }

    public @NotNull PackStatus pack(@NotNull Player player, int levels, int amount) {
        return pack(player, levels, amount, config.bottle().requireGlassBottle());
    }

    public @NotNull PackStatus pack(
            @NotNull Player player,
            int levels,
            int amount,
            boolean requireGlass
    ) {
        int points = ExperienceUtil.pointsAtLevel(levels);
        long required = (long) points * amount;
        int current = ExperienceUtil.totalPoints(player);
        if (required > current || required > Integer.MAX_VALUE) {
            return PackStatus.NOT_ENOUGH_XP;
        }
        if (requireGlass && ExperienceUtil.Glass.count(player) < amount) {
            return PackStatus.NO_GLASS;
        }

        if (requireGlass && !ExperienceUtil.Glass.consume(player, amount)) {
            return PackStatus.NO_GLASS;
        }
        ExperienceUtil.setPoints(player, current - (int) required);
        give(player, levels, amount);
        return PackStatus.SUCCESS;
    }

    public void give(@NotNull Player player, int levels, int amount) {
        int maxStack = config.bottle().material().getMaxStackSize();
        int left = amount;
        while (left > 0) {
            int stackSize = Math.min(left, maxStack);
            player.getInventory().addItem(create(levels, stackSize)).values()
                    .forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            left -= stackSize;
        }
    }

    public int drink(@NotNull Player player, @NotNull EquipmentSlot hand) {
        ItemStack item = player.getInventory().getItem(hand);
        int bottlePoints = points(item);
        if (bottlePoints <= 0) {
            return 0;
        }

        int capacity = Integer.MAX_VALUE - ExperienceUtil.totalPoints(player);
        if (capacity / bottlePoints < 1) {
            return -1;
        }
        int left = item.getAmount() - 1;
        if (left == 0) {
            player.getInventory().setItem(hand, null);
        } else {
            item.setAmount(left);
            player.getInventory().setItem(hand, item);
        }

        ExperienceUtil.addPoints(player, bottlePoints);
        return bottlePoints;
    }
}
