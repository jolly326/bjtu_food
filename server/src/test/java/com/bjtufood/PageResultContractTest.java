package com.bjtufood;

import com.bjtufood.common.result.PageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分页契约一致性护栏（2026-09-29 架构收口 P1-3）。
 * <p>
 * <b>要防的具体事故</b>：分页壳已在「契约精简」中收敛为 {@code PageResult<T>{ records }},
 * 但文档、agent 规范与端上类型里<b>大量残留</b> {@code total / page / pageSize} 的旧描述。
 * 这类漂移的危害不是编译失败，而是<b>认知误导</b>——下一次协作照着文档去读
 * {@code data.total}，会写出一个永远为 {@code undefined} 的判断：
 * <pre>
 *   if (list.length &gt;= data.total) finished = true   // undefined 比较恒为 false
 * </pre>
 * 结果是<b>分页永不判到底</b>，表现为一直转圈加载更多。
 * <p>
 * <b>三条判据</b>：
 * <ol>
 *   <li><b>序列化（权威）</b>：真实 {@link ObjectMapper} 序列化 {@link PageResult}，
 *       断言顶层<b>有且仅有 {@code records} 一个键</b>。若有人「顺手把 total 加回来」立即变红；</li>
 *   <li><b>文档</b>：{@code docs/} 与 agent 规范中不得再出现四字段写法
 *       （<b>历史归档目录 {@code backup/} 与 {@code archive/} 除外</b>——
 *       归档按定义记录的是旧契约，不应回改）；</li>
 *   <li><b>端上类型</b>：client / web 不得声明 {@code page} / {@code pageSize}（后端从不回传）。</li>
 * </ol>
 * <p>
 * <b>为何不用 ArchUnit</b>：同 {@link ApiVersionPrefixTest}——本条约束的是
 * <b>跨仓库的文档与文本声明</b>，不进入字节码，ArchUnit 无从分析。
 */
class PageResultContractTest {

    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    /** 后端分页壳唯一字段 */
    private static final String ONLY_FIELD = "records";

    // ==================== ① 序列化判据（契约权威） ====================

    @Test
    @DisplayName("PageResult 序列化后顶层有且仅有 records 一个键")
    void pageResult_serializesToRecordsOnly() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        PageResult<String> page = PageResult.of(List.of("a", "b"));

        var node = mapper.readTree(mapper.writeValueAsString(page));

        assertThat(node.fieldNames()).toIterable().containsExactly(ONLY_FIELD);
        assertThat(node.get(ONLY_FIELD).isArray()).isTrue();
        assertThat(node.get(ONLY_FIELD).size()).isEqualTo(2);
    }

    @Test
    @DisplayName("PageResult 空列表仍须输出 records 键（端上 recordsOf 才能无判空直读）")
    void pageResult_alwaysEmitsRecordsKey() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        PageResult<String> page = PageResult.of(List.of());

        var node = mapper.readTree(mapper.writeValueAsString(page));

        assertThat(node.has(ONLY_FIELD)).isTrue();
    }

    // ==================== ② 文档判据 ====================

    /** 四字段旧写法（出现即视为陈旧契约描述） */
    private static final Pattern STALE_FOUR_FIELD = Pattern.compile(
            "PageResult\\s*<[^>]*>\\s*\\{[^}]*records[^}]*,[^}]*total",
            Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("docs/ 与 .codebuddy/ 不得再描述 PageResult{records,total,page,pageSize}")
    void docs_doNotDescribeStaleFourFieldContract() throws IOException {
        var violations = new ArrayList<String>();
        var files = new ArrayList<Path>();

        for (Path dir : List.of(ROOT.resolve("docs"), ROOT.resolve(".codebuddy"))) {
            if (!Files.isDirectory(dir)) continue;
            try (var stream = Files.walk(dir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".md"))
                        // 排除历史归档：`.codebuddy/backup/` 与 `docs/archive/` 是**当时快照**，
                        // 其内容按定义记录的是旧契约。修它等于伪造历史；且每新增一份归档
                        // 都会让本护栏无谓变红。规范只看现行文档。
                        .filter(p -> !p.toString().replace('\\', '/').contains("/backup/"))
                        .filter(p -> !p.toString().replace('\\', '/').contains("/archive/"))
                        .forEach(files::add);
            }
        }
        assertThat(files).as("应至少扫描到 docs/ 与 .codebuddy/ 下的若干 md 文档").isNotEmpty();

        for (Path file : files) {
            for (String line : readLines(file)) {
                if (STALE_FOUR_FIELD.matcher(line).find()) {
                    violations.add(ROOT.relativize(file) + " : " + line.strip());
                }
            }
        }

        assertThat(violations)
                .as("分页壳实际只有 records 一项（见 PageResult 的 @JsonPropertyOrder）。"
                        + "文档若再写四字段，后人照此写出的 data.total 恒为 undefined，分页永不判到底。")
                .isEmpty();
    }

    // ==================== ③ 端上类型判据 ====================

    @Test
    @DisplayName("client/web 分页类型不得声明 page / pageSize（后端从不回传）")
    void clientAndWebTypes_haveNoPageOrPageSize() throws IOException {
        List<Path> typeFiles = List.of(
                ROOT.resolve("client/src/api/shared.ts"),
                ROOT.resolve("web/src/api/adapter.ts"));

        var violations = new ArrayList<String>();

        for (Path file : typeFiles) {
            assertThat(Files.isRegularFile(file)).as("类型声明文件应存在: " + file).isTrue();
            for (String line : readLines(file)) {
                String stripped = line.strip();
                // 跳过注释行：说明文字里提到这些词是合理的（如「pageSize 由请求侧掌握」）
                if (stripped.startsWith("//") || stripped.startsWith("*") || stripped.startsWith("/*")) continue;
                boolean isTypeDecl = stripped.contains("interface ")
                        || stripped.contains("PageEnvelope")
                        || stripped.contains("PageResult");
                if (!isTypeDecl) continue;
                if (stripped.contains("pageSize") || stripped.contains("page?")) {
                    violations.add(ROOT.relativize(file) + " : " + stripped);
                }
            }
        }

        assertThat(violations)
                .as("page / pageSize 由请求侧掌握、服务端不回传，端上声明它们只会诱导写出恒 false 的判断")
                .isEmpty();
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
