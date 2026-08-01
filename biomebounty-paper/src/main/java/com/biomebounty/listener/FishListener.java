package com.biomebounty.listener;

import com.biomebounty.BiomeBountyPlugin;
import com.biomebounty.database.dao.PlayerAssignmentDAO.Assignment;
import com.biomebounty.util.BiomeUtil;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

import java.time.LocalDate;
import java.util.*;

public class FishListener implements Listener {
    private final BiomeBountyPlugin plugin;
    public FishListener(BiomeBountyPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(event.getCaught() instanceof Item)) return;

        Player player = event.getPlayer();
        String caught = ((Item) event.getCaught()).getItemStack().getType().name();
        UUID id = player.getUniqueId();
        String world = player.getWorld().getName();
        String playerBiome = BiomeUtil.getBiomeId(player.getLocation().getBlock());
        String today = LocalDate.now().toString();

        for (Assignment a : plugin.getPlayerAssignmentDAO().findActiveByTarget(id, world, today, "fish", playerBiome)) {
            if (caught.equalsIgnoreCase(a.target)) {
                int newProgress = plugin.getPlayerAssignmentDAO().addProgress(id, a.id, 1);
                if (newProgress == a.amount) {
                    player.sendMessage(BiomeUtil.buildCompletedMessage(
                            plugin.getI18nManager().getMessage("messages.prefix"),
                            a.target, a.taskType, a.amount));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    plugin.getDailyAssigner().checkGroupCompletion(a.groupId);
                }
            }
        }
    }
}
