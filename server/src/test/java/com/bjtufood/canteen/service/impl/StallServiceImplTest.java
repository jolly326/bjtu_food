package com.bjtufood.canteen.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.canteen.dto.StallAdminVO;
import com.bjtufood.canteen.dto.StallBriefVO;
import com.bjtufood.canteen.dto.StallSaveReq;
import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.mapper.CanteenMapper;
import com.bjtufood.canteen.mapper.StallMapper;
import com.bjtufood.common.exception.BusinessException;
import com.bjtufood.common.utils.ImageUrlUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 档口写入口的契约单测（口径见 docs/api/web/stalls.md 与 docs/schema/stall.md）：
 * <ol>
 *   <li><b>新增</b>：可编辑字段逐项落库（name 去空白、floor 走受控字典并规范化、windowNo 空白落 NULL）；
 *       食堂缺失 / 不存在、同食堂重名、floor 空串或字典外 → {@code 400}，且一律不落库；</li>
 *   <li><b>编辑</b>：canteenId / name 整体替换（改名同样查重）；floor 缺省 = 保持原值（不给该列），
 *       给值即覆盖并按字典规范化，空串 → {@code 400}；windowNo 空串 = 清空；目标档口不存在 → {@code 4001}；</li>
 *   <li><b>出参</b>：{@code StallAdminVO} 的 location / floor / windowNo / description 恒非空串（{@code null} → {@code ""}）；</li>
 *   <li><b>按名 upsert</b>：同名档口复用原 ID（不重复建档、不迁移归属）；新建档口必须挂到有效食堂
 *       （食堂名未建档则建档，空值语义食堂名 → {@code 400}）。</li>
 *   <li><b>列表出参</b>：{@code StallAdminVO} 逐项映射（食堂名批量取回、取不到落 {@code null}；
 *       均分未补齐时按 {@code 0.00} 兜底；可空字符串列恒非空串）；空列表不回查食堂；</li>
 *   <li><b>定点写回楼层</b>（纠错采纳）：空白 / 字典外 → {@code 400}，影响 0 行 → {@code 4001}，
 *       只写 id + floor 两列；</li>
 *   <li><b>删除与只读查询</b>：{@code deleteStall} 目标不存在 → {@code 4001}（幂等语义见契约）；
 *       {@code findIdByName} / {@code getNameById} / {@code getFloorById} / {@code getCanteenNameByStallId}
 *       的「入参为空 / 查不到 ⇒ {@code null}」口径；{@code listBriefCandidates} 的
 *       指定食堂 / 未指定食堂 / 食堂名查不到三条分支。</li>
 * </ol>
 * 断言口径为「错误码 + 文案 + 落库字段」三锁：只断言抛异常无法区分 {@code 400}（表单非法）
 * 与 {@code 4001}（目标不存在）的语义差异。
 */
class StallServiceImplTest {

    /** 楼层字典外的探针值（真源 FloorDict：负一层 / 一层 / 二层 / 三层 / 四层） */
    private static final String FLOOR_OUTSIDE_DICT = "五层";

    private static final String MSG_CANTEEN_REQUIRED = "请选择所属食堂";
    private static final String MSG_STALL_NAME_DUPLICATE = "该食堂下已存在同名档口";

    /** MyBatis-Plus 的 lambda 缓存需显式初始化，否则构造 LambdaQueryWrapper 会抛异常 */
    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(),
                StallServiceImplTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, Stall.class);
        TableInfoHelper.initTableInfo(assistant, Canteen.class);
    }

    private final StallMapper stallMapper = mock(StallMapper.class);
    private final CanteenMapper canteenMapper = mock(CanteenMapper.class);
    private final ImageUrlUtil imageUrlUtil = mock(ImageUrlUtil.class);

    private StallServiceImpl service() {
        return new StallServiceImpl(stallMapper, canteenMapper, imageUrlUtil);
    }

    private static Canteen canteen(Long id, String name) {
        Canteen c = new Canteen();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private static StallSaveReq saveReq(Long canteenId, String name) {
        StallSaveReq req = new StallSaveReq();
        req.setCanteenId(canteenId);
        req.setName(name);
        return req;
    }

    /**
     * 打桩 {@code insert}：自增主键在真实链路里由 MySQL 回填，纯 Mockito 下须显式补上，
     * 否则随后的 {@code selectById(saved.getId())} 收到 {@code null}。
     *
     * @return 落库实体的持有者（供字段断言）
     */
    private AtomicReference<Stall> stubInsertAutoId(long id) {
        AtomicReference<Stall> inserted = new AtomicReference<>();
        when(stallMapper.insert(any())).thenAnswer(invocation -> {
            Stall entity = invocation.getArgument(0);
            entity.setId(id);
            inserted.set(entity);
            return 1;
        });
        return inserted;
    }

    /** 打桩「食堂存在」与「同食堂无重名」（`createStall` / `update` 的前置校验）。 */
    private void stubCanteenPresentAndNameFree() {
        when(canteenMapper.selectById(1L)).thenReturn(canteen(1L, "第一食堂"));
        when(stallMapper.selectCount(any())).thenReturn(0L);
    }

    /** 断言「业务码 + 文案」双锁（避免每个用例重复强制转换样板）。 */
    private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
                                       int code, String message) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .hasMessage(message)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(code));
    }

    // ==================== createStall ====================

    @Test
    @DisplayName("createStall：正常落库 —— canteenId / name（去空白）/ floor / windowNo 逐项映射")
    void createStall_mapsEditableFields() {
        stubCanteenPresentAndNameFree();
        AtomicReference<Stall> inserted = stubInsertAutoId(9L);
        when(stallMapper.selectById(9L)).thenAnswer(invocation -> inserted.get());

        StallSaveReq req = saveReq(1L, "  面食窗口  ");
        req.setFloor("二层");
        req.setWindowNo("3号窗口");

        StallAdminVO vo = service().createStall(req);

        Stall saved = inserted.get();
        assertThat(saved.getCanteenId()).isEqualTo(1L);
        assertThat(saved.getName()).isEqualTo("面食窗口");
        assertThat(saved.getFloor()).isEqualTo("二层");
        assertThat(saved.getWindowNo()).isEqualTo("3号窗口");
        assertThat(vo.getId()).isEqualTo(9L);
        assertThat(vo.getCanteenName()).isEqualTo("第一食堂");
    }

    @Test
    @DisplayName("createStall：同食堂下重名 → 400「该食堂下已存在同名档口」，不落库")
    void createStall_duplicateNameInSameCanteen_rejected400() {
        when(canteenMapper.selectById(1L)).thenReturn(canteen(1L, "第一食堂"));
        when(stallMapper.selectCount(any())).thenReturn(1L);

        assertRejected(() -> service().createStall(saveReq(1L, "面食窗口")), 400, MSG_STALL_NAME_DUPLICATE);

        verify(stallMapper, never()).insert(any());
    }

    @Test
    @DisplayName("createStall：食堂缺失 / 非正数 / 不存在 → 400「请选择所属食堂」，不落库")
    void createStall_canteenUnresolved_rejected400() {
        // canteenId 有值但查无此食堂（mock 未打桩即返回 null）
        assertRejected(() -> service().createStall(saveReq(7L, "面食窗口")), 400, MSG_CANTEEN_REQUIRED);
        // 未提交（null）与非法值（≤0）一律不收：canteenId=0 会产出 INNER JOIN 下不可见的脏档口
        assertRejected(() -> service().createStall(saveReq(null, "面食窗口")), 400, MSG_CANTEEN_REQUIRED);
        assertRejected(() -> service().createStall(saveReq(0L, "面食窗口")), 400, MSG_CANTEEN_REQUIRED);

        verify(stallMapper, never()).insert(any());
    }

    @Test
    @DisplayName("createStall：floor 字典外 → 400「楼层不在预设范围内」，不落库")
    void createStall_floorOutsideDict_rejected400() {
        stubCanteenPresentAndNameFree();

        StallSaveReq req = saveReq(1L, "面食窗口");
        req.setFloor(FLOOR_OUTSIDE_DICT);

        assertRejected(() -> service().createStall(req), 400, "楼层不在预设范围内");

        verify(stallMapper, never()).insert(any());
    }

    @Test
    @DisplayName("createStall：floor 空白串 → 400「楼层不能为空」（字典内无「空楼层」，不支持清空）")
    void createStall_floorBlank_rejected400() {
        stubCanteenPresentAndNameFree();

        StallSaveReq req = saveReq(1L, "面食窗口");
        req.setFloor("   ");

        assertRejected(() -> service().createStall(req), 400, "楼层不能为空");

        verify(stallMapper, never()).insert(any());
    }

    @Test
    @DisplayName("createStall：floor 首尾空白 → 命中字典并规范化落库；windowNo 空白串 → 落 NULL")
    void createStall_floorNormalizedAndBlankWindowNoSavedAsNull() {
        stubCanteenPresentAndNameFree();
        AtomicReference<Stall> inserted = stubInsertAutoId(11L);
        when(stallMapper.selectById(11L)).thenAnswer(invocation -> inserted.get());

        StallSaveReq req = saveReq(1L, "面食窗口");
        req.setFloor(" 三层 ");
        req.setWindowNo("   ");

        service().createStall(req);

        assertThat(inserted.get().getFloor()).isEqualTo("三层");
        // 空白窗口号不落空串：出参侧统一归一为空串，端上无需判空
        assertThat(inserted.get().getWindowNo()).isNull();
    }

    @Test
    @DisplayName("createStall 出参：location / floor / windowNo / description 恒非空串（NULL → 空串）")
    void createStall_adminVoStringFieldsNeverEmpty() {
        stubCanteenPresentAndNameFree();
        Stall saved = new Stall();
        saved.setId(12L);
        saved.setCanteenId(1L);
        saved.setName("面食窗口");
        when(stallMapper.selectById(any())).thenReturn(saved);

        StallAdminVO vo = service().createStall(saveReq(1L, "面食窗口"));

        assertThat(vo.getLocation()).isEmpty();
        assertThat(vo.getFloor()).isEmpty();
        assertThat(vo.getWindowNo()).isEmpty();
        assertThat(vo.getDescription()).isEmpty();
    }

    // ==================== update ====================

    @Test
    @DisplayName("update：floor 缺省 → 保持原值（局部实体不带该列，NOT_NULL 策略跳过写入）")
    void update_floorMissing_keepsOriginal() {
        stubCanteenPresentAndNameFree();
        when(stallMapper.updateById(any())).thenReturn(1);

        service().update(5L, saveReq(1L, " 面食窗口 "));

        Stall patch = capturedPatch();
        assertThat(patch.getId()).isEqualTo(5L);
        assertThat(patch.getCanteenId()).isEqualTo(1L);
        assertThat(patch.getName()).isEqualTo("面食窗口");
        assertThat(patch.getFloor()).isNull();
        // windowNo 同理：缺省 ≠ 清空
        assertThat(patch.getWindowNo()).isNull();
    }

    @Test
    @DisplayName("update：floor 给值 → 覆盖并按字典规范化，windowNo 给值 → 覆盖")
    void update_floorGiven_overwritesNormalized() {
        stubCanteenPresentAndNameFree();
        when(stallMapper.updateById(any())).thenReturn(1);

        StallSaveReq req = saveReq(1L, "面食窗口");
        req.setFloor(" 三层 ");
        req.setWindowNo("5号窗口");

        service().update(5L, req);

        Stall patch = capturedPatch();
        assertThat(patch.getFloor()).isEqualTo("三层");
        assertThat(patch.getWindowNo()).isEqualTo("5号窗口");
    }

    @Test
    @DisplayName("update：floor 空串 → 400「楼层不能为空」，不写库")
    void update_floorBlank_rejected400() {
        when(canteenMapper.selectById(1L)).thenReturn(canteen(1L, "第一食堂"));

        StallSaveReq req = saveReq(1L, "面食窗口");
        req.setFloor("");

        assertRejected(() -> service().update(5L, req), 400, "楼层不能为空");

        verify(stallMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("update：floor 字典外 → 400「楼层不在预设范围内」，不写库")
    void update_floorOutsideDict_rejected400() {
        when(canteenMapper.selectById(1L)).thenReturn(canteen(1L, "第一食堂"));

        StallSaveReq req = saveReq(1L, "面食窗口");
        req.setFloor(FLOOR_OUTSIDE_DICT);

        assertRejected(() -> service().update(5L, req), 400, "楼层不在预设范围内");

        verify(stallMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("update：windowNo 空串 → 落空串（清空），与「缺省 = 保持原值」区分")
    void update_windowNoEmptyString_clears() {
        stubCanteenPresentAndNameFree();
        when(stallMapper.updateById(any())).thenReturn(1);

        StallSaveReq req = saveReq(1L, "面食窗口");
        req.setWindowNo("");

        service().update(5L, req);

        // 清空以空串落地：updateById 对 null 是「不写列」，无法表达清空
        assertThat(capturedPatch().getWindowNo()).isEmpty();
    }

    @Test
    @DisplayName("update：改名撞同食堂其它档口 → 400「该食堂下已存在同名档口」，不写库")
    void update_renameToExistingName_rejected400() {
        when(canteenMapper.selectById(1L)).thenReturn(canteen(1L, "第一食堂"));
        when(stallMapper.selectCount(any())).thenReturn(1L);

        assertRejected(() -> service().update(5L, saveReq(1L, "面食窗口")), 400, MSG_STALL_NAME_DUPLICATE);

        verify(stallMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("update：名称为空 → 400「档口名称不能为空」，不写库")
    void update_nameBlank_rejected400() {
        when(canteenMapper.selectById(1L)).thenReturn(canteen(1L, "第一食堂"));

        assertRejected(() -> service().update(5L, saveReq(1L, "   ")), 400, "档口名称不能为空");

        verify(stallMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("update：目标档口不存在（影响 0 行）→ 4001「档口不存在」")
    void update_missingStall_rejected4001() {
        stubCanteenPresentAndNameFree();
        when(stallMapper.updateById(any())).thenReturn(0);

        assertRejected(() -> service().update(5L, saveReq(1L, "面食窗口")), 4001, "档口不存在");
    }

    @Test
    @DisplayName("update：食堂缺失 / 不存在 → 400「请选择所属食堂」，不写库")
    void update_canteenUnresolved_rejected400() {
        assertRejected(() -> service().update(5L, saveReq(7L, "面食窗口")), 400, MSG_CANTEEN_REQUIRED);
        assertRejected(() -> service().update(5L, saveReq(null, "面食窗口")), 400, MSG_CANTEEN_REQUIRED);

        verify(stallMapper, never()).updateById(any());
    }

    /** 取本次 {@code updateById} 提交的局部实体（只带本次可编辑列）。 */
    private Stall capturedPatch() {
        ArgumentCaptor<Stall> captor = ArgumentCaptor.forClass(Stall.class);
        verify(stallMapper).updateById(captor.capture());
        return captor.getValue();
    }

    // ==================== upsertStallByName ====================

    @Test
    @DisplayName("upsertStallByName：同名档口已存在 → 复用原 ID，不重复建档、不迁移归属")
    void upsertStallByName_existingName_reusesId() {
        Stall existing = new Stall();
        existing.setId(3L);
        existing.setName("面食窗口");
        existing.setCanteenId(1L);
        when(stallMapper.selectOne(any())).thenReturn(existing);

        assertThat(service().upsertStallByName("面食窗口", "第二食堂")).isEqualTo(3L);

        verify(stallMapper, never()).insert(any());
        // 提交的食堂名只在新建档口时消费：既有档口不因本次提交而改归属
        verify(canteenMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("upsertStallByName：新档口 + 食堂名命中已有食堂 → 挂该食堂落库并返回新 ID")
    void upsertStallByName_newStall_createsUnderExistingCanteen() {
        when(stallMapper.selectOne(any())).thenReturn(null);
        when(canteenMapper.selectOne(any())).thenReturn(canteen(2L, "第二食堂"));
        stubInsertAutoId(8L);

        assertThat(service().upsertStallByName("新窗口", " 第二食堂 ")).isEqualTo(8L);

        ArgumentCaptor<Stall> saved = ArgumentCaptor.forClass(Stall.class);
        verify(stallMapper).insert(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("新窗口");
        assertThat(saved.getValue().getCanteenId()).isEqualTo(2L);
        verify(canteenMapper, never()).insert(any());
    }

    @Test
    @DisplayName("upsertStallByName：食堂名未建档 → 自动建档并挂新食堂（档口不允许无归属）")
    void upsertStallByName_canteenMissing_registersCanteen() {
        when(stallMapper.selectOne(any())).thenReturn(null);
        when(canteenMapper.selectOne(any())).thenReturn(null);
        when(canteenMapper.insert(any())).thenAnswer(invocation -> {
            Canteen entity = invocation.getArgument(0);
            entity.setId(4L);
            return 1;
        });
        stubInsertAutoId(8L);

        assertThat(service().upsertStallByName("新窗口", "第三食堂")).isEqualTo(8L);

        ArgumentCaptor<Stall> saved = ArgumentCaptor.forClass(Stall.class);
        verify(stallMapper).insert(saved.capture());
        assertThat(saved.getValue().getCanteenId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("upsertStallByName：空值语义食堂名（null / 空白 /「其他」）→ 400「请选择所属食堂」，不落库")
    void upsertStallByName_emptySemanticCanteenName_rejected400() {
        when(stallMapper.selectOne(any())).thenReturn(null);

        assertRejected(() -> service().upsertStallByName("新窗口", null), 400, MSG_CANTEEN_REQUIRED);
        assertRejected(() -> service().upsertStallByName("新窗口", "   "), 400, MSG_CANTEEN_REQUIRED);
        assertRejected(() -> service().upsertStallByName("新窗口", "其他"), 400, MSG_CANTEEN_REQUIRED);

        verify(stallMapper, never()).insert(any());
        verify(canteenMapper, never()).insert(any());
    }

    @Test
    @DisplayName("createStall / update：名称超 64 字 → 400「档口名称不能超过 64 字」，不写库")
    void nameTooLong_rejected400() {
        when(canteenMapper.selectById(1L)).thenReturn(canteen(1L, "第一食堂"));
        when(stallMapper.selectCount(any())).thenReturn(0L);

        assertRejected(() -> service().createStall(saveReq(1L, "名".repeat(65))), 400, "档口名称不能超过 64 字");
        assertRejected(() -> service().update(5L, saveReq(1L, "名".repeat(65))), 400, "档口名称不能超过 64 字");

        verify(stallMapper, never()).insert(any());
        verify(stallMapper, never()).updateById(any());
    }

    // ==================== 列表出参 ====================

    @Test
    @DisplayName("listAllForAdmin：档口自身字段逐项映射；食堂名批量取回、取不到落 null；均分未补齐按 0.00 兜底")
    void listAllForAdmin_mapsOwnFieldsAndCanteenNames() {
        Stall first = stall(1L, 1L, "面食窗口");
        Stall second = stall(2L, 2L, "清真窗口");
        when(stallMapper.selectList(any())).thenReturn(List.of(first, second));
        // 食堂 2 已不存在 ⇒ 不出现在映射中
        when(canteenMapper.selectBatchIds(any())).thenReturn(List.of(canteen(1L, "第一食堂")));

        List<StallAdminVO> list = service().listAllForAdmin();

        assertThat(list).hasSize(2);
        StallAdminVO vo = list.get(0);
        assertThat(vo.getId()).isEqualTo(1L);
        assertThat(vo.getCanteenId()).isEqualTo(1L);
        assertThat(vo.getCanteenName()).isEqualTo("第一食堂");
        assertThat(vo.getName()).isEqualTo("面食窗口");
        // 可空字符串列恒非空串（null → ""），端上无需判空
        assertThat(vo.getLocation()).isEmpty();
        assertThat(vo.getFloor()).isEmpty();
        assertThat(vo.getWindowNo()).isEmpty();
        assertThat(vo.getDescription()).isEmpty();
        // 均分由调用方批量补齐；未补齐时按 0.00（2 位小数）兜底
        assertThat(vo.getAvgRating()).isEqualByComparingTo("0.00");
        assertThat(vo.getAvgRating().scale()).isEqualTo(2);
        // 食堂查不到 ⇒ canteenName 为 null（与逐档口回查同口径）
        assertThat(list.get(1).getCanteenName()).isNull();
        assertThat(list.get(1).getWindowNo()).isEmpty();
        // dishCount 由 controller 编排补齐，服务层不产出
        assertThat(vo.getDishCount()).isNull();
    }

    @Test
    @DisplayName("listAllForAdmin：无档口 → 空列表且不回查食堂（提前返回）")
    void listAllForAdmin_empty_skipsCanteenLookup() {
        when(stallMapper.selectList(any())).thenReturn(List.of());

        assertThat(service().listAllForAdmin()).isEmpty();

        verify(canteenMapper, never()).selectBatchIds(any());
    }

    // ==================== 定点写回楼层（纠错采纳） ====================

    @Test
    @DisplayName("updateFloor：档口 ID 缺失 / 楼层空白（含 null）→ 400「楼层不能为空」，不写库")
    void updateFloor_blankFloor_rejected400() {
        assertRejected(() -> service().updateFloor(null, "三层"), 400, "楼层不能为空");
        assertRejected(() -> service().updateFloor(7L, null), 400, "楼层不能为空");
        assertRejected(() -> service().updateFloor(7L, "   "), 400, "楼层不能为空");

        verify(stallMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("updateFloor：楼层字典外 → 400「楼层不在预设范围内」，不写库")
    void updateFloor_floorOutsideDict_rejected400() {
        assertRejected(() -> service().updateFloor(7L, FLOOR_OUTSIDE_DICT), 400, "楼层不在预设范围内");

        verify(stallMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("updateFloor：只写 id + floor 两列（局部实体，不整行覆盖）")
    void updateFloor_writesIdAndFloorOnly() {
        when(stallMapper.updateById(any())).thenReturn(1);

        service().updateFloor(7L, "  三层  ");

        Stall patch = capturedPatch();
        assertThat(patch.getId()).isEqualTo(7L);
        assertThat(patch.getFloor()).isEqualTo("三层");
        assertThat(patch.getName()).isNull();
        assertThat(patch.getCanteenId()).isNull();
        assertThat(patch.getWindowNo()).isNull();
    }

    @Test
    @DisplayName("updateFloor：影响 0 行 → 4001「档口不存在」（并发 / 脏 id 兜底）")
    void updateFloor_missingStall_rejected4001() {
        when(stallMapper.updateById(any())).thenReturn(0);

        assertRejected(() -> service().updateFloor(7L, "三层"), 4001, "档口不存在");
    }

    // ==================== 删除与只读查询 ====================

    @Test
    @DisplayName("deleteStall：ID 缺失 / 目标不存在 → 4001「档口不存在」，不删库（幂等语义）")
    void deleteStall_unresolvedTarget_rejected4001() {
        assertRejected(() -> service().deleteStall(null), 4001, "档口不存在");
        assertRejected(() -> service().deleteStall(9L), 4001, "档口不存在");

        // deleteById 在 MyBatis-Plus 中对 Serializable 与实体各有一个重载，须指名参数类型
        verify(stallMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("deleteStall：目标存在 → 按 ID 删除")
    void deleteStall_existing_deletes() {
        when(stallMapper.selectById(9L)).thenReturn(stall(9L, 1L, "面食窗口"));

        service().deleteStall(9L);

        verify(stallMapper).deleteById(9L);
    }

    @Test
    @DisplayName("existsById：null / 查不到 → false；命中 → true")
    void existsById_reportsPresence() {
        assertThat(service().existsById(null)).isFalse();
        assertThat(service().existsById(9L)).isFalse();

        when(stallMapper.selectById(9L)).thenReturn(stall(9L, 1L, "面食窗口"));
        assertThat(service().existsById(9L)).isTrue();
    }

    @Test
    @DisplayName("findIdByName：名称空白 → null 且不查库；命中 → ID；查不到 → null")
    void findIdByName_matchesByName() {
        assertThat(service().findIdByName(null)).isNull();
        assertThat(service().findIdByName("   ")).isNull();
        verify(stallMapper, never()).selectOne(any());

        when(stallMapper.selectOne(any())).thenReturn(stall(9L, 1L, "面食窗口"));
        assertThat(service().findIdByName("面食窗口")).isEqualTo(9L);

        when(stallMapper.selectOne(any())).thenReturn(null);
        assertThat(service().findIdByName("不存在窗口")).isNull();
    }

    @Test
    @DisplayName("getNameById / getFloorById：ID 为 null 不查库；查不到 → null；命中 → 对应列")
    void getNameAndFloorById_returnsColumnOrNull() {
        assertThat(service().getNameById(null)).isNull();
        assertThat(service().getFloorById(null)).isNull();
        verify(stallMapper, never()).selectById(any());

        assertThat(service().getNameById(9L)).isNull();
        assertThat(service().getFloorById(9L)).isNull();

        Stall existing = stall(9L, 1L, "面食窗口");
        existing.setFloor("三层");
        when(stallMapper.selectById(9L)).thenReturn(existing);
        assertThat(service().getNameById(9L)).isEqualTo("面食窗口");
        assertThat(service().getFloorById(9L)).isEqualTo("三层");
    }

    @Test
    @DisplayName("getCanteenNameByStallId：ID 为 null / 档口不存在 / 未挂食堂 / 食堂已删 → null；命中 → 食堂名")
    void getCanteenNameByStallId_resolvesOrNull() {
        assertThat(service().getCanteenNameByStallId(null)).isNull();

        assertThat(service().getCanteenNameByStallId(9L)).isNull();

        Stall withoutCanteen = stall(9L, null, "面食窗口");
        when(stallMapper.selectById(9L)).thenReturn(withoutCanteen);
        assertThat(service().getCanteenNameByStallId(9L)).isNull();
        verify(canteenMapper, never()).selectById(any());

        when(stallMapper.selectById(9L)).thenReturn(stall(9L, 3L, "面食窗口"));
        assertThat(service().getCanteenNameByStallId(9L)).isNull();

        when(canteenMapper.selectById(3L)).thenReturn(canteen(3L, "第三食堂"));
        assertThat(service().getCanteenNameByStallId(9L)).isEqualTo("第三食堂");
    }

    @Test
    @DisplayName("countWithoutDish：返回「其下无菜品的档口数」（子查询排除已挂菜品的档口）")
    void countWithoutDish_returnsCount() {
        when(stallMapper.selectCount(any())).thenReturn(3L);

        assertThat(service().countWithoutDish()).isEqualTo(3L);
    }

    @Test
    @DisplayName("listBriefCandidates：指定食堂名 → 只返回该食堂档口（出参只有 id + name）")
    void listBriefCandidates_byCanteenName() {
        when(canteenMapper.selectOne(any())).thenReturn(canteen(2L, "第二食堂"));
        when(stallMapper.selectList(any())).thenReturn(List.of(stall(8L, 2L, "面食窗口")));

        List<StallBriefVO> briefs = service().listBriefCandidates(" 第二食堂 ");

        assertThat(briefs).hasSize(1);
        assertThat(briefs.get(0).getId()).isEqualTo(8L);
        assertThat(briefs.get(0).getName()).isEqualTo("面食窗口");
    }

    @Test
    @DisplayName("listBriefCandidates：食堂名空白 → 全量档口；食堂名查不到 → 同样回落全量（不返回空）")
    void listBriefCandidates_fallsBackToAllStalls() {
        when(stallMapper.selectList(any())).thenReturn(List.of(
                stall(8L, 2L, "面食窗口"), stall(9L, 1L, "清真窗口")));

        // 空白食堂名不查食堂
        assertThat(service().listBriefCandidates("   ")).hasSize(2);
        verify(canteenMapper, never()).selectOne(any());

        // 食堂名给了但查不到 ⇒ 走全量分支（纠错时仍能给出候选）
        assertThat(service().listBriefCandidates("不存在的食堂")).hasSize(2);
    }

    /** 档口夹具（只关心出参映射涉及到的列）。 */
    private static Stall stall(Long id, Long canteenId, String name) {
        Stall stall = new Stall();
        stall.setId(id);
        stall.setCanteenId(canteenId);
        stall.setName(name);
        return stall;
    }
}
