package com.biomebounty.api;

import java.util.Collection;
import java.util.List;

/**
 * 群系提供者接口
 */
public interface BiomeProvider {

    /**
     * 获取所有群系
     */
    Collection<BiomeData> getAllBiomes();

    /**
     * 获取指定群系
     */
    BiomeData getBiome(String id);

    /**
     * 获取指定命名空间的群系
     */
    List<BiomeData> getBiomesByNamespace(String namespace);

    /**
     * 获取所有命名空间
     */
    List<String> getAllNamespaces();

    /**
     * 检查群系是否存在
     */
    boolean hasBiome(String id);

    /**
     * 获取群系数量
     */
    int getSize();
}
