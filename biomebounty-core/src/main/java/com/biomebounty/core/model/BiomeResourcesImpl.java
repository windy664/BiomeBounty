package com.biomebounty.core.model;

import com.biomebounty.api.BiomeResources;

import java.util.ArrayList;
import java.util.List;

/**
 * 群系资源实现
 */
public class BiomeResourcesImpl implements BiomeResources {

    private final List<String> trees;
    private final List<String> ores;
    private final List<String> plants;
    private final List<String> structures;

    public BiomeResourcesImpl() {
        this.trees = new ArrayList<>();
        this.ores = new ArrayList<>();
        this.plants = new ArrayList<>();
        this.structures = new ArrayList<>();
    }

    public BiomeResourcesImpl(List<String> trees, List<String> ores,
                              List<String> plants, List<String> structures) {
        this.trees = trees != null ? trees : new ArrayList<>();
        this.ores = ores != null ? ores : new ArrayList<>();
        this.plants = plants != null ? plants : new ArrayList<>();
        this.structures = structures != null ? structures : new ArrayList<>();
    }

    @Override
    public List<String> getTrees() {
        return trees;
    }

    @Override
    public List<String> getOres() {
        return ores;
    }

    @Override
    public List<String> getPlants() {
        return plants;
    }

    @Override
    public List<String> getStructures() {
        return structures;
    }

    @Override
    public boolean hasTrees() {
        return !trees.isEmpty();
    }

    @Override
    public boolean hasOres() {
        return !ores.isEmpty();
    }

    @Override
    public boolean hasPlants() {
        return !plants.isEmpty();
    }

    @Override
    public boolean hasStructures() {
        return !structures.isEmpty();
    }

    @Override
    public String getRandomTree() {
        return getRandom(trees);
    }

    @Override
    public String getRandomOre() {
        return getRandom(ores);
    }

    @Override
    public String getRandomPlant() {
        return getRandom(plants);
    }

    @Override
    public String getRandomStructure() {
        return getRandom(structures);
    }

    private String getRandom(List<String> list) {
        if (list.isEmpty()) return null;
        return list.get((int) (Math.random() * list.size()));
    }

    public void addTree(String tree) {
        if (!trees.contains(tree)) trees.add(tree);
    }

    public void addOre(String ore) {
        if (!ores.contains(ore)) ores.add(ore);
    }

    public void addPlant(String plant) {
        if (!plants.contains(plant)) plants.add(plant);
    }

    public void addStructure(String structure) {
        if (!structures.contains(structure)) structures.add(structure);
    }
}
