# Pagination Contract Specification

## Purpose

统一所有列表类端点的分页响应壳，使任意列表接口都能用同一路径读取数据，且不携带与主字段恒等的历史兼容副本。

## Requirements

### Requirement: 列表响应分页壳

所有返回列表的端点 SHALL 以 `records` / `total` / `page` / `pageSize` 作为响应壳：`records` 为当前页数据行、`total` 为符合条件的总条数、`page` 与 `pageSize` 为经服务端归一化后**实际生效**的页码与每页条数（`page` 小于 1 时回退为 1；`pageSize` 小于 1 时回退为默认值，超过服务端上限时截断为该上限）。响应 SHALL NOT 输出 `list` 或任何与 `records` 恒等派生的兼容字段。

#### Scenario: 响应壳字段唯一

- **WHEN** 检查任一分页端点（`GET /dishes`、`GET /dishes/{id}/reviews`、`GET /my/reviews`、`GET /my/notifications`、`/admin/*` 列表）的响应
- **THEN** 分页壳字段恰为 `records` / `total` / `page` / `pageSize`，不含 `list`

#### Scenario: 归一化后的实际生效值回执

- **WHEN** 请求的 `pageSize` 超过服务端上限，或 `page` 小于 1
- **THEN** 响应中的 `pageSize` / `page` 为归一化后的实际生效值，而非请求原值

#### Scenario: 消费端单一读取路径

- **WHEN** 前端（小程序 / 管理后台）读取任一分页端点返回的行数据
- **THEN** 仅从 `records` 取值，代码中不存在 `list` 兜底分支
