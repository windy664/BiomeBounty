package com.biomebounty;

import com.biomebounty.api.BiomeData;
import com.biomebounty.command.AdminCommand;
import com.biomebounty.command.BountyCommand;
import com.biomebounty.command.CommandBridge;
import com.biomebounty.core.registry.BiomeRegistry;
import com.biomebounty.core.scanner.BiomeParser;
import com.biomebounty.core.scanner.JarScanner;
import com.biomebounty.database.DatabaseFactory;
import com.biomebounty.database.DatabaseManager;
import com.biomebounty.database.TableCreator;
import com.biomebounty.database.dao.PlayerAssignmentDAO;
import com.biomebounty.database.dao.PlayerBiomeHistoryDAO;
import com.biomebounty.database.dao.TaskGroupDAO;
import com.biomebounty.database.dao.PlayerStatsDAO;
import com.biomebounty.database.dao.TaskPoolDAO;
import com.biomebounty.gui.AssignmentGUI;
import com.biomebounty.gui.AssignmentGUIListener;
import com.biomebounty.i18n.I18nManager;
import com.biomebounty.listener.*;
import com.biomebounty.manager.*;
import com.biomebounty.papi.BiomeBountyExpansion;
import com.biomebounty.scheduler.DailyReset;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class BiomeBountyPlugin extends JavaPlugin {

    private static BiomeBountyPlugin instance;

    private DatabaseManager databaseManager;
    private PlayerStatsDAO playerStatsDAO;
    private TaskPoolDAO taskPoolDAO;
    private PlayerAssignmentDAO playerAssignmentDAO;
    private PlayerBiomeHistoryDAO playerBiomeHistoryDAO;
    private TaskGroupDAO taskGroupDAO;

    private BiomeRegistry biomeRegistry;
    private DailyAssigner dailyAssigner;
    private AssignmentGUI assignmentGUI;
    private StatsManager statsManager;
    private I18nManager i18nManager;

    private List<Pattern> biomeBlacklist = new ArrayList<>();
    private boolean blacklistEnabled = false;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        initializeI18n();
        initializeDatabase();
        initializeRegistries();
        initializeManagers();
        loadBiomeBlacklist();
        scanBiomes();
        syncTaskPool();
        logTaskPoolDistribution();
        registerListeners();
        registerCommands();
        registerPAPIExpansion();
        startSchedulers();
        getLogger().info("BiomeBounty enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) databaseManager.shutdown();
        getLogger().info("BiomeBounty disabled!");
    }

    private void initializeI18n() {
        i18nManager = new I18nManager(this);
        i18nManager.loadMessages();
    }

    private void initializeDatabase() {
        try {
            databaseManager = DatabaseFactory.create(getConfig(), getDataFolder());
            databaseManager.initialize(getDataFolder()); // 内部已调用 createTables + migration
            playerStatsDAO = new PlayerStatsDAO(databaseManager);
            taskPoolDAO = new TaskPoolDAO(databaseManager);
            playerAssignmentDAO = new PlayerAssignmentDAO(databaseManager);
            playerBiomeHistoryDAO = new PlayerBiomeHistoryDAO(databaseManager);
            taskGroupDAO = new TaskGroupDAO(databaseManager);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Database init failed", e);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    private void initializeRegistries() {
        biomeRegistry = new BiomeRegistry();
    }

    private void initializeManagers() {
        statsManager = new StatsManager(this, playerStatsDAO);
        dailyAssigner = new DailyAssigner(this, taskPoolDAO, playerAssignmentDAO, playerBiomeHistoryDAO, taskGroupDAO);
        assignmentGUI = new AssignmentGUI(this);
    }

    private void scanBiomes() {
        if (!getConfig().getBoolean("settings.auto-scan", true)) return;
        try {
            Path serverDir = Bukkit.getWorldContainer().toPath();
            Path[] worldDirs = Bukkit.getWorlds().stream()
                    .map(w -> w.getWorldFolder().toPath())
                    .toArray(Path[]::new);

            // 扫描群系（主线程，需要 Bukkit API）
            Map<String, BiomeData> biomes =
                    new JarScanner().scanServer(serverDir, new BiomeParser(), worldDirs);
            biomeRegistry.registerAll(biomes);
            getLogger().info("Scanned " + biomes.size() + " biomes");
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Biome scan failed", e);
        }
    }

    private void syncTaskPool() {
        int count = 0, skipped = 0;
        for (com.biomebounty.api.BiomeData biome : biomeRegistry.getAllBiomes()) {
            String biomeId = biome.getId();

            // 群系黑名单过滤
            if (isBiomeBlacklisted(biomeId)) {
                skipped++;
                continue;
            }

            java.util.List<String> resources = new java.util.ArrayList<>();
            resources.addAll(biome.getResources().getTrees());
            resources.addAll(biome.getResources().getOres());
            resources.addAll(biome.getResources().getPlants());
            for (String r : resources) {
                taskPoolDAO.savePoolEntry(biomeId, "collect", r,
                        "采集 " + com.biomebounty.util.BiomeUtil.formatTargetName(r), 5, 15, 10);
                count++;
            }

            for (com.biomebounty.api.MobSpawn m : biome.getSpawners()) {
                if (m.isHostile()) {
                    String fullId = m.getType(); // 保留完整命名空间，如 "twilightforest:quartz_crawler"
                    taskPoolDAO.savePoolEntry(biomeId, "kill", fullId,
                            "击杀 " + com.biomebounty.util.BiomeUtil.formatTargetName(fullId), 8, 15, 10);
                    count++;
                }
            }

            for (com.biomebounty.api.MobSpawn m : biome.getSpawners()) {
                if ("water_creature".equals(m.getCategory()) || "water_ambient".equals(m.getCategory())) {
                    String fullId = m.getType();
                    taskPoolDAO.savePoolEntry(biomeId, "fish", fullId,
                            "钓 " + com.biomebounty.util.BiomeUtil.formatTargetName(fullId), 3, 8, 8);
                    count++;
                }
            }

            for (com.biomebounty.api.MobSpawn m : biome.getSpawners()) {
                if (m.isPassive()) {
                    String fullId = m.getType();
                    taskPoolDAO.savePoolEntry(biomeId, "breed", fullId,
                            "繁殖 " + com.biomebounty.util.BiomeUtil.formatTargetName(fullId), 2, 5, 8);
                    count++;
                }
            }

            if (biome.hasStructure()) {
                for (String s : biome.getResources().getStructures()) {
                    taskPoolDAO.savePoolEntry(biomeId, "explore", s,
                            "探索 " + com.biomebounty.util.BiomeUtil.formatTargetName(s), 1, 1, 5);
                    count++;
                }
            }
        }
        getLogger().info("Synced " + count + " task pool entries from " + (biomeRegistry.getSize() - skipped) + " biomes"
                + (skipped > 0 ? " (blacklisted " + skipped + " biomes)" : ""));
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BlockListener(this), this);
        Bukkit.getPluginManager().registerEvents(new EntityListener(this), this);
        Bukkit.getPluginManager().registerEvents(new FishListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BreedListener(this), this);
        Bukkit.getPluginManager().registerEvents(new FarmListener(this), this);
        Bukkit.getPluginManager().registerEvents(new StructureExploreListener(this), this);
        Bukkit.getPluginManager().registerEvents(new AssignmentGUIListener(this, assignmentGUI), this);
    }

    private void registerCommands() {
        BountyCommand bountyExec = new BountyCommand(this);
        AdminCommand adminExec = new AdminCommand(this);

        getLifecycleManager().registerEventHandler(
                io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.COMMANDS, event -> {
                    event.registrar().register(
                            CommandBridge.wrap("bounty", bountyExec),
                            "打开群系悬赏", java.util.List.of("bb"));
                    event.registrar().register(
                            CommandBridge.wrap("bountyadmin", adminExec),
                            "管理群系悬赏", java.util.List.of("bba"));
                });
    }

    private void registerPAPIExpansion() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new BiomeBountyExpansion(this).register();
        }
    }

    private void startSchedulers() {
        new DailyReset(this).start();
    }

    public void reload() {
        reloadConfig();
        loadBiomeBlacklist();
        i18nManager.loadMessages();
        scanBiomes();
        syncTaskPool();
    }

    // ==================== 群系黑名单 ====================

    private void loadBiomeBlacklist() {
        biomeBlacklist.clear();
        blacklistEnabled = getConfig().getBoolean("biome-blacklist.enabled", false);
        if (!blacklistEnabled) {
            getLogger().info("Biome blacklist: disabled");
            return;
        }
        List<String> patterns = getConfig().getStringList("biome-blacklist.patterns");
        for (String p : patterns) {
            try {
                biomeBlacklist.add(Pattern.compile(p));
            } catch (Exception e) {
                getLogger().warning("Invalid biome blacklist regex: " + p + " — " + e.getMessage());
            }
        }
        getLogger().info("Biome blacklist: loaded " + biomeBlacklist.size() + " patterns");
    }

    /**
     * 检查群系是否被黑名单匹配
     */
    public boolean isBiomeBlacklisted(String biomeId) {
        if (!blacklistEnabled || biomeId == null) return false;
        for (Pattern p : biomeBlacklist) {
            if (p.matcher(biomeId).matches()) return true;
        }
        return false;
    }

    /**
     * 启动时输出任务池概率分布列
     */
    private void logTaskPoolDistribution() {
        Map<String, List<TaskPoolDAO.PoolEntry>> pool = taskPoolDAO.getAllPool();
        if (pool.isEmpty()) return;

        // 按命名空间汇总
        Map<String, int[]> nsData = new LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<String, List<TaskPoolDAO.PoolEntry>> e : pool.entrySet()) {
            String ns = e.getKey().split(":")[0];
            nsData.computeIfAbsent(ns, k -> new int[]{0})[0] += e.getValue().size();
            total += e.getValue().size();
        }

        getLogger().info("══════ 任务池分布列 (N=" + total + ") ══════");
        getLogger().info(String.format("%-25s %5s %5s %8s", "群系", "任务数", "群系数", "P(X)"));
        getLogger().info("────────────────────────────────────────────");

        // 按命名空间输出
        final int finalTotal = total;
        List<Map.Entry<String, int[]>> sorted = new ArrayList<>(nsData.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));

        for (Map.Entry<String, int[]> nsEntry : sorted) {
            String ns = nsEntry.getKey();
            int nsTotal = nsEntry.getValue()[0];
            long biomeCount = pool.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(ns + ":")).count();
            double pct = (double) nsTotal / finalTotal * 100;
            getLogger().info(String.format("%-25s %5d %5d %7.1f%%", ns, nsTotal, biomeCount, pct));

            // 命名空间下每个群系
            pool.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(ns + ":"))
                    .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                    .forEach(e -> {
                        String name = e.getKey();
                        int cnt = e.getValue().size();
                        double p = (double) cnt / finalTotal * 100;
                        getLogger().info(String.format("  %-23s %5d        %6.1f%%", name, cnt, p));
                    });
        }
        getLogger().info("════════════════════════════════════════════");
    }

    // Getters
    public static BiomeBountyPlugin getInstance() { return instance; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public PlayerStatsDAO getPlayerStatsDAO() { return playerStatsDAO; }
    public TaskPoolDAO getTaskPoolDAO() { return taskPoolDAO; }
    public PlayerAssignmentDAO getPlayerAssignmentDAO() { return playerAssignmentDAO; }
    public PlayerBiomeHistoryDAO getPlayerBiomeHistoryDAO() { return playerBiomeHistoryDAO; }
    public TaskGroupDAO getTaskGroupDAO() { return taskGroupDAO; }
    public BiomeRegistry getBiomeRegistry() { return biomeRegistry; }
    public DailyAssigner getDailyAssigner() { return dailyAssigner; }
    public AssignmentGUI getAssignmentGUI() { return assignmentGUI; }
    public StatsManager getStatsManager() { return statsManager; }
    public I18nManager getI18nManager() { return i18nManager; }
    public boolean isDebug() { return getConfig().getBoolean("settings.debug", false); }
    public void debug(String msg) { if (isDebug()) getLogger().info("[DEBUG] " + msg); }
}
