package com.bjtufood;

import com.bjtufood.auth.config.AdminProperties;
import com.bjtufood.auth.config.JwtProperties;
import com.bjtufood.common.config.CorsProperties;
import com.bjtufood.common.config.UploadProperties;
import com.bjtufood.wechat.config.WechatProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 校园食堂信息系统 - 后端启动类
 * <p>
 * 技术栈：Spring Boot 3.2 + Java 21 + MyBatis-Plus + MySQL + JWT + SpringDoc OpenAPI (Swagger UI)
 * 模块说明：
 * - common：公共模块（配置、异常、工具类）
 * - auth：认证模块（登录注册、JWT校验、用户管理）
 * - canteen：食堂档口模块（食堂/档口 CRUD）
 * - dish：菜品模块（菜品展示、搜索、管理、统计）
 * - review：评价模块（评价提交、审核）
 * - list：清单模块（创建清单、分享）
 * - upload：文件上传模块（图片上传）
 * <p>
 * {@code @EnableConfigurationProperties}：启用类型化配置绑定。
 * 配置类随所属域走（{@code wechat.config.WechatProperties} / {@code auth.config.JwtProperties} /
 * {@code auth.config.AdminProperties}），不集中塞进 common——避免 common 反向依赖业务域。
 * <p>
 * 例外：{@code common.config.CorsProperties} / {@code common.config.UploadProperties} 就在 common——
 * 它们服务的 {@code CorsConfig} / {@code WebMvcConfig} / {@code JwtAuthFilter 的 Origin 校验}
 * 本身属公共基础设施，放 common 不构成反向依赖，反而是「配置与使用方同域」。
 * <p>
 * <b>为何显式列举而非 {@code @ConfigurationPropertiesScan}</b>：实测在本项目的切片测试下
 * （{@code SmokeApiTest} 用 {@code @ContextConfiguration} <b>取代</b>主配置来屏蔽全量扫描），
 * 扫描式注册不会生效，而被 {@code @Import} 进切片的 {@code JwtUtil} / {@code AdminTokenFilter}
 * 仍需这两个 Bean → 上下文加载失败（25 个用例全红）。显式列举则可由各测试上下文
 * 自行声明所需配置类，行为确定、不依赖扫描。
 * 未来新增配置类时，请在<b>此处与相关测试上下文</b>同步登记（这是显式注册的唯一代价）。
 */
@MapperScan("com.bjtufood.**.mapper")
@SpringBootApplication
@EnableConfigurationProperties({WechatProperties.class, JwtProperties.class, AdminProperties.class,
        CorsProperties.class, UploadProperties.class})
@EnableScheduling
public class BjtuFoodApplication {

    public static void main(String[] args) {
        SpringApplication.run(BjtuFoodApplication.class, args);
    }
}
