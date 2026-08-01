package com.biomebounty.gui;

import com.biomebounty.BiomeBountyPlugin;
import com.biomebounty.database.dao.PlayerAssignmentDAO;
import com.biomebounty.database.dao.PlayerAssignmentDAO.Assignment;
import com.biomebounty.database.dao.TaskGroupDAO.TaskGroup;
import com.biomebounty.util.BiomeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDate;
import java.util.*;

/**
 * 每日任务组 GUI（支持分页，每页最多 28 个任务组）
 *
 *  ┌─────────────────────────────────────────────────┐
 *  │ ■ ■ ■ ■ ■ ■ ■ ■ ■                             │ ← 边框
 *  │ ■ [组1] [组2] [组3] [组4] [组5] [组6] [组7] ■   │
 *  │ ■ [组8] [组9] ...                         ■   │
 *  │ ■ ...                                            ■   │
 *  │ ■ [◀上页]   [统计]   [下页▶]   [关闭] ■   │ ← 底栏
 *  │ ■ ■ ■ ■ ■ ■ ■ ■ ■                             │ ← 边框
 *  └─────────────────────────────────────────────────┘
 */
public class AssignmentGUI {

    private final BiomeBountyPlugin plugin;
    private final Map<UUID, List<TaskGroup>> openGUIs = new HashMap<>();
    private final Map<UUID, Map<Integer, List<Assignment>>> openTasks = new HashMap<>();
    private final Map<UUID, Integer> openPages = new HashMap<>();
    private static final int GROUPS_PER_PAGE = 14;

    public AssignmentGUI(BiomeBountyPlugin plugin) { this.plugin = plugin; }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        UUID playerId = player.getUniqueId();
        String world = player.getWorld().getName();
        String today = LocalDate.now().toString();

        List<TaskGroup> groups = plugin.getDailyAssigner().assignPlayer(player);
        if (groups.isEmpty()) {
            player.sendMessage(plugin.getI18nManager().getPrefixedMessage("messages.task.pool-empty"));
            return;
        }

        Map<Integer, List<Assignment>> groupTasks = new LinkedHashMap<>();
        for (TaskGroup g : groups) {
            groupTasks.put(g.id, plugin.getPlayerAssignmentDAO().getAssignmentsByGroup(g.id));
        }

        openGUIs.put(playerId, groups);
        openTasks.put(playerId, groupTasks);

        int totalPages = (int) Math.ceil((double) groups.size() / GROUPS_PER_PAGE);
        page = Math.max(0, Math.min(page, totalPages - 1));
        openPages.put(playerId, page);

        // 预计算衰减
        double decay = plugin.getDailyAssigner().getEconomicDecay(playerId);

        Inventory gui = plugin.getServer().createInventory(null, 54,
                "§6§l每日悬赏 §7- §f" + today + " §7(" + (page + 1) + "/" + totalPages + ")");

        // 四周玻璃边框
        for (int i = 0; i < 54; i++) {
            int row = i / 9, col = i % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) {
                gui.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" ")));
            }
        }

        // 中间区域放任务组（行1-4，列1-7 = 每行7个，共28个）
        int[] centerSlots = {
            10, 11, 12, 13, 14, 15, 16,  // 行1
            19, 20, 21, 22, 23, 24, 25,  // 行2
            28, 29, 30, 31, 32, 33, 34,  // 行3
            37, 38, 39, 40, 41, 42, 43   // 行4
        };
        int start = page * GROUPS_PER_PAGE;

        for (int i = 0; i < GROUPS_PER_PAGE && start + i < groups.size(); i++) {
            TaskGroup group = groups.get(start + i);
            List<Assignment> tasks = groupTasks.getOrDefault(group.id, Collections.emptyList());
            gui.setItem(centerSlots[i], createGroupItem(group, tasks, decay));
        }

        // 底栏：统计、翻页、关闭
        long totalDone = groupTasks.values().stream()
                .flatMap(List::stream).filter(Assignment::isDone).count();
        long totalTasks = groupTasks.values().stream().mapToLong(List::size).sum();

        gui.setItem(49, createItem(Material.CLOCK,
                Component.text("§e§l今日悬赏"),
                Component.text("§7进度: §f" + totalDone + "/" + totalTasks),
                Component.text("§7完成: §f" + groups.stream().filter(g -> g.isCompleted() || g.isClaimed()).count() + "/" + groups.size())));

        if (page > 0) gui.setItem(45, createItem(Material.ARROW, Component.text("§e◀ 上一页")));
        if (page < totalPages - 1) gui.setItem(53, createItem(Material.ARROW, Component.text("§e下一页 ▶")));

        gui.setItem(50, createItem(Material.BARRIER, Component.text("§c§l关闭")));

        player.openInventory(gui);
    }

    /**
     * 创建任务组物品
     * lore 包含：群系名、每个任务的详细进度、奖励说明
     */
    private ItemStack createGroupItem(TaskGroup group, List<Assignment> tasks, double decay) {
        long doneCount = tasks.stream().filter(Assignment::isDone).count();
        boolean allDone = doneCount == tasks.size();

        // 材质：用第一个任务的目标材质
        Material mat;
        if (group.isClaimed()) mat = Material.LIME_STAINED_GLASS_PANE;
        else if (allDone) mat = Material.GOLD_INGOT;
        else if (!tasks.isEmpty()) {
            String target = tasks.get(0).target;
            mat = resolveMaterial(target, tasks.get(0).taskType);
            if (mat == Material.PAPER) {
                plugin.debug("[图标] target=\"" + target + "\" → PAPER (type=" + tasks.get(0).taskType + ")");
            }
        }
        else mat = Material.PAPER;

        // 状态图标
        String statusIcon;
        if (group.isClaimed()) statusIcon = "§a✓";
        else if (allDone) statusIcon = "§6★";
        else if (group.isActive()) statusIcon = "§e●";
        else statusIcon = "§7○"; // pending

        // lore
        List<Component> lore = new ArrayList<>();

        // 维度-群系
        lore.add(Component.text("§7")
                .append(BiomeUtil.resolveDimensionComponent(group.biomeId, plugin.getI18nManager()))
                .append(Component.text(" §8- §f"))
                .append(BiomeUtil.resolveBiomeNameComponent(group.biomeId)));
        lore.add(Component.text("§8§m─────────────"));

        // 每个任务
        for (Assignment a : tasks) {
            String taskIcon = getTaskTypeIcon(a.taskType);
            String checkmark = a.isDone() ? " §a✓" : "";
            Component taskName = BiomeUtil.resolveDisplayNameComponent(a.target, a.taskType);

            lore.add(Component.text("  " + taskIcon + " ")
                    .append(taskName)
                    .append(Component.text(" §7×" + a.amount + " §f" + a.getProgressString() + checkmark)));
        }

        lore.add(Component.text("§8§m─────────────"));

        // 奖励（显示衰减后的实际金额）
        int actualReward = Math.max(1, (int) (group.rewardGold * decay));
        if (decay < 0.95) {
            lore.add(Component.text("§6奖励: §e" + actualReward + " 金币 §7(基础" + group.rewardGold + ")"));
            lore.add(Component.text("§7经济衰减: §c" + String.format("%.0f", decay * 100) + "%"));
        } else {
            lore.add(Component.text("§6奖励: §e" + actualReward + " 金币"));
        }

        // 操作提示
        if (group.isClaimed()) {
            lore.add(Component.text("§7已领取"));
        } else if (allDone) {
            lore.add(Component.text("§a点击领取奖励"));
        } else if (group.isPending()) {
            lore.add(Component.text("§e点击领取此任务组"));
        } else {
            lore.add(Component.text("§7完成所有任务后可领取奖励"));
        }

        // 名称（群系名用客户端翻译）
        String[] titleParts = group.title.split("·", 2);
        Component name;
        if (titleParts.length == 2) {
            name = Component.text(statusIcon + " §6§l" + titleParts[0] + "·")
                    .append(BiomeUtil.resolveBiomeNameComponent(group.biomeId));
        } else {
            name = Component.text(statusIcon + " §6§l" + group.title);
        }

        return createItem(mat, name, lore.toArray(new Component[0]));
    }

    public boolean handleClick(Player player, int slot) {
        UUID playerId = player.getUniqueId();
        List<TaskGroup> groups = openGUIs.get(playerId);
        Map<Integer, List<Assignment>> groupTasks = openTasks.get(playerId);
        Integer page = openPages.get(playerId);
        if (groups == null || groupTasks == null || page == null) return false;

        // 关闭
        if (slot == 50) {
            player.closeInventory();
            return true;
        }

        // 上一页
        if (slot == 45 && page > 0) {
            open(player, page - 1);
            return true;
        }

        // 下一页
        if (slot == 53) {
            int totalPages = (int) Math.ceil((double) groups.size() / GROUPS_PER_PAGE);
            if (page < totalPages - 1) {
                open(player, page + 1);
                return true;
            }
        }

        // 找点击的组（中间区域）
        int[] centerSlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };
        int start = page * GROUPS_PER_PAGE;
        for (int i = 0; i < GROUPS_PER_PAGE && start + i < groups.size(); i++) {
            if (slot == centerSlots[i]) {
                handleGroupClick(player, groups.get(start + i), groupTasks.getOrDefault(groups.get(start + i).id, Collections.emptyList()));
                return true;
            }
        }

        return false;
    }

    private void handleGroupClick(Player player, TaskGroup group, List<Assignment> tasks) {
        long doneCount = tasks.stream().filter(Assignment::isDone).count();
        boolean allDone = doneCount == tasks.size();

        if (group.isClaimed()) {
            player.sendMessage("§7已经领取过奖励了");
        } else if (allDone) {
            // 全部完成 → 领取奖励
            if (plugin.getDailyAssigner().claimGroupReward(player, group.id)) {
                player.sendMessage(plugin.getI18nManager().getPrefixedMessage("messages.reward.claimed"));
                open(player);
            }
        } else if (group.isPending()) {
            // 待领取 → 激活组内所有任务
            plugin.getDailyAssigner().acceptGroup(group.id);
            player.sendMessage(Component.text("§a已领取任务组: §6")
                    .append(BiomeUtil.resolveBiomeNameComponent(group.biomeId)));
            open(player);
        } else {
            // 进行中 → 显示进度
            player.sendMessage(Component.text("§6§l")
                    .append(BiomeUtil.resolveBiomeNameComponent(group.biomeId))
                    .append(Component.text(" §7(" + doneCount + "/" + tasks.size() + ")")));
            for (Assignment a : tasks) {
                String icon = a.isDone() ? "§a✓" : "§e●";
                player.sendMessage("  " + icon + " " + BiomeUtil.resolveDisplayName(a.target, a.taskType)
                        + " §7" + a.getProgressString());
            }
        }
    }

    public void closeGUI(UUID playerId) {
        openGUIs.remove(playerId);
        openTasks.remove(playerId);
        openPages.remove(playerId);
    }

    private String getTaskTypeIcon(String type) {
        switch (type) {
            case "collect": return "⛏"; case "kill": return "⚔";
            case "explore": return "🗺"; case "fish": return "🎣";
            case "breed": return "🐄"; case "farm": return "🌾";
            default: return "●";
        }
    }

    /**
     * 把任务目标解析成材质（优先非生物图标）
     *
     * 策略：
     *  1. 直接匹配 Material（OAK_LOG → OAK_LOG）
     *  2. 常见方块后缀回退（COAL → COAL_BLOCK, IRON → IRON_BLOCK）
     *  3. 实体 → 刷怪蛋
     *  4. Mod 物品/实体 → 按任务类型给合理图标
     *  5. 兜底 PAPER
     */
    private Material resolveMaterial(String target, String taskType) {
        if (target == null) return Material.PAPER;

        // 去掉命名空间
        String name = target;
        int colon = target.indexOf(':');
        String ns = "minecraft";
        if (colon >= 0) {
            ns = target.substring(0, colon);
            name = target.substring(colon + 1);
        }
        name = name.toUpperCase();

        // 非 minecraft 命名空间 → 尝试拼接 Material 名（modded server 支持）
        if (!"minecraft".equals(ns)) {
            Material modMat = tryMaterial(ns.toUpperCase() + "_" + name);
            if (modMat != null) return modMat;
            return resolveModMaterial(taskType);
        }

        // 1. 直接匹配材质
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException ignored) {}

        // 2. 常见方块名回退（extractor 提取的名可能没有 _BLOCK/_ORE 后缀）
        Material aliased = tryBlockAliases(name);
        if (aliased != null) return aliased;

        // 3. 实体类型 → 刷怪蛋
        try {
            org.bukkit.entity.EntityType etype = org.bukkit.entity.EntityType.valueOf(name);
            Material egg = trySpawnEgg(etype.name());
            if (egg != null) return egg;
            return Material.BONE;
        } catch (IllegalArgumentException ignored) {}

        // 4. 原版也匹配不到 → 按任务类型兜底
        return resolveModMaterial(taskType);
    }

    /**
     * Mod 物品/实体的兜底图标（按任务类型给最合理的）
     */
    private Material resolveModMaterial(String taskType) {
        if (taskType == null) return Material.PAPER;
        switch (taskType) {
            case "kill":    return Material.IRON_SWORD;
            case "breed":   return Material.WHEAT;
            case "fish":    return Material.FISHING_ROD;
            case "farm":    return Material.WHEAT_SEEDS;
            case "explore": return Material.MAP;
            case "collect": return Material.PAPER; // mod 方块确实没有对应材质
            default:        return Material.PAPER;
        }
    }

    /**
     * 方块名常见回退映射（优先非生物图标）
     * extractor 提取的名可能不含 _BLOCK/_ORE 后缀，需要猜测
     */
    private Material tryBlockAliases(String name) {
        // ——— 矿石 ———
        // DEEPSLATE_xxx_ORE
        if (name.startsWith("DEEPSLATE_") && name.endsWith("_ORE")) {
            return tryMaterial(name); // DEEPSLATE_IRON_ORE 等直接存在
        }
        // xxx_ORE（RAW_xxx → xxx_ORE 的兜底）
        if (name.endsWith("_ORE")) return tryMaterial(name);

        // ——— 存储方块 / 原材料 ———
        // COAL → COAL_BLOCK, IRON → IRON_BLOCK, GOLD → GOLD_BLOCK, etc.
        String[] minerals = {"COAL", "IRON", "GOLD", "COPPER", "DIAMOND", "EMERALD",
                "LAPIS", "REDSTONE", "AMETHYST", "QUARTZ", "NETHERITE", "RAW_GOLD", "RAW_IRON", "RAW_COPPER"};
        for (String m : minerals) {
            if (name.equals(m)) {
                Material block = tryMaterial(m + "_BLOCK");
                if (block != null) return block;
                return tryMaterial(m); // COAL, IRON_INGOT 等
            }
            // RAW_IRON → RAW_IRON_BLOCK
            if (name.equals("RAW_" + m)) {
                Material block = tryMaterial("RAW_" + m + "_BLOCK");
                if (block != null) return block;
                return tryMaterial("RAW_" + m);
            }
        }

        // ——— 木头 ———
        String[] woodTypes = {"OAK", "BIRCH", "SPRUCE", "JUNGLE", "ACACIA", "DARK_OAK",
                "CHERRY", "MANGROVE", "CRIMSON", "WARPED"};
        for (String w : woodTypes) {
            if (name.equals(w + "_LOG") || name.equals(w + "_WOOD"))
                return tryMaterial(name);
            if (name.equals(w))
                return tryMaterial(w + "_LOG");
        }

        // ——— 石头类 ———
        if (name.equals("STONE")) return Material.STONE;
        if (name.equals("COBBLESTONE")) return Material.COBBLESTONE;
        if (name.equals("DEEPSLATE")) return Material.DEEPSLATE;
        if (name.equals("GRANITE")) return Material.GRANITE;
        if (name.equals("DIORITE")) return Material.DIORITE;
        if (name.equals("ANDESITE")) return Material.ANDESITE;
        if (name.equals("TUFF")) return Material.TUFF;
        if (name.equals("CALCITE")) return Material.CALCITE;
        if (name.equals("DRIPSTONE_BLOCK")) return Material.DRIPSTONE_BLOCK;
        if (name.equals("SANDSTONE")) return Material.SANDSTONE;
        if (name.equals("RED_SANDSTONE")) return Material.RED_SANDSTONE;
        if (name.equals("BASALT")) return Material.BASALT;
        if (name.equals("BLACKSTONE")) return Material.BLACKSTONE;

        // ——— 植物 ———
        if (name.equals("GRASS")) return Material.GRASS_BLOCK;
        if (name.equals("TALL_GRASS")) return Material.TALL_GRASS;
        if (name.equals("FERN")) return Material.FERN;
        if (name.equals("LARGE_FERN")) return Material.LARGE_FERN;
        if (name.equals("DEAD_BUSH")) return Material.DEAD_BUSH;
        if (name.equals("SEAGRASS")) return Material.SEAGRASS;
        if (name.equals("KELP")) return Material.KELP;
        if (name.equals("BAMBOO")) return Material.BAMBOO;
        if (name.equals("SUGAR_CANE")) return Material.SUGAR_CANE;
        if (name.equals("VINE")) return Material.VINE;
        if (name.equals("GLOW_LICHEN")) return Material.GLOW_LICHEN;
        if (name.equals("MOSS_BLOCK")) return Material.MOSS_BLOCK;
        if (name.equals("MOSS_CARPET")) return Material.MOSS_CARPET;
        if (name.equals("AZALEA")) return Material.AZALEA;
        if (name.equals("FLOWERING_AZALEA")) return Material.FLOWERING_AZALEA;
        // 花
        if (name.equals("DANDELION")) return Material.DANDELION;
        if (name.equals("POPPY")) return Material.POPPY;
        if (name.equals("BLUE_ORCHID")) return Material.BLUE_ORCHID;
        if (name.equals("ALLIUM")) return Material.ALLIUM;
        if (name.equals("AZURE_BLUET")) return Material.AZURE_BLUET;
        if (name.equals("RED_TULIP")) return Material.RED_TULIP;
        if (name.equals("OXEYE_DAISY")) return Material.OXEYE_DAISY;
        if (name.equals("CORNFLOWER")) return Material.CORNFLOWER;
        if (name.equals("LILY_OF_THE_VALLEY")) return Material.LILY_OF_THE_VALLEY;
        if (name.equals("WITHER_ROSE")) return Material.WITHER_ROSE;
        if (name.equals("SUNFLOWER")) return Material.SUNFLOWER;
        if (name.equals("LILAC")) return Material.LILAC;
        if (name.equals("ROSE_BUSH")) return Material.ROSE_BUSH;
        if (name.equals("PEONY")) return Material.PEONY;
        // 孢子花
        if (name.equals("CRIMSON_FUNGUS")) return Material.CRIMSON_FUNGUS;
        if (name.equals("WARPED_FUNGUS")) return Material.WARPED_FUNGUS;
        if (name.equals("CRIMSON_ROOTS")) return Material.CRIMSON_ROOTS;
        if (name.equals("WARPED_ROOTS")) return Material.WARPED_ROOTS;
        if (name.equals("NETHER_SPROUTS")) return Material.NETHER_SPROUTS;
        // 蘑菇
        if (name.equals("BROWN_MUSHROOM")) return Material.BROWN_MUSHROOM;
        if (name.equals("RED_MUSHROOM")) return Material.RED_MUSHROOM;
        // 树苗
        if (name.endsWith("_SAPLING")) return tryMaterial(name);

        // ——— 结构（不是方块，用指南针/地图代替） ———
        if (name.contains("VILLAGE")) return Material.STRUCTURE_BLOCK;
        if (name.contains("TEMPLE") || name.contains("MONUMENT") || name.contains("FORTRESS")
                || name.contains("OUTPOST") || name.contains("MANSION") || name.contains("MINESHAFT")
                || name.contains("STRONGHOLD") || name.contains("RUINED_PORTAL")
                || name.contains("BURIED_TREASURE") || name.contains("SHIPWRECK")
                || name.contains("OCEAN_RUIN") || name.contains("TRAIL_RUINS"))
            return Material.MAP;

        // ——— 杂项 ———
        if (name.equals("CLAY")) return Material.CLAY;
        if (name.equals("GRAVEL")) return Material.GRAVEL;
        if (name.equals("SAND")) return Material.SAND;
        if (name.equals("RED_SAND")) return Material.RED_SAND;
        if (name.equals("SNOW")) return Material.SNOW_BLOCK;
        if (name.equals("ICE")) return Material.ICE;
        if (name.equals("PACKED_ICE")) return Material.PACKED_ICE;
        if (name.equals("BLUE_ICE")) return Material.BLUE_ICE;
        if (name.equals("OBSIDIAN")) return Material.OBSIDIAN;
        if (name.equals("CRYING_OBSIDIAN")) return Material.CRYING_OBSIDIAN;

        return null;
    }

    private Material tryMaterial(String name) {
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Material trySpawnEgg(String entityName) {
        String eggName = entityName + "_SPAWN_EGG";
        try {
            return Material.valueOf(eggName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ItemStack createItem(Material mat, Component name, Component... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        if (lore.length > 0) meta.lore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }
}
