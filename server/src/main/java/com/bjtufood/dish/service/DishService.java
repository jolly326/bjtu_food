package com.bjtufood.dish.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.dish.dto.DishAdminReq;
import com.bjtufood.dish.dto.DishAdminVO;
import com.bjtufood.dish.dto.DishCorrectionCmd;
import com.bjtufood.dish.dto.DishDetailVO;
import com.bjtufood.dish.dto.DishListItemVO;
import com.bjtufood.dish.dto.DishQueryReq;
import com.bjtufood.dish.dto.GuessLikeVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 菜品服务接口
 * <p>
 * 菜品展示、搜索、管理、统计相关业务逻辑。
 * 评价模块通过事件机制通知本模块更新评分。
 */
public interface DishService {

    // ==================== 公开接口 ====================

    /**
     * 菜品列表查询（分页+筛选；排序由服务端决定：推荐流按 seed 伪随机序，其余热度倒序）
     * <p>
     * 支持参数：keyword / mealType / seed（2026-09-22 K3：{@code canteenId} / {@code minPrice} /
     * {@code maxPrice} 随「食堂 / 价格筛选全量下线」删除；2026-09-21 §7.33：
     * {@code stallId} / {@code sortBy} / {@code sortOrder} 已删除，端上无排序入口）。
     * 排序双分支（2026-09-27 方案 C 会话种子）：无 keyword / mealType 且 {@code seed} 非空 →
     * DishMapper.xml 的 {@code CRC32(CONCAT(seed,'-',id)), id} 稳定伪随机序（同 seed 全序恒定，
     * 翻页不重不漏）；其余情形 → heatScoreExpr 倒序（热度口径不变）。
     * {@code mealType} 白名单校验（MealTypeConst），非法值抛 BusinessException(400)。
     * 公开接口只查 status=on 的菜品
     *
     * @param req 查询参数
     * @return 分页菜品列表（**列表专用 {@link DishListItemVO} 8 字段**：2026-09-22 D 项拆分）
     */
    IPage<DishListItemVO> listDishes(DishQueryReq req);

    /**
     * 菜品大类字典（2026-09-21 §7.34）：{@code GET /dishes/meal-types} 出参。
     * <p>
     * 标签文案与顺序来自 {@link com.bjtufood.dish.constant.MealTypeConst}（唯一真源），
     * 只下发「当前有在售菜品」的大类（空类自动隐藏，有菜自动出现）。
     *
     * @return 按 order 升序的大类字典项
     */
    List<com.bjtufood.dish.dto.MealTypeVO> listMealTypes();

    /**
     * 菜品描述属性编辑态选项（{@code GET /dishes/{id}/attributes} 出参，**按需**）。
     * <p>
     * 只返回该菜<b>现有维度</b>（由 {@code dish.attributes} 的键集合 ∩ 维度字典得出，按维度 {@code order} 升序），
     * 且只补编辑要用的 {@code valueType} + 该维度全部候选 {@code options}
     * （维度名与当前值在 {@code GET /dishes/{id}} 里已有，本端点不重复下发）。
     * {@code options} 为空数组 = 自由文本维度。
     *
     * @param dishId 菜品ID
     * @return 编辑态属性项列表（按维度 order 升序）
     * @throws com.bjtufood.common.exception.BusinessException 菜品不存在或已下架（4001）
     */
    List<com.bjtufood.dish.dto.DishAttributeEditVO> listDishAttributes(Long dishId);

    /**
     * 获取菜品详情
     * <p>
     * <b>浏览计数副作用（PV 口径）</b>：本方法在**成功取到详情后**执行
     * {@code view_count + 1}（原子 UPDATE）。菜品不存在（含已下架）抛
     * {@code BusinessException(4001)}，**不计数**。
     * <p>
     * {@code avgRating} 读缓存列 {@code dish.avg_rating}（零评价为 null），不做实时聚合；
     * {@code attributes} 由 {@code dish.attributes} JSON + 字典两表整理为「机器值 + 中文」。
     *
     * @param id 菜品ID
     * @return 菜品详情（**详情专用 {@link DishDetailVO}**：11 字段）
     * @throws com.bjtufood.common.exception.BusinessException 菜品不存在（4001）
     */
    DishDetailVO getDishDetail(Long id);

    // ==================== 一期新增：搜索 / 发现页公开接口 ====================

    /**
     * 猜你喜欢（原「热搜词条 TOP10」，2026-09-22 change search-page-refresh 改名 + 语义变更）
     * <p>
     * 当前实现：**每次请求随机抽取在售菜品名**下发——不看热度、不排序、不做个性化推荐算法；
     * 因此**不加缓存**（响应缓存会让「每次随机」退化为「全站同一份」）。出参仅 {@code keyword}。
     * 契约留扩展位：将来升级为个性化 / 推荐算法时端上契约不变（仅换服务端取数逻辑）。
     *
     * @return 猜你喜欢词条列表（仅 keyword=在售菜品名）
     */
    List<GuessLikeVO> guessLike();

    // ==================== 管理端接口（管理员） ====================

    /**
     * 查询全部菜品列表（含已下架），返回带完整图片 URL 的 VO（分页）
     *
     * @param page     页码（从 1 开始）
     * @param pageSize 每页条数（上限由 PageUtil 约束）
     * @return 分页后台菜品 VO
     */
    IPage<DishAdminVO> listAllForAdmin(int page, int pageSize);

    /**
     * 新增菜品
     *
     * @param req     菜品信息
     */
    void addDish(DishAdminReq req);

    /**
     * 编辑菜品
     *
     * @param id  菜品ID
     * @param req 菜品信息
     * @throws com.bjtufood.common.exception.BusinessException 菜品不存在
     */
    void updateDish(Long id, DishAdminReq req);

    /**
     * 删除菜品
     * <p>
     * 物理删除菜品，并级联清理该菜品下的全部评价与浏览足迹。
     *
     * @param id 菜品ID
     */
    void deleteDish(Long id);

    // ==================== 评分更新（事件驱动） ====================

    /**
     * 重新计算菜品平均评分
     * <p>
     * 由 RatingUpdateListener 在评价提交事件后调用
     *
     * @param dishId 菜品ID
     */
    void recalcAvgRating(Long dishId);

    // ==================== 跨域契约（P0-1：供 correction 等域消费，替代其直连 DishMapper） ====================

    /**
     * 菜品是否存在（不区分上架态）。
     * <p>
     * 供纠错采纳前置校验（采纳要求菜品物理存在，已下架菜品同样可采纳）。
     *
     * @param dishId 菜品ID（可空）
     * @return true=存在
     */
    boolean existsById(Long dishId);

    /**
     * 菜品是否存在且在售（{@code status=on}）。
     * <p>
     * 供纠错提交等公开入口做「不存在与已下架同款处理」的存在性校验，
     * 状态口径由 dish 域唯一持有（调用方不再 import {@code DishConst} / {@code Dish} 实体）。
     *
     * @param dishId 菜品ID（可空）
     * @return true=存在且在售
     */
    boolean existsOnSale(Long dishId);

    /**
     * 批量取菜品名（管理端列表补齐「关联菜品名」用，一次 IN 查询消除 N+1）。
     * <p>
     * 口径：不过滤 status/上架态（纠错/举报对象可能已下架，管理端仍需回看）；
     * 已物理删除的菜品不在结果集，调用方 VO 保持 null。
     *
     * @param dishIds 菜品ID集合（null/空集合返回空 Map，不发起查询）
     * @return dishId → name 映射
     */
    Map<Long, String> mapNameByIds(Collection<Long> dishIds);

    /**
     * 纠错采纳写回（跨域写契约：correction → dish）。
     * <p>
     * 写库动作与落库形态（images 的 JSON 序列化、null 字段跳过不覆盖策略）由 dish 域唯一持有；
     * 调用方 SHALL NOT 再构造 {@code Dish} 实体或注入 DishMapper。
     *
     * @param cmd 写回指令（可空字段按「不覆盖既有值」处理）
     * @return true=已写回；false=目标菜品行不存在（并发删除，调用方按 4001 处理）
     */
    boolean applyCorrection(DishCorrectionCmd cmd);
}
