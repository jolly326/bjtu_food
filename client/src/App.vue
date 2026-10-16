<script setup lang="ts">
// 全局入口：只承担样式挂载，不放业务逻辑
</script>

<style lang="scss">
/* 主题（色值 + 非颜色 token）与全局基础样式 */
@use './styles/global.scss';
@import './theme/generated-colors.css';
@import './theme/design-tokens.css';

page {
  background: var(--bg-page);
}
</style>
