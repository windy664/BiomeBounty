package com.biomebounty.core.algorithm;

import java.util.*;

/**
 * 纯算法逻辑，无 Bukkit 依赖，可独立测试
 */
public class AssignmentAlgorithm {

    // 每周难度基调
    private static final Map<Integer, Integer> WEEKLY_DIFFICULTY = Map.of(
            1, 30,  // MONDAY
            2, 45,  // TUESDAY
            3, 60,  // WEDNESDAY
            4, 35,  // THURSDAY (喘息)
            5, 65,  // FRIDAY
            6, 80,  // SATURDAY (挑战)
            7, 50   // SUNDAY
    );

    /**
     * 计算每组的目标难度
     *
     * @param groupIndex    组序号 (0~5)
     * @param totalGroups   总组数
     * @param dayOfWeek     星期几 (1=Mon, 7=Sun)
     * @param playerLevel   玩家历史平均完成难度 (0~100)
     * @param completionRate 玩家完成率 (0~1)
     * @param abandonedCount 近期放弃次数
     * @param consecutiveHigh 连续全完成天数
     * @param randomSeed    随机种子（测试用）
     * @return 目标难度 (10~95)
     */
    public static double calcGroupDifficulty(int groupIndex, int totalGroups,
                                              int dayOfWeek, double playerLevel,
                                              double completionRate, int abandonedCount,
                                              int consecutiveHigh, long randomSeed) {
        Random random = new Random(randomSeed + groupIndex);

        int weeklyBase = WEEKLY_DIFFICULTY.getOrDefault(dayOfWeek, 50);

        // 组序递进
        double progression = groupIndex * (8.0 + random.nextInt(5));

        // 玩家画像修正
        double profileMod = 0;
        if (completionRate >= 0.9) profileMod = 10;
        else if (completionRate < 0.6) profileMod = -15;
        if (abandonedCount >= 3) profileMod -= 10;

        // 轻松局
        double relief = 0;
        if (random.nextInt(10) == 0) relief = -25;

        // 连续高压
        if (consecutiveHigh >= 3 && groupIndex >= 3) relief = -30;

        double target = weeklyBase + progression + profileMod + relief + random.nextInt(11) - 5;
        return Math.max(10, Math.min(95, target));
    }

    /**
     * 根据目标难度决定每组任务数
     * 难度越高任务越多，不封顶
     *
     * @param targetDifficulty 目标难度 (0~100)
     * @return 任务数 (3~N)
     */
    public static int calcTasksPerGroup(double targetDifficulty) {
        // 基础 3 个，每 15 点难度 +1 个
        return 3 + (int) (targetDifficulty / 15);
    }

    /**
     * 根据目标难度校准任务数量
     */
    public static int calibrateAmount(int minAmount, int maxAmount, double targetDifficulty, long seed) {
        if (maxAmount <= minAmount) return minAmount;
        Random random = new Random(seed);
        int amount = minAmount + (int) Math.round((maxAmount - minAmount) * targetDifficulty / 100.0);
        return Math.max(minAmount, Math.min(maxAmount, amount));
    }

    /**
     * 按目标难度加权选任务
     * 约束：不重复类型
     */
    public static List<TaskCandidate> selectTasksForGroup(List<TaskCandidate> pool,
                                                           double target,
                                                           Set<String> globalUsedTypes,
                                                           int maxTasks, long seed) {
        Random random = new Random(seed);

        // 按类型分组
        Map<String, List<TaskCandidate>> byType = new LinkedHashMap<>();
        for (TaskCandidate tc : pool) {
            byType.computeIfAbsent(tc.taskType, k -> new ArrayList<>()).add(tc);
        }

        // 排序：优先当天没用过的类型
        List<String> types = new ArrayList<>(byType.keySet());
        types.sort((a, b) -> {
            boolean aUsed = globalUsedTypes.contains(a);
            boolean bUsed = globalUsedTypes.contains(b);
            if (aUsed != bUsed) return aUsed ? 1 : -1;
            return 0;
        });
        Collections.shuffle(types, random);

        List<TaskCandidate> selected = new ArrayList<>();
        Set<String> usedInGroup = new HashSet<>();

        for (String type : types) {
            if (selected.size() >= maxTasks) break;
            if (usedInGroup.contains(type)) continue;

            List<TaskCandidate> candidates = byType.get(type);
            if (candidates.isEmpty()) continue;

            TaskCandidate pick = weightedPick(candidates, target, random);
            selected.add(pick);
            usedInGroup.add(type);
        }

        // 不够就补
        if (selected.size() < maxTasks) {
            for (String type : byType.keySet()) {
                if (selected.size() >= maxTasks) break;
                for (TaskCandidate tc : byType.get(type)) {
                    if (selected.size() >= maxTasks) break;
                    if (!selected.contains(tc)) selected.add(tc);
                }
            }
        }

        return selected;
    }

    /**
     * 加权随机选一个（越接近目标难度权重越高）
     */
    public static TaskCandidate weightedPick(List<TaskCandidate> candidates, double target, Random random) {
        double[] weights = new double[candidates.size()];
        double total = 0;

        for (int i = 0; i < candidates.size(); i++) {
            TaskCandidate tc = candidates.get(i);
            double mid = (tc.minAmount + tc.maxAmount) / 2.0;
            double max = getDefaultMax(tc.taskType);
            double difficulty = max > 0 ? mid / max * 100 : 50;

            double w = 1.0 / (1.0 + Math.abs(difficulty - target) / 10.0);
            if (difficulty < 30) w += 0.3;
            if (difficulty > 80 && target < 40) w *= 0.3;

            weights[i] = w;
            total += w;
        }

        double roll = random.nextDouble() * total;
        double cumulative = 0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += weights[i];
            if (roll <= cumulative) return candidates.get(i);
        }
        return candidates.get(candidates.size() - 1);
    }

    /**
     * 生成组标题
     */
    public static String generateGroupTitle(String biomeName, List<TaskCandidate> tasks, int groupIndex) {
        String[] prefixes = {"入门", "基础", "进阶", "挑战", "精英", "试炼"};
        String prefix = groupIndex < prefixes.length ? prefixes[groupIndex] : "终极";
        return prefix + "·" + biomeName;
    }

    public static int getDefaultMax(String taskType) {
        switch (taskType) {
            case "collect": return 15;
            case "kill": return 15;
            case "fish": return 8;
            case "breed": return 5;
            case "farm": return 10;
            case "explore": return 1;
            default: return 10;
        }
    }

    private static String getTypeTheme(String taskType) {
        switch (taskType) {
            case "collect": return "采集";
            case "kill": return "狩猎";
            case "explore": return "探索";
            case "fish": return "垂钓";
            case "breed": return "畜牧";
            case "farm": return "农耕";
            default: return taskType;
        }
    }

    /**
     * 任务候选（测试用数据类）
     */
    public static class TaskCandidate {
        public final String biomeId;
        public final String taskType;
        public final String target;
        public final int minAmount;
        public final int maxAmount;

        public TaskCandidate(String biomeId, String taskType, String target, int minAmount, int maxAmount) {
            this.biomeId = biomeId;
            this.taskType = taskType;
            this.target = target;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
        }

        @Override
        public String toString() {
            return taskType + ":" + target + " [" + minAmount + "-" + maxAmount + "]";
        }
    }
}
