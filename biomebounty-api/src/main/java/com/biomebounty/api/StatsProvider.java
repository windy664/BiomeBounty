package com.biomebounty.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 统计提供者接口
 */
public interface StatsProvider {

    /**
     * 获取玩家总完成任务数
     */
    int getTotalCompleted(UUID playerId);

    /**
     * 获取玩家当前连续天数
     */
    int getCurrentStreak(UUID playerId);

    /**
     * 获取玩家最佳连续天数
     */
    int getBestStreak(UUID playerId);

    /**
     * 获取玩家排名
     */
    int getRank(UUID playerId);

    /**
     * 获取排行榜
     */
    List<Map.Entry<UUID, Integer>> getTopPlayers(int limit);

    /**
     * 增加完成任务数
     */
    void incrementCompleted(UUID playerId, String playerName);

    /**
     * 增加放弃任务数
     */
    void incrementAbandoned(UUID playerId);

    /**
     * 更新连续天数
     */
    void updateStreak(UUID playerId, boolean completed);
}
