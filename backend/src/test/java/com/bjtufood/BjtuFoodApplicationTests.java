package com.bjtufood;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 后端启动测试
 * <p>
 * 验证 Spring Boot 应用能否成功启动。
 * 测试内容：
 * - 应用上下文加载是否正常
 * - Bean 依赖注入是否完整
 * - 所有配置项是否合法
 */
@SpringBootTest
class BjtuFoodApplicationTests {

    @Test
    void contextLoads() {
        // 测试应用上下文能否正常加载
        // 如果此测试通过，说明所有 Bean 的依赖注入正确
        // 常见的失败原因：
        // - Mapper 接口找不到（@MapperScan 配置缺失）
        // - 数据源配置错误
        // - Bean 循环依赖
    }
}
