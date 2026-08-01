package com.biomebounty.papi;

import com.biomebounty.BiomeBountyPlugin;
import com.biomebounty.database.dao.PlayerAssignmentDAO.Assignment;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.*;

public class BiomeBountyExpansion extends PlaceholderExpansion {
    private final BiomeBountyPlugin plugin;
    public BiomeBountyExpansion(BiomeBountyPlugin plugin) { this.plugin = plugin; }

    @Override public String getIdentifier() { return "biomebounty"; }
    @Override public String getAuthor() { return "BiomeBounty"; }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onPlaceholderRequest(Player player, String id) {
        if (player == null) return "";
        UUID pid = player.getUniqueId();
        String world = player.getWorld().getName();
        String today = LocalDate.now().toString();

        if (id.toLowerCase().startsWith("rank_")) {
            try {
                int n = Integer.parseInt(id.substring(5));
                List<Map.Entry<UUID, Integer>> top = plugin.getStatsManager().getTopPlayers(n);
                if (n > 0 && n <= top.size()) {
                    Player p = plugin.getServer().getPlayer(top.get(n - 1).getKey());
                    return p != null ? p.getName() : "";
                }
            } catch (NumberFormatException e) { /* ignore */ }
            return "";
        }

        List<Assignment> assignments = plugin.getPlayerAssignmentDAO().getTodayAssignments(pid, world, today);

        switch (id.toLowerCase()) {
            case "task_count": return String.valueOf(assignments.size());
            case "task_completed": return String.valueOf((int) assignments.stream()
                    .filter(Assignment::isDone).count());
            case "total_completed": return String.valueOf(plugin.getStatsManager().getTotalCompleted(pid));
            case "streak": return String.valueOf(plugin.getStatsManager().getCurrentStreak(pid));
            case "rank": return String.valueOf(plugin.getStatsManager().getRank(pid));
            default: return null;
        }
    }
}
