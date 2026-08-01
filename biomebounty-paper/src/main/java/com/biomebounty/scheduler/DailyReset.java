package com.biomebounty.scheduler;

import com.biomebounty.BiomeBountyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.Calendar;

public class DailyReset {
    private final BiomeBountyPlugin plugin;
    public DailyReset(BiomeBountyPlugin plugin) { this.plugin = plugin; }

    public void start() {
        long delay = calcDelay();
        Bukkit.getScheduler().runTaskTimer(plugin, this::reset, delay, 24 * 60 * 60 * 20);
    }

    private long calcDelay() {
        int hour = plugin.getConfig().getInt("settings.daily-reset-hour", 3);
        Calendar now = Calendar.getInstance(), reset = Calendar.getInstance();
        reset.set(Calendar.HOUR_OF_DAY, hour); reset.set(Calendar.MINUTE, 0); reset.set(Calendar.SECOND, 0);
        if (now.after(reset)) reset.add(Calendar.DAY_OF_MONTH, 1);
        return (reset.getTimeInMillis() - now.getTimeInMillis()) / 50;
    }

    private void reset() {
        // 先清任务，再清组（外键约束）
        plugin.getPlayerAssignmentDAO().resetAllAssignments();
        plugin.getTaskGroupDAO().resetAll();
        plugin.debug("DailyReset: cleared all assignments and groups");

        // 为在线玩家重新分配
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getDailyAssigner().assignPlayer(player);
        }

        Bukkit.broadcastMessage(plugin.getI18nManager().getPrefixedMessage("messages.notify.daily-reset"));
    }
}
