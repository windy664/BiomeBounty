package com.biomebounty.command;

import com.biomebounty.BiomeBountyPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BountyCommand implements CommandExecutor {
    private final BiomeBountyPlugin plugin;

    public BountyCommand(BiomeBountyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§c只能由玩家执行"); return true; }

        if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            for (String line : plugin.getI18nManager().getMessage("messages.command.help").split("\n")) player.sendMessage(line);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload") && player.hasPermission("biomebounty.admin.reload")) {
            plugin.reload();
            player.sendMessage(plugin.getI18nManager().getPrefixedMessage("messages.command.reload"));
            return true;
        }

        // /bounty → 打开每日任务 GUI
        plugin.getAssignmentGUI().open(player);
        return true;
    }
}
