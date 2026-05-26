package com.bjtufood.common.constant;

/**
 * 菜品模块常量
 * <p>
 * 定义菜品相关的枚举值和状态常量，
 * 避免在代码中直接使用魔法字符串。
 */
public interface DishConst {

    // ==================== 菜品状态 ====================

    /** 上架 */
    String STATUS_ON = "on";

    /** 下架 */
    String STATUS_OFF = "off";

    // ==================== 菜品标签 ====================

    /** 必吃推荐标签（首页推荐流重点展示） */
    String TAG_RECOMMENDED = "recommended";

    /** 招牌菜标签 */
    String TAG_SIGNATURE = "signature";

    // ==================== 排序方式 ====================

    /** 按评分排序 */
    String SORT_BY_RATING = "rating";

    /** 按收藏量排序 */
    String SORT_BY_COLLECTS = "collects";

    /** 按价格排序 */
    String SORT_BY_PRICE = "price";

    /** 按发布时间排序 */
    String SORT_BY_CREATED_AT = "created_at";
}
