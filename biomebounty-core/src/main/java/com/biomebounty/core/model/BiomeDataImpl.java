package com.biomebounty.core.model;

import com.biomebounty.api.BiomeData;
import com.biomebounty.api.BiomeResources;
import com.biomebounty.api.MobSpawn;

import java.util.ArrayList;
import java.util.List;

/**
 * 群系数据实现
 */
public class BiomeDataImpl implements BiomeData {

    private final String id;
    private final String name;
    private final String namespace;
    private final List<String> features;
    private final List<MobSpawn> spawners;
    private final BiomeResources resources;
    private final String permission;

    public BiomeDataImpl(String id, String name, String namespace,
                         List<String> features, List<MobSpawn> spawners,
                         BiomeResources resources, String permission) {
        this.id = id;
        this.name = name;
        this.namespace = namespace;
        this.features = features != null ? features : new ArrayList<>();
        this.spawners = spawners != null ? spawners : new ArrayList<>();
        this.resources = resources != null ? resources : new BiomeResourcesImpl();
        this.permission = permission;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public String getDisplayName() {
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(part.substring(0, 1).toUpperCase());
            sb.append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    @Override
    public List<String> getFeatures() {
        return features;
    }

    @Override
    public List<MobSpawn> getSpawners() {
        return spawners;
    }

    @Override
    public BiomeResources getResources() {
        return resources;
    }

    @Override
    public String getPermission() {
        return permission;
    }

    @Override
    public boolean hasFeature(String feature) {
        return features.stream().anyMatch(f -> f.contains(feature));
    }

    @Override
    public boolean hasStructure() {
        return features.stream().anyMatch(f ->
                f.contains("village") ||
                f.contains("temple") ||
                f.contains("monument") ||
                f.contains("fortress") ||
                f.contains("mansion"));
    }

    @Override
    public String toString() {
        return "BiomeData{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
