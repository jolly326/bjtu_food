<script setup lang="ts">
// 全局入口：样式挂载 + 主题初始化（读取持久化的明暗选择）
import { onLaunch } from '@dcloudio/uni-app'
import { initTheme } from '@/theme'

onLaunch(initTheme)
</script>

<style lang="scss">
/* 主题（色值 + 非颜色 token）与全局基础样式 */
@use './styles/global.scss';
@import './theme/generated-colors.css';
@import './theme/design-tokens.css';
</style>
