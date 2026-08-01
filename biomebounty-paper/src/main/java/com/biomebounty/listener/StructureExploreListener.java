package com.biomebounty.listener;

import com.biomebounty.BiomeBountyPlugin;
import com.biomebounty.database.dao.PlayerAssignmentDAO.Assignment;
import com.biomebounty.util.BiomeUtil;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.generator.structure.Structure;
import org.bukkit.util.StructureSearchResult;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StructureExploreListener implements Listener {

    private static final int SEARCH_RADIUS = 1;
    private static final int ARRIVAL_RANGE = 48;
    private static final long COOLDOWN_MS = 10_000;

    private static final Map<String, String> TARGET_TO_STRUCTURE = new HashMap<>();
    static {
        TARGET_TO_STRUCTURE.put("village", "village_plains");
        TARGET_TO_STRUCTURE.put("fortress", "fortress");
        TARGET_TO_STRUCTURE.put("monument", "monument");
        TARGET_TO_STRUCTURE.put("mansion", "mansion");
        TARGET_TO_STRUCTURE.put("outpost", "pillager_outpost");
        TARGET_TO_STRUCTURE.put("shipwreck", "shipwreck");
        TARGET_TO_STRUCTURE.put("ruined_portal", "ruined_portal");
        TARGET_TO_STRUCTURE.put("igloo", "igloo");
        TARGET_TO_STRUCTURE.put("pyramid", "desert_pyramid");
        TARGET_TO_STRUCTURE.put("hut", "swamp_hut");
        TARGET_TO_STRUCTURE.put("temple", "jungle_pyramid");
    }

    private final BiomeBountyPlugin plugin;
    private final ConcurrentHashMap<String, Long> checkCache = new ConcurrentHashMap<>();

    public StructureExploreListener(BiomeBountyPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::cleanupCache, 6000L, 6000L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getChunk().getX() == to.getChunk().getX()
                && from.getChunk().getZ() == to.getChunk().getZ()) return;

        UUID playerId = player.getUniqueId();
        String world = player.getWorld().getName();
        String playerBiome = BiomeUtil.getBiomeId(player.getLocation().getBlock());
        String today = LocalDate.now().toString();

        for (Assignment a : plugin.getPlayerAssignmentDAO().findActiveByTarget(playerId, world, today, "explore", playerBiome)) {
            String cacheKey = playerId + ":" + a.target + ":" + to.getChunk().getX() + ":" + to.getChunk().getZ();
            Long lastCheck = checkCache.get(cacheKey);
            if (lastCheck != null && System.currentTimeMillis() - lastCheck < COOLDOWN_MS) continue;
            checkCache.put(cacheKey, System.currentTimeMillis());

            if (isNearStructure(player, a.target)) {
                plugin.getPlayerAssignmentDAO().addProgress(playerId, a.id, 1);
                player.sendMessage(BiomeUtil.buildCompletedMessage(
                        plugin.getI18nManager().getMessage("messages.prefix"),
                        a.target, a.taskType, 1));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    plugin.getDailyAssigner().checkGroupCompletion(a.groupId);
            }
        }
    }

    private boolean isNearStructure(Player player, String target) {
        Structure structure = resolveStructure(target);
        if (structure == null) return false;

        try {
            StructureSearchResult result = player.getWorld().locateNearestStructure(
                    player.getLocation(), structure, SEARCH_RADIUS, false);
            if (result != null && result.getLocation().distance(player.getLocation()) < ARRIVAL_RANGE) {
                return true;
            }
        } catch (Exception e) {
            plugin.debug("Structure search failed for " + target + ": " + e.getMessage());
        }
        return false;
    }

    private Structure resolveStructure(String target) {
        String lower = target.toLowerCase();
        String mapped = null;
        for (Map.Entry<String, String> e : TARGET_TO_STRUCTURE.entrySet()) {
            if (lower.contains(e.getKey())) {
                mapped = e.getValue();
                break;
            }
        }
        if (mapped == null) mapped = lower;

        NamespacedKey key = NamespacedKey.minecraft(mapped);
        Structure structure = Registry.STRUCTURE.get(key);
        if (structure != null) return structure;

        try {
            key = NamespacedKey.minecraft(lower.replace(" ", "_"));
            return Registry.STRUCTURE.get(key);
        } catch (Exception e) {
            return null;
        }
    }

    private void cleanupCache() {
        long now = System.currentTimeMillis();
        checkCache.entrySet().removeIf(e -> now - e.getValue() > 30_000);
    }
}
