package com.bjtufood.canteen.service;

import com.bjtufood.canteen.dto.StallAdminVO;
import com.bjtufood.canteen.dto.StallBriefVO;
import com.bjtufood.canteen.entity.Stall;

import java.util.List;

/**
 * 档口服务接口
 * <p>
 * 档口已去实体化：降级为「菜品筛选属性字典」，生命周期仅「新增 / 改名（编辑）」＋列表查询，
 * 无删除、无停业/审核能力；一个档口仍从属于一个食堂（canteen_id）。
 */
public interface StallService {

    /**
     * 后台档口列表
     *
     * @return 后台档口 VO 列表（含完整图片 URL）
     */
    List<StallAdminVO> listAllForAdmin();

    /**
     * 档口列表（{@code GET /admin/stalls}）：可选按食堂筛选，排序「食堂 → 档口名」。
     *
     * @param canteenId 食堂 ID；null = 全部
     */
    List<StallAdminVO> listAllForAdmin(Long canteenId);

    /**
     * 新增档口（{@code POST /admin/stalls}）。
     *
     * @return 新建的 VO
     * @throws com.bjtufood.common.exception.BusinessException code=400 食堂缺失/不存在、名称为空/超长/同食堂重名、楼层不在字典
     */
    StallAdminVO createStall(Stall stall);

    /**
     * 删除档口（{@code DELETE /admin/stalls/{id}}）。
     * <p>
     * ⚠️ 「其下仍有菜品」的受阻判据在 <b>controller</b> 层（跨域计数不反向依赖 dish 域）。
     *
     * @throws com.bjtufood.common.exception.BusinessException code=4001 档口不存在
     */
    void deleteStall(Long id);

    /**
     * 空档口数（D1 看板健康度：一个菜品都没有的档口 —— 管理员当场可修）。
     *
     * @return 无菜品的档口数
     */
    long countWithoutDish();

    /**
     * 编辑档口
     *
     * @param stall 档口信息（含ID）
     */
    void update(Stall stall);

    /**
     * 写回档口楼层（**跨域写契约：correction → canteen**，楼层纠错新增）。
     * <p>
     * <b>为何是独立入口而不是复用 {@link #update(Stall)}</b>：
     * <ol>
     *   <li>{@code update} 的契约是「管理端提交整份档口表单」（含 canteenId 校验、全字段覆盖语义），
     *       而纠错采纳只需要写<b>一个列</b>；复用会把「楼层纠错」耦合进后台编辑口径，</li>
     *   <li>{@code update} 在 {@code updateById} 影响 0 行时抛「Stall not found」——
     *       该实现依赖 MyBatis-Plus 的 NOT_NULL 跳过策略，语义是「整行整存」；本入口的语义是
     *       「按 id 定点写 floor 列」，两者不该共用同一段防御逻辑。</li>
     * </ol>
     * <p>
     * 调用方（纠错采纳）负责「是否该写」的判定（本次纠错 {@code floor} 是否非空）；
     * 本方法负责「写到哪、写什么形态」——落库细节不外泄，与 {@code DishService.applyCorrection} 同一收口口径。
     * <p>
     * 楼层是<b>档口级</b>描述：写回后该档口下<b>全部菜品</b>的详情楼层一并生效。
     *
     * @param stallId 目标档口ID（调用方已解析/校验）
     * @param floor   楼层（受控字典值·值即汉字；调用方保证非空白、命中 {@code FloorDict}，
     *                且长度 ≤ {@code CorrectionConst.FLOOR_MAX_LENGTH}）
     * @throws com.bjtufood.common.exception.BusinessException 楼层为空白（400「楼层不能为空」）、
     *         楼层不在字典内（400「楼层不在预设范围内」）或目标档口不存在（400「档口不存在」，含并发删除兜底）
     */
    void updateFloor(Long stallId, String floor);

    /**
     * 按名 upsert 档口：同名不重复建档（精确匹配，名称列无唯一键，
     * 并发双写极端情况由调用方幂等容忍）。菜品录入/编辑（DishServiceImpl#resolveStallId）
     * 与菜品问题反馈采纳（createIfMissing=true）共用本入口。
     * <p>
     * 新建档口必须有可解析的有效所属食堂（canteenName 有效并按名 upsert 所属食堂，
     * 空白/「其他」等空值语义名称 → 400「请选择所属食堂」），不允许落 canteen_id=0。
     *
     * @param stallName      档口名（调用方保证非空白）
     * @param rawCanteenName 所属食堂名（仅新建档口时消费，可空）
     * @return 档口 ID
     */
    Long upsertStallByName(String stallName, String rawCanteenName);

    /**
     * 档口存在性校验（纠错采纳带 stallId 时防御性复查用）。
     *
     * @param stallId 档口ID（可空）
     * @return true=存在
     */
    boolean existsById(Long stallId);

    /**
     * 按名取档口ID（同名多条时取一条，与 {@link #upsertStallByName} 的「精确匹配、无唯一键」口径一致）。
     * <p>
     * 供 correction 域采纳纠错时解析「用户填的档口名 → stallId」，替代其直接注入 StallMapper
     * 手写查询（P0-1 跨域 Mapper 违规）。仅空白名返回 null；「其他」等空值语义名称按字面值匹配
     * （与迁移前 correction 侧的查询行为一致，建档拦截仍由 {@link #upsertStallByName} 负责）。
     *
     * @param stallName 档口名（可空）
     * @return 档口ID；未建档或名称属空值语义返回 null
     */
    Long findIdByName(String stallName);

    /**
     * 按ID取档口名（correction 管理端列表补齐「目标档口」用）。
     *
     * @param stallId 档口ID（可空）
     * @return 档口名；不存在返回 null
     */
    String getNameById(Long stallId);

    /**
     * 取档口楼层（B4 纠错详情：楼层差异需给出**当前实时值**做对照）。
     * <p>
     * 楼层归属**档口**（{@code stall.floor}）而非菜品 ⇒ 必须经 canteen 域契约回查。
     *
     * @param stallId 档口 ID（可空）
     * @return 楼层；档口不存在或未配置时为 {@code null}
     */
    String getFloorById(Long stallId);

    /**
     * 取档口所属食堂名称（管理端菜品详情回填用；档口或食堂不存在返回 null）。
     *
     * @param stallId 档口 ID
     * @return 食堂名称；null=档口不存在 / 未挂食堂
     */
    String getCanteenNameByStallId(Long stallId);

    /**
     * 档口确认候选列表（correction 采纳两段式确认用，替代其直接注入 StallMapper/CanteenMapper 自查）。
     * <p>
     * 口径不变：提交食堂名匹配现有食堂时 = 该食堂下全部档口（sort_order 升序）；
     * 无匹配食堂时 = 全量档口（canteen_id 升序 → sort_order 升序 → updated_at 倒序）。
     * 「按名找食堂」属 canteen 域自有知识，故一并收在本实现内。
     *
     * @param canteenName 提交/纠错的食堂名（可空，空则返回全量档口）
     * @return 候选档口投影（id + name），永不为 null
     */
    List<StallBriefVO> listBriefCandidates(String canteenName);
}
