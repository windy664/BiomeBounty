package com.biomebounty.command;

import com.biomebounty.BiomeBountyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.*;

public class AdminCommand implements CommandExecutor {
    private final BiomeBountyPlugin plugin;
    public AdminCommand(BiomeBountyPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) { showHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("biomebounty.admin.reload")) { noPerm(sender); return true; }
                plugin.reload();
                sender.sendMessage(plugin.getI18nManager().getPrefixedMessage("messages.command.reload"));
                break;
            case "reset":
                if (!sender.hasPermission("biomebounty.admin.reset")) { noPerm(sender); return true; }
                if (args.length < 2) { sender.sendMessage("§c用法: /bountyadmin reset <玩家>"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { sender.sendMessage(plugin.getI18nManager().getPrefixedMessage("messages.error.player-not-found")); return true; }
                plugin.getPlayerAssignmentDAO().resetAllAssignments();
                Map<String, String> resetPh = new HashMap<>(); resetPh.put("player", target.getName());
                sender.sendMessage(plugin.getI18nManager().getPrefixedMessage("messages.command.reset", resetPh));
                break;
            case "resetall":
                if (!sender.hasPermission("biomebounty.admin.reset")) { noPerm(sender); return true; }
                plugin.getPlayerAssignmentDAO().resetAllAssignments();
                sender.sendMessage(plugin.getI18nManager().getPrefixedMessage("messages.command.reset-all"));
                break;
            case "scan":
                if (!sender.hasPermission("biomebounty.admin.reload")) { noPerm(sender); return true; }
                plugin.reload();
                sender.sendMessage("§a已重新扫描群系数据，共 " + plugin.getBiomeRegistry().getSize() + " 个群系");
                break;
            case "debug":
                if (!sender.hasPermission("biomebounty.admin.debug")) { noPerm(sender); return true; }
                boolean debug = !plugin.isDebug();
                plugin.getConfig().set("settings.debug", debug);
                plugin.saveConfig();
                Map<String, String> debugPh = new HashMap<>(); debugPh.put("status", debug ? "开启" : "关闭");
                sender.sendMessage(plugin.getI18nManager().getPrefixedMessage("messages.command.debug", debugPh));
                break;
            default: showHelp(sender);
        }
        return true;
    }

    private void showHelp(CommandSender sender) {
        for (String line : plugin.getI18nManager().getMessage("messages.command.admin-help").split("\n")) sender.sendMessage(line);
    }

    private void noPerm(CommandSender sender) { sender.sendMessage(plugin.getI18nManager().getPrefixedMessage("messages.error.no-permission")); }
}
