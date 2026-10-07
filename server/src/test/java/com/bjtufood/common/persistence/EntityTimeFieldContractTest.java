package com.bjtufood.common.persistence;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「时间戳唯一来源 = DB 时钟」的<b>可执行护栏</b>（口径见
 * {@code docs/schema/README.md} 的〈时间戳写入来源〉）。
 *
 * <p><b>本类防的具体事故</b>：{@code created_at} / {@code updated_at} 由 MySQL 写入
 * （INSERT = {@code DEFAULT CURRENT_TIMESTAMP}，UPDATE = {@code ON UPDATE CURRENT_TIMESTAMP}），
 * 应用层不写、不填充（{@link MybatisMetaObjectHandler} 刻意空实现）。
 * 但只要某个实体的时间字段带上 {@code @TableField(fill = …)}，
 * MyBatis-Plus 就会把它<b>无条件</b>放进 UPDATE 的 SET 子句
 * （{@link TableFieldInfo#isWithUpdateFill()} 为真时跳过 NOT_NULL 判断）——
 * 空填充 ⇒ 显式 SET 一个 {@code null} ⇒ {@code Column 'updated_at' cannot be null}，
 * 且此时 DB 的 {@code ON UPDATE} 被显式赋值覆盖、失效。该失效态<b>编译、类型检查、接口冒烟都不报错</b>，
 * 只在真实写库时以 400「数据冲突」暴露，故在此以断言锁定。
 *
 * <p>两层断言：
 * <ol>
 *   <li><b>注解层</b>（源码真源）：全部带 {@code @TableName} 的实体，其任一 {@code @TableField}
 *       的 {@code fill} 必须是 {@link FieldFill#DEFAULT}；</li>
 *   <li><b>运行时层</b>（MyBatis-Plus 实际消费的元数据）：各实体的时间属性
 *       {@code isWithUpdateFill()} 必须为假，且其 SET 片段必须仍被
 *       {@code <if test="… != null">} 包裹 ⇒ null 时该列<b>不参与 SET</b>、由库时钟维护。</li>
 * </ol>
 *
 * <p>第 2 层按<b>属性名</b>（{@code createdAt} / {@code updatedAt}）定位字段：列名取决于
 * {@code mapUnderscoreToCamelCase}，而 {@code TableInfoHelper} 的缓存是 JVM 级共享的
 * （同一次 {@code mvn test} 里其它测试类可能先以默认配置初始化过），按列名断言会随执行顺序漂移。
 */
class EntityTimeFieldContractTest {

    /** 时间属性名（与 {@code created_at} / {@code updated_at} 驼峰对应） */
    private static final List<String> TIME_PROPERTIES = List.of("createdAt", "updatedAt");

    /** 生产源码中全部带 `@TableName` 的实体类（自动发现 ⇒ 新增实体同样被覆盖） */
    private static List<JavaClass> entityClasses() {
        JavaClasses imported = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bjtufood");
        List<JavaClass> entities = new ArrayList<>();
        for (JavaClass javaClass : imported) {
            if (javaClass.isAnnotatedWith(TableName.class)) {
                entities.add(javaClass);
            }
        }
        assertThat(entities).as("未发现任何 @TableName 实体（扫描范围失真）").isNotEmpty();
        return entities;
    }

    @Test
    @DisplayName("注解层：实体不得对时间字段声明 @TableField(fill = …)（否则 MP 会无条件 SET 该列）")
    void entityTimeFieldsCarryNoFillAnnotation() throws ClassNotFoundException {
        List<String> offenders = new ArrayList<>();
        for (JavaClass javaClass : entityClasses()) {
            Class<?> entity = Class.forName(javaClass.getName());
            for (Field field : entity.getDeclaredFields()) {
                TableField tableField = field.getAnnotation(TableField.class);
                if (tableField != null && tableField.fill() != FieldFill.DEFAULT) {
                    offenders.add(entity.getSimpleName() + "#" + field.getName() + " fill=" + tableField.fill());
                }
            }
        }
        assertThat(offenders)
                .as("时间戳唯一来源是 DB 时钟，实体时间字段不得带 fill 注解（见 docs/schema/README.md）")
                .isEmpty();
    }

    @Test
    @DisplayName("运行时层：时间属性不参与 UPDATE 的 SET（isWithUpdateFill=false 且仍被 <if> 包裹）")
    void entityTimeColumnsAreNotUnconditionallyWrittenOnUpdate() throws ClassNotFoundException {
        List<String> offenders = new ArrayList<>();
        List<String> checked = new ArrayList<>();
        for (JavaClass javaClass : entityClasses()) {
            Class<?> entity = Class.forName(javaClass.getName(), false,
                    EntityTimeFieldContractTest.class.getClassLoader());
            TableInfo tableInfo = tableInfoOf(entity);
            for (TableFieldInfo fieldInfo : tableInfo.getFieldList()) {
                if (!TIME_PROPERTIES.contains(fieldInfo.getProperty())) {
                    continue;
                }
                checked.add(tableInfo.getTableName() + "." + fieldInfo.getProperty());
                if (fieldInfo.isWithUpdateFill()) {
                    offenders.add(tableInfo.getTableName() + "." + fieldInfo.getProperty()
                            + " 带 fill ⇒ UPDATE 无条件 SET");
                    continue;
                }
                // SET 片段必须仍被 <if> 包裹（null 时整段不出现）——这正是 updateById(局部实体) 的安全前提
                if (!fieldInfo.getSqlSet("et.").startsWith("<if")) {
                    offenders.add(tableInfo.getTableName() + "." + fieldInfo.getProperty()
                            + " 的 SET 片段未做 null 判断");
                }
            }
        }
        assertThat(offenders).as("时间列不得被无条件写入").isEmpty();
        assertThat(checked).as("未覆盖到任何时间属性（属性名口径失真）").isNotEmpty();
        // 至少覆盖到「同时含 createdAt + updatedAt」与「仅 updatedAt」两类表
        assertThat(checked).contains("dish.createdAt", "canteen.updatedAt", "report_reason.updatedAt");
    }

    @Test
    @DisplayName("时间属性名契约：实体的时间属性只有 createdAt / updatedAt（列名经驼峰映射）")
    void entityTimePropertiesAreNamedConsistently() {
        List<String> offenders = new ArrayList<>();
        for (JavaClass javaClass : entityClasses()) {
            for (var javaField : javaClass.getFields()) {
                String name = javaField.getName();
                String lower = name.toLowerCase();
                if (!(lower.contains("created") || lower.contains("updated"))) {
                    continue;
                }
                if (!TIME_PROPERTIES.contains(name)) {
                    offenders.add(javaClass.getSimpleName() + "#" + name);
                }
            }
        }
        assertThat(offenders)
                .as("时间属性命名须与列名驼峰映射一一对应（createdAt / updatedAt）")
                .isEmpty();
    }

    /**
     * 取实体的 MyBatis-Plus 元数据（存在即复用；缺失时以显式配置初始化，避免依赖 Spring 启动）。
     */
    private static TableInfo tableInfoOf(Class<?> entity) {
        TableInfo existing = TableInfoHelper.getTableInfo(entity);
        if (existing != null && !existing.getFieldList().isEmpty()) {
            return existing;
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        TableInfo initialized = TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, EntityTimeFieldContractTest.class.getName()), entity);
        assertThat(initialized).as("%s 的表元数据初始化失败", entity.getSimpleName()).isNotNull();
        return initialized;
    }
}
