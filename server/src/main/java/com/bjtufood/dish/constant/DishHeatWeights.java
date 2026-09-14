package com.bjtufood.dish.constant;

/**
 * 菜品综合热度分权重常量（Java 侧唯一真源）。
 * <p>
 * 热度分公式：{@code view_count*WEIGHT_VIEW + rating_count*WEIGHT_RATING_COUNT*RATING_SCALE + avg_rating*RATING_SCALE}
 * <br>即等价形式：{@code view_count*1 + rating_count*5*20 + COALESCE(avg_rating,0)*20}。
 * <p>
 * <b>必须与 DishMapper.xml 的 {@code heatScoreExpr} 片段保持等价，改动需同步。</b>
 * 当前排序/热搜的 SQL 表达式已收敛到该 XML 片段，本类仅作为权重口径的集中登记与
 * Java 侧（如需在代码中计算/校验热度）的引用点，避免权重散落多处、改一处漏一处。
 */
public final class DishHeatWeights {

    /** 浏览量权重（weight of view_count） */
    public static final int WEIGHT_VIEW = 1;

    /** 评价数权重（weight of rating_count，乘 RATING_SCALE 前的基础权重） */
    public static final int WEIGHT_RATING_COUNT = 5;

    /** 评分统一放大倍数（rating 维度量纲对齐，评价数 5*20=100 与该倍数共用） */
    public static final int RATING_SCALE = 20;

    private DishHeatWeights() {
    }
}
