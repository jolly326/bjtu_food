# page-scroll-layout Specification

## Purpose
Defines the layout contract for main-package pages so the native scroll container correctly constrains its height inside a fixed `100vh` flex-column shell, preventing page-level overflow and ensuring all content is reachable.

## Requirements

### Requirement: Scroll container shrinks to available height
The page scroll container (`.scroll-wrap`) SHALL combine `flex:1` with `min-height:0` so that, within a `height:100vh` flex-column page shell, it resolves to the remaining height (viewport height minus header height) instead of the content height.

#### Scenario: Short content page
- **WHEN** a page's body content is shorter than the viewport
- **THEN** the scroll-view height equals the remaining height and no page-level overflow scroll appears

#### Scenario: Tall content page
- **WHEN** a page's body content (for example user card + feature grid + entry group + version footer) exceeds the remaining height
- **THEN** the scroll-view becomes the internal scroller and every part of the content is reachable by scrolling within it

### Requirement: Page shell clips overflow

The scroll-based page root elements (`.home-page` / `.dynamic-page`, plus any page that hosts a dedicated scroll-view) SHALL set `overflow:hidden` so the `100vh` shell clips its children and the WeChat page itself never becomes the scroll container. Static short-content pages (profile / about / notifications, etc.) SHALL follow the "no forced scroll-view when content fits" rule in the page-static requirement instead of this overflow-hidden rule.

#### Scenario: Content exceeds shell height
- **WHEN** the scroll-view content height exceeds the fixed shell of a scroll-based page
- **THEN** the overflow is clipped by the shell and scrolling occurs only inside the scroll-view, never at the page level

#### Scenario: Static page without scroll-view
- **WHEN** a static short-content page (e.g. profile) has no scroll-view and content fits
- **THEN** no page-level overflow scroll appears and content is fully visible without an inner scroll-view

### Requirement: 滚动壳仅 home 主列表页，静态页内容适配

滚动壳（`.scroll-wrap` `flex:1` + `min-height:0`、页面壳 `overflow:hidden`）SHALL 应用于 **home** 这一个承载长列表流的主根页。「我的」与其他静态短内容页（个人信息、通知、反馈等）SHALL NOT 在内容可完整放下时强制 `scroll-view`/`.scroll-wrap`；当其内容在超大字体 / 小屏下确会超高时 SHALL 保留可达性——通过仅在发生溢出时可用的内容滚动或页面级自然滚动承担，而非常驻滚动区。静态页 SHALL 为任何固定底栏保留安全区留白。

#### Scenario: Home keeps the scroll shell
- **WHEN** 实施完成后查看 home
- **THEN** 其 `.scroll-wrap` 具 `min-height:0`、页面壳 `overflow:hidden`，滚动仅发生在内容区内

#### Scenario: Static short-content page fits viewport
- **WHEN** 静态短内容页（我的/个人信息等）内容可完整放入一屏
- **THEN** 页面不渲染常驻 scroll-view，无可视滚动区且内容完整可见

#### Scenario: Tall profile/static content stays reachable
- **WHEN** 我的页（用户卡+功能格+底部信息）或静态页内容在超大字体/小屏下超过可视高度
- **THEN** 内容通过内部溢出滚动或页面级滚动保持完整可达，不出现常驻装饰性滚动区或裁剪

### Requirement: Profile feature grid uses flex layout
The profile page feature grid SHALL use a flex two-column layout instead of CSS `display:grid`, to avoid layout collapse on base library versions with incomplete grid support.

#### Scenario: Base library with partial grid support
- **WHEN** the page runs on a base library that supports `display:grid` incompletely
- **THEN** the two feature cards still render side by side via flex without collapsing or overlapping
