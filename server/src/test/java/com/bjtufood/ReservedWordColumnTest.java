package com.bjtufood;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实体列名「保留字转义」门禁（回归锁定）。
 * <p>
 * <b>为什么必须有本测试</b>：MyBatis-Plus 由实体字段名直接生成列名，<b>不自动转义 MySQL 保留字</b>。
 * 若实体保留 {@code private Integer order;} 这类字段，会生成 {@code SELECT ... order ... ORDER BY order} ⇒
 * 服务端 <b>SQL 语法错误（1064）→ 接口 500</b>，而单测 / 冒烟测试普遍 {@code @MockBean} 掉 Mapper，
 * <b>SQL 级错误在测试里完全测不出来</b>。
 * <p>
 * 判据：实体（带 {@code @TableName}）中字段名命中 MySQL 保留字的，必须通过
 * {@code @TableField("<反引号包裹的列名>")} 显式转义（或改用非保留字列名，如 {@code sort_order}）。
 */
class ReservedWordColumnTest {

    /**
     * 会破坏「未转义即直接拼进 SQL」的 MySQL 保留字（只收真保留字；
     * {@code status} / {@code value} / {@code type} 等非保留关键字不列 —— 未加反引号亦合法）。
     */
    private static final Set<String> RESERVED_WORDS = Set.of(
            "order", "key", "desc", "describe", "group", "rank", "system", "usage", "interval",
            "read", "condition", "index", "level", "option", "match", "left", "right", "like",
            "limit", "lines", "load", "lock", "logs", "long", "max", "min", "rows", "range",
            "add", "all", "and", "as", "by", "case", "check", "column", "create", "delete",
            "distinct", "drop", "from", "having", "in", "insert", "into", "is", "join", "not",
            "on", "or", "select", "set", "table", "then", "union", "update", "when", "where");

    @Test
    @DisplayName("实体中命中 MySQL 保留字的字段必须用 @TableField 加反引号转义")
    void reservedWordColumnsMustBeEscaped() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.bjtufood");
        List<String> violations = new ArrayList<>();
        int checked = 0;

        for (JavaClass clazz : classes) {
            if (!clazz.isAnnotatedWith(TableName.class)) {
                continue;
            }
            for (JavaField field : clazz.getFields()) {
                String name = field.getName().toLowerCase(Locale.ROOT);
                if (!RESERVED_WORDS.contains(name)) {
                    continue;
                }
                checked++;
                if (isEscapedColumn(field)) {
                    continue;
                }
                violations.add(clazz.getName() + "#" + field.getName());
            }
        }

        assertTrue(violations.isEmpty(),
                "以下实体字段名是 MySQL 保留字但未用 @TableField(\"`列名`\") 转义，"
                        + "会导致生成的 SQL 语法错误（接口 500）：" + violations
                        + "（已核查 " + checked + " 个保留字字段）");
    }

    /** 是否为「显式转义过的列」：{@code @TableField} 值含反引号，或声明为非表字段（{@code exist = false}） */
    private static boolean isEscapedColumn(JavaField field) {
        if (!field.isAnnotatedWith(TableField.class)) {
            return false;
        }
        TableField tableField = field.getAnnotationOfType(TableField.class);
        if (!tableField.exist()) {
            return true;
        }
        return tableField.value().contains("`");
    }
}
