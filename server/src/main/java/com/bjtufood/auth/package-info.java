/**
 * 认证与账号模块（package {@code com.bjtufood.auth}）。
 * <p>
 * <b>职责</b>：微信静默登录、学号邮箱认证（UGC 写准入判据）、个人资料、账号注销、账号归属迁移。
 * <p>
 * <b>依赖方向</b>：common、wechat（平台登录与凭据）、notification/review/feedback（经事件）
 * <p>
 * <b>对外契约</b>：UserService（mapBriefByIds / getAuthContext / requireUgcAuthorized）、UserBriefVO、UserAuthContextVO
 * <p>
 * <b>领域事件</b>：UserOwnershipMigratedEvent（归属迁移）、UserAccountClosedEvent（账号注销）
 * <p>
 * 模块边界由 {@code ArchTests}（ArchUnit）在测试阶段强制校验，详见 {@code docs/architecture.md}。
 */
package com.bjtufood.auth;
