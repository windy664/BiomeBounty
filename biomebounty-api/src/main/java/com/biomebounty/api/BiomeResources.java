package com.biomebounty.api;

import java.util.List;

/**
 * 群系资源接口
 */
public interface BiomeResources {

    /**
     * 获取树木列表
     */
    List<String> getTrees();

    /**
     * 获取矿石列表
     */
    List<String> getOres();

    /**
     * 获取植物列表
     */
    List<String> getPlants();

    /**
     * 获取结构列表
     */
    List<String> getStructures();

    /**
     * 是否有树木
     */
    boolean hasTrees();

    /**
     * 是否有矿石
     */
    boolean hasOres();

    /**
     * 是否有植物
     */
    boolean hasPlants();

    /**
     * 是否有结构
     */
    boolean hasStructures();

    /**
     * 获取随机树木
     */
    String getRandomTree();

    /**
     * 获取随机矿石
     */
    String getRandomOre();

    /**
     * 获取随机植物
     */
    String getRandomPlant();

    /**
     * 获取随机结构
     */
    String getRandomStructure();
}
