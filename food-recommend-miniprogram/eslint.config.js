export default [
  {
    ignores: ['node_modules/**', 'miniprogram_npm/**']
  },
  {
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        wx: 'readonly',
        App: 'readonly',
        Page: 'readonly',
        Component: 'readonly',
        Behavior: 'readonly',
        getApp: 'readonly',
        getCurrentPages: 'readonly'
      }
    },
    rules: {
      'no-unused-vars': ['warn', { argsIgnorePattern: '^_', caughtErrorsIgnorePattern: '^_?' }]
    }
  }
]
