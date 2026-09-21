# Canteen Dictionary Specification

## Purpose

为学生端提供唯一的公开食堂字典端点（可选带档口树），并只暴露调用方实际使用的标识与名称字段，避免同一能力存在两个端点与一批零消费出参。

## Requirements

### Requirement: 唯一公开食堂字典端点

公开 API SHALL 仅提供一个食堂字典端点 `GET /canteens`。该端点 SHALL 接受可选参数 `include`，其唯一合法值为 `stalls`；不传或传非法值时按「不含档口树」处理。系统 SHALL NOT 再提供 `GET /canteens/all` 或其它并存的公开食堂端点。

#### Scenario: 端点唯一

- **WHEN** 检索公开路由中提供食堂字典的端点
- **THEN** 仅有 `GET /canteens`，`GET /canteens/all` 不存在（请求失败）

#### Scenario: 不含档口树

- **WHEN** 首页 / 搜索页请求 `GET /canteens`（不传 `include`）
- **THEN** 返回食堂字典行，不含档口树

#### Scenario: 含档口树

- **WHEN** 反馈页请求 `GET /canteens?include=stalls`
- **THEN** 返回食堂行且每行包含其下属档口列表（原 `GET /canteens/all` 的语义）

#### Scenario: 非法 include 值

- **WHEN** 请求 `include` 传 `stalls` 以外的值
- **THEN** 按不含档口树处理，不返回档口树

### Requirement: 食堂字典最小出参

不传 `include` 时，`GET /canteens` 的每行 SHALL 恰为 `id` / `name`。传 `include=stalls` 时，每行 SHALL 恰为 `id` / `name` / `stalls`，且 `stalls` 每项 SHALL 恰为 `id` / `name`。食堂层与档口层 SHALL NOT 返回 `location` / `description` / `images` / `floor` / `windowNo` / `avgRating` 等无消费方的字段。

#### Scenario: 字典形态出参

- **WHEN** 检查不含档口树响应的单行字段
- **THEN** 恰为 `id` / `name`

#### Scenario: 含档口树出参

- **WHEN** 检查 `include=stalls` 响应的单行字段与 `stalls` 子项字段
- **THEN** 食堂行恰为 `id` / `name` / `stalls`，档口项恰为 `id` / `name`

#### Scenario: 零消费字段不再出现

- **WHEN** 检查两种形态的全部返回字段
- **THEN** 不含 `location` / `description` / `images` / `floor` / `windowNo` / `avgRating`

### Requirement: 字典顺序稳定

食堂字典 SHALL 按食堂 `sort_order` 升序返回；含档口树时，同一食堂下的档口 SHALL 按档口 `sort_order` 升序返回。

#### Scenario: 顺序与字典序一致

- **WHEN** 请求食堂字典（含或不含档口树）
- **THEN** 食堂顺序与 `sort_order` 升序一致；含档口树时档口顺序亦与 `sort_order` 升序一致
