package com.biomebounty.core.model;

import com.biomebounty.api.MobSpawn;

/**
 * 生物生成实现
 */
public class MobSpawnImpl implements MobSpawn {

    private final String type;
    private final String category;
    private final int minCount;
    private final int maxCount;
    private final int weight;

    public MobSpawnImpl(String type, String category, int minCount, int maxCount, int weight) {
        this.type = type;
        this.category = category;
        this.minCount = minCount;
        this.maxCount = maxCount;
        this.weight = weight;
    }

    public MobSpawnImpl(String type, String category) {
        this(type, category, 1, 4, 10);
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getCategory() {
        return category;
    }

    @Override
    public int getMinCount() {
        return minCount;
    }

    @Override
    public int getMaxCount() {
        return maxCount;
    }

    @Override
    public int getWeight() {
        return weight;
    }

    @Override
    public String getEntityName() {
        String[] parts = type.split(":");
        return parts.length > 1 ? parts[1] : type;
    }

    @Override
    public String getDisplayName() {
        String name = getEntityName();
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(part.substring(0, 1).toUpperCase());
            sb.append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    @Override
    public boolean isHostile() {
        return "monster".equals(category);
    }

    @Override
    public boolean isPassive() {
        return "creature".equals(category);
    }

    @Override
    public boolean isAmbient() {
        return "ambient".equals(category);
    }
}
