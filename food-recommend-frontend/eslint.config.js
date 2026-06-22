import pluginVue from 'eslint-plugin-vue'
import vueConfigPrettier from '@vue/eslint-config-prettier'

export default [
  {
    ignores: ['dist/**', 'node_modules/**']
  },
  ...pluginVue.configs['flat/recommended'],
  vueConfigPrettier,
  {
    rules: {
      'vue/multi-word-component-names': 'off',
      'no-unused-vars': ['warn', { argsIgnorePattern: '^_', caughtErrorsIgnorePattern: '^_?' }]
    }
  }
]
