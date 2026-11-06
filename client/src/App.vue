<script lang="ts">
// 全局入口（主题状态由 hooks/useTheme 承载：页面根容器挂 theme-dark 即整页切深色）
export default {}
</script>

<style lang="scss">
/* 色值 token + 非颜色 token + 全局基础样式（顺序不可缺失，否则全站 var(--…) 失效） */
@use './styles/global.scss';
@use './styles/motion.scss';
@import './theme/generated-colors.css';
@import './theme/design-tokens.css';
</style>
