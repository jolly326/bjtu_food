package com.bjtufood;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 构建输入护栏：<b>部署必需的配置文件必须处于 git 跟踪状态</b>。
 * <p>
 * <b>要防的具体事故（2026-10-02 线上全站 401）</b>：
 * 提交 {@code 13102c76「chore: 移除入库的环境配置与文档」} 把
 * {@code server/src/main/resources/application.yml} 等 5 个文件移出了 git 跟踪，
 * 同时 {@code .gitignore} 里存在 {@code application.yml} / {@code application-*.yml} / {@code .env.*}
 * 这类无锚点规则把它们一并忽略。结果是：
 * <ul>
 *   <li><b>本地开发树完全正常</b>——文件仍在磁盘上，IDE 能读、单测能跑、全链路手测都通过；</li>
 *   <li><b>云构建完全看不到它们</b>——Docker 构建上下文来自 <b>GitHub 克隆</b>，不是本地工作树；</li>
 *   <li>⇒ jar 不含任何 Spring 配置 ⇒ 全部走默认值 ⇒ {@code server.servlet.context-path} 未设置
 *       ⇒ Tomcat 跑在根上下文 {@code /} ⇒ 小程序 {@code GET /api/v1/dishes} 的前缀不被剥离
 *       ⇒ 命中不到 {@code /dishes/**} 白名单 ⇒ {@code anyRequest().authenticated()}
 *       ⇒ <b>401「请先登录或重新登录」</b>（且因在 Security 层被拒，服务端一条业务日志都没有）。</li>
 * </ul>
 * <p>
 * <b>为何既有测试都测不出来</b>：{@code ApiVersionPrefixTest} 断言的是
 * {@code Files.isRegularFile(...)}——即「磁盘上存在」，而本故障恰恰是「磁盘上有、<b>git 里没有</b>」。
 * {@code SmokeApiTest} / {@code PublicPathWhitelistTest} 走的是 MockMvc 切片，
 * contextPath 由测试显式指定，与部署产物无关。故本类专门断言 <b>git 跟踪状态</b>。
 * <p>
 * <b>判据</b>：对每个部署必需文件执行 {@code git ls-files --error-unmatch}——
 * 该命令在「文件未被跟踪」时以非 0 退出，故可直接用作机器可判定的跟踪断言。
 * 若当前环境没有 git 或不在仓库内（例如某些 CI 镜像裁剪了 {@code .git}），本类跳过而非误报。
 * <p>
 * <b>安全性说明</b>：这些文件<b>不含任何真实凭据</b>，敏感值一律写成
 * {@code ${ENV_VAR:默认值}} 占位符（数据源口令 / JWT / SMTP / 微信 / COS 全部如此），
 * 真实值由云托管环境变量或被忽略的 {@code server/.env} 注入。入库的是「键名 + 占位符」。
 */
class BuildInputConfigTest {

    /** 仓库根（本测试以 server 模块为工作目录，故上一级为根） */
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    /**
     * 部署必需文件清单。
     * <p>
     * 三个 server 配置决定运行时形态（{@code context-path}、prod 的 actuator 暴露面、
     * prod 的 JWT fail-fast）；两个 client env 决定小程序端 {@code VITE_API_BASE_URL}，
     * 缺失时会静默回落到 {@code http://127.0.0.1:8080/api/v1}。
     */
    private static final List<String> REQUIRED_TRACKED_FILES = List.of(
            "server/src/main/resources/application.yml",
            "server/src/main/resources/application-dev.yml",
            "server/src/main/resources/application-prod.yml",
            "client/.env.development",
            "client/.env.production");

    @Test
    @DisplayName("部署必需配置文件必须被 git 跟踪（未跟踪 ⇒ 云构建克隆不到 ⇒ 线上退化为根上下文全站 401）")
    void buildRequiredConfigs_areTrackedByGit() throws Exception {
        assumeTrue(gitAvailable(), "当前环境无 git 或不在 git 仓库内，跳过跟踪状态断言");

        List<String> untracked = new ArrayList<>();
        for (String relative : REQUIRED_TRACKED_FILES) {
            if (!isTracked(relative)) {
                untracked.add(relative);
            }
        }

        assertThat(untracked)
                .as("""
                        以下部署必需文件未被 git 跟踪，云构建（从 GitHub 克隆，Dockerfile 只 COPY src/ 与 pom.xml）\
                        拿不到它们 ⇒ jar 不含该配置 ⇒ 运行时退化为 Spring 默认值。\
                        已知后果：application*.yml 缺失使 server.servlet.context-path 失效，\
                        Tomcat 跑在根上下文 /，小程序 GET /api/v1/dishes 前缀不被剥离，\
                        命中不到 /dishes/** 白名单而落入 anyRequest().authenticated() ⇒ 全站 401「请先登录或重新登录」。\
                        client/.env.* 缺失使 VITE_API_BASE_URL 静默回落到 127.0.0.1 本地默认值。\
                        修复：git add -f <文件>，并确认 .gitignore 末尾存在对应的 ! 反选规则。\
                        这些文件只含 ENV_VAR 占位符，不含真实凭据，可安全入库。""")
                .isEmpty();
    }

    @Test
    @DisplayName("context-path 必须写死在入库的 application.yml 里（它是端上 /api/v1 的唯一来源）")
    void contextPath_isDeclaredInTrackedApplicationYml() throws IOException {
        Path applicationYml = ROOT.resolve("server/src/main/resources/application.yml");
        assertThat(Files.isRegularFile(applicationYml))
                .as("application.yml 应存在于 %s", applicationYml)
                .isTrue();

        String content = Files.readString(applicationYml, StandardCharsets.UTF_8);
        assertThat(content)
                .as("""
                        application.yml 必须声明 server.servlet.context-path: /api/v1。\
                        它是小程序请求前缀与 SecurityConfig/AdminAuthFilter「应用内路径」口径的唯一共同基准：\
                        一旦缺失或被改写，端上全部路径与后端白名单会整体错位。""")
                .contains("context-path: /api/v1");
    }

    @Test
    @DisplayName(".gitignore 必须保留 application*.yml 与 client env 的反选规则（防再次误删入库）")
    void gitIgnoreKeepsNegationRules() throws IOException {
        Path gitignore = ROOT.resolve(".gitignore");
        assertThat(Files.isRegularFile(gitignore)).as(".gitignore 应存在于 %s", gitignore).isTrue();

        String content = Files.readString(gitignore, StandardCharsets.UTF_8);
        List<String> requiredRules = List.of(
                "!server/src/main/resources/application.yml",
                "!server/src/main/resources/application-dev.yml",
                "!server/src/main/resources/application-prod.yml",
                "!client/.env.development",
                "!client/.env.production");

        List<String> missing = requiredRules.stream()
                .filter(rule -> !content.contains(rule))
                .toList();

        assertThat(missing)
                .as("""
                        .gitignore 中存在无锚点规则 application.yml / application-*.yml / .env.*，\
                        它们会连带忽略上述必须入库的文件。本事故即由此产生（见类注释）。\
                        .gitignore 末尾必须以 ! 显式反选把它们捞回来，否则下一次 git add -A 又会静默丢文件。""")
                .isEmpty();
    }

    /** 执行 {@code git ls-files --error-unmatch}：返回 true 表示该文件已被 git 跟踪 */
    private boolean isTracked(String relativePath) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "git", "ls-files", "--error-unmatch", "--", relativePath)
                .directory(ROOT.toFile())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("git ls-files 超时：" + relativePath);
        }
        // 退出码 0 = 已跟踪；非 0 = 未跟踪（--error-unmatch 的语义）
        return process.exitValue() == 0;
    }

    /** 探测 git 是否可用且当前目录位于仓库内 */
    private boolean gitAvailable() {
        if (!new File(ROOT.toFile(), ".git").exists()) {
            return false;
        }
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "--is-inside-work-tree")
                    .directory(ROOT.toFile())
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}