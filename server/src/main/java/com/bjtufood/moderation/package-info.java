/**
 * UGC 治理模块（package {@code com.bjtufood.moderation}）：内容审核 + 管理端处置状态机。
 * <p>
 * <b>职责一 · 内容审核</b>：微信 msgSecCheck v2 与 imgSecCheck 权威判定（<b>主防线</b>），
 * 加本地 DFA 敏感词兜底。
 * <p>
 * <b>两者的分工</b>：微信机审是主防线，已覆盖全部四条 UGC 写链路的文本
 * （评价 / 反馈 / 昵称 / <b>菜品问题反馈</b>，纠错于 补齐）与上传图片
 * （{@code imgSecCheck}，在 {@code /upload/cloud-image} 统一执行）。
 * 本地词库兜底的是微信<b>够不到</b>的场景：
 * <ol>
 *   <li><b>登录态缺失</b>（{@code userId=null}，如静默登录失败 / 非微信端 H5 联调）
 *       与<b>历史无 openid 账号</b>：{@code msgSecCheck v2} 的 {@code openid} 必填，
 *       服务内按既有口径（报告已备案）跳过机审放行 → 这部分 UGC 仅经本地词库；</li>
 *   <li><b>微信凭据未配置</b>（本地开发环境）：同样跳过机审。</li>
 * </ol>
 * <b>故本地词库不是冗余副本，而是微信链路的必要补丁</b>——其词库文件缺失/为空时
 * {@code init()} 会 fail-fast 拒绝启动，避免兜底在无感知中失效。
 * <p>
 * <b>职责二 · 管理端处置状态机</b>：{@code moderation_task} 承载三类用户提交
 * （意见反馈 / 评价举报 / 菜品问题反馈）的「处置状态 + 处置结论 + 处置文案 + 处置人」，
 * 提交时由工单属主域在<b>同一事务</b>内预建 {@code status=pending} 行，
 * 处置时更新该行并投递站内回执快照（{@code ModerationTaskService}）。
 * 看板待处置数 = 本表单表 {@code COUNT}，故三类工单的「待办」判据天然一致。
 * <p>
 * <b>依赖方向</b>：common、wechat（端点常量与凭据）、auth（处置人登录名投影）、notification（回执投递）
 * <p>
 * <b>对外契约</b>：ContentSecurityService（只管是否违规）、LocalSensitiveFilter（本地词库过滤）、
 * ModerationTaskService（处置状态机读写）、ModerationTaskFilters（按处置状态 / 处置文案筛选工单的条件片段）
 * <p>
 * <b>领域事件</b>：无
 * <p>
 * 模块边界由 {@code ArchTests}（ArchUnit）在测试阶段强制校验。
 */
package com.bjtufood.moderation;
