# Dish List Query Specification

## Purpose

规定公开菜品列表端点（首页瀑布流、食堂筛选、关键词搜索共用）可接受的查询参数集与唯一排序口径，避免同一端点按调用方分化出多种排序行为。

## Requirements

### Requirement: 公开菜品列表查询参数集

`GET /dishes` SHALL 接受分页参数 `page` / `pageSize` 与筛选参数 `keyword` / `canteenId` / `minPrice` / `maxPrice`（金额单位：分），并 SHALL NOT 接受任何排序类参数（`sortBy` / `sortOrder`）或档口参数（`stallId`）。`keyword` SHALL 同时匹配菜名、搜索别名、档口名与食堂名。端点 SHALL 仅返回上架（在售）菜品。**筛选维度的增补（如菜品大类 `mealType`）由后续 change 在其自身 capability 中引入（如 `dish-meal-category`），不在本 change 范围。**

#### Scenario: 不含排序与档口参数

- **WHEN** 检查 `GET /dishes` 接受的查询参数
- **THEN** 含分页参数与筛选参数（`page` / `pageSize` / `keyword` / `canteenId` / `minPrice` / `maxPrice`），且不含 `sortBy` / `sortOrder` / `stallId`

#### Scenario: 已删除参数不改变响应

- **WHEN** 请求携带 `stallId` 或 `sortBy` / `sortOrder`
- **THEN** 响应内容不受影响（既不做档口筛选，也不切换排序），与不带这些参数时一致

#### Scenario: 关键词四路匹配

- **WHEN** 以档口名或食堂名作为 `keyword` 请求列表
- **THEN** 返回该档口 / 食堂下的在售菜品

#### Scenario: 价格区间筛选

- **WHEN** 传入 `minPrice` / `maxPrice`（分）
- **THEN** 仅返回价格落在该闭区间内的在售菜品

### Requirement: 列表排序唯一口径为热度

`GET /dishes` SHALL 对任何请求一律按**热度倒序**返回，排序口径由服务端唯一决定；学生端 SHALL NOT 提供排序入口，亦 SHALL NOT 通过请求参数改变排序。列表排序 SHALL NOT 因请求未携带排序参数而切换为其它口径（如「评价数优先」）。

#### Scenario: 首页默认流

- **WHEN** 首页进入时请求第 1 页且不带任何筛选条件
- **THEN** 结果按热度倒序排列

#### Scenario: 筛选流与搜索流同为热度口径

- **WHEN** 请求仅携带 `canteenId`（食堂筛选）或 `keyword`（搜索）而不带排序参数
- **THEN** 结果仍按热度倒序排列，与首页默认流口径一致

#### Scenario: 端上无排序入口

- **WHEN** 检查首页与搜索页的界面与请求
- **THEN** 不存在排序控件，请求中不含任何排序参数
