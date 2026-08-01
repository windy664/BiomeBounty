package com.biomebounty.manager;

import com.biomebounty.BiomeBountyPlugin;
import com.biomebounty.api.StatsProvider;
import com.biomebounty.database.dao.PlayerStatsDAO;
import java.util.*;

public class StatsManager implements StatsProvider {
    private final BiomeBountyPlugin plugin;
    private final PlayerStatsDAO dao;

    public StatsManager(BiomeBountyPlugin plugin, PlayerStatsDAO dao) { this.plugin = plugin; this.dao = dao; }
    public int getTotalCompleted(UUID id) { return dao.getTotalCompleted(id); }
    public int getCurrentStreak(UUID id) { return dao.getCurrentStreak(id); }
    public int getBestStreak(UUID id) { return dao.getBestStreak(id); }
    public int getRank(UUID id) { return dao.getRank(id); }
    public List<Map.Entry<UUID, Integer>> getTopPlayers(int limit) { return dao.getTopPlayers(limit); }
    public void incrementCompleted(UUID id, String name) { dao.incrementCompleted(id, name); }
    public void incrementAbandoned(UUID id) { dao.incrementAbandoned(id); }
    public void updateStreak(UUID id, boolean completed) { dao.updateStreak(id, completed); }
}
