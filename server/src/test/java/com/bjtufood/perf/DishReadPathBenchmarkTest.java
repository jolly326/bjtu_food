package com.bjtufood.perf;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.config.CacheConfig;
import com.bjtufood.common.utils.ImageUrlUtil;
import com.bjtufood.dish.constant.DishConst;
import com.bjtufood.dish.dto.DishAttributeEditVO;
import com.bjtufood.dish.dto.DishAttributeItem;
import com.bjtufood.dish.dto.DishDetailVO;
import com.bjtufood.dish.dto.DishListItemVO;
import com.bjtufood.dish.dto.DishQueryReq;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.entity.DishAttributeDimension;
import com.bjtufood.dish.mapper.DishAttributeDimensionMapper;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.dish.service.DishAttributeCatalog;
import com.bjtufood.dish.service.impl.DishServiceImpl;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/**
 * 菜品读路径热点基准：产出「优化前 / 优化后」可直接对比的性能数值。
 * <p>
 * <b>为什么是纯 Mockito 而不是压测</b>：本仓测试栈无数据库（MySQL 不可达、未引入 H2），
 * 端到端 QPS / 首屏时间在本机不可复现。本类因此只度量<b>可复现且不依赖 DB 的三项成本</b>：
 * <ol>
 *   <li><b>单请求 Mapper 调用次数</b>（= 落到 DB 的查询次数下界，按 Mockito 调用记录精确计数）；</li>
 *   <li><b>候选值聚合的计算成本</b>：{@code GET /dishes/{id}/attributes} 现按「全库在售菜品
 *       attributes 逐行 JSON 解析 + 频次统计」实现，耗时随菜品行数线性增长——用 5000 行
 *       合成数据把该成本量化，缓存化后同规模下热路径应接近 0；</li>
 *   <li><b>出参体积</b>（Jackson 序列化字节数，与线上同一 Jackson 口径：未配置
 *       {@code default-property-inclusion} ⇒ null 字段原样下发）。</li>
 * </ol>
 * <p>
 * 本类<b>不断言耗时</b>（避免机器负载导致构建抖动），只记录数值；正确性由业务单元测试保证。
 *
 * @see PerfMetrics 数值输出格式与落地位置
 */
@DisplayName("菜品读路径热点基准（查询次数 / 候选值聚合耗时 / 出参体积）")
class DishReadPathBenchmarkTest {

    /** 合成「全库在售菜品」行数：候选值聚合的线性成本据此量化（线上量级为百至千级，此处取偏保守值） */
    private static final int ON_SALE_ROWS = 5_000;

    /** 同一端点的重复调用次数：第 1 次为冷路径，其余为热路径（现无缓存 ⇒ 热 ≈ 冷） */
    private static final int CALLS = 5;

    /** 与线上口径一致的序列化器：未设 inclusion，null 字段照常下发 */
    private static final ObjectMapper AS_IS = new ObjectMapper();

    /** 对照口径：若改为 NON_NULL 包含策略，出参可缩减多少 */
    private static final ObjectMapper NON_NULL = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /** 图片 URL 合成样本（COS 绝对路径，长度与线上同量级） */
    private static final String COS_URL =
            "https://bjtu-food-1250000000.cos.ap-beijing.myqcloud.com/images/2026-09-20/1727000000-a1b2c3.jpg";

    /** 维度字典（4 维：与 dish_attribute_dimension 生产数据同构） */
    private static final List<DishAttributeDimension> DIMENSIONS = List.of(
            dimension(1L, "dietType", "饮食属性", "single", 1),
            dimension(2L, "spiceLevel", "辣度", "single", 2),
            dimension(3L, "flavorTags", "口味", "multi", 3),
            dimension(4L, "serveTemp", "出餐温度", "single", 4));

    private DishMapper dishMapper;
    private DishAttributeDimensionMapper dimensionMapper;
    private DishServiceImpl dishService;

    /** 纯 Mockito 无 Spring 上下文，MyBatis-Plus 的 lambda 缓存需显式初始化 */
    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), DishReadPathBenchmarkTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, Dish.class);
        TableInfoHelper.initTableInfo(assistant, DishAttributeDimension.class);
    }

    @BeforeEach
    void setUp() {
        dishMapper = mock(DishMapper.class);
        dimensionMapper = mock(DishAttributeDimensionMapper.class);
        StallService stallService = mock(StallService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        ImageUrlUtil imageUrlUtil = mock(ImageUrlUtil.class);
        // 图片绝对化不参与本次口径，原样透传即可
        when(imageUrlUtil.toAbsoluteUrls(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // 刻意用「未加代理」的 catalog：@Cacheable 靠 Spring 代理生效，纯 Mockito 下注解惰性无效，
        // 因此本类度量的始终是**无缓存基线**（缓存生效后的对照见 DishCacheBenchmarkTest）。
        DishAttributeCatalog catalog =
                new DishAttributeCatalog(dishMapper, dimensionMapper, CacheConfig.buildCacheManager());
        dishService = new DishServiceImpl(dishMapper, stallService, publisher, imageUrlUtil, catalog);
        when(dimensionMapper.selectList(any())).thenReturn(DIMENSIONS);
    }

    @Test
    @DisplayName("单请求 Mapper 调用次数：逐端点记录（缓存化后应显著下降）")
    void queryCountPerEndpoint() {
        // ---------- GET /dishes/{id} ----------
        when(dishMapper.selectDishDetail(anyLong())).thenReturn(sampleDetail());
        when(dishMapper.increaseViewCount(anyLong())).thenReturn(1);
        PerfMetrics.emit("server.mapper_calls.dish_detail", measureCalls(() -> dishService.getDishDetail(1L)), "次/请求",
                "现状=详情联表 + 浏览计数自增 + 维度字典");

        // ---------- GET /dishes/{id}/attributes ----------
        when(dishMapper.selectById(1L)).thenReturn(onSaleDish(sampleAttributesJson(0)));
        when(dishMapper.selectAttributesJsonOnSale(anyInt())).thenReturn(onSaleAttributesJson(ON_SALE_ROWS));
        PerfMetrics.emit("server.mapper_calls.dish_attributes_edit",
                measureCalls(() -> dishService.listDishAttributes(1L)), "次/请求",
                "现状=单次取行（存在性+在售态+属性）+ 维度字典 + 全库attributes扫描（rows=" + ON_SALE_ROWS + "）");

        // ---------- GET /dishes（列表分页） ----------
        when(dishMapper.selectDishPage(any(), any())).thenReturn(dishPage());
        DishQueryReq req = new DishQueryReq();
        req.setSeed("session-seed");
        PerfMetrics.emit("server.mapper_calls.dish_list", measureCalls(() -> dishService.listDishes(req)), "次/请求",
                "现状=单条分页联表查询");

        // ---------- GET /dishes/views（筛选视图字典） ----------
        when(dishMapper.selectInStockMealTypes()).thenReturn(List.of("staple", "dish"));
        PerfMetrics.emit("server.mapper_calls.dish_views", measureCalls(dishService::listDishViews), "次/请求",
                "现状=每次进入首页都查一次在售大类集合");

        // ---------- GET /dishes/guess-like ----------
        PerfMetrics.emit("server.mapper_calls.guess_like",
                measureCalls(() -> dishService.guessLike("session-seed")), "次/请求", "现状=每次进入发现态都查一次");
    }

    @Test
    @DisplayName("候选值聚合耗时：预热后测冷路径与热路径（无缓存时热 ≈ 冷）")
    void attributeCandidateLatency() {
        when(dishMapper.selectById(1L)).thenReturn(onSaleDish(sampleAttributesJson(0)));
        when(dishMapper.selectAttributesJsonOnSale(anyInt())).thenReturn(onSaleAttributesJson(ON_SALE_ROWS));
        // 预热：让 JIT 完成热点编译，避免把编译成本算进「冷路径」
        for (int i = 0; i < 3; i++) {
            dishService.listDishAttributes(1L);
        }

        long coldNanos = 0L;
        long warmNanos = 0L;
        for (int i = 0; i < CALLS; i++) {
            long start = System.nanoTime();
            List<DishAttributeEditVO> result = dishService.listDishAttributes(1L);
            long cost = System.nanoTime() - start;
            if (i == 0) {
                coldNanos = cost;
            } else {
                warmNanos += cost;
            }
            // 口径校验：确保度量的是真实聚合链路而非空转
            assertThat(result).hasSize(4);
            assertThat(result.get(2).getOptions()).isNotEmpty();
        }
        PerfMetrics.emit("server.dish_attributes_edit.latency_ms_cold", coldNanos / 1_000_000.0, "ms",
                "rows=" + ON_SALE_ROWS + " dims=" + DIMENSIONS.size() + "（预热后首次调用）");
        PerfMetrics.emit("server.dish_attributes_edit.latency_ms_warm",
                warmNanos / 1_000_000.0 / (CALLS - 1), "ms", "同参数重复调用均值（第 2~" + CALLS + " 次）");
    }

    @Test
    @DisplayName("出参体积：列表页 / 详情 / 属性编辑（当前口径 vs NON_NULL 对照）")
    void responsePayloadBytes() throws JsonProcessingException {
        List<DishListItemVO> page = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            page.add(sampleListItem(i));
        }
        PerfMetrics.emit("server.payload_bytes.dish_list_page", bytesOf(AS_IS, page), "B", "10 行/页（首页与搜索共用）");
        PerfMetrics.emit("server.payload_bytes.dish_list_page_non_null", bytesOf(NON_NULL, page), "B",
                "对照：null 字段不下发时的体积");

        DishDetailVO detail = sampleDetail();
        detail.setAttributes(List.of(
                new DishAttributeItem("dietType", "饮食属性", "荤菜"),
                new DishAttributeItem("spiceLevel", "辣度", "中辣"),
                new DishAttributeItem("flavorTags", "口味", List.of("咸鲜", "香辣"))));
        PerfMetrics.emit("server.payload_bytes.dish_detail", bytesOf(AS_IS, detail), "B", "公开 11 字段");
        PerfMetrics.emit("server.payload_bytes.dish_detail_non_null", bytesOf(NON_NULL, detail), "B",
                "对照：null 字段不下发时的体积");

        List<DishAttributeEditVO> edit = new ArrayList<>();
        for (DishAttributeDimension dim : DIMENSIONS) {
            edit.add(new DishAttributeEditVO(dim.getFieldKey(), dim.getValueType(), candidateOptions(20)));
        }
        PerfMetrics.emit("server.payload_bytes.dish_attributes_edit", bytesOf(AS_IS, edit), "B",
                "4 维 × 每维 20 个参考候选（编辑弹层一次拉全）");
    }


    // ==================== 计数与合成数据 ====================

    /**
     * 执行一次端点调用，返回期间发生的 Mapper 调用次数（增量口径）。
     * <p>
     * 不用 {@code mockingDetails().getInvocations().clear()}：Mockito 的调用集合由内部账本持有，
     * 对外返回的是快照视图，clear 不会回写账本 —— 那样只能拿到累积值。改用前后差值。
     */
    private long measureCalls(Runnable call) {
        long before = totalMapperCalls();
        call.run();
        return totalMapperCalls() - before;
    }

    private long totalMapperCalls() {
        return mockingDetails(dishMapper).getInvocations().size()
                + mockingDetails(dimensionMapper).getInvocations().size();
    }

    private static long bytesOf(ObjectMapper objectMapper, Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8).length;
    }

    private static DishAttributeDimension dimension(long id, String fieldKey, String name, String valueType, int order) {
        DishAttributeDimension dim = new DishAttributeDimension();
        dim.setId(id);
        dim.setFieldKey(fieldKey);
        dim.setName(name);
        dim.setValueType(valueType);
        dim.setOrder(order);
        return dim;
    }

    /** 全库在售菜品 attributes 合成：值随下标轮转，保证频次分布真实（排序与去重成本据此产生） */
    private static List<String> onSaleAttributesJson(int rows) {
        List<String> list = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            list.add(sampleAttributesJson(i));
        }
        return list;
    }

    private static String sampleAttributesJson(int index) {
        int v = index % 40;
        return "{\"dietType\":\"" + dietType(v) + "\",\"spiceLevel\":\"辣度" + v
                + "\",\"flavorTags\":[\"口味" + v + "\",\"口味" + ((v + 7) % 40) + "\"],\"serveTemp\":\"温度"
                + ((v + 3) % 40) + "\"}";
    }

    private static String dietType(int v) {
        String[] pool = {"荤菜", "素菜", "清真", "主食"};
        return pool[v % pool.length];
    }

    private static List<String> candidateOptions(int count) {
        List<String> options = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            options.add("口味" + i);
        }
        return options;
    }

    private static IPage<DishListItemVO> dishPage() {
        Page<DishListItemVO> page = new Page<>(1, 10);
        page.setRecords(List.of(sampleListItem(0), sampleListItem(1)));
        return page;
    }

    private static DishListItemVO sampleListItem(int index) {
        DishListItemVO vo = new DishListItemVO();
        vo.setId((long) (index + 1));
        vo.setName("牛肉拉面" + index);
        vo.setImageUrls(List.of(COS_URL));
        vo.setCoverImage(COS_URL);
        vo.setPrice(1200 + index);
        vo.setOriginalPrice(index % 2 == 0 ? 1500 + index : null);
        vo.setAvgRating(new BigDecimal("4.5"));
        vo.setCanteenName("第一食堂");
        vo.setStallName("面食窗口");
        return vo;
    }

    /** 在售菜品合成实体：`GET /dishes/{id}/attributes` 单次取行（存在性 + 在售态 + 属性）用 */
    private static Dish onSaleDish(String attributesJson) {
        Dish dish = new Dish();
        dish.setId(1L);
        dish.setStatus(DishConst.STATUS_ON);
        dish.setAttributes(attributesJson);
        return dish;
    }

    private static DishDetailVO sampleDetail() {
        DishDetailVO vo = new DishDetailVO();
        vo.setId(1L);
        vo.setName("牛肉拉面");
        vo.setPrice(1200);
        vo.setOriginalPrice(null);
        vo.setDescription("手工拉面配牛骨清汤，附香菜与蒜苗，可按口味加辣。");
        vo.setImages(List.of(COS_URL, COS_URL.replace("a1b2c3", "d4e5f6"), COS_URL.replace("a1b2c3", "g7h8i9")));
        vo.setStallName("面食窗口");
        vo.setCanteenName("第一食堂");
        vo.setFloor("1F");
        vo.setAvgRating(new BigDecimal("4.5"));
        vo.setAttributesJson("{\"dietType\":\"荤菜\",\"flavorTags\":[\"咸鲜\",\"香辣\"]}");
        return vo;
    }
}

