/**
 * UGC 内容审核模块（package {@code com.bjtufood.moderation}）。
 * <p>
 * <b>职责</b>：微信 msgSecCheck v2 与 imgSecCheck 权威判定（<b>主防线</b>），
 * 加本地 DFA 敏感词兜底。
 * <p>
 * <b>两者的分工（2026-09-29 复核）</b>：微信机审覆盖评价、反馈、昵称与上传图片，
 * 但有<b>三个够不到的场景</b>，由 {@link com.bjtufood.moderation.service.LocalSensitiveFilter} 兜底：
 * <ol>
 *   <li><b>游客</b>（{@code userId=null}）与<b>历史无 openid 账号</b>：
 *       {@code msgSecCheck v2} 的 {@code openid} 必填，服务内按既有口径
 *       （报告已备案）跳过机审放行 → 这部分 UGC 仅经本地词库过滤；</li>
 *   <li><b>菜品纠错</b>：整条链路不走微信机审，仅本地词库把关菜品名称。</li>
 * </ol>
 * <b>故本地词库不是冗余副本，而是微信链路的必要补丁</b>——其词库文件缺失/为空时
 * {@code init()} 会 fail-fast 拒绝启动，避免兜底在无感知中失效。
 * <p>
 * <b>依赖方向</b>：common、wechat（端点常量与凭据）
 * <p>
 * <b>对外契约</b>：ContentSecurityService（只管是否违规）、LocalSensitiveFilter（本地词库过滤）
 * <p>
 * <b>领域事件</b>：无
 * <p>
 * 模块边界由 {@code ArchTests}（ArchUnit）在 {@code mvn test} 阶段强制校验：跨域只走
 * Service 契约或领域事件，禁止直连他域 Mapper / Entity / 实现类；域间依赖必须无环；
 * {@code common} 与 {@code wechat} 位于依赖图底部，不得反向依赖业务域。
 * 完整架构约定见 {@code docs/architecture.md}。
 */
package com.bjtufood.moderation;
