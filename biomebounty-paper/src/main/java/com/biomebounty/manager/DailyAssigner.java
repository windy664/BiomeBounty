package com.biomebounty.manager;

import com.biomebounty.BiomeBountyPlugin;
import com.biomebounty.core.algorithm.AssignmentAlgorithm;
import com.biomebounty.core.algorithm.AssignmentAlgorithm.TaskCandidate;
import com.biomebounty.database.dao.PlayerAssignmentDAO;
import com.biomebounty.database.dao.PlayerAssignmentDAO.Assignment;
import com.biomebounty.database.dao.PlayerBiomeHistoryDAO;
import com.biomebounty.database.dao.TaskGroupDAO;
import com.biomebounty.database.dao.TaskGroupDAO.TaskGroup;
import com.biomebounty.database.dao.TaskPoolDAO;
import com.biomebounty.database.dao.TaskPoolDAO.PoolEntry;
import com.biomebounty.util.BiomeUtil;
import org.bukkit.entity.Player;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 每日任务分配器（Bukkit 层，委托 AssignmentAlgorithm 做纯计算）
 */
public class DailyAssigner {

    private final BiomeBountyPlugin plugin;
    private final TaskPoolDAO poolDAO;
    private final PlayerAssignmentDAO assignmentDAO;
    private final PlayerBiomeHistoryDAO historyDAO;
    private final TaskGroupDAO groupDAO;

    private int groupsPerDay = 6;
    private int cooldownDays = 3;
    private double preferenceBonus = 1.2;
    private double cooldownPenalty = 0.1;
    private int baseRewardGold = 300;

    public DailyAssigner(BiomeBountyPlugin plugin, TaskPoolDAO poolDAO,
                         PlayerAssignmentDAO assignmentDAO, PlayerBiomeHistoryDAO historyDAO,
                         TaskGroupDAO groupDAO) {
        this.plugin = plugin;
        this.poolDAO = poolDAO;
        this.assignmentDAO = assignmentDAO;
        this.historyDAO = historyDAO;
        this.groupDAO = groupDAO;
        loadConfig();
    }

    private void loadConfig() {
        groupsPerDay = plugin.getConfig().getInt("assignment.groups-per-day", 6);
        cooldownDays = plugin.getConfig().getInt("assignment.cooldown-days", 3);
        preferenceBonus = plugin.getConfig().getDouble("assignment.preference-bonus", 1.2);
        cooldownPenalty = plugin.getConfig().getDouble("assignment.cooldown-penalty", 0.1);
        baseRewardGold = plugin.getConfig().getInt("rewards.group.base-gold", 300);
    }

    public List<TaskGroup> assignPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        String world = player.getWorld().getName();
        String today = LocalDate.now().toString();

        List<TaskGroup> existing = groupDAO.getTodayGroups(playerId, world, today);
        if (!existing.isEmpty()) return existing;

        Map<String, List<PoolEntry>> pool = poolDAO.getAllPool();
        if (pool.isEmpty()) {
            plugin.debug("Task pool is empty! Cannot assign tasks to " + player.getName());
            return Collections.emptyList();
        }
        plugin.debug("Task pool has " + pool.size() + " biomes for assignment");
        // Debug: 每个群系的任务数
        if (plugin.isDebug()) {
            for (Map.Entry<String, List<PoolEntry>> e : pool.entrySet()) {
                Map<String, Long> byType = e.getValue().stream()
                        .collect(Collectors.groupingBy(pe -> pe.taskType, Collectors.counting()));
                plugin.debug("  " + e.getKey() + ": " + e.getValue().size() + " tasks " + byType);
            }
        }

        // 玩家画像
        PlayerProfile profile = buildProfile(playerId);
        int dayOfWeek = LocalDate.now().getDayOfWeek().getValue();
        long seed = System.nanoTime() + playerId.hashCode();

        // 选 6 个不同群系
        List<String> biomes = selectDistinctBiomes(playerId, new ArrayList<>(pool.keySet()), today, groupsPerDay, seed);
        plugin.debug("Selected " + biomes.size() + " biomes: " + biomes);

        // 按难度排序
        List<String> sorted = orderBiomesByDifficulty(biomes, pool, new Random(seed));

        for (int i = 0; i < sorted.size(); i++) {
            String biomeId = sorted.get(i);
            String biomeName = BiomeUtil.resolveBiomeName(biomeId);
            List<PoolEntry> biomePool = pool.getOrDefault(biomeId, Collections.emptyList());
            if (biomePool.isEmpty()) continue;

            // 算目标难度
            double target = AssignmentAlgorithm.calcGroupDifficulty(
                    i, sorted.size(), dayOfWeek, profile.avgCompletedDifficulty,
                    profile.completionRate, profile.abandonedCount, profile.consecutiveHigh, seed);

            // 转成 TaskCandidate
            List<TaskCandidate> candidates = biomePool.stream()
                    .map(e -> new TaskCandidate(e.biomeId, e.taskType, e.target, e.minAmount, e.maxAmount))
                    .collect(Collectors.toList());

            // 根据难度动态决定任务数
            int tasksThisGroup = AssignmentAlgorithm.calcTasksPerGroup(target);

            // 从该群系任务池中选任务（每组独立，不跨组去重）
            List<TaskCandidate> selected = AssignmentAlgorithm.selectTasksForGroup(
                    candidates, target, Collections.emptySet(), tasksThisGroup, seed + i);
            if (selected.isEmpty()) continue;

            int rewardGold = baseRewardGold + (int) (target * 3) + new Random(seed + i).nextInt(100);
            String title = AssignmentAlgorithm.generateGroupTitle(biomeName, selected, i);

            int groupId = groupDAO.createGroup(playerId, world, biomeId, title, rewardGold, today);
            if (groupId < 0) continue;

            for (TaskCandidate tc : selected) {
                int amount = AssignmentAlgorithm.calibrateAmount(tc.minAmount, tc.maxAmount, target, seed + i + tc.target.hashCode());
                PoolEntry entry = findPoolEntry(biomePool, tc);
                if (entry != null) {
                    assignmentDAO.assign(playerId, world, groupId, entry.id, entry.biomeId,
                            entry.taskType, entry.target, amount, today);
                }
            }

            // 任务保持 pending，玩家点击领取后激活

            historyDAO.recordAssignment(playerId, biomeId, today);
        }

        List<TaskGroup> groups = groupDAO.getTodayGroups(playerId, world, today);
        plugin.debug("Assigned " + groups.size() + " groups to " + player.getName()
                + " (dayOfWeek=" + dayOfWeek + ", level=" + (int) profile.avgCompletedDifficulty + ")");
        return groups;
    }

    private PoolEntry findPoolEntry(List<PoolEntry> pool, TaskCandidate tc) {
        return pool.stream().filter(e -> e.taskType.equals(tc.taskType) && e.target.equals(tc.target))
                .findFirst().orElse(null);
    }

    /**
     * 领取任务组（pending → active，激活组内所有任务）
     */
    public void acceptGroup(int groupId) {
        groupDAO.updateStatus(groupId, "active");
        assignmentDAO.activateByGroup(groupId);
    }

    public void checkGroupCompletion(int groupId) {
        List<Assignment> tasks = assignmentDAO.getAssignmentsByGroup(groupId);
        if (tasks.isEmpty()) return;
        if (tasks.stream().allMatch(Assignment::isDone))
            groupDAO.updateStatus(groupId, "completed");
    }

    /**
     * 领取任务组奖励（带经济衰减）
     */
    public boolean claimGroupReward(Player player, int groupId) {
        UUID playerId = player.getUniqueId();
        List<TaskGroup> groups = groupDAO.getTodayGroups(playerId,
                player.getWorld().getName(), LocalDate.now().toString());
        TaskGroup group = groups.stream().filter(g -> g.id == groupId).findFirst().orElse(null);
        if (group == null || !group.isCompleted()) return false;

        if (group.rewardGold > 0) {
            double decay = calcEconomicDecay(playerId);
            int actualReward = Math.max(1, (int) (group.rewardGold * decay));

            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                    "eco give " + player.getName() + " " + actualReward);
            plugin.getPlayerStatsDAO().incrementGold(playerId, actualReward);

            if (decay < 0.95) {
                plugin.debug("Economic decay: " + group.rewardGold + " → " + actualReward
                        + " (decay=" + String.format("%.2f", decay) + ")");
            }
        }
        groupDAO.updateStatus(groupId, "claimed");
        plugin.getPlayerStatsDAO().incrementCompleted(playerId, player.getName());
        return true;
    }

    /**
     * 获取玩家当前的经济衰减系数（供 GUI 预览）
     */
    public double getEconomicDecay(UUID playerId) {
        return calcEconomicDecay(playerId);
    }

    /**
     * 计算经济衰减系数 = 个人衰减 × 全局衰减
     */
    private double calcEconomicDecay(UUID playerId) {
        double personalDecay = calcPersonalDecay(playerId);
        double globalDecay = calcGlobalDecay();
        return Math.max(0.1, Math.min(1.0, personalDecay * globalDecay));
    }

    /**
     * 个人衰减：基于最近7天收益趋势
     */
    private double calcPersonalDecay(UUID playerId) {
        List<Integer> dailyEarnings = plugin.getPlayerStatsDAO().getRecentDailyEarnings(playerId, 7);
        if (dailyEarnings.size() < 3) return 1.0;

        double slope = linearRegressionSlope(dailyEarnings);
        double avg = dailyEarnings.stream().mapToInt(Integer::intValue).average().orElse(0);
        double targetDaily = plugin.getConfig().getDouble("economy.target-daily-income", 2000);

        double decay = 1.0;
        if (avg > targetDaily) decay = targetDaily / avg;
        if (slope > 0) decay *= 0.9;
        else if (slope < 0) decay *= Math.min(1.1, 1.0 - slope / Math.max(avg, 1));

        return Math.max(0.3, Math.min(1.0, decay));
    }

    /**
     * 全局衰减：基于全服活跃玩家数（内卷效应）
     *
     * 参与者越多 → 每人分到的越少
     * 参与者越少 → 奖励越丰厚（吸引参与）
     */
    private double calcGlobalDecay() {
        int activePlayers = plugin.getPlayerStatsDAO().getTodayActivePlayerCount();
        int targetPlayers = plugin.getConfig().getInt("economy.target-active-players", 20);

        if (activePlayers <= 1) return 1.0;

        // 活跃玩家超过目标 → 压低
        // 活跃玩家低于目标 → 不压（甚至可以加成）
        double ratio = (double) activePlayers / targetPlayers;

        if (ratio <= 1.0) {
            // 人少：不衰减，最多加成到 1.1
            return Math.min(1.1, 1.0 + (1.0 - ratio) * 0.1);
        } else {
            // 人多：衰减，最多压到 0.3
            return Math.max(0.3, 1.0 / ratio);
        }
    }

    /**
     * 线性回归斜率
     */
    private double linearRegressionSlope(List<Integer> values) {
        int n = values.size();
        if (n < 2) return 0;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += i; sumY += values.get(i);
            sumXY += i * values.get(i); sumX2 += i * i;
        }
        double denom = n * sumX2 - sumX * sumX;
        return denom == 0 ? 0 : (n * sumXY - sumX * sumY) / denom;
    }

    // ==================== 群系选择 ====================

    private List<String> selectDistinctBiomes(UUID playerId, List<String> allBiomes, String today, int count, long seed) {
        Map<String, Double> weights = new LinkedHashMap<>();
        Set<String> recentBiomes = historyDAO.getRecentBiomes(playerId, cooldownDays, today);
        Map<String, Integer> preferences = historyDAO.getBiomePreferences(playerId);

        // 分组：原版 vs 模组
        List<String> vanilla = new ArrayList<>();
        List<String> modded = new ArrayList<>();
        for (String biomeId : allBiomes) {
            if (biomeId.startsWith("minecraft:")) vanilla.add(biomeId);
            else modded.add(biomeId);
        }

        // 确保混合：至少2个原版，至少2个模组，剩下的随机
        int vanillaCount = Math.min(Math.max(2, count / 3), vanilla.size());
        int moddedCount = Math.min(Math.max(2, count - vanillaCount), modded.size());
        vanillaCount = Math.min(count - moddedCount, vanilla.size());

        Random random = new Random(seed);

        List<String> result = new ArrayList<>();
        // 从原版中选
        List<String> vanillaPool = new ArrayList<>(vanilla);
        Collections.shuffle(vanillaPool, random);
        result.addAll(vanillaPool.subList(0, Math.min(vanillaCount, vanillaPool.size())));

        // 从模组中选
        List<String> modPool = new ArrayList<>(modded);
        Collections.shuffle(modPool, random);
        result.addAll(modPool.subList(0, Math.min(moddedCount, modPool.size())));

        // 不够就补
        if (result.size() < count) {
            List<String> remaining = new ArrayList<>();
            remaining.addAll(vanilla);
            remaining.addAll(modded);
            remaining.removeAll(result);
            Collections.shuffle(remaining, random);
            for (String b : remaining) {
                if (result.size() >= count) break;
                result.add(b);
            }
        }

        // 冷却/偏好打乱顺序（不影响选中，只影响分配到哪一组）
        result.sort((a, b) -> {
            double wa = recentBiomes.contains(a) ? 1 : 0;
            double wb = recentBiomes.contains(b) ? 1 : 0;
            return Double.compare(wa, wb); // 最近用过的排后面
        });

        return result;
    }

    private List<String> orderBiomesByDifficulty(List<String> biomes, Map<String, List<PoolEntry>> pool, Random random) {
        List<BiomeWithDifficulty> list = new ArrayList<>();
        for (String biomeId : biomes) {
            List<PoolEntry> entries = pool.getOrDefault(biomeId, Collections.emptyList());
            double avg = entries.stream()
                    .mapToDouble(e -> (double) (e.minAmount + e.maxAmount) / 2 / AssignmentAlgorithm.getDefaultMax(e.taskType) * 100)
                    .average().orElse(50);
            list.add(new BiomeWithDifficulty(biomeId, avg));
        }
        list.sort(Comparator.comparingDouble((BiomeWithDifficulty b) -> b.difficulty + random.nextDouble() * 10));
        return list.stream().map(b -> b.biomeId).collect(Collectors.toList());
    }

    private List<String> weightedRandomSelect(Map<String, Double> weights, int count) {
        List<String> result = new ArrayList<>();
        Map<String, Double> remaining = new LinkedHashMap<>(weights);
        Random random = new Random();
        for (int i = 0; i < count && !remaining.isEmpty(); i++) {
            double total = remaining.values().stream().mapToDouble(Double::doubleValue).sum();
            double roll = random.nextDouble() * total, cumulative = 0;
            for (Map.Entry<String, Double> entry : remaining.entrySet()) {
                cumulative += entry.getValue();
                if (roll <= cumulative) { result.add(entry.getKey()); remaining.remove(entry.getKey()); break; }
            }
        }
        return result;
    }

    // ==================== 玩家画像 ====================

    private PlayerProfile buildProfile(UUID playerId) {
        String sinceDate = LocalDate.now().minusDays(14).toString();
        List<Assignment> history = assignmentDAO.getAssignmentsSince(playerId, sinceDate);
        PlayerProfile p = new PlayerProfile();
        if (history.isEmpty()) { p.avgCompletedDifficulty = 40; p.completionRate = 1.0; return p; }

        List<Double> diffs = new ArrayList<>();
        int total = 0, completed = 0, abandoned = 0;
        for (Assignment a : history) {
            total++;
            if (a.isDone()) { completed++; diffs.add((double) a.amount / AssignmentAlgorithm.getDefaultMax(a.taskType) * 100); }
            if ("abandoned".equals(a.status)) abandoned++;
        }
        p.avgCompletedDifficulty = diffs.isEmpty() ? 40 : diffs.stream().mapToDouble(Double::doubleValue).average().orElse(40);
        p.completionRate = total > 0 ? (double) completed / total : 1.0;
        p.abandonedCount = abandoned;

        Map<String, List<Assignment>> byDay = history.stream()
                .collect(Collectors.groupingBy(a -> a.assignedDate, LinkedHashMap::new, Collectors.toList()));
        int streak = 0;
        for (List<Assignment> day : byDay.values()) {
            if (day.stream().allMatch(Assignment::isDone)) streak++; else streak = 0;
        }
        p.consecutiveHigh = streak;
        return p;
    }

    private static class PlayerProfile {
        double avgCompletedDifficulty = 40, completionRate = 1.0;
        int abandonedCount = 0, consecutiveHigh = 0;
    }

    private static class BiomeWithDifficulty {
        final String biomeId; final double difficulty;
        BiomeWithDifficulty(String biomeId, double difficulty) { this.biomeId = biomeId; this.difficulty = difficulty; }
    }
}
