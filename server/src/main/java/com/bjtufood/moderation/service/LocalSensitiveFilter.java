package com.bjtufood.moderation.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 本地 DFA 敏感词过滤器（UGC 内容审核链路的<b>第一级：本地词库兜底</b>）。
 * <p>
 * 功能：对 UGC 文本（评价 / 反馈 / 昵称等）做敏感词检测与替换（命中即替换为 {@code *}）。
 * 采用 DFA（Deterministic Finite Automaton，确定性有限自动机）算法，效率高、性能稳定；
 * 词库在启动时（{@code @PostConstruct}）加载进字典树，运行时零 IO。
 * <p>
 * <b>与远端审核的分工</b>：
 * <p>
 * 远端微信机审（{@link ContentSecurityService}）是<b>主防线</b>，覆盖评价、反馈、昵称与上传图片；
 * 但它有<b>三个够不到的场景</b>，本类正是这些场景的兜底：
 * <ol>
 *   <li><b>登录态缺失</b>（微信静默登录失败 / 非微信端 H5 联调，{@code userId=null}）：
 *       {@code msgSecCheck v2} 的 {@code openid} 是<b>必填</b>，取不到即只能跳过机审放行
 *       ——这部分 UGC 仅经本类；</li>
 *   <li><b>历史学号账号</b>：openid 为 NULL，同上跳过机审；</li>
 *   <li><b>微信凭据未配置</b>（本地开发）：{@code ContentSecurityService} 跳过机审。</li>
 * </ol>
 * <p>
 * 补齐：<b>菜品纠错链路已于当日接入 {@code msgSecCheck v2}</b>
 * （{@code CorrectionServiceImpl#submit} 合并四类自由文本字段为单次调用送检），
 * 故纠错不再是本类独自兜底的链路。
 * <p>
 * 典型调用顺序见 {@code FeedbackServiceImpl.submit}：先 {@code localSensitiveFilter.filter(...)}
 * 落库前脱敏，再 {@code contentSecurityService.checkText(...)} 过权威审核。
 * <p>
 * <b>与微信服务的区别</b>：本类只做<b>词面精确匹配</b>（DFA 字典树），无网络往返、无调用额度限制，
 * 但查不出谐音/变体/语义违规；微信是语义级模型，覆盖面更广但需联网且 openid 必填。二者互补，不可互相替代。
 * <p>
 * 架构收口 P1-B：自 {@code common.utils.SensitiveFilter} 迁入 {@code moderation.service}，
 * 并更名为 {@code LocalSensitiveFilter}。两处改动的原因：
 * <ul>
 *   <li><b>位置</b>：本类与 {@link ContentSecurityService} 是同一条内容安全链路的两级，
 *       原先一个在 {@code common.utils}、一个在 {@code content.security}，
 *       调用方需同时依赖两处才能看清「同一条链路的两步」；现同归 {@code moderation} 域。</li>
 *   <li><b>命名</b>：原名 {@code Filter} 易与 Servlet 的 {@code Filter}（如
 *       {@code common.config.RequestLoggingFilter}）混淆，且未体现「本地 vs 远端」的分工；
 *       加 {@code Local} 前缀后，调用点 {@code localSensitiveFilter.filter(...)}
 *       与 {@code contentSecurityService.checkText(...)} 并列时语义自明。</li>
 * </ul>
 * 注：与原类不同，本类是 <b>有状态 Spring Bean</b>（持有 DFA 字典树），
 * 故置于 {@code service} 而非原 {@code utils} 包（该包惯例放无状态静态方法）。
 * <p>
 * 使用方式：
 * <pre>
 * // 在评价 / 反馈 Service 中调用（先脱敏落库，再送远端审核）
 * String filtered = localSensitiveFilter.filter(content);
 * if (localSensitiveFilter.containsSensitive(content)) {
 *     // 命中本地词库（已替换为 *），仍需经 contentSecurityService.checkText 过远端审核
 * }
 * </pre>
 */
@Slf4j
@Component
public class LocalSensitiveFilter {

    /** 敏感词库文件路径（classpath 下的文本文件，每行一个敏感词） */
    private static final String SENSITIVE_WORDS_FILE = "sensitive_words.txt";

    /** 替换字符 */
    private static final char REPLACE_CHAR = '*';

    /** DFA 字典树根节点 */
    private final TrieNode root = new TrieNode();

    /**
     * 初始化：项目启动时加载敏感词库到 DFA 字典树。
     *
     * <p><b>为何空词库要 fail-fast</b>：本类是微信机审的
     * <b>必要补丁</b>而非冗余——{@code msgSecCheck v2} 的 {@code openid} 必填，
     * 游客（{@code userId=null}）与历史无 openid 账号一律<b>跳过机审放行</b>；
     * 菜品纠错链路更是<b>完全不走机审</b>。这些场景全靠本类兜底。
     * <p>
     * 而旧实现对「词库文件缺失 / 解析出 0 条」只 {@code log.warn} 后静默 {@code return}，
     * 导致 DFA 树为空 → {@code containsSensitive} 对任意文本恒 {@code false}，
     * 即<b>上述兜底场景全部失守却无任何报错</b>——比没有这个类更危险：
     * 它让「已做本地兜底」这一安全假设在无感知中失效。
     * <p>
     * 故改为：词库为空直接抛异常终止启动（fail-fast），由
     * {@code LocalSensitiveFilterTest} 守住该前置条件。
     *
     * @throws IllegalStateException 词库文件不存在、为空或全部被注释掉时
     */
    @PostConstruct
    public void init() {
        ClassPathResource resource = new ClassPathResource(SENSITIVE_WORDS_FILE);
        if (!resource.exists()) {
            throw new IllegalStateException(
                    "敏感词库文件不存在: " + SENSITIVE_WORDS_FILE + "（须位于 src/main/resources/ 根下才会进 classpath）。"
                            + "本地过滤是游客 UGC 与菜品纠错链路的唯一兜底，缺失将使这些场景失去审核，不应启动。");
        }
        int count;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            count = 0;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();
                if (!word.isEmpty() && !word.startsWith("#")) {
                    addWord(word);
                    count++;
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("加载敏感词库失败: " + SENSITIVE_WORDS_FILE, e);
        }
        if (count == 0) {
            throw new IllegalStateException(
                    "敏感词库为空: " + SENSITIVE_WORDS_FILE
                            + "（解析出 0 个有效词条——是否整份被注释？）。"
                            + "本地过滤是游客 UGC 与菜品纠错链路的唯一兜底，空词库等同无兜底，不应启动。");
        }
        log.info("敏感词库加载完成，共 {} 个敏感词", count);
    }

    /**
     * 向 DFA 字典树中添加一个敏感词
     *
     * @param word 敏感词
     */
    private void addWord(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
        }
        node.isEnd = true;
    }

    /**
     * 检测文本中是否包含敏感词
     *
     * @param text 待检测文本
     * @return true=包含敏感词
     */
    public boolean containsSensitive(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        int n = text.length();
        for (int i = 0; i < n; i++) {
            TrieNode node = root;
            int j = i;
            while (j < n) {
                node = node.children.get(text.charAt(j));
                if (node == null) {
                    break;
                }
                if (node.isEnd) {
                    return true;
                }
                j++;
            }
        }
        return false;
    }

    /**
     * 过滤敏感词，将命中的敏感词（连续字符）替换为 *
     *
     * @param text 原始文本
     * @return 替换后的文本；null/空串原样返回
     */
    public String filter(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        int n = text.length();
        StringBuilder sb = new StringBuilder(n);
        int i = 0;
        while (i < n) {
            // 从当前位置尝试在字典树中匹配最长敏感词
            TrieNode node = root;
            int matchEnd = -1; // 命中的末尾索引（不含）
            int j = i;
            while (j < n) {
                node = node.children.get(text.charAt(j));
                if (node == null) {
                    break;
                }
                if (node.isEnd) {
                    matchEnd = j + 1;
                }
                j++;
            }
            if (matchEnd != -1) {
                // 命中：将 [i, matchEnd) 连续字符替换为 *
                for (int k = i; k < matchEnd; k++) {
                    sb.append(REPLACE_CHAR);
                }
                i = matchEnd;
            } else {
                sb.append(text.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }

    // ==================== DFA 字典树节点 ====================

    /**
     * DFA 字典树节点
     * <p>
     * children：子节点映射（字符 → 节点）
     * isEnd：是否为一个敏感词的结尾
     */
    private static class TrieNode {
        java.util.Map<Character, TrieNode> children = new java.util.HashMap<>();
        boolean isEnd = false;
    }
}
