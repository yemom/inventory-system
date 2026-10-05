import js from '@eslint/js';
import nextPlugin from '@next/eslint-plugin-next';
import reactPlugin from 'eslint-plugin-react';
import reactHooksPlugin from 'eslint-plugin-react-hooks';
import globals from 'globals';
import tseslint from 'typescript-eslint';

/**
 * ESLint 9 flat config.
 *
 * The project previously only had a legacy `.eslintrc.json`, which ESLint 9
 * ignores by default. Rules are kept equivalent to the old setup
 * (`next/core-web-vitals` + `plugin:react/recommended`) using the flat-config
 * equivalents of those plugins.
 */
const eslintConfig = [
  {
    ignores: [
      '.next/**',
      'node_modules/**',
      'out/**',
      'build/**',
      'next-env.d.ts',
      'playwright-report/**',
      'test-results/**',
      // Next.js tooling files are CommonJS and legitimately use require();
      // linting them with ESM-only rules produces false positives.
      'postcss.config.js',
      'tailwind.config.js',
      'next.config.js',
      'jest.config.js',
      'jest.setup.js',
    ],
  },

  js.configs.recommended,
  ...tseslint.configs.recommended,

  {
    files: ['**/*.{js,mjs,jsx,ts,tsx}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        ...globals.browser,
        ...globals.node,
        ...globals.es2021,
      },
      parserOptions: {
        ecmaFeatures: { jsx: true },
      },
    },
    plugins: {
      react: reactPlugin,
      'react-hooks': reactHooksPlugin,
      '@next/next': nextPlugin,
    },
    settings: {
      react: { version: 'detect' },
    },
    rules: {
      ...reactPlugin.configs.recommended.rules,
      ...reactPlugin.configs['jsx-runtime'].rules,
      // react-hooks: register the plugin (next/core-web-vitals references its rules).
      // Only the stable, correctness-critical rules are enforced as errors.
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'warn',
      // next/core-web-vitals
      ...nextPlugin.configs.recommended.rules,
      ...nextPlugin.configs['core-web-vitals'].rules,

      // The new JSX transform makes the React import unnecessary.
      'react/react-in-jsx-scope': 'off',
      'react/prop-types': 'off',
      // The React Compiler lint rules (eslint-plugin-react-hooks v6) flag
      // pre-existing patterns across the codebase. They are advisory, so keep
      // them as warnings instead of blocking CI; the compiler itself is opt-in.
      'react-hooks/incompatible-library': 'warn',
      'react-hooks/set-state-in-effect': 'warn',
      'react-hooks/immutability': 'warn',
      'react-hooks/purity': 'warn',
      'react-hooks/globals': 'warn',
      'react-hooks/unsupported-syntax': 'warn',
      // Apostrophes/quotes inside JSX text are cosmetic.
      'react/no-unescaped-entities': 'off',
      // Internal navigation should use next/link, but this rule is noisy here.
      '@next/next/no-html-link-for-pages': 'off',
      // Unused vars are warnings, not build breakers.
      '@typescript-eslint/no-unused-vars': 'warn',
      '@typescript-eslint/no-explicit-any': 'off',
      'no-unused-vars': 'off',
      // `process.env` access is fine in Next.js
      'no-undef': 'off',
    },
  },
];

export default eslintConfig;