/**
 * 跨域共享层模块（package {@code com.bjtufood.common}）。
 * <p>
 * <b>职责</b>：统一响应、异常、注解切片、通用工具与<strong>按关注点分层的</strong>基础设施
 * （{@code config} = Web/Spring 基础设施；{@code persistence} = MyBatis 持久化设施；
 * {@code ratelimit} = 基于 IP 的防滥用频控）。
 * <p>
 * <b>子包划分（按关注点）</b>：{@code config} = Web 基础设施
 * （Cors/Swagger/Jackson/Async/两个 Filter/WebMvc）；{@code persistence} = MyBatis 持久化设施
 * （MybatisPlusConfig、MybatisMetaObjectHandler、{@code StringListTypeHandler}）；
 * {@code ratelimit} = 基于 IP 的防滥用频控（{@code IpRateLimiter}）。
 * 判读 {@code config} 时无需逐个甄别某个 Config 属于 Web 还是 DB。
 * <p>
 * <b>依赖方向</b>：零业务域依赖（ArchTests 锁定）：业务常量与领域判定一律留在属主域
 * <p>
 * <b>对外契约</b>：Result、PageResult、BusinessException、GlobalExceptionHandler、RequireVerified 注解
 * <p>
 * <b>领域事件</b>：无
 * <p>
 * 模块边界由 {@code ArchTests}（ArchUnit）在测试阶段强制校验。
 */
package com.bjtufood.common;
