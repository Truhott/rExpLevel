package ru.truhot.rexplevel.command.sub;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import ru.truhot.rexplevel.command.SubCommand;
import ru.truhot.rexplevel.manager.ConfigManager;
import ru.truhot.rexplevel.util.MessageUtil;
import ru.truhot.rexplevel.util.UpdateUtil;

@RequiredArgsConstructor
public final class UpdateSubCommand implements SubCommand {

    private final @NotNull UpdateUtil updates;
    private final @NotNull ConfigManager config;

    @Override
    public @NotNull String getName() {
        return "update";
    }

    @Override
    public @NotNull String getPermission() {
        return "rexp.update";
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(getPermission())) {
            sender.sendMessage(MessageUtil.parseText(config.getCommandMessage("no-permission")));
            return true;
        }
        if (args.length != 0) {
            sender.sendMessage(MessageUtil.parseText(config.getCommandMessage("update.usage")));
            return true;
        }
        updates.update(sender);
        return true;
    }
}
