package com.biomebounty.util;

import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;

import java.util.Set;

/**
 * 群系 ID 工具类
 * 统一返回 "namespace:name" 格式（如 "minecraft:plains"）
 *
 * 翻译策略：直接用 Component.translatable() 发翻译键给客户端，
 * 客户端用自己的 lang 文件翻译（原版 + mod 均自动支持）。
 */
public final class BiomeUtil {

    private BiomeUtil() {}

    /**
     * 获取方块所在群系的完整 ID
     * 1.13+: Biome 实现 Keyed，getKey().toString() → "minecraft:plains"
     */
    public static String getBiomeId(Block block) {
        Biome biome = block.getBiome();
        return biome.getKey().toString();
    }

    /**
     * 标准化实体类型名
     * "minecraft:zombie" → "ZOMBIE"
     * "ZOMBIE" → "ZOMBIE"
     */
    public static String normalizeEntityType(String type) {
        if (type == null) return "";
        int colon = type.indexOf(':');
        String name = colon >= 0 ? type.substring(colon + 1) : type;
        return name.toUpperCase();
    }

    /**
     * 格式化目标名为可读名称（纯文本兜底，无翻译键匹配时使用）
     * "OAK_LOG" → "Oak Log"
     * "minecraft:zombie" → "Zombie"
     */
    public static String formatTargetName(String target) {
        if (target == null) return "";
        int colon = target.indexOf(':');
        String name = colon >= 0 ? target.substring(colon + 1) : target;
        String[] words = name.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(w.substring(0, 1).toUpperCase()).append(w.substring(1));
        }
        return sb.toString();
    }

    /**
     * 把原始目标名转成 MC 翻译键
     * "minecraft:oak_log" + "collect" → "block.minecraft.oak_log"
     * "OAK_LOG" + "collect"           → "block.minecraft.oak_log"
     * "minecraft:zombie" + "kill"     → "entity.minecraft.zombie"
     * "minecraft:plains" + "explore"  → "biome.minecraft.plains"
     */
    public static String toTranslationKey(String target, String taskType) {
        if (target == null) return "";
        String normalized = target.toLowerCase().replace(" ", "_");
        String ns, name;
        int colon = normalized.indexOf(':');
        if (colon >= 0) {
            ns = normalized.substring(0, colon);
            name = normalized.substring(colon + 1);
        } else {
            ns = "minecraft";
            name = normalized;
        }

        // 群系任务
        if ("explore".equals(taskType)) {
            return "biome." + ns + "." + name;
        }
        // 击杀 / 钓鱼 / 繁殖
        if ("kill".equals(taskType) || "fish".equals(taskType) || "breed".equals(taskType)) {
            return "entity." + ns + "." + name;
        }
        // 采集 / 种植 → 矿石/原木/植物都是方块
        return "block." + ns + "." + name;
    }

    /**
     * 把群系 ID 转成翻译键
     * "minecraft:plains" → "biome.minecraft.plains"
     * "twilightforest:dark_forest" → "biome.twilightforest.dark_forest"
     */
    public static String toBiomeKey(String biomeId) {
        if (biomeId == null) return "";
        String normalized = biomeId.toLowerCase();
        int colon = normalized.indexOf(':');
        if (colon >= 0) {
            return "biome." + normalized.substring(0, colon) + "." + normalized.substring(colon + 1);
        }
        return "biome.minecraft." + normalized;
    }

    /**
     * 解析显示名称（Component 版）— 用客户端翻译
     */
    public static Component resolveDisplayNameComponent(String target, String taskType) {
        return Component.translatable(toTranslationKey(target, taskType));
    }

    /**
     * 解析显示名称（字符串版）— 服务端兜底，返回格式化名
     */
    public static String resolveDisplayName(String target, String taskType) {
        return formatTargetName(target);
    }

    /**
     * 解析群系显示名称（Component 版）— 用客户端翻译
     */
    public static Component resolveBiomeNameComponent(String biomeId) {
        return Component.translatable(toBiomeKey(biomeId));
    }

    /**
     * 解析群系显示名称（字符串版）— 服务端兜底，返回格式化名
     */
    public static String resolveBiomeName(String biomeId) {
        return formatTargetName(biomeId);
    }

    /**
     * 构建任务完成消息（Component 版，客户端翻译目标名）
     */
    public static Component buildCompletedMessage(String prefix, String target, String taskType, int amount) {
        return Component.text(prefix + "§a任务完成: §f")
                .append(resolveDisplayNameComponent(target, taskType))
                .append(Component.text(" x" + amount));
    }

    // ==================== 维度推断 ====================

    private static final Set<String> NETHER_BIOMES = Set.of(
            "nether_wastes", "soul_sand_valley", "crimson_forest",
            "warped_forest", "basalt_deltas"
    );

    private static final Set<String> END_BIOMES = Set.of(
            "the_end", "end_highlands", "end_midlands", "end_barrens", "small_end_islands"
    );

    /** mod 命名空间 → 维度翻译键（mod 自己注册的语言文件里有这些 key） */
    private static final java.util.Map<String, String> MOD_DIMENSION_KEYS = java.util.Map.of(
            "twilightforest", "dimension.twilightforest.twilight_forest",
            "the_aether",      "dimension.aether.the_aether",
            "aether",          "dimension.aether.the_aether",
            "undergarden",     "dimension.undergarden.undergarden",
            "blue_skies",      "dimension.blue_skies.everbright"
    );

    /**
     * 推断维度名，返回 Component
     *  - 原版：从 i18n 文件取翻译（主世界/下界/末地）
     *  - Mod：用 Component.translatable()（客户端 mod 语言文件翻译）
     *
     * @param biomeId 群系 ID，如 "minecraft:plains" 或 "twilightforest:dark_forest"
     * @param i18n    i18n 管理器（用于原版维度翻译），可为 null
     */
    public static Component resolveDimensionComponent(String biomeId, com.biomebounty.i18n.I18nManager i18n) {
        if (biomeId == null) return Component.text(i18n != null ? i18n.getMessage("dimension.overworld") : "主世界");

        String ns, name;
        int colon = biomeId.indexOf(':');
        if (colon >= 0) { ns = biomeId.substring(0, colon); name = biomeId.substring(colon + 1); }
        else { ns = "minecraft"; name = biomeId; }

        // 原版：从 i18n 文件取翻译
        if ("minecraft".equals(ns)) {
            String key;
            if (NETHER_BIOMES.contains(name)) key = "dimension.nether";
            else if (END_BIOMES.contains(name)) key = "dimension.end";
            else key = "dimension.overworld";
            return Component.text(i18n != null ? i18n.getMessage(key) : key);
        }

        // Mod：用翻译键（客户端 mod 语言文件会翻译）
        String modKey = MOD_DIMENSION_KEYS.get(ns);
        if (modKey != null) return Component.translatable(modKey);

        // 未知 mod：格式化命名空间名
        return Component.text(formatTargetName(ns));
    }

    /**
     * 推断维度纯文本（服务端兜底，无 i18n 时使用）
     */
    public static String resolveDimensionName(String biomeId) {
        if (biomeId == null) return "主世界";
        String ns, name;
        int colon = biomeId.indexOf(':');
        if (colon >= 0) { ns = biomeId.substring(0, colon); name = biomeId.substring(colon + 1); }
        else { ns = "minecraft"; name = biomeId; }

        if ("minecraft".equals(ns)) {
            if (NETHER_BIOMES.contains(name)) return "下界";
            if (END_BIOMES.contains(name))    return "末地";
            return "主世界";
        }
        return formatTargetName(ns);
    }
}
