package com.biomebounty.listener;

import com.biomebounty.BiomeBountyPlugin;
import com.biomebounty.database.dao.PlayerAssignmentDAO.Assignment;
import com.biomebounty.util.BiomeUtil;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.time.LocalDate;
import java.util.*;

public class EntityListener implements Listener {
    private final BiomeBountyPlugin plugin;
    public EntityListener(BiomeBountyPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        String type = event.getEntity().getType().name();
        UUID id = killer.getUniqueId();
        String world = killer.getWorld().getName();
        String playerBiome = BiomeUtil.getBiomeId(killer.getLocation().getBlock());
        String today = LocalDate.now().toString();

        for (Assignment a : plugin.getPlayerAssignmentDAO().findActiveByTarget(id, world, today, "kill", playerBiome)) {
            String normalizedTarget = BiomeUtil.normalizeEntityType(a.target);
            if (type.equalsIgnoreCase(normalizedTarget)) {
                int newProgress = plugin.getPlayerAssignmentDAO().addProgress(id, a.id, 1);
                if (newProgress == a.amount) {
                    killer.sendMessage(BiomeUtil.buildCompletedMessage(
                            plugin.getI18nManager().getMessage("messages.prefix"),
                            a.target, a.taskType, a.amount));
                    killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    plugin.getDailyAssigner().checkGroupCompletion(a.groupId);
                }
            }
        }
    }
}
