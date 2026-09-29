package com.bjtufood;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>前后端接口边界护栏</b>（2026-09-29 架构评审，方案 B：<b>安全区隔离</b>而非端点全分离）。
 * <p>
 * <b>业界惯例（本项目采用）</b>：一个后端 + 一套 API + <b>两种鉴权</b>，
 * 而非「给每个客户端复制一份端点」（GitHub / Stripe / 各类 SaaS 均如此）。
 * 真正的隔离在<b>安全区</b>：承载写操作与敏感数据的 {@code /admin/**} 由
 * {@code X-Admin-Token} 把关，与学生端 JWT 体系互不通。
 * <p>
 * <b>允许共享什么</b>：<b>非敏感的公开只读字典/枚举端点</b>（如 {@code GET /dishes/views}、
 * {@code GET /feedback/report-reasons}）——它们在 {@code SecurityConfig} 内是 {@code permitAll}，
 * 数据本身公开，两端复用不产生安全暴露，也避免为 web 复制一份冗余出口。
 * <p>
 * <b>护栏规则</b>：
 * <ol>
 *   <li><b>web 禁调需鉴权的学生端路径</b>：凡带「我的 / 写操作」语义（{@code /auth/**}、
 *       {@code /my/**}、{@code /reviews/**} 写、{@code /upload/cloud-image} 等）的路径，
 *       管理后台<b>不得</b>调用——那些端点要么要 JWT（web 无从携带），
 *       要么是用户私有数据（越权）。这是本护栏的核心价值；</li>
 *   <li><b>client 禁调 /admin/**</b>：管理端口令只在 web 侧配置，小程序持有也无从校验；</li>
 *   <li><b>web 调用的路径必须在后端真实存在</b>——防「前端调了一个不存在的端点却长期 404」
 *       （2026-09-29 实测：web 调 {@code GET /dishes/attributes}，而该端点当时<b>后端根本没有</b>，
 *       管理后台「描述四维录入选项」一直是空的就是这么来的）。</li>
 * </ol>
 * <p>
 * <b>为何不用 ArchUnit</b>：ArchUnit 分析编译产物中的类依赖，而本条约束的是
 * <b>跨仓库的 TS 源码文本</b>（前端 api 层调了哪些路径）与后端 {@code @GetMapping} 声明，
 * 不进入 Java 字节码。故独立成 {@code FrontendApiIsolationTest}。
 */
class FrontendApiIsolationTest {

    /** 仓库根（测试运行目录为 server/）。 */
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    /**
     * 路径字面量提取：覆盖<b>全部</b>发起请求的写法，不只是 {@code get|post|put|del} helper。
     * <p>
     * <b>为何要覆盖 fetch</b>：本项目存在<b>绕过 helper 直接发请求</b>的通道
     * （{@code web/src/api/upload.ts} 的 multipart 上传走独立 {@code fetch}，
     * 因 http.ts 只处理 JSON）。若护栏只扫 helper，该路径就成了<b>盲区</b>——
     * 它日后若改成学生端路径，规则 2（web 只许 /admin/**）将形同虚设。
     * <p>
     * 匹配两类：① {@code get|post|put|del(} 的首参；② {@code fetch(} 的 URL 表达式。
     * <p>
     * <b>刻意不匹配 {@code url: '/xxx'} 这类裸字段</b>：实测会误伤数据转换函数
     * （{@code adapter.ts#toAbsoluteImageUrl(url: string)}）与 Vue Router 路由表——
     * 它们不是接口调用。故只认「紧跟 {@code fetch(} 的 URL 表达式」与 helper 首参两种
     * <b>确证是发请求</b>的形态。
     * <p>
     * 只抓<b>字面量首参</b>——本项目所有 api 层调用都是
     * {@code get('/admin/dishes', params)}、{@code get(`/admin/dishes/${id}`)}、
     * {@code fetch(`${API_BASE_URL}/admin/upload/image`)} 形态，无变量拼接的路径，
     * 故该模式覆盖完整（由样本量兜底用例守住）。
     */
    private static final Pattern CALL = Pattern.compile(
            "(?:get|post|put|del)(?:<[^>]*>)?\\(\\s*['\"`]([^'\"`]+)['\"`]"
                    // fetch(`${API_BASE_URL}/admin/xxx`) —— 取 `${...}` 之后、以引号收尾的路径段
                    + "|fetch\\(\\s*`[^`$]*\\$\\{[^}]*\\}([^`]+)`");

    /** 归一化：把 {@code ${id}} / {@code {id}} 一律折成 {@code {p}}，使两端同构可比。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{[^}]*\\}|\\{[^}]*\\}");

    private static Map<String, List<String>> collect(String apiDir) {
        Path dir = ROOT.resolve(apiDir);
        assertThat(Files.isDirectory(dir)).as("应存在前端 api 目录: %s", dir).isTrue();
        Map<String, List<String>> found = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".ts")).toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                Matcher m = CALL.matcher(content);
                while (m.find()) {
                    // 两个分支中只有一个捕获组非空
                    String raw = m.group(1) != null ? m.group(1) : m.group(2);
                    if (raw == null || raw.isBlank() || !raw.startsWith("/")) {
                        continue;   // 非路径字面量（如 http 层内部的 method 形参）
                    }
                    String norm = PLACEHOLDER.matcher(raw).replaceAll("{p}");
                    found.computeIfAbsent(norm, k -> new ArrayList<>()).add(file.getFileName().toString());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("读取前端 api 目录失败: " + dir, e);
        }
        return found;
    }

    /**
     * <b>需鉴权的学生端路径前缀</b>——web 禁调这些。
     * <p>
     * 判据取自 {@code SecurityConfig}：这些路径<b>不在</b>白名单内，故需 JWT。
     * 管理后台只有 {@code X-Admin-Token}，调用它们必然 401；且多数是用户私有数据
     * （{@code /my/**} 我的通知 / 我的评价），调用即越权。
     */
    private static final List<String> AUTHENTICATED_STUDENT_PREFIXES = List.of(
            "/auth/",        // 学生账号体系（profile / wechat-login …）
            "/my/",          // 用户私有数据（我的评价 / 我的通知）
            "/upload/");     // 学生端上传（走 JWT）

    /** 学生端<b>公开只读</b>路径前缀（permitAll，非敏感字典/枚举）——web 允许复用。 */
    private static final List<String> PUBLIC_READONLY_PREFIXES = List.of(
            "/dishes/",      // 菜品浏览 + 字典（views / attributes）
            "/feedback/",    // 反馈提交（PUB）与举报原因字典（PUB）
            "/banners/",     // 轮播
            "/images/");     // 静态图片

    @Test
    @DisplayName("web 禁调需鉴权的学生端路径（web 无 JWT，且多为用户私有数据）")
    void web_doesNotCallAuthenticatedStudentPaths() {
        Map<String, List<String>> web = collect("web/src/api");

        List<String> violations = web.entrySet().stream()
                .filter(e -> AUTHENTICATED_STUDENT_PREFIXES.stream().anyMatch(e.getKey()::startsWith))
                .map(e -> e.getKey() + "  <- " + String.join(", ", e.getValue()))
                .toList();

        assertThat(violations)
                .as("web 只有 X-Admin-Token（无 JWT），调用需鉴权的学生端端点必然 401；"
                        + "且 /my/** 是用户私有数据，管理端调用属越权。管理端需求应走 /admin/** 专属端点")
                .isEmpty();
    }

    @Test
    @DisplayName("web 的非 admin 路径必须是公开只读端点（方案 B：允许复用公开字典）")
    void web_nonAdminPathsMustBePublicReadonly() {
        Map<String, List<String>> web = collect("web/src/api");

        List<String> violations = web.entrySet().stream()
                .filter(e -> !e.getKey().startsWith("/admin/"))
                .filter(e -> PUBLIC_READONLY_PREFIXES.stream().noneMatch(e.getKey()::startsWith))
                .map(e -> e.getKey() + "  <- " + String.join(", ", e.getValue()))
                .toList();

        assertThat(violations)
                .as("web 复用学生端端点时，只允许复用**非敏感的公开只读**端点；其余须改用 /admin/** 专属端点")
                .isEmpty();
    }

    @Test
    @DisplayName("client 不得调用 /admin/**（管理端口令只在 web 侧配置）")
    void client_callsNoAdminPaths() {
        Map<String, List<String>> client = collect("client/src/api");

        List<String> violations = client.entrySet().stream()
                .filter(e -> e.getKey().startsWith("/admin/"))
                .map(e -> e.getKey() + "  <- " + String.join(", ", e.getValue()))
                .toList();

        assertThat(violations)
                .as("client（小程序）调用了管理端路径——ADMIN_TOKEN 只配在 web 侧，小程序无从携带")
                .isEmpty();
    }
    @Test
    @DisplayName("web 调用的路径必须在后端真实存在（防「调了不存在的端点却长期 404」）")
    void web_calledPathsExistInBackend() {
        // 2026-09-29 实测事故：web 调 GET /dishes/attributes，而该端点后端**从未存在**
        // （只有 /dishes/{id}/attributes），管理后台「描述四维录入选项」长期 404 且无人察觉
        // ——因为 404 发生在网关层，后端日志里根本收不到该请求。
        Set<String> backend = collectBackendPaths();
        Map<String, List<String>> web = collect("web/src/api");

        List<String> missing = web.entrySet().stream()
                .filter(e -> !matchesAnyBackendPath(e.getKey(), backend))
                .map(e -> e.getKey() + "  <- " + String.join(", ", e.getValue()))
                .toList();

        assertThat(missing)
                .as("web 调用了后端不存在的路径（请求恒 404，且后端日志收不到、极难排查）")
                .isEmpty();
    }

    /** 收集后端 Controller 声明的路径（剥 context-path，路径变量归一为 {@code {p}}）。 */
    private static Set<String> collectBackendPaths() {
        Path ctrlRoot = ROOT.resolve("server/src/main/java/com/bjtufood");
        // 容忍三种写法：@GetMapping("/x")、@GetMapping(value = "/x")、@GetMapping（无参，如 @GetMapping）
        Pattern mapping = Pattern.compile(
                "@(?:Request|Get|Post|Put|Delete|Patch)Mapping"
                        + "(?:\\(\\s*(?:value\\s*=\\s*)?[\"'`]([^\"'`]*)[\"'`]\\s*[^)]*\\))?");
        Pattern classMapping = Pattern.compile("@RequestMapping\\(\\s*[\"'`]([^\"'`]+)[\"'`]\\s*\\)");

        Set<String> paths = new TreeSet<>();
        try (Stream<Path> files = Files.walk(ctrlRoot)) {
            for (Path file : files.filter(p -> p.toString().endsWith("Controller.java")).toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                String prefix = "";
                Matcher cm = classMapping.matcher(content);
                if (cm.find()) {
                    prefix = cm.group(1);
                }
                Matcher m = mapping.matcher(content);
                while (m.find()) {
                    String sub = m.group(1) == null ? "" : m.group(1);
                    String full = (prefix + sub).replaceAll("/+$", "");
                    if (full.isEmpty()) {
                        full = "/";
                    }
                    paths.add(PLACEHOLDER.matcher(full).replaceAll("{p}"));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("扫描后端 Controller 失败", e);
        }
        return paths;
    }

    /**
     * 前端路径是否被后端某条路径覆盖。
     * <p>
     * 两侧 {@code {p}} 先折叠再比：后端常量段（{@code /dishes/views}）匹配前端同名路径；
     * 后端参数化段（{@code /dishes/{p}}）以 Ant 风格覆盖前端 {@code /dishes/1}。
     */
    private static boolean matchesAnyBackendPath(String frontendPath, Set<String> backendPaths) {
        String flat = frontendPath.replaceAll("\\{p\\}", "");
        for (String backendPath : backendPaths) {
            String backendFlat = backendPath.replaceAll("\\{p\\}", "");
            if (backendFlat.equals(flat)) {
                return true;
            }
            if (backendPath.contains("{p}") && !backendFlat.isEmpty() && flat.startsWith(backendFlat + "/")) {
                return true;
            }
        }
        return false;
    }



    @Test
    @DisplayName("提取样本量兜底：路径提取正则本身没失效（防止空集合假绿）")
    void pathExtraction_coversBothFrontends() {
        // 若正则意外失配，两端都会得到空集合，上述规则会**假绿**通过。
        assertThat(collect("client/src/api")).as("client 路径提取").hasSizeGreaterThanOrEqualTo(15);
        // web 共 12 个 admin 路径 + 3 个公开只读字典路径（方案 B 允许复用）
        assertThat(collect("web/src/api")).as("web 路径提取").hasSizeGreaterThanOrEqualTo(12);
        assertThat(collectBackendPaths()).as("后端路径提取").hasSizeGreaterThanOrEqualTo(30);
    }

    @Test
    @DisplayName("web 的 multipart 上传走独立 fetch 通道，其路径必须同样受 /admin/** 约束")
    void webIndependentFetchChannelIsCovered() {
        // web/src/api/upload.ts 绕过 http.ts helper 直接 fetch（multipart 场景），
        // 若护栏未覆盖该写法，它日后改路径就无人把关。故显式锁定这条路径被纳入规则 2。
        Map<String, List<String>> web = collect("web/src/api");

        assertThat(web)
                .as("应提取到 web multipart 上传路径（独立 fetch 通道）")
                .containsKey("/admin/upload/image");
    }
}