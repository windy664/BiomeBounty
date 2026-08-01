package com.biomebounty.core.scanner;

import com.biomebounty.api.BiomeData;
import com.biomebounty.core.model.BiomeDataImpl;
import com.biomebounty.core.model.BiomeResourcesImpl;
import com.biomebounty.core.model.MobSpawnImpl;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * 群系 JSON 解析器
 * 解析 biome JSON + 通过 FeatureResourceExtractor 提取实际资源
 */
public class BiomeParser {

    private final FeatureResourceExtractor extractor = new FeatureResourceExtractor();

    public BiomeData parseBiome(JarFile jar, String path, String namespace, String biomeName) {
        try {
            ZipEntry entry = jar.getEntry(path);
            if (entry == null) return null;

            InputStream is = jar.getInputStream(entry);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
            is.close();

            List<MobSpawnImpl> spawners = parseSpawners(json);
            List<String> features = parseFeatures(json);
            BiomeResourcesImpl resources = extractResources(jar, features, spawners);
            String permission = "biomebounty.biome." + namespace + "." + biomeName;

            return new BiomeDataImpl(
                    namespace + ":" + biomeName,
                    biomeName,
                    namespace,
                    features,
                    new ArrayList<>(spawners),
                    resources,
                    permission
            );
        } catch (Exception e) {
            return null;
        }
    }

    private List<MobSpawnImpl> parseSpawners(JsonObject json) {
        List<MobSpawnImpl> spawners = new ArrayList<>();
        JsonObject spawnersObj = json.getAsJsonObject("spawners");
        if (spawnersObj == null) return spawners;

        for (String category : spawnersObj.keySet()) {
            JsonArray mobs = spawnersObj.getAsJsonArray(category);
            if (mobs == null) continue;

            for (JsonElement mob : mobs) {
                JsonObject obj = mob.getAsJsonObject();
                String type = obj.get("type").getAsString();
                int min = obj.has("minCount") ? obj.get("minCount").getAsInt() : 1;
                int max = obj.has("maxCount") ? obj.get("maxCount").getAsInt() : 4;
                int weight = obj.has("weight") ? obj.get("weight").getAsInt() : 10;
                spawners.add(new MobSpawnImpl(type, category, min, max, weight));
            }
        }
        return spawners;
    }

    private List<String> parseFeatures(JsonObject json) {
        List<String> features = new ArrayList<>();
        JsonArray arr = json.getAsJsonArray("features");
        if (arr == null) return features;

        for (JsonElement list : arr) {
            if (list.isJsonArray()) {
                for (JsonElement f : list.getAsJsonArray()) {
                    features.add(f.getAsString());
                }
            }
        }
        return features;
    }

    /**
     * 从文件路径解析群系（用于 datapacks 目录下的展开包）
     */
    public BiomeData parseBiomeFile(Path biomeFile, String namespace, String biomeName) {
        try (InputStream is = Files.newInputStream(biomeFile)) {
            JsonObject json = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();

            List<MobSpawnImpl> spawners = parseSpawners(json);
            List<String> features = parseFeatures(json);
            // 展开包没有 JarFile，跳过 feature 深度提取，用关键词回退
            BiomeResourcesImpl resources = extractResourcesFallback(features, spawners);
            String permission = "biomebounty.biome." + namespace + "." + biomeName;

            return new BiomeDataImpl(
                    namespace + ":" + biomeName,
                    biomeName,
                    namespace,
                    features,
                    new ArrayList<>(spawners),
                    resources,
                    permission
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 提取群系资源
     * 优先通过 FeatureResourceExtractor 从 feature 配置中提取实际方块
     * 回退到关键词匹配
     */
    private BiomeResourcesImpl extractResources(JarFile jar, List<String> features, List<MobSpawnImpl> spawners) {
        BiomeResourcesImpl resources = new BiomeResourcesImpl();

        // 第一步：通过 feature 配置提取实际方块
        Map<String, List<String>> extracted = extractor.extractFromFeatures(features, jar);
        for (String tree : extracted.getOrDefault("trees", new ArrayList<>())) {
            resources.addTree(tree);
        }
        for (String ore : extracted.getOrDefault("ores", new ArrayList<>())) {
            resources.addOre(ore);
        }
        for (String plant : extracted.getOrDefault("plants", new ArrayList<>())) {
            resources.addPlant(plant);
        }

        // 第二步：对没有提取到的类型，回退到关键词匹配
        if (!resources.hasTrees() || !resources.hasOres() || !resources.hasPlants()) {
            for (String f : features) {
                String lower = f.toLowerCase();
                if (!resources.hasTrees() && (lower.contains("tree") || lower.contains("oak") || lower.contains("birch") ||
                        lower.contains("spruce") || lower.contains("jungle"))) {
                    resources.addTree(f);
                }
                if (!resources.hasOres() && (lower.contains("ore") || lower.contains("coal") || lower.contains("iron") ||
                        lower.contains("gold") || lower.contains("diamond"))) {
                    resources.addOre(f);
                }
                if (!resources.hasPlants() && (lower.contains("flower") || lower.contains("grass") || lower.contains("mushroom"))) {
                    resources.addPlant(f);
                }
                if (lower.contains("village") || lower.contains("temple") || lower.contains("monument")) {
                    resources.addStructure(f);
                }
            }
        }

        return resources;
    }

    /**
     * 提取群系资源（关键词回退版，无 JarFile 时使用）
     */
    private BiomeResourcesImpl extractResourcesFallback(List<String> features, List<MobSpawnImpl> spawners) {
        BiomeResourcesImpl resources = new BiomeResourcesImpl();

        for (String f : features) {
            String lower = f.toLowerCase();
            if (lower.contains("tree") || lower.contains("oak") || lower.contains("birch") ||
                    lower.contains("spruce") || lower.contains("jungle")) {
                resources.addTree(f);
            }
            if (lower.contains("ore") || lower.contains("coal") || lower.contains("iron") ||
                    lower.contains("gold") || lower.contains("diamond")) {
                resources.addOre(f);
            }
            if (lower.contains("flower") || lower.contains("grass") || lower.contains("mushroom")) {
                resources.addPlant(f);
            }
            if (lower.contains("village") || lower.contains("temple") || lower.contains("monument")) {
                resources.addStructure(f);
            }
        }

        return resources;
    }
}
