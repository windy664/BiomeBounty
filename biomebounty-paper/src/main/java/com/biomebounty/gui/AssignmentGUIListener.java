package com.biomebounty.gui;

import com.biomebounty.BiomeBountyPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * 每日任务 GUI 点击监听
 */
public class AssignmentGUIListener implements Listener {
    private final BiomeBountyPlugin plugin;
    private final AssignmentGUI assignmentGUI;

    public AssignmentGUIListener(BiomeBountyPlugin plugin, AssignmentGUI assignmentGUI) {
        this.plugin = plugin;
        this.assignmentGUI = assignmentGUI;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().contains("每日悬赏")) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot >= 0 && slot < 54) {
            assignmentGUI.handleClick(player, slot);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            assignmentGUI.closeGUI(player.getUniqueId());
        }
    }
}
