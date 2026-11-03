<script lang="ts">
// 全局入口（单主题，无启动期主题逻辑）
export default {}
</script>

<style lang="scss">
/* 色值 token + 非颜色 token + 全局基础样式（顺序不可缺失，否则全站 var(--…) 失效） */
@use './styles/global.scss';
@use './styles/motion.scss';
@import './theme/generated-colors.css';
@import './theme/design-tokens.css';
</style>
