package com.bjtufood.moderation.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bjtufood.feedback.entity.Feedback;
import com.bjtufood.moderation.constant.ModerationConst;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ModerationTaskFilters} 的**生成 SQL** 断言。
 * <p>
 * <b>为什么必须单独测 SQL 文本</b>：管理端列表在单测里被打桩的 Mapper 承接，
 * 生成的条件片段根本不会下发到数据库 —— 一旦占位符写法（{@code {0}} / {@code {1}}）
 * 或括号 / OR 组合出错，<b>全部用例仍然全绿，线上却是一条 SQL 语法错误（1064 → 500）</b>。
 * 故此处直接对 {@code getTargetSql()} 断言：
 * <ol>
 *   <li>处置任务的 EXISTS 子查询真的出现在条件里，且工单表以<b>本域常量</b>限定（{@code feedback.id}）；</li>
 *   <li>占位符被替换为预编译参数（SQL 里出现 {@code ?}，且不含任何 {@code {n}} 残留）；</li>
 *   <li>关键词口径是「提交正文 <b>OR</b> 处置文案」——OR 必须落在<b>同一组括号内</b>，
 *       否则退化为「同时满足」而让搜索结果凭空变少。</li>
 * </ol>
 */
class ModerationTaskFiltersTest {

    /** 纯 Mockito 无 Spring 上下文，MyBatis-Plus 的 lambda 缓存需显式初始化 */
    @BeforeAll
    static void initMybatisLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(),
                ModerationTaskFiltersTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, Feedback.class);
    }

    @Test
    @DisplayName("byStatus：生成「处置任务 EXISTS + 状态条件」并完成占位符替换")
    void byStatus_generatesExistenceSubQuery() {
        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<>();

        ModerationTaskFilters.byStatus(wrapper, ModerationConst.TYPE_FEEDBACK, "feedback.id",
                ModerationConst.STATUS_PENDING);

        String sql = wrapper.getTargetSql();
        assertThat(sql)
                .contains("EXISTS (SELECT 1 FROM moderation_task t")
                .contains("t.type_id = feedback.id")
                .contains("t.status = ?")
                // 占位符必须已被替换（残留 {0} 会原样进 SQL → 语法错误）
                .doesNotContain("{0}")
                .doesNotContain("{1}");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(ModerationConst.TYPE_FEEDBACK, ModerationConst.STATUS_PENDING);
    }

    @Test
    @DisplayName("关键词口径：正文 LIKE 与处置文案 EXISTS 以 OR 组合在同一组括号内（不得退化为 AND）")
    void keywordCondition_isOredWithOwnColumn() {
        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<>();
        String keyword = "素食";

        wrapper.and(w -> w.like(Feedback::getContent, keyword)
                .or()
                .apply(ModerationTaskFilters.replyKeywordCondition("feedback.id"),
                        ModerationConst.TYPE_FEEDBACK, ModerationTaskFilters.likePattern(keyword)));

        String sql = wrapper.getTargetSql();
        assertThat(sql)
                .contains("(content LIKE ?")
                .contains("OR EXISTS (SELECT 1 FROM moderation_task t")
                .contains("t.reply_content LIKE ?")
                .doesNotContain("{0}")
                .doesNotContain("{1}");
        // 两个 LIKE 各绑一份模式参数（MyBatis-Plus 的 like 自带两侧通配，故与本类 likePattern 同形）
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(ModerationTaskFilters.likePattern(keyword), ModerationConst.TYPE_FEEDBACK);
    }

    @Test
    @DisplayName("LIKE 模式：两侧通配（前后缀匹配）")
    void likePattern_wrapsKeywordOnBothSides() {
        assertThat(ModerationTaskFilters.likePattern("素食")).isEqualTo("%素食%");
    }
}
