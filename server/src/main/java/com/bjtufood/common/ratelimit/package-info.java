/**
 * 基于 IP 的请求频控（package {@code com.bjtufood.common.ratelimit}）。
 * <p>
 * 进程内计数，用于「公开写入口」的防滥用兜底：登录发码、反馈提交、菜品问题反馈提交
 * （均为 permitAll 的匿名可写端点）。
 * <p>
 * 本包<b>不是基础设施</b> —— 它承载的是「某类端点该限几次」这条业务风控知识
 * （规则值由各 Controller 自定：反馈 2 次/分钟、10 次/小时等）；
 * 与 CORS/Swagger 等 Web 基础设施分处两包，避免业务风控规则看起来像框架配置。
 */
package com.bjtufood.common.ratelimit;
