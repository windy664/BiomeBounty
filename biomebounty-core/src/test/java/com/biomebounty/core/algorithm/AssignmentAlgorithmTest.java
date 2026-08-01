package com.biomebounty.core.algorithm;

import com.biomebounty.core.algorithm.AssignmentAlgorithm.TaskCandidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AssignmentAlgorithmTest {

    // ==================== 难度递进 ====================

    @Test
    @DisplayName("难度应随组序递增")
    void difficultyShouldIncreaseWithGroupIndex() {
        // 固定参数，只变 groupIndex
        double[] diffs = new double[6];
        for (int i = 0; i < 6; i++) {
            diffs[i] = AssignmentAlgorithm.calcGroupDifficulty(
                    i, 6, 3, 50, 0.8, 0, 0, 42L);
        }

        // 每组应比前一组难（允许小幅抖动，整体趋势递增）
        double firstAvg = (diffs[0] + diffs[1]) / 2;
        double lastAvg = (diffs[4] + diffs[5]) / 2;
        assertTrue(lastAvg > firstAvg,
                "后半段应比前半段难: first=" + firstAvg + ", last=" + lastAvg);
    }

    @Test
    @DisplayName("每周基调：周六应比周一难")
    void saturdayShouldBeHarderThanMonday() {
        double monday = AssignmentAlgorithm.calcGroupDifficulty(2, 6, 1, 50, 0.8, 0, 0, 42L);
        double saturday = AssignmentAlgorithm.calcGroupDifficulty(2, 6, 6, 50, 0.8, 0, 0, 42L);

        assertTrue(saturday > monday,
                "周六(" + saturday + ")应比周一(" + monday + ")难");
    }

    @Test
    @DisplayName("周四应是喘息日")
    void thursdayShouldBeEasier() {
        double wed = AssignmentAlgorithm.calcGroupDifficulty(2, 6, 3, 50, 0.8, 0, 0, 42L);
        double thu = AssignmentAlgorithm.calcGroupDifficulty(2, 6, 4, 50, 0.8, 0, 0, 42L);

        assertTrue(thu < wed,
                "周四(" + thu + ")应比周三(" + wed + ")轻松");
    }

    // ==================== 玩家画像影响 ====================

    @Test
    @DisplayName("高完成率玩家应更难")
    void highCompletionRateShouldBeHarder() {
        double easy = AssignmentAlgorithm.calcGroupDifficulty(2, 6, 3, 50, 0.5, 0, 0, 42L);
        double hard = AssignmentAlgorithm.calcGroupDifficulty(2, 6, 3, 50, 0.95, 0, 0, 42L);

        assertTrue(hard > easy,
                "高手(" + hard + ")应比新手(" + easy + ")难");
    }

    @Test
    @DisplayName("频繁放弃应降低难度")
    void abandonShouldLowerDifficulty() {
        double normal = AssignmentAlgorithm.calcGroupDifficulty(2, 6, 3, 50, 0.8, 0, 0, 42L);
        double abandoned = AssignmentAlgorithm.calcGroupDifficulty(2, 6, 3, 50, 0.8, 5, 0, 42L);

        assertTrue(abandoned < normal,
                "放弃多(" + abandoned + ")应比正常(" + normal + ")低");
    }

    @Test
    @DisplayName("连续高压应强制轻松")
    void consecutiveHighShouldForceEasy() {
        double normal = AssignmentAlgorithm.calcGroupDifficulty(4, 6, 3, 50, 0.8, 0, 0, 42L);
        double forced = AssignmentAlgorithm.calcGroupDifficulty(4, 6, 3, 50, 0.8, 0, 4, 42L);

        assertTrue(forced < normal,
                "连续高压(" + forced + ")应比正常(" + normal + ")低");
    }

    // ==================== 类型多样性 ====================

    @Test
    @DisplayName("组内不应重复任务类型")
    void groupShouldNotDuplicateTypes() {
        List<TaskCandidate> pool = Arrays.asList(
                new TaskCandidate("plains", "collect", "OAK_LOG", 5, 15),
                new TaskCandidate("plains", "collect", "STONE", 5, 15),
                new TaskCandidate("plains", "kill", "ZOMBIE", 8, 15),
                new TaskCandidate("plains", "kill", "SKELETON", 8, 15),
                new TaskCandidate("plains", "fish", "COD", 3, 8),
                new TaskCandidate("plains", "farm", "WHEAT", 3, 10)
        );

        List<TaskCandidate> selected = AssignmentAlgorithm.selectTasksForGroup(
                pool, 50, new HashSet<>(), 3, 42L);

        Set<String> types = selected.stream().map(t -> t.taskType).collect(Collectors.toSet());
        assertEquals(selected.size(), types.size(),
                "组内不应重复类型: " + selected);
    }

    @Test
    @DisplayName("应优先选当天未用过的类型")
    void shouldPreferUnusedTypes() {
        List<TaskCandidate> pool = Arrays.asList(
                new TaskCandidate("plains", "collect", "OAK_LOG", 5, 15),
                new TaskCandidate("plains", "kill", "ZOMBIE", 8, 15),
                new TaskCandidate("plains", "fish", "COD", 3, 8)
        );

        Set<String> globalUsed = new HashSet<>(Arrays.asList("collect"));

        List<TaskCandidate> selected = AssignmentAlgorithm.selectTasksForGroup(
                pool, 50, globalUsed, 2, 42L);

        // 不应选到 collect（已用过）
        boolean hasCollect = selected.stream().anyMatch(t -> "collect".equals(t.taskType));
        assertFalse(hasCollect, "不应选已用过的类型 collect: " + selected);
    }

    // ==================== 每组任务数 ====================

    @Test
    @DisplayName("简单组应有较少任务，困难组应有更多任务")
    void tasksPerGroupShouldVaryByDifficulty() {
        int easy = AssignmentAlgorithm.calcTasksPerGroup(10);
        int medium = AssignmentAlgorithm.calcTasksPerGroup(45);
        int hard = AssignmentAlgorithm.calcTasksPerGroup(80);

        assertEquals(3, easy, "简单(10)应3个任务");
        assertTrue(medium > easy, "中等(45)应比简单多");
        assertTrue(hard > medium, "困难(80)应比中等多");
    }

    // ==================== 数量校准 ====================

    @Test
    @DisplayName("高难度应产出更多数量")
    void higherDifficultyShouldProduceMore() {
        int easy = AssignmentAlgorithm.calibrateAmount(5, 15, 20, 42L);
        int hard = AssignmentAlgorithm.calibrateAmount(5, 15, 80, 42L);

        assertTrue(hard > easy,
                "高难度(" + hard + ")应比低难度(" + easy + ")数量多");
    }

    @Test
    @DisplayName("数量应在 min~max 范围内")
    void amountShouldBeInRange() {
        for (int d = 0; d <= 100; d += 10) {
            int amount = AssignmentAlgorithm.calibrateAmount(5, 15, d, 42L);
            assertTrue(amount >= 5 && amount <= 15,
                    "难度=" + d + " 时数量=" + amount + " 应在 5~15 内");
        }
    }

    // ==================== 标题生成 ====================

    @Test
    @DisplayName("标题应包含难度前缀和群系名")
    void titleShouldContainPrefixAndBiome() {
        List<TaskCandidate> tasks = Arrays.asList(
                new TaskCandidate("plains", "collect", "OAK_LOG", 5, 15));

        String title = AssignmentAlgorithm.generateGroupTitle("平原", tasks, 0);
        assertTrue(title.contains("入门"), "标题应含难度前缀: " + title);
        assertTrue(title.contains("平原"), "标题应含群系名: " + title);
        assertTrue(title.contains("采集"), "标题应含任务类型: " + title);
    }

    @Test
    @DisplayName("不同组序应有不同的难度前缀")
    void differentGroupIndexShouldHaveDifferentPrefix() {
        List<TaskCandidate> tasks = Arrays.asList(
                new TaskCandidate("plains", "collect", "OAK_LOG", 5, 15));

        String t0 = AssignmentAlgorithm.generateGroupTitle("平原", tasks, 0);
        String t3 = AssignmentAlgorithm.generateGroupTitle("沙漠", tasks, 3);
        String t5 = AssignmentAlgorithm.generateGroupTitle("雪山", tasks, 5);

        assertTrue(t0.contains("入门"), t0);
        assertTrue(t3.contains("挑战"), t3);
        assertTrue(t5.contains("试炼"), t5);
    }

    // ==================== 线性回归 ====================

    @Test
    @DisplayName("上升趋势斜率应为正")
    void risingTrendShouldHavePositiveSlope() {
        // 1, 2, 3, 4, 5 → 斜率应为正
        double slope = calcSlope(Arrays.asList(100, 200, 300, 400, 500));
        assertTrue(slope > 0, "上升趋势斜率应为正: " + slope);
    }

    @Test
    @DisplayName("下降趋势斜率应为负")
    void fallingTrendShouldHaveNegativeSlope() {
        double slope = calcSlope(Arrays.asList(500, 400, 300, 200, 100));
        assertTrue(slope < 0, "下降趋势斜率应为负: " + slope);
    }

    @Test
    @DisplayName("平稳趋势斜率应接近零")
    void stableTrendShouldHaveNearZeroSlope() {
        double slope = calcSlope(Arrays.asList(300, 310, 290, 305, 295));
        assertTrue(Math.abs(slope) < 20, "平稳趋势斜率应接近零: " + slope);
    }

    private double calcSlope(List<Integer> values) {
        int n = values.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += i; sumY += values.get(i);
            sumXY += i * values.get(i); sumX2 += i * i;
        }
        double denom = n * sumX2 - sumX * sumX;
        return denom == 0 ? 0 : (n * sumXY - sumX * sumY) / denom;
    }

    // ==================== 综合模拟 ====================

    @Test
    @DisplayName("模拟一天完整分配：6组6群系，类型多样，难度递进")
    void simulateFullDayAssignment() {
        // 模拟任务池：6 个群系，各有多种任务
        Map<String, List<TaskCandidate>> biomePools = new LinkedHashMap<>();
        biomePools.put("minecraft:plains", Arrays.asList(
                new TaskCandidate("minecraft:plains", "collect", "OAK_LOG", 5, 15),
                new TaskCandidate("minecraft:plains", "collect", "WHEAT", 3, 10),
                new TaskCandidate("minecraft:plains", "kill", "ZOMBIE", 8, 15),
                new TaskCandidate("minecraft:plains", "farm", "WHEAT", 3, 10)));
        biomePools.put("minecraft:forest", Arrays.asList(
                new TaskCandidate("minecraft:forest", "collect", "SPRUCE_LOG", 5, 15),
                new TaskCandidate("minecraft:forest", "kill", "SPIDER", 8, 15),
                new TaskCandidate("minecraft:forest", "breed", "WOLF", 2, 5)));
        biomePools.put("minecraft:desert", Arrays.asList(
                new TaskCandidate("minecraft:desert", "collect", "CACTUS", 3, 10),
                new TaskCandidate("minecraft:desert", "explore", "DESERT_PYRAMID", 1, 1),
                new TaskCandidate("minecraft:desert", "kill", "HUSK", 8, 15)));
        biomePools.put("minecraft:ocean", Arrays.asList(
                new TaskCandidate("minecraft:ocean", "fish", "COD", 3, 8),
                new TaskCandidate("minecraft:ocean", "fish", "SALMON", 3, 8),
                new TaskCandidate("minecraft:ocean", "explore", "MONUMENT", 1, 1)));
        biomePools.put("minecraft:swamp", Arrays.asList(
                new TaskCandidate("minecraft:swamp", "collect", "MANGROVE_LOG", 5, 15),
                new TaskCandidate("minecraft:swamp", "kill", "SLIME", 8, 15),
                new TaskCandidate("minecraft:swamp", "farm", "SUGAR_CANE", 3, 10)));
        biomePools.put("minecraft:snowy_plains", Arrays.asList(
                new TaskCandidate("minecraft:snowy_plains", "kill", "STRAY", 8, 15),
                new TaskCandidate("minecraft:snowy_plains", "collect", "ICE", 5, 15),
                new TaskCandidate("minecraft:snowy_plains", "breed", "RABBIT", 2, 5)));

        List<String> biomes = new ArrayList<>(biomePools.keySet());
        long seed = 12345L;

        // 模拟分配
        Set<String> allUsedTypes = new HashSet<>();
        double prevTarget = 0;
        int groupCount = 0;

        for (int i = 0; i < biomes.size(); i++) {
            String biomeId = biomes.get(i);
            List<TaskCandidate> pool = biomePools.get(biomeId);

            double target = AssignmentAlgorithm.calcGroupDifficulty(
                    i, biomes.size(), 3, 50, 0.8, 0, 0, seed);

            List<TaskCandidate> selected = AssignmentAlgorithm.selectTasksForGroup(
                    pool, target, allUsedTypes, 2, seed + i);

            // 验证
            assertFalse(selected.isEmpty(), "组 " + i + " 不应为空");

            // 类型不重复
            Set<String> groupTypes = selected.stream().map(t -> t.taskType).collect(Collectors.toSet());
            assertEquals(selected.size(), groupTypes.size(),
                    "组 " + i + " 类型不应重复: " + selected);

            // 难度递进（允许抖动，但整体趋势）
            if (i > 0) {
                assertTrue(target >= prevTarget - 15,
                        "组 " + i + " 难度(" + target + ")不应比前一组(" + prevTarget + ")低太多");
            }

            // 数量在范围内
            for (TaskCandidate tc : selected) {
                int amount = AssignmentAlgorithm.calibrateAmount(tc.minAmount, tc.maxAmount, target, seed + i);
                assertTrue(amount >= tc.minAmount && amount <= tc.maxAmount,
                        "数量应在范围内: " + tc + " -> " + amount);
            }

            // 标题
            String title = AssignmentAlgorithm.generateGroupTitle(biomeId, selected, i);
            assertNotNull(title);
            assertFalse(title.isEmpty());

            allUsedTypes.addAll(groupTypes);
            prevTarget = target;
            groupCount++;

            System.out.printf("  组%d: %s | 难度=%.0f | 任务=%s | 标题=%s%n",
                    i, biomeId, target, selected, title);
        }

        // 至少覆盖 4 种类型
        assertTrue(allUsedTypes.size() >= 4,
                "应覆盖≥4种类型，实际=" + allUsedTypes);
        assertEquals(6, groupCount, "应有6组");

        System.out.println("  覆盖类型: " + allUsedTypes);
    }
}
