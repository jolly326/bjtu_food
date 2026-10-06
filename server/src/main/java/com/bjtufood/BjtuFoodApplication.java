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

import java.util.Arrays;

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
 * 扫描式注册不会生效，而被 {@code @Import} 进切片的 {@code JwtUtil} / {@code AdminAuthFilter}
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

    /**
     * 启动失败兜底报告（2026-10-02 线上三次部署失败后加入）。
     *
     * <p><b>要解决的问题</b>：云托管部署失败时，平台「启动日志」区段会被截断
     * ——实测三次均停在 {@code Starting BjtuFoodApplication} 之后、{@code Started ...} 之前，
     * 只看得到 banner 而看不到真正的异常，**无法判断是配置缺失、Bean 装配失败还是资源不足**。
     * 现象统一为「就绪/存活探针 connection refused」（应用在绑定 8080 之前就退出或卡住）。
     *
     * <p><b>为什么直接写 stderr 而不用 logger</b>：
     * <ol>
     *   <li>启动失败常常发生在日志系统自身初始化完成之前，用 logger 可能静默无输出；</li>
     *   <li>容器平台对 <b>stderr</b> 的采集优先级与保真度高于 stdout，这是平台侧最可靠的通道；</li>
     *   <li>先打<b>单行根因</b>再打堆栈——即使平台截断，也大概率能保住根因那一行。</li>
     * </ol>
     *
     * <p><b>不吞异常</b>：仍以退出码 1 结束，保证容器编排能感知失败（探针与重启策略依赖它）。
     */
    public static void main(String[] args) {
        try {
            SpringApplication.run(BjtuFoodApplication.class, args);
        } catch (Throwable t) {
            Throwable root = rootCause(t);
            // 第一行：根因单行摘要（抗平台截断，排查时先看它）
            System.err.println();
            System.err.println("################ STARTUP FAILED ################");
            System.err.println("ROOT_CAUSE: " + root.getClass().getName() + ": " + root.getMessage());
            System.err.println("PROFILE   : " + Arrays.stream(args)
                    .filter(a -> a.startsWith("spring.profiles.active"))
                    .findFirst().orElse("(未在 args 指定，见 application.yml)"));
            System.err.println("############ FULL STACK TRACE ############");
            t.printStackTrace(System.err);
            System.err.println("#############################################");
            System.err.flush();
            System.exit(1);
        }
    }

    /**
     * 剥离包装异常取根因：Spring 的启动失败几乎总被 {@code BeanCreationException} /
     * {@code IllegalStateException} 层层包裹，真因（密钥缺失、连不上库）在最内层。
     * 平台日志常只看得到最外层，故必须下钻，否则等于什么都没报。
     */
    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        // 防御：循环引用或过深时停在 32 层，避免异常链异常本身再抛
        for (int i = 0; i < 32 && cur.getCause() != null && cur.getCause() != cur; i++) {
            cur = cur.getCause();
        }
        return cur;
    }
}
