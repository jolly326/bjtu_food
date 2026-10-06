/**
 * MyBatis 持久化基础设施（package {@code com.bjtufood.common.persistence}）。
 * <p>
 * 按关注点分包：{@code config} 只留 Web/Spring 基础设施，本包只留 MyBatis 相关 ——
 * 两类设施混装时，读 {@code config} 需逐个判断某个 Config 到底是 Web 还是 DB。
 * <p>
 * 无状态、无业务语义，故仍属 {@code common}（不违反「common 零业务依赖」不变式）。
 */
package com.bjtufood.common.persistence;
