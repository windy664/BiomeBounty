package com.biomebounty.listener;

import com.biomebounty.BiomeBountyPlugin;
import com.biomebounty.database.dao.PlayerAssignmentDAO.Assignment;
import com.biomebounty.util.BiomeUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.time.LocalDate;
import java.util.*;

public class FarmListener implements Listener {
    private final BiomeBountyPlugin plugin;
    public FarmListener(BiomeBountyPlugin plugin) { this.plugin = plugin; }

    private static final Map<Material, String> CROP_TO_HARVEST = new HashMap<>();
    static {
        CROP_TO_HARVEST.put(Material.WHEAT, "WHEAT");
        CROP_TO_HARVEST.put(Material.CARROTS, "CARROTS");
        CROP_TO_HARVEST.put(Material.POTATOES, "POTATOES");
        CROP_TO_HARVEST.put(Material.BEETROOTS, "BEETROOTS");
        CROP_TO_HARVEST.put(Material.MELON_STEM, "MELON");
        CROP_TO_HARVEST.put(Material.PUMPKIN_STEM, "PUMPKIN");
        CROP_TO_HARVEST.put(Material.SWEET_BERRY_BUSH, "SWEET_BERRIES");
        CROP_TO_HARVEST.put(Material.COCOA, "COCOA_BEANS");
        CROP_TO_HARVEST.put(Material.NETHER_WART, "NETHER_WART");
        CROP_TO_HARVEST.put(Material.CHORUS_FLOWER, "CHORUS_FLOWER");
        CROP_TO_HARVEST.put(Material.TORCHFLOWER_CROP, "TORCHFLOWER");
        CROP_TO_HARVEST.put(Material.PITCHER_CROP, "PITCHER_PLANT");
        CROP_TO_HARVEST.put(Material.MELON, "MELON");
        CROP_TO_HARVEST.put(Material.PUMPKIN, "PUMPKIN");
        CROP_TO_HARVEST.put(Material.SUGAR_CANE, "SUGAR_CANE");
        CROP_TO_HARVEST.put(Material.BAMBOO, "BAMBOO");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material mat = block.getType();

        String harvest = CROP_TO_HARVEST.get(mat);
        if (harvest == null) return;

        if (block.getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() < ageable.getMaximumAge()) return;
        }

        UUID id = player.getUniqueId();
        String world = player.getWorld().getName();
        String playerBiome = BiomeUtil.getBiomeId(block);
        String today = LocalDate.now().toString();

        for (Assignment a : plugin.getPlayerAssignmentDAO().findActiveByTarget(id, world, today, "farm", playerBiome)) {
            if (harvest.equalsIgnoreCase(a.target)) {
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
