package com.biomebounty.api;

import java.util.List;

/**
 * 群系数据接口
 */
public interface BiomeData {

    /**
     * 获取群系 ID
     */
    String getId();

    /**
     * 获取群系名称
     */
    String getName();

    /**
     * 获取命名空间
     */
    String getNamespace();

    /**
     * 获取显示名称
     */
    String getDisplayName();

    /**
     * 获取特征列表
     */
    List<String> getFeatures();

    /**
     * 获取可生成的生物
     */
    List<MobSpawn> getSpawners();

    /**
     * 获取资源
     */
    BiomeResources getResources();

    /**
     * 获取权限节点
     */
    String getPermission();

    /**
     * 是否有指定特征
     */
    boolean hasFeature(String feature);

    /**
     * 是否有结构
     */
    boolean hasStructure();
}
