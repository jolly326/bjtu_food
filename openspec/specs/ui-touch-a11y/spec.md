# ui-touch-a11y Specification

## Purpose
补齐小程序可访问性短板：保证最小点击热区、为图标按钮提供语义标签，并复核暗色模式对比度，满足基础可用性基线。

## Requirements

### Requirement: 最小可点热区
所有可点元素（TabBar 项、筛选 pill、图标按钮、卡片内操作）SHALL 拥有 ≥ 44×44px 的逻辑可点区域（按 750rpx 设计宽换算为 ≥ 88×88rpx，不足时用 padding/透明扩展区补足）。

#### Scenario: 小图标热区达标
- **WHEN** 渲染关闭/筛选等纯图标按钮
- **THEN** 其可点区域不小于 44×44px（含透明 padding），不因图标本身 24~28px 而缩小热区

### Requirement: 图标按钮语义标签
纯图标可点元素（关闭、筛选、喜欢/点赞、排序、价格）MUST 携带 `aria-label` 描述其操作；文本按钮无需额外标签。

#### Scenario: 筛选图标可识别
- **WHEN** 价格筛选胶囊仅以图标 + 文字呈现
- **THEN** 其 `aria-label` 暴露「价格筛选」语义供辅助模式识别

### Requirement: 暗色对比度达标
在 dark 主题下，正文文字与图标相对于背景的对比度 SHALL 达到 WCAG AA（正文 4.5:1，大号图元/图标 3:1）；若现有 `--text-*` 在暗色下不达标 MUST 调整 token 取值。

#### Scenario: 暗色正文可读
- **WHEN** 用户切换至 dark 主题浏览信息流
- **THEN** 卡片标题/描述文字对比度 ≥ 4.5:1，无灰底灰字不可辨情况
