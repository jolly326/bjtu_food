/**
 * 微信平台集成（叶子域）模块（package {@code com.bjtufood.wechat}）。
 * <p>
 * <b>职责</b>：jscode2Session 登录会话，stable_token 凭据获取、缓存与失效自愈。
 * <p>
 * <b>依赖方向</b>：仅 common；不得反向依赖任何业务域（ArchTests 锁定）
 * <p>
 * <b>对外契约</b>：WechatService、WechatAccessTokenProvider
 * <p>
 * <b>领域事件</b>：无
 * <p>
 * 模块边界由 {@code ArchTests}（ArchUnit）在测试阶段强制校验，详见 {@code docs/architecture.md}。
 */
package com.bjtufood.wechat;
