module.exports = {
  root: true,
  env: {
    browser: true,
    node: true,
    es6: true
  },
  extends: [
    'eslint:recommended',
    'plugin:vue/recommended',
    '@vue/standard',
    '@vue/typescript'
  ],
  rules: {
    // 生产环境禁止 console / debugger 调试输出
    'no-console': ['warn', { allow: ['warn', 'error'] }],
    'no-debugger': process.env.NODE_ENV === 'production' ? 'error' : 'warn',
    // 历史代码统一为 foo() {} 风格，放开该规则
    'space-before-function-paren': 0,
    // 强制使用严格相等
    'eqeqeq': 'error',
    'vue/eqeqeq': 'error',
    // vue 模板属性允许换行书写
    'vue/max-attributes-per-line': 0
  },
  parserOptions: {
    parser: '@typescript-eslint/parser'
  },
  overrides: [
    {
      files: [
        '**/__tests__/*.{j,t}s?(x)',
        '**/tests/unit/**/*.spec.{j,t}s?(x)'
      ],
      env: {
        jest: true
      }
    }
  ]
}
