# home-filter-price Specification

## Purpose
Lets users narrow the home dish feed by a price range through the filter bar's price chip, without introducing new backend contracts.

## Requirements

### Requirement: 首页价格筛选
首页筛选条 SHALL 提供「价格 ▾」胶囊，点击弹出底部 Sheet，含预设 `不限 / 0–10 / 10–20 / 20元以上` 与自定义最低/最高输入框（确定/重置）。选中区间 SHALL 透传 `minPrice`/`maxPrice`（元，api 层 `yuanToFen` 转分）至 `searchDishesPage`；默认"全部"流经 `getHotDishesPage` 扩展可选价格参数亦可感。后端 `DishQuery`/`DishQueryReq` 已支持 `minPrice`/`maxPrice`，**本筛选不新增后端契约/接口**。品类筛选本次不接（UI 不暴露）；**且自 2026-09-15 起品类维度已整链删除（已删除：端上 / 后台均无品类——`category` 表、`dish.category_id`、`/admin/categories` 与品类页面 / 组件全部移除，`project_spec.md` §7.22 第 1 条），本项目不存在品类维度可接**。

#### Scenario: 选择价格区间
- **WHEN** 用户于价格 Sheet 选「10–20」并确认
- **THEN** 首页菜品仅保留单价 10–20 元者，计数同步更新为「共N道」

#### Scenario: 自定义价格
- **WHEN** 用户输入最低/最高并确认
- **THEN** 按自定义区间过滤，Sheet 内显示当前区间

#### Scenario: 价格筛选无新契约
- **WHEN** 价格筛选生效
- **THEN** 仅前端透传既有 `minPrice`/`maxPrice` 参数，无新接口/字段

#### Scenario: 价格胶囊选中态
- **WHEN** 已选定某价格区间
- **THEN** 「价格」胶囊文字/图标转 `--color-primary`，底仍 `--bg-soft`，提示筛选已生效
