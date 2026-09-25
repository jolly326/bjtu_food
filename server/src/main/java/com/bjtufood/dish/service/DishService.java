package com.bjtufood.dish.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.dish.dto.DishAdminReq;
import com.bjtufood.dish.dto.DishAdminVO;
import com.bjtufood.dish.dto.DishDetailVO;
import com.bjtufood.dish.dto.DishListItemVO;
import com.bjtufood.dish.dto.DishQueryReq;
import com.bjtufood.dish.dto.GuessLikeVO;

import java.util.List;

/**
 * 菜品服务接口
 * <p>
 * 菜品展示、搜索、管理、统计相关业务逻辑。
 * 评价模块通过事件机制通知本模块更新评分。
 */
public interface DishService {

    // ==================== 公开接口 ====================

    /**
     * 菜品列表查询（分页+筛选，排序恒为服务端热度倒序）
     * <p>
     * 支持参数：keyword / mealType（2026-09-22 K3：{@code canteenId} / {@code minPrice} /
     * {@code maxPrice} 随「食堂 / 价格筛选全量下线」删除；2026-09-21 §7.33：
     * {@code stallId} / {@code sortBy} / {@code sortOrder} 已删除，排序唯一口径 =
     * DishMapper.xml 的 heatScoreExpr 倒序）。
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
     * 菜品描述四维字典（2026-09-23 §7.40 R4 / R13）：{@code GET /dishes/attributes} 出参。
     * <p>
     * 四维（{@code dietType} / {@code ingredients} / {@code flavorTags} / {@code serveTemp}）的
     * 取值与中文标签唯一真源为 {@link com.bjtufood.dish.constant.DishAttributeConst}；
     * 小程序端与管理端**共用同一份字典**（端上映射展示、管理端渲染表单选项），
     * 两端 SHALL NOT 再硬编码映射表。
     * <p>
     * 与 {@link #listMealTypes()} 的差异：本字典**下发全部取值、不做在售过滤** ——
     * 四维是「描述属性」，管理端录入表单需要完整选项（大类是「筛选维度」，才按在售过滤）。
     *
     * @return 四维字典项（按维度分组、组内 order 升序）
     */
    List<com.bjtufood.dish.dto.DishAttributeVO> listAttributes();

    /**
     * 获取菜品详情
     * <p>
     * 2026-09-15：原「登录时附加 hasReviewed（是否已评价）」已下线（三端零消费）。
     *
     * <p><b>浏览计数副作用（PV 口径）</b>：本方法在**成功取到详情后**执行
     * {@code view_count + 1}（原子 UPDATE）并写入一条访问日志（view_log，append-only；
     * 游客 {@code userId=null} 记 {@code user_id=0}）。菜品不存在（含已下架）抛
     * {@code BusinessException(4001)}，**不计数、不写日志**。
     *
     * @param id 菜品ID
     * @param userId 当前用户ID（**可为 null** = 游客；仅决定日志行的 user_id 取值，不影响计数）
     * @return 菜品详情（**详情专用 {@link DishDetailVO}**：15 字段 + `ratingDistribution`）
     * @throws com.bjtufood.common.exception.BusinessException 菜品不存在（4001）
     */
    DishDetailVO getDishDetail(Long id, Long userId);

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
}
