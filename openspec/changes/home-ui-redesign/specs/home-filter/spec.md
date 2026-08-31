## Purpose

重做首页筛选入口与首屏结构：以食堂（canteen）筛选替代冗余区块，让内容区可按食堂快速过滤。

## ADDED Requirements

### Requirement: 首页头部筛选按钮
首页头部 SHALL 显示「筛选」按钮（替代原头像框 `user-chip`），点击后打开食堂筛选下拉。

#### Scenario: 点击筛选按钮
- **WHEN** 用户点击首页头部「筛选」按钮
- **THEN** 食堂筛选下拉展开（若食堂列表尚未加载则展示加载态/「全部」）

### Requirement: 红色食堂筛选下拉
下拉面板 SHALL 使用红色背景（`--color-primary` 设计 token，与 header 同源），与 header 同一红色块、视觉衔接无间隙，容器内列出各食堂选项；点击某食堂即按该食堂过滤下方内容区；提供「全部」选项以清空筛选。

#### Scenario: 选择某食堂
- **WHEN** 用户在下拉中选择食堂「X」
- **THEN** 下拉关闭，下方内容区仅展示属于食堂 X 的菜品/内容

#### Scenario: 选择「全部」
- **WHEN** 用户在下拉中选择「全部」
- **THEN** 下拉关闭，下方内容区恢复展示全部菜品（不限食堂）

#### Scenario: 点击下拉外部区域
- **WHEN** 下拉展开时用户点击面板以外的遮罩区域
- **THEN** 下拉关闭且不改变当前筛选

### Requirement: 内容区按食堂过滤
首页内容区（瀑布流）SHALL 反映当前所选食堂；未选（默认「全部」）时展示全部菜品。

#### Scenario: 默认进入首页
- **WHEN** 用户首次进入首页且未主动筛选
- **THEN** 内容区展示全部菜品（不限食堂）

### Requirement: 移除冗余首页区块
首页 SHALL 不再渲染广播条（`BroadcastBar`）、最新活动/反馈菜品万能区（`UniversalGrid`）、以及横向滑动品类卡（`FilterBar`）。

#### Scenario: 首页首屏结构
- **WHEN** 用户进入首页
- **THEN** 首屏仅含头部（含筛选按钮）+ 食堂筛选下拉（收起）+ 内容区瀑布流，不出现广播条、万能区双卡、横滑品类卡

### Requirement: 无障碍与动效规范
下拉展开/收起 SHALL 支持点击外部关闭，并遵循 UI 红线（§4.9）：使用 `@tap`、颜色走 token（禁裸 hex）、支持 `prefers-reduced-motion` 降级（关闭位移/弹性，仅保留必要透明度过渡）。

#### Scenario: 减少动效偏好开启
- **WHEN** 系统开启 `prefers-reduced-motion`
- **THEN** 下拉展开/收起不使用位移或弹性过冲动画
