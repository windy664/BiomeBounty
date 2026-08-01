package com.biomebounty.listener;

import com.biomebounty.BiomeBountyPlugin;
import com.biomebounty.database.dao.TaskGroupDAO.TaskGroup;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;

public class PlayerListener implements Listener {
    private final BiomeBountyPlugin plugin;
    public PlayerListener(BiomeBountyPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            List<TaskGroup> groups = plugin.getDailyAssigner().assignPlayer(player);
            if (groups.isEmpty()) return;

            long completed = groups.stream().filter(g -> g.isCompleted() || g.isClaimed()).count();

            player.sendMessage(plugin.getI18nManager().getPrefixedMessage("messages.notify.daily-reset"));
            player.sendMessage("§7今日悬赏: §f" + groups.size() + " §7组 " +
                    "(§6" + completed + " 已完成§7)");
            player.sendMessage("§7输入 §e/bounty §7打开每日悬赏面板");
        }, 40L);
    }
}
