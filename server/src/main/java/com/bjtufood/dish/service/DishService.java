package com.bjtufood.dish.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bjtufood.dish.dto.DishAdminReq;
import com.bjtufood.dish.dto.DishAdminListItemVO;
import com.bjtufood.dish.dto.DishAdminListQuery;
import com.bjtufood.dish.dto.DishAdminVO;
import com.bjtufood.dish.dto.DishHealthVO;
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
     * 菜品列表查询（分页+筛选；筛选与排序由所选视图唯一决定）
     * <p>
     * 支持参数：keyword / view / seed（{@code canteenId} / {@code minPrice} / {@code maxPrice}
     * 与排序参数均不接受：食堂 / 价格筛选全量下线，端上无排序入口）。
     * 视图的 `conditions` 与 `sortKind` 都存 `dish_filter_view`（A6：`DishViewCatalog` 查表 →
     * `DishViewResolver` 解析为 {@code DishListQuery}）：条件经**字段白名单**翻译为参数化 WHERE
     * （AND 组合），`sortKind` 决定 ORDER BY。随机序视图（`random`）走
     * {@code CRC32(CONCAT(seed,'-',id)), id} 稳定伪随机序（同 seed 全序恒定，翻页不重不漏）；
     * 其余口径见 {@code DishListQuery.SortKind}。
     * {@code view} 白名单校验（值域 = 视图表），非法值抛 BusinessException(400)。
     * 公开接口只查 status=on 的菜品
     *
     * @param req 查询参数
     * @return 分页菜品列表（**列表专用 {@link DishListItemVO} 8 字段**：D 项拆分）
     */
    IPage<DishListItemVO> listDishes(DishQueryReq req);

    /**
     * 首页筛选视图字典（{@code GET /dishes/views} 出参）。
     * <p>
     * 标签文案与顺序来自视图表（唯一真源）；**空视图自动隐藏**（当前无在售菜品匹配即不下发，
     * 有菜自动出现），故「为你推荐」等聚合视角同样按匹配数规则下发。
     *
     * @return 可见视图（`enabled` + 匹配数规则；按 `order` 升序），端上按此序渲染标签栏
     */
    List<com.bjtufood.dish.view.DishViewVO> listDishViews();

    /**
     * 菜品描述属性编辑态候选（{@code GET /dishes/{id}/attributes} 出参，**按需**）。
     * <p>
     * 只返回该菜<b>现有维度</b>（由 {@code dish.attributes} 的键集合 ∩ 维度字典得出，按维度 {@code order} 升序），
     * 且只补编辑要用的 {@code valueType} + 参考候选 {@code options}
     * （候选 = 该维度「全库已用中文值」去重、按使用频次倒序；仅为参考、不构成约束）。
     *
     * @param dishId 菜品ID
     * @return 编辑态属性项列表（按维度 order 升序）
     * @throws com.bjtufood.common.exception.BusinessException 菜品不存在或已下架（4001）
     */
    List<com.bjtufood.dish.dto.DishAttributeEditVO> listDishAttributes(Long dishId);

    /**
     * 获取菜品详情
     * <p>
     * <b>浏览计数副作用（PV 口径）</b>：本方法在**成功取到详情后**向 {@code dish_view_log}
     * 插一行浏览明细（**不去重**：同一用户反复看同一道菜每次都计；🔴 <b>不累加 {@code dish.view_count}</b>，
     * 该列已停写）。菜品不存在（含已下架）抛
     * {@code BusinessException(4001)}，**不计数**。
     * <p>
     * 🔴 <b>浏览量不参与任何排序</b>（热度算法已全量下线）：唯一消费方是管理端「近 30 天浏览」列，
     * 详见 {@code docs/schema/dish_view_log.md}。
     * <p>
     * {@code avgRating} 读缓存列 {@code dish.avg_rating}（库内零评价为 NULL），🔴 **出参兜底为 5.0**（仅出参层，不落库、不参与排序）；
     * {@code attributes} 直接取 {@code dish.attributes} JSON（**值即中文**）。
     *
     * @param id 菜品ID
     * @return 菜品详情（**详情专用 {@link DishDetailVO}**：11 字段）
     * @throws com.bjtufood.common.exception.BusinessException 菜品不存在（4001）
     */
    DishDetailVO getDishDetail(Long id);

    // ==================== 一期新增：搜索 / 发现页公开接口 ====================

    /**
     * 猜你喜欢：抽取在售菜品名下发——不看热度、不排序、不做个性化推荐算法。出参仅 {@code name}。
     * <p>
     * <b>刷新边界 = 重进小程序</b>：端上<b>没有任何「主动换一批」入口</b>
     * （全仓无下拉刷新）——用户无法解释内容为何变化，体验上更像「界面不稳定」而非「新鲜」。
     * 故随机性归于<b>会话</b>：端上冷启动生成 seed、会话内恒定，服务端按
     * {@code CRC32(seed:ID)} 稳定伪随机序取数 ⇒ 同一次会话内多次进入拿到同一批词条，
     * 重进小程序才整体重洗。
     * <p>
     * {@code seed} 为<b>可选</b>参数：为空时退回 {@code ORDER BY RAND()}，
     * 旧端 / 第三方 / Swagger 直连不受影响（不加 {@code required}，避免破坏性变更）。
     * 契约留扩展位：将来升级为个性化 / 推荐算法时端上契约不变（仅换服务端取数逻辑）。
     *
     * @param seed 会话随机种子（可选；为空按真随机取数）
     * @return 猜你喜欢词条列表（仅 name=在售菜品名）
     */
    List<GuessLikeVO> guessLike(String seed);

    // ==================== 管理端接口（管理员） ====================

    /**
     * 查询全部菜品列表（含已下架），返回带完整图片 URL 的 VO（分页）
     *
     * @param page     页码（从 1 开始）
     * @param pageSize 每页条数（上限由 PageUtil 约束）
     * @return 分页后台菜品 VO
     */
    /**
     * 批量投影：dishId → 所属 stallId（供跨域调用方**消除 N+1**；空集合返回空 Map，不发查询）。
     *
     * @param dishIds 菜品 ID
     * @return dishId → stallId；菜品不存在或档口为空则不入图
     */
    Map<Long, Long> mapStallIdByIds(Collection<Long> dishIds);

    IPage<DishAdminListItemVO> listAllForAdmin(DishAdminListQuery query, int page, int pageSize);

    /**
     * 新增菜品
     *
     * @param req     菜品信息
     */
    DishAdminVO addDish(DishAdminReq req);

    /**
     * 单条详情（编辑回填）：{@code GET /admin/dishes/{id}}。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=4001 菜品不存在
     */
    DishAdminVO getForAdmin(Long id);

    /**
     * 复制为新菜品（{@code POST /admin/dishes/{id}/copy}）：只改菜名，其余字段复制源菜品；
     * 副本默认 <b>下架</b>（半成品，确认后再上架）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=4001 源菜品不存在 / code=400 名称非法
     */
    DishAdminVO copyDish(Long id, String name);

    /**
     * 上下架（{@code PUT /admin/dishes/{id}/status}）：只改 {@code status}。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=4001 菜品不存在 / code=400 status 非法
     */
    void updateStatus(Long id, String status);

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
     * 统计某档口下的菜品数（管理端删除档口的受阻判据；跨域计数由 controller 编排，避免
     * canteen → dish 的反向包级依赖）。
     *
     * @param stallId 档口 ID
     * @return 菜品数；stallId 为 null 时返回 0
     */
    long countByStallId(Long stallId);

    /**
     * 批量统计多个档口下的菜品数（管理端档口列表 {@code GET /admin/stalls} 用）。
     * <p>
     * 一次 {@code COUNT(*) GROUP BY stall_id} 取回「档口 → 菜品数」映射，替代逐档口调用
     * {@link #countByStallId}（档口虽为十数条量级，逐行查询仍会随列表行数线性放大）。
     * 计入口径与 {@link #countByStallId} <b>完全一致</b>：只按档口过滤、不筛在售状态
     * （该计数回答「档口下还有没有菜」，下架菜同样不能随档口一起消失）。
     *
     * @param stallIds 档口 ID 集合（null / 空集合 → 直接返回空 Map，不发 SQL）
     * @return 档口 ID → 菜品数；<b>无菜品的档口不会出现在结果集中</b>，调用方按需兜底 0
     */
    Map<Long, Long> countByStallIds(Collection<Long> stallIds);

    /**
     * 菜品健康度计数（D1 看板：在售数 + 三个「当场能修」的缺失项）。
     * <p>
     * 跨域聚合由 `dashboard.controller` 编排，本方法只出**本域**的 4 个计数。
     * 口径：在售 = `status='on'`；三个缺失项统计**全部菜品（含下架）**（存量清理指标）。
     *
     * @return 健康度计数
     */
    DishHealthVO countHealth();

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
