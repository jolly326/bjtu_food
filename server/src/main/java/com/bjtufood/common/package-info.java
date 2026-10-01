/**
 * 跨域共享层模块（package {@code com.bjtufood.common}）。
 * <p>
 * <b>职责</b>：统一响应、异常、注解切片、通用工具与<strong>按关注点分层的</strong>基础设施
 * （{@code config} = Web/Spring 基础设施；{@code persistence} = MyBatis 持久化设施；
 * {@code ratelimit} = 基于 IP 的防滥用频控）。
 * <p>
 * <b>子包划分</b>：此前 {@code config} 混装了三类关注点——Web 基础设施
 * （Cors/Swagger/Jackson/Async/两个 Filter/WebMvc）、MyBatis 持久化设施（MybatisPlusConfig、
 * MybatisMetaObjectHandler）、以及承载业务风控知识的 {@code IpRateLimiter}；另有孤立的
 * {@code handler} 包只放一个 {@code StringListTypeHandler}。现已按关注点拆为三包，
 * 判读 {@code config} 时不再需要逐个甄别某个 Config 属于 Web 还是 DB。
 * <p>
 * <b>依赖方向</b>：零业务域依赖（ArchTests 锁定）：业务常量与领域判定一律留在属主域
 * <p>
 * <b>对外契约</b>：Result、PageResult、BusinessException、GlobalExceptionHandler、RequireVerified 注解
 * <p>
 * <b>领域事件</b>：无
 * <p>
 * 模块边界由 {@code ArchTests}（ArchUnit）在 {@code mvn test} 阶段强制校验：跨域只走
 * Service 契约或领域事件，禁止直连他域 Mapper / Entity / 实现类；域间依赖必须无环；
 * {@code common} 与 {@code wechat} 位于依赖图底部，不得反向依赖业务域。
 * 完整架构约定见 {@code docs/architecture.md}。
 */
package com.bjtufood.common;
