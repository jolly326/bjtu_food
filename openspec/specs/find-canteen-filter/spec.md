# Find Canteen Filter Specification

## Purpose

为 find（搜索）页在 header 正下方提供一行类首页的食堂维度筛选，复用首页 `FilterBar` 交互，按所选食堂过滤搜索/列表结果。

## Requirements

### Requirement: 筛选行位于 header 下方独立一行
find（搜索）页 SHALL 在 header 正下方以**独立一行**呈现食堂筛选入口；点击该入口展开的下拉面板与首页筛选栏（`FilterBar`）视觉与交互一致（红色块、含「全部」项、点击遮罩/关闭收起）。

#### Scenario: 进入 find 页可见筛选行
- **WHEN** 用户进入 find（搜索）页
- **THEN** header 正下方出现一行食堂筛选入口，不与 header 同块、不与搜索结果列表混排

#### Scenario: 点击展开与首页一致的面板
- **WHEN** 用户点击该筛选入口
- **THEN** 展开 `FilterBar` 式面板，列出食堂列表与「全部」，遮罩/关闭交互与首页一致

### Requirement: 按食堂过滤搜索结果
选中某食堂（含「全部」）后，find 页的搜索结果/列表 SHALL 按所选食堂过滤并实时刷新。

#### Scenario: 选中具体食堂
- **WHEN** 用户在面板中选择某个食堂并确认
- **THEN** find 页结果仅展示该食堂下的菜品/内容，面板收起且筛选行显示当前所选食堂

#### Scenario: 选择「全部」
- **WHEN** 用户选择「全部」
- **THEN** find 页恢复展示全部食堂结果

### Requirement: 与首页筛选状态隔离
find 页的食堂筛选状态须独立于首页（home）的食堂筛选状态，二者互不影响。

#### Scenario: 各自独立
- **WHEN** 用户在首页设置筛选食堂 A，再进入 find 页设置筛选食堂 B
- **THEN** 首页仍按 A 过滤、find 页按 B 过滤，互不串扰
