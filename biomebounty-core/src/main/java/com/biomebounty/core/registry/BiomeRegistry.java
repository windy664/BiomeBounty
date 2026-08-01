package com.biomebounty.core.registry;

import com.biomebounty.api.BiomeData;
import com.biomebounty.api.BiomeProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 群系注册表
 */
public class BiomeRegistry implements BiomeProvider {

    private final Map<String, BiomeData> biomes = new HashMap<>();

    public void register(BiomeData biome) {
        biomes.put(biome.getId(), biome);
    }

    public void registerAll(Map<String, ? extends BiomeData> biomes) {
        this.biomes.putAll(biomes);
    }

    @Override
    public Collection<BiomeData> getAllBiomes() {
        return biomes.values();
    }

    @Override
    public BiomeData getBiome(String id) {
        return biomes.get(id);
    }

    @Override
    public List<BiomeData> getBiomesByNamespace(String namespace) {
        List<BiomeData> result = new ArrayList<>();
        for (BiomeData biome : biomes.values()) {
            if (biome.getNamespace().equals(namespace)) {
                result.add(biome);
            }
        }
        return result;
    }

    @Override
    public List<String> getAllNamespaces() {
        List<String> namespaces = new ArrayList<>();
        for (BiomeData biome : biomes.values()) {
            if (!namespaces.contains(biome.getNamespace())) {
                namespaces.add(biome.getNamespace());
            }
        }
        return namespaces;
    }

    @Override
    public boolean hasBiome(String id) {
        return biomes.containsKey(id);
    }

    @Override
    public int getSize() {
        return biomes.size();
    }

    public List<String> getAllBiomeIds() {
        return new ArrayList<>(biomes.keySet());
    }

    public void clear() {
        biomes.clear();
    }
}
