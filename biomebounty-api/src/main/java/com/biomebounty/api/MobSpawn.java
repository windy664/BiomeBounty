package com.biomebounty.api;

/**
 * 生物生成接口
 */
public interface MobSpawn {

    /**
     * 获取实体类型
     */
    String getType();

    /**
     * 获取分类
     */
    String getCategory();

    /**
     * 最小数量
     */
    int getMinCount();

    /**
     * 最大数量
     */
    int getMaxCount();

    /**
     * 权重
     */
    int getWeight();

    /**
     * 获取实体名称
     */
    String getEntityName();

    /**
     * 获取显示名称
     */
    String getDisplayName();

    /**
     * 是否是敌对生物
     */
    boolean isHostile();

    /**
     * 是否是友好生物
     */
    boolean isPassive();

    /**
     * 是否是环境生物
     */
    boolean isAmbient();
}
