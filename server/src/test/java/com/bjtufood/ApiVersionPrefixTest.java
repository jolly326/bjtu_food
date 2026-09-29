package com.bjtufood;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 接口版本前缀一致性护栏（2026-09-29 架构收口 P1-2）。
 * <p>
 * <b>要防的具体事故</b>：后端 {@code application.yml} 的 {@code server.servlet.context-path}
 * 携带版本段 {@code /api/v1}，而 {@code client/} 与 {@code web/} 的 API base 曾经长期停留在
 * {@code .../api}（缺 {@code v1}）。这类不一致<b>不会编译失败、不会在服务端报错</b>，
 * 表现为端上「全站 404」，且因后端日志里根本收不到该请求而极难定位——
 * 2026-09-29 评审时实测 4 处默认值（两个 {@code config.ts} + 两个 env 文件）全部缺版本段。
 * <p>
 * <b>为何不用 ArchUnit</b>：ArchUnit 分析的是编译产物中的<b>类依赖</b>，
 * 而本条约束的是<b>跨仓库的文本配置</b>（yml 与前端 env），不进入字节码。
 * 故独立成 {@code ApiVersionPrefixTest}，与 {@link ArchTests} 职责不重叠。
 * <p>
 * <b>判据</b>：凡声明后端 API 基址的配置项（前端 {@code VITE_API_BASE_URL}、
 * 后端 {@code APP_PUBLIC_BASE_URL} 及其 yml 默认值），其值必须以
 * {@code /api/v1} 结尾——即与 {@code server.servlet.context-path} 的版本段严格一致。
 * 后端升 v2 时，本测试会因版本段不再匹配而变红，强制同步三端配置，
 * 而不是等到用户报「打不开」。
 * <p>
 * <b>维护说明</b>：{@link #CONTEXT_PATH} 刻意<b>不</b>去解析 {@code application.yml}
 * （避免为一条断言引入 YAML 解析依赖，且 yml 里有大量含 {@code ${...}} 的占位符），
 * 而是声明后端版本段真值。若改动 {@code context-path}，请同步更新此常量与三端配置。
 */
class ApiVersionPrefixTest {

    /** 后端接口版本段真值——与 {@code server/src/main/resources/application.yml} 的
     * {@code server.servlet.context-path: /api/v1} 保持一致。 */
    private static final String CONTEXT_PATH = "/api/v1";

    /** 后端 API 基址的配置项 key（各端命名统一为 VITE_API_BASE_URL，后端为 APP_PUBLIC_BASE_URL）。 */
    private static final Pattern BASE_URL =
            Pattern.compile("^(?:\\s*#\\s*)?(VITE_API_BASE_URL|APP_PUBLIC_BASE_URL)\\s*=\\s*(\\S+)\\s*$");

    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    @DisplayName("前端与后端的 API 基址必须携带 /api/v1 版本段（缺版本段 = 端上全站 404）")
    void apiBaseUrls_carryVersionSegment() {
        List<Path> configFiles = List.of(
                ROOT.resolve("client/.env.development"),
                ROOT.resolve("client/.env.production"),
                ROOT.resolve("client/.env.example"),
                ROOT.resolve("web/.env.example"),
                ROOT.resolve("server/.env.example"));

        var violations = new java.util.ArrayList<String>();
        int checked = 0;

        for (Path file : configFiles) {
            if (!Files.isRegularFile(file)) {
                violations.add(file + " : 文件不存在——端上会静默回落到 src/api/config.ts 的默认值");
                continue;
            }
            for (String line : readLines(file)) {
                Matcher m = BASE_URL.matcher(line);
                // 跳过示例注释行（# VITE_API_BASE_URL=...），只校验真正生效的赋值
                if (!m.matches() || line.trim().startsWith("#")) {
                    continue;
                }
                checked++;
                String value = m.group(2);
                if (!value.endsWith(CONTEXT_PATH)) {
                    violations.add(file + " : " + m.group(1) + "=" + value
                            + " 缺少版本段（应为 *" + CONTEXT_PATH + "）");
                }
            }
        }

        assertThat(violations)
                .as("以下配置的 API 基址与后端 context-path（%s）不一致，端上会全站 404", CONTEXT_PATH)
                .isEmpty();
        assertThat(checked).as("应至少校验到 4 个生效的 API 基址赋值").isGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("前端源码默认值（config.ts）必须携带 /api/v1 版本段")
    void frontendDefaultBaseUrls_carryVersionSegment() {
        List<Path> sources = List.of(
                ROOT.resolve("client/src/api/config.ts"),
                ROOT.resolve("web/src/api/config.ts"));

        for (Path source : sources) {
            String content = readString(source);
            Matcher m = Pattern.compile("DEFAULT_API_BASE_URL\\s*=\\s*'([^']+)'").matcher(content);
            assertThat(m.find()).as("%s 应定义 DEFAULT_API_BASE_URL", source).isTrue();
            assertThat(m.group(1))
                    .as("%s 的 DEFAULT_API_BASE_URL 缺少版本段（env 未注入时端上会全站 404）", source)
                    .endsWith(CONTEXT_PATH);
        }
    }

    @Test
    @DisplayName("小程序 callContainer 的 path 不得再硬编码 /api 前缀（须经 buildContainerPath 从 API_BASE_URL 推导）")
    void miniProgramPath_neverHardcodesApiPrefix() {
        Path http = ROOT.resolve("client/src/api/http.ts");
        String content = readString(http);

        // 回归形态：path: url.startsWith('/api') ? url : `/api${url}`
        // 该写法在小程序分支自行拼 /api，完全不读 API_BASE_URL；后端 context-path 升为 /api/v1 后
        // 小程序侧全部请求落到 /api/xxx → 云托管网关直接 404，且后端日志收不到该请求，极难定位。
        var hardcoded = Pattern.compile("path\\s*:\\s*url\\.startsWith\\(\\s*['\"]/?api").matcher(content);
        assertThat(hardcoded.find())
                .as("client/src/api/http.ts 的 callContainer 又出现了硬编码 /api 前缀："
                        + "小程序端将丢失版本段导致全站 404。须改用 buildContainerPath(url)。")
                .isFalse();

        // 正向：必须经由 config.ts 的推导函数取路径
        assertThat(content)
                .as("callContainer 的 path 应改用 buildContainerPath(url) 从 API_BASE_URL 推导")
                .contains("buildContainerPath(url)");

        Path config = ROOT.resolve("client/src/api/config.ts");
        assertThat(readString(config))
                .as("config.ts 应导出 buildContainerPath 供 http.ts 使用")
                .contains("export function buildContainerPath");
    }

    private static List<String> readLines(Path file) {
        return readString(file).lines().toList();
    }

    private static String readString(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取配置失败: " + file, e);
        }
    }
}