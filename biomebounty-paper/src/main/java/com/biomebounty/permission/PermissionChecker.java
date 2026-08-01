package com.biomebounty.permission;

import org.bukkit.entity.Player;

public class PermissionChecker {
    public static boolean canAccessBiome(Player player, String biomeId) {
        if (player.hasPermission("biomebounty.biome." + biomeId)) return true;
        String ns = biomeId.split(":")[0];
        if (player.hasPermission("biomebounty.biome." + ns + ".*")) return true;
        return player.hasPermission("biomebounty.biome.*");
    }

    public static boolean canAcceptTask(Player player, String type) {
        return player.hasPermission("biomebounty.task." + type) || player.hasPermission("biomebounty.task.*");
    }

    public static boolean canOpenGUI(Player player, String type) {
        return player.hasPermission("biomebounty.gui." + type) || player.hasPermission("biomebounty.gui.*");
    }
}
