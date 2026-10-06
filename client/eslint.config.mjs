import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import vueParser from 'vue-eslint-parser'
import tseslint from 'typescript-eslint'

/**
 * 学生端（uni-app / 微信小程序）ESLint flat 配置。
 *
 * <p>定位：补齐 `client` 缺失的**静态风格与死代码门禁**（与 `web` 侧对齐），纳入 `npm run verify`。
 * 取舍：只开 `js recommended + vue flat/essential + ts recommended` —— 不做类型感知规则
 * （类型正确性由 `vue-tsc` 承担），避免与既有工程约束重复。
 *
 * <p>解析器绑定：`.vue` 由 `vue-eslint-parser` 解析、其 `<script lang="ts">` 交 `tseslint.parser`
 * （必须显式声明 —— 否则 `.vue` 会被当作 TS 文本解析而整片报「Parsing error」）。
 */

/** uni-app / 小程序端全局对象（端 API 与 Vue 编译宏） */
const UNI_GLOBALS = {
  uni: 'readonly',
  wx: 'readonly',
  plus: 'readonly',
  getApp: 'readonly',
  getCurrentPages: 'readonly',
  UniApp: 'readonly',
  defineProps: 'readonly',
  defineEmits: 'readonly',
  defineExpose: 'readonly',
  defineOptions: 'readonly',
}

/** Node / 现代 JS 运行时全局（scripts/ 下的 .mjs 工具脚本） */
const NODE_GLOBALS = {
  console: 'readonly',
  process: 'readonly',
  URL: 'readonly',
  fetch: 'readonly',
  setTimeout: 'readonly',
  clearTimeout: 'readonly',
  setInterval: 'readonly',
  clearInterval: 'readonly',
}

export default [
  {
    ignores: [
      'dist/**',
      'node_modules/**',
      'src/types/generated/**',
      'src/theme/generated-colors.css',
    ],
  },
  {
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: { ...UNI_GLOBALS, ...NODE_GLOBALS },
    },
  },
  js.configs.recommended,
  ...pluginVue.configs['flat/essential'],
  ...tseslint.configs.recommended,
  {
    files: ['**/*.vue'],
    languageOptions: {
      parser: vueParser,
      parserOptions: {
        parser: tseslint.parser,
        ecmaVersion: 'latest',
        sourceType: 'module',
        extraFileExtensions: ['.vue'],
      },
    },
  },
  {
    files: ['**/*.ts'],
    languageOptions: {
      parser: tseslint.parser,
      parserOptions: { ecmaVersion: 'latest', sourceType: 'module' },
    },
  },
  {
    files: ['**/*.{ts,vue}'],
    rules: {
      /* 页面 / 组件文件名沿用 uni-app 目录约定（如 pages/mine/index.vue），不强制多词 */
      'vue/multi-word-component-names': 'off',
      /* TS 下 `no-undef` 与 tsc 重复，且不识别类型位（如 `(e: Event)`）→ 交 tsc 判定 */
      'no-undef': 'off',
      /* 小程序具名插槽**必须**写 `slot="name"` 属性（端上语法），非 Vue2 遗留写法 */
      'vue/no-deprecated-slot-attribute': 'off',
      /* 表单组件以对象 prop（`model`）承载表单模型、子组件直接改其字段；
         单向数据流收敛属独立重构项，先以 warn 保持可见（不阻断门禁）。 */
      'vue/no-mutating-props': 'warn',
      /* 未使用参数 / 变量以 `_` 前缀显式表示「有意忽略」 */
      'no-unused-vars': 'off',
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
    },
  },
]
