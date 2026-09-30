package com.bjtufood;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAPI 契约产物同步护栏（2026-09-29，方案 A 的<b>服务端侧</b>护栏）。
 *
 * <p><b>要防的具体事故</b>：端上类型保障建立在
 * 「{@code client/src/types/generated/api.d.ts} 是当前 VO 的产物」这一前提上。
 * 后端改了 VO 却<b>没重新生成</b>时，端上 {@code npm run type-check} 依然全绿——
 * 它只对着旧产物检查。于是出现最危险的状态：<b>两边都绿、线上错</b>。
 * {@code ArchTests} 与其余护栏都进不了这个盲区（它们不读生成产物）。
 *
 * <p><b>四条判据</b>：
 * <ol>
 *   <li><b>产物存在性</b>：契约镜像与生成类型必须入库，否则新成员无任何类型保障；</li>
 *   <li><b>信封合法性</b>：须是 OpenAPI 3 文档，防止把网关 HTML 错误页当契约；</li>
 *   <li><b>schema 新鲜度</b>：契约须覆盖全部端点出参 VO，少一个即说明产物已陈旧；</li>
 *   <li><b>字段级抽样</b>：对核心 VO 逐字段比对，防「schema 名字还在、字段却被悄悄改名」。</li>
 * </ol>
 *
 * <p><b>为何不在此直接跑生成器</b>：那需要后端运行时上下文（本护栏是纯静态 JUnit），
 * 且生成产物是 <b>client 的资产</b>，刷新动作属前端侧流程（{@code gen:api:fresh}）。
 * 本类只<b>报告事实</b>：产物是否还跟得上 VO。
 */
class OpenApiContractSyncTest {

    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    /** 端上契约镜像 + 生成产物（两者都必须入库，见 .gitignore 说明） */
    private static final Path OPENAPI_JSON = ROOT.resolve("client/openapi.json");
    private static final Path GENERATED_DTS = ROOT.resolve("client/src/types/generated/api.d.ts");

    /** 端上 re-export 的契约类型清单（与 client/src/api/shared.ts 保持同步） */
    private static final List<String> ENDPOINT_VO = List.of(
            "BannerVO", "DishListItemVO", "DishDetailVO", "DishViewVO",
            "GuessLikeVO", "ReviewVO", "MyReviewVO", "NotificationVO",
            "UserInfoVO", "LoginVO");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ==================== ① 产物存在性 ====================

    @Test
    @DisplayName("端上契约产物（openapi.json / api.d.ts）必须存在且非空")
    void generatedArtifacts_existAndNotEmpty() throws IOException {
        for (Path file : List.of(OPENAPI_JSON, GENERATED_DTS)) {
            assertThat(Files.exists(file))
                    .as("契约产物缺失：%s（跑 `cd client && npm run gen:api:fresh` 重新生成）", file.getFileName())
                    .isTrue();
            assertThat(Files.size(file))
                    .as("契约产物为空：%s", file.getFileName())
                    .isGreaterThan(0L);
        }
    }

    // ==================== ② 信封合法性 ====================

    @Test
    @DisplayName("openapi.json 须是合法 OpenAPI 信封（防抓网关 HTML 错误页）")
    void openApiJson_isValidEnvelope() throws IOException {
        JsonNode root = readOpenApi();

        assertThat(root.path("openapi").asText())
                .as("openapi.json 不是 OpenAPI 3 文档（多半是 HTTP 抓到了网关错误页）")
                .startsWith("3.");
        assertThat(root.path("info").path("version").asText())
                .as("缺少 info.version，无法判断契约版本")
                .isNotBlank();
    }

    // ==================== ③ schema 新鲜度（核心） ====================

    @Test
    @DisplayName("端上契约须覆盖全部端点出参 VO（产物陈旧则本条变红）")
    void openApiCoversAllEndpointVo() throws IOException {
        var schemas = readOpenApi().path("components").path("schemas");
        assertThat(schemas.isObject())
                .as("openapi.json 缺少 components.schemas")
                .isTrue();

        var missing = new ArrayList<String>();
        for (String vo : ENDPOINT_VO) {
            if (!schemas.has(vo)) {
                missing.add(vo);
            }
        }

        assertThat(missing)
                .as("以下 VO 已不在契约中：可能后端已改名/删除 VO 而未重新生成端上类型。"
                        + "此时端上 type-check 仍会全绿（它只对着旧产物检查），"
                        + "但线上字段会静默变空。修复：启动后端后执行 `cd client && npm run gen:api:fresh`，"
                        + "并同步修正 client/src/api/shared.ts 的 re-export。")
                .isEmpty();
    }

    // ==================== ④ 字段级抽样（防「名字还在、字段被改」） ====================

    @Test
    @DisplayName("DishListItemVO 字段集与后端列表出参一致（字段改名必须同步生成）")
    void dishListItemVo_fieldsMatchContract() throws IOException {
        // 后端 DishListItemVO 的 8 字段（见 dish/view/DishListItemVO.java）
        assertSchemaFields("DishListItemVO",
                List.of("id", "name", "coverImage", "price",
                        "originalPrice", "avgRating", "canteenName", "stallName"));
    }

    @Test
    @DisplayName("UserInfoVO 字段集与后端账号出参一致（4 字段，禁止复活已删字段）")
    void userInfoVo_fieldsMatchContract() throws IOException {
        // 后端 UserInfoVO 的 4 字段：verified/email/status/username 等已按「零消费即删」移除
        assertSchemaFields("UserInfoVO", List.of("id", "nickname", "avatar", "bindEmail"));
    }

    @Test
    @DisplayName("分页壳 schema 只有 records（与 PageResultContractTest 互为印证）")
    void pageResultSchema_hasOnlyRecords() throws IOException {
        // ⚠️ 必须读 properties：schema 节点本身还有 type/description 等**元信息**键，
        // 直接取 fieldNames() 会把它们一并算进来（曾误判过一次）。
        var properties = readOpenApi()
                .path("components").path("schemas").path("PageResultDishListItemVO")
                .path("properties");
        assertThat(properties.isObject())
                .as("契约中应存在 PageResultDishListItemVO.properties")
                .isTrue();

        var fieldNames = new ArrayList<String>();
        properties.fieldNames().forEachRemaining(fieldNames::add);

        assertThat(fieldNames)
                .as("分页壳实际只有 records（后端 PageResult 只输出该项），契约里多出的字段会"
                        + "诱导端上写出恒为 undefined 的 total 判断")
                .containsExactly("records");
    }

    // ==================== helpers ====================

    private JsonNode readOpenApi() {
        try {
            return MAPPER.readTree(Files.readString(OPENAPI_JSON, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("读取 client/openapi.json 失败", e);
        }
    }

    private void assertSchemaFields(String schemaName, List<String> expected) {
        var schema = readOpenApi().path("components").path("schemas").path(schemaName);
        assertThat(schema.isObject()).as("契约中应存在 schema %s（请重新生成）", schemaName).isTrue();

        var actual = new ArrayList<String>();
        schema.path("properties").fieldNames().forEachRemaining(actual::add);

        assertThat(actual)
                .as("%s 的字段集与后端 VO 不一致。后端改了字段而未重新生成时，"
                        + "端上 type-check 不会报错（它只对着旧产物检查），直到真机才发现字段变空。"
                        + "修复：启动后端后执行 `cd client && npm run gen:api:fresh`", schemaName)
                .containsExactlyInAnyOrderElementsOf(expected);
    }
}
