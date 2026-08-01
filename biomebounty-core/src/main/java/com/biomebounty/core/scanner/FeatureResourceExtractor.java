package com.biomebounty.core.scanner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * 群系资源提取器
 * 从 placed feature → configured feature JSON 中提取实际方块类型
 * 替代关键词猜测
 */
public class FeatureResourceExtractor {

    /**
     * 从群系的特征列表中提取资源
     * @param features biome JSON 中的 features 数组（每个元素是 placed feature 的 registry key）
     * @param jar 服务器/mod JAR
     * @return {trees: [...], ores: [...], plants: [...]}
     */
    public Map<String, List<String>> extractFromFeatures(List<String> features, JarFile jar) {
        List<String> trees = new ArrayList<>();
        List<String> ores = new ArrayList<>();
        List<String> plants = new ArrayList<>();

        for (String featureRef : features) {
            JsonObject config = lookupConfiguredFeature(featureRef, jar);
            if (config == null) continue;

            Map<String, List<String>> blocks = extractBlocksFromConfig(config);
            trees.addAll(blocks.getOrDefault("trees", Collections.emptyList()));
            ores.addAll(blocks.getOrDefault("ores", Collections.emptyList()));
            plants.addAll(blocks.getOrDefault("plants", Collections.emptyList()));
        }

        // 去重
        Map<String, List<String>> result = new HashMap<>();
        result.put("trees", deduplicate(trees));
        result.put("ores", deduplicate(ores));
        result.put("plants", deduplicate(plants));
        return result;
    }

    /**
     * 查找 configured feature 的 config 对象
     * placed feature → configured feature → config
     */
    private JsonObject lookupConfiguredFeature(String featureRef, JarFile jar) {
        if (featureRef == null || !featureRef.contains(":")) return null;
        String[] parts = featureRef.split(":", 2);
        String ns = parts[0];
        String name = parts[1];

        // 尝试不同的路径（版本差异）
        String[] paths = {
                "data/" + ns + "/worldgen/configured_feature/" + name + ".json",
                "data/" + ns + "/worldgen/placed_feature/" + name + ".json",
                "data/" + ns + "/worldgen/feature/" + name + ".json"
        };

        for (String path : paths) {
            try {
                ZipEntry entry = jar.getEntry(path);
                if (entry == null) continue;

                InputStream is = jar.getInputStream(entry);
                JsonObject json = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
                is.close();

                // placed feature 可能引用 configured feature
                if (json.has("feature")) {
                    JsonElement featureEl = json.get("feature");
                    if (featureEl.isJsonPrimitive()) {
                        // 引用另一个 feature，递归查找
                        return lookupConfiguredFeature(featureEl.getAsString(), jar);
                    } else if (featureEl.isJsonObject()) {
                        return featureEl.getAsJsonObject().has("config")
                                ? featureEl.getAsJsonObject().getAsJsonObject("config") : featureEl.getAsJsonObject();
                    }
                }

                // 直接返回 config
                if (json.has("config")) return json.getAsJsonObject("config");
                return json;
            } catch (Exception e) { /* try next path */ }
        }
        return null;
    }

    /**
     * 从 feature config 中提取方块名，按资源类型分类
     * 递归遍历 JSON，找所有 "state" / "Name" 字段
     */
    private Map<String, List<String>> extractBlocksFromConfig(JsonObject config) {
        List<String> blocks = new ArrayList<>();
        findBlocksRecursive(config, blocks);

        List<String> trees = new ArrayList<>();
        List<String> ores = new ArrayList<>();
        List<String> plants = new ArrayList<>();

        for (String block : blocks) {
            String upper = block.toUpperCase();
            if (isTree(upper)) trees.add(upper);
            else if (isOre(upper)) ores.add(upper);
            else if (isPlant(upper)) plants.add(upper);
        }

        // 从 feature type 推断资源类型（如果方块名没匹配上）
        String featureType = config.has("type") ? config.get("type").getAsString() : "";
        if (trees.isEmpty() && featureType.contains("tree")) {
            // 尝试从 trunk/foliage 提取
            extractTreeBlocks(config, trees);
        }

        Map<String, List<String>> result = new HashMap<>();
        result.put("trees", trees);
        result.put("ores", ores);
        result.put("plants", plants);
        return result;
    }

    /**
     * 递归查找 JSON 中所有方块引用
     * 方块通常在 "state": {"Name": "minecraft:stone"} 或 "state": {"Name": "...", "Properties": {...}}
     */
    private void findBlocksRecursive(JsonElement element, List<String> blocks) {
        if (element == null || element.isJsonNull()) return;

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();

            // 检查 state 字段
            if (obj.has("state")) {
                JsonElement stateEl = obj.get("state");
                if (stateEl.isJsonObject()) {
                    JsonObject state = stateEl.getAsJsonObject();
                    if (state.has("Name")) {
                        String name = state.get("Name").getAsString().replace("minecraft:", "");
                        if (!blocks.contains(name)) blocks.add(name);
                    }
                } else if (stateEl.isJsonPrimitive()) {
                    String name = stateEl.getAsString().replace("minecraft:", "");
                    if (!blocks.contains(name)) blocks.add(name);
                }
            }

            // 检查直接的 Name 字段（某些版本格式）
            if (obj.has("Name") && obj.get("Name").isJsonPrimitive()) {
                String name = obj.get("Name").getAsString().replace("minecraft:", "");
                if (!blocks.contains(name)) blocks.add(name);
            }

            // 检查 type 字段是否是方块类型引用
            if (obj.has("block") && obj.get("block").isJsonPrimitive()) {
                String name = obj.get("block").getAsString().replace("minecraft:", "");
                if (!blocks.contains(name)) blocks.add(name);
            }

            // 递归所有字段
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                findBlocksRecursive(entry.getValue(), blocks);
            }
        } else if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                findBlocksRecursive(item, blocks);
            }
        }
    }

    /**
     * 从 tree feature config 提取树干/树叶方块
     */
    private void extractTreeBlocks(JsonObject config, List<String> trees) {
        // 1.18+ 格式
        if (config.has("trunk_provider")) {
            extractBlockFromStateProvider(config.get("trunk_provider"), trees);
        }
        if (config.has("foliage_provider")) {
            extractBlockFromStateProvider(config.get("foliage_provider"), trees);
        }
        // 旧格式
        if (config.has("trunk")) {
            JsonObject trunk = config.getAsJsonObject("trunk");
            if (trunk.has("state_provider")) extractBlockFromStateProvider(trunk.get("state_provider"), trees);
            if (trunk.has("provider")) extractBlockFromStateProvider(trunk.get("provider"), trees);
        }
        if (config.has("foliage")) {
            JsonObject foliage = config.getAsJsonObject("foliage");
            if (foliage.has("state_provider")) extractBlockFromStateProvider(foliage.get("state_provider"), trees);
            if (foliage.has("provider")) extractBlockFromStateProvider(foliage.get("provider"), trees);
        }
    }

    /**
     * 从 state provider 提取方块名
     */
    private void extractBlockFromStateProvider(JsonElement provider, List<String> blocks) {
        if (provider == null || !provider.isJsonObject()) return;
        JsonObject obj = provider.getAsJsonObject();

        // simple_state_provider / random_state_provider
        if (obj.has("state")) {
            JsonObject state = obj.getAsJsonObject("state");
            if (state.has("Name")) {
                String name = state.get("Name").getAsString().replace("minecraft:", "");
                if (!blocks.contains(name)) blocks.add(name);
            }
        }
        // weighted_state_provider
        if (obj.has("entries")) {
            for (JsonElement entry : obj.getAsJsonArray("entries")) {
                if (entry.isJsonObject() && entry.getAsJsonObject().has("data")) {
                    extractBlockFromStateProvider(entry.getAsJsonObject().get("data"), blocks);
                }
            }
        }
    }

    private boolean isTree(String name) {
        return name.contains("LOG") || name.contains("WOOD") || name.contains("STEM")
                || name.contains("OAK") || name.contains("BIRCH") || name.contains("SPRUCE")
                || name.contains("JUNGLE") || name.contains("ACACIA") || name.contains("DARK_OAK")
                || name.contains("CHERRY") || name.contains("MANGROVE") || name.contains("CRIMSON")
                || name.contains("WARPED");
    }

    private boolean isOre(String name) {
        return name.contains("ORE") || name.contains("COAL") || name.contains("IRON")
                || name.contains("GOLD") || name.contains("DIAMOND") || name.contains("EMERALD")
                || name.contains("LAPIS") || name.contains("REDSTONE") || name.contains("COPPER")
                || name.contains("NETHER_GOLD") || name.contains("ANCIENT_DEBRIS")
                || name.contains("QUARTZ");
    }

    private boolean isPlant(String name) {
        return name.contains("FLOWER") || name.contains("GRASS") || name.contains("FERN")
                || name.contains("MUSHROOM") || name.contains("TALL_") || name.contains("BUSH")
                || name.contains("SAPLING") || name.contains("WARPED_FUNGUS") || name.contains("CRIMSON_FUNGUS")
                || name.contains("SEAGRASS") || name.contains("KELP") || name.contains("BAMBOO");
    }

    private List<String> deduplicate(List<String> list) {
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (!result.contains(s)) result.add(s);
        }
        return result;
    }
}
