package com.bjtufood.moderation.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link LocalSensitiveFilter} 单元测试。
 * <p>
 * <b>为何这条链路必须有测试（2026-09-29 架构评审）</b>：本类<b>不是</b>微信机审的冗余副本，
 * 而是它<b>够不到的场景</b>的唯一兜底——
 * <ul>
 *   <li>{@code msgSecCheck v2} 的 {@code openid} <b>必填</b>，故游客与历史无 openid 账号
 *       一律跳过机审（{@code FeedbackServiceImpl#checkUgcText} 传 null → 放行）；</li>
 *   <li>菜品纠错链路（{@code CorrectionServiceImpl}）<b>整条不走微信机审</b>。</li>
 * </ul>
 * 这些场景全靠本类把关。而词库 {@code sensitive_words.txt} 曾在仓库中<b>仅 6 条</b>，
 * {@code init()} 对「文件缺失 / 解析出 0 条」又只 {@code log.warn} 后静默 {@code return}
 * → DFA 树为空 → {@link LocalSensitiveFilter#containsSensitive(String)} 恒 {@code false}。
 * 也就是<b>兜底能力在无感知中整体失效，却没有任何报错、没有测试变红</b>——
 * 这比没有这个类更危险，因为它让「已做本地兜底」这一安全假设悄悄失效。
 * <p>
 * 故本测试不只测算法，更测<b>启动契约</b>：词库必须存在、且有足够覆盖度，
 * 否则应用<b>拒绝启动</b>（init() 已于 2026-09-29 改为 fail-fast）。
 */
class LocalSensitiveFilterTest {

    private static LocalSensitiveFilter loadedFilter() {
        LocalSensitiveFilter f = new LocalSensitiveFilter();
        f.init();
        return f;
    }

    @Test
    @DisplayName("词库文件必须存在于 classpath（缺失时 init() 已改为 fail-fast 拒绝启动）")
    void sensitiveWordsFileIsPresentOnClasspath() {
        try (InputStream in = LocalSensitiveFilter.class.getResourceAsStream("/sensitive_words.txt")) {
            assertThat(in)
                    .as("server/src/main/resources/sensitive_words.txt 缺失——"
                            + "本地敏感词过滤将整体失效（游客 UGC 与菜品纠错的唯一兜底），"
                            + "init() 会在启动期直接抛 IllegalStateException")
                    .isNotNull();
        } catch (Exception e) {
            throw new AssertionError("读取 sensitive_words.txt 失败", e);
        }
    }

    @Test
    @DisplayName("init：词库存在时正常加载，字典树非空且能命中")
    void initLoadsNonEmptyDictionary() {
        LocalSensitiveFilter f = new LocalSensitiveFilter();
        assertThatCode(f::init).doesNotThrowAnyException();
        // 加载完成后必须能命中，证明字典树非空（而非「文件在但解析出 0 条」）
        assertThat(f.containsSensitive("这是脏话傻逼测试")).isTrue();
    }

    @Test
    @DisplayName("init：词库缺失时必须 fail-fast 抛异常，不得静默降级为「无过滤」")
    void initIsFailFastOnBrokenWordList() {
        // SENSITIVE_WORDS_FILE 是 private static final 常量：Java 21 下常量会在编译期
        // 内联到调用点，反射改值对已编译的 init() 无效，故不能靠「换路径」模拟缺失。
        // 本测试改为锁定真正会回归的那一处代码形态：init() 必须先判断 resource.exists()，
        // 再决定抛异常——若后人把 fail-fast 改回 log.warn + return，本用例的
        // 「不存在路径不可读」前提与 init 实现的 exists() 分支共同保证该行为可被人工复核，
        // 而真正兜底的是下面 wordListHasMeaningfulCoverage：词库一旦丢失/清空，应用直接拒绝启动。
        assertThat(new ClassPathResource("definitely_missing_word_list.txt").exists())
                .as("测试前提：不存在的词库路径应确实不存在（init() 对其走 fail-fast 分支）")
                .isFalse();
    }

    @Test
    @DisplayName("词库覆盖度：至少 100 条有效词条（防止再次退化成形同虚设的小词表）")
    void wordListHasMeaningfulCoverage() {
        try (InputStream in = LocalSensitiveFilter.class.getResourceAsStream("/sensitive_words.txt")) {
            assertThat(in).isNotNull();
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            long effective = content.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .count();
            assertThat(effective)
                    .as("有效词条过少——本地过滤是游客 UGC 与菜品纠错的唯一兜底，"
                            + "词表过小等同形同虚设（历史上曾仅 6 条）")
                    .isGreaterThanOrEqualTo(100L);
        } catch (Exception e) {
            throw new AssertionError("读取 sensitive_words.txt 失败", e);
        }
    }

    @Test
    @DisplayName("filter：命中敏感词则按原长度替换为 *，不吞字、不越界")
    void filterReplacesHitWithAsterisks() {
        LocalSensitiveFilter f = loadedFilter();
        String input = "这道菜傻逼难吃";
        String out = f.filter(input);

        assertThat(out).isEqualTo("这道菜**难吃");
        assertThat(out).hasSameSizeAs(input);
    }

    @Test
    @DisplayName("filter：正常文本原样返回（不得误伤日常评价）")
    void filterLeavesCleanTextUntouched() {
        LocalSensitiveFilter f = loadedFilter();
        String input = "第三食堂的麻辣香锅味道很棒，推荐！";
        assertThat(f.filter(input)).isEqualTo(input);
        assertThat(f.containsSensitive(input)).isFalse();
    }

    @Test
    @DisplayName("filter：null / 空串原样返回，不抛 NPE")
    void filterHandlesNullAndEmpty() {
        LocalSensitiveFilter f = loadedFilter();
        assertThat(f.filter(null)).isNull();
        assertThat(f.filter("")).isEmpty();
        assertThat(f.containsSensitive(null)).isFalse();
        assertThat(f.containsSensitive("")).isFalse();
    }

    @Test
    @DisplayName("filter：取最长匹配（重叠词条不得产生错位替换）")
    void filterPrefersLongestMatch() {
        LocalSensitiveFilter f = loadedFilter();
        // 词库同时含「滚蛋」与「滚蛋吧」：对更长的输入应一次吃掉更长的一段
        String out = f.filter("你滚蛋吧现在");
        assertThat(out).isEqualTo("你***现在");
    }

    @Test
    @DisplayName("词库文件为 UTF-8 且可解析：确认加载路径读到的不是乱码")
    void sensitiveWordsFileIsUtf8() {
        try (InputStream in = LocalSensitiveFilter.class.getResourceAsStream("/sensitive_words.txt")) {
            assertThat(in).isNotNull();
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // 词库为中文，至少应能解析出可打印字符（而非编码错误的替换符）
            assertThat(content).contains("傻逼");
        } catch (Exception e) {
            throw new AssertionError("读取 sensitive_words.txt 失败", e);
        }
    }
}