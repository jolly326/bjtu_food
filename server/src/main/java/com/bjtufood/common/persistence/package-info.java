/**
 * MyBatis 持久化基础设施（package {@code com.bjtufood.common.persistence}）。
 * <p>
 * <b>2026-09-28 架构收口</b>：自 {@code common.config} / {@code common.handler} 迁入并合并。
 * 原先这三个类散落在「Web 配置」与一个孤立的 {@code handler} 包里，与 CORS/Swagger 等
 * Web 基础设施混装——<b>关注点不同却同处一个包</b>，读 {@code common.config} 时需逐个判断
 * 某个 Config 到底是 Web 还是 DB。
 * <p>
 * 归并后：{@code config} 只留 Web/Spring 基础设施，本包只留 MyBatis 相关。
 * 无状态、无业务语义，故仍属 {@code common}（不违反「common 零业务依赖」不变式）。
 */
package com.bjtufood.common.persistence;
