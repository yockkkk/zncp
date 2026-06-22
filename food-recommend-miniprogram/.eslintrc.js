module.exports = {
  root: true,
  env: { browser: true, es2022: true },
  parserOptions: { ecmaVersion: 'latest', sourceType: 'module' },
  globals: {
    wx: 'readonly',
    App: 'readonly',
    Page: 'readonly',
    Component: 'readonly',
    Behavior: 'readonly',
    getApp: 'readonly',
    getCurrentPages: 'readonly'
  },
  extends: ['eslint:recommended'],
  rules: {
    'no-unused-vars': ['warn', { argsIgnorePattern: '^_' }]
  },
  ignorePatterns: ['node_modules/', 'miniprogram_npm/']
}
