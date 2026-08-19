import coreWebVitals from 'eslint-config-next/core-web-vitals';
import nextTypescript from 'eslint-config-next/typescript';

/**
 * Flat config. eslint-config-next 16 exports flat config arrays directly, so
 * no FlatCompat shim is needed.
 */
const config = [
  { ignores: ['.next/**', 'node_modules/**', 'next-env.d.ts'] },
  ...coreWebVitals,
  ...nextTypescript,
  {
    rules: {
      // A leading underscore marks a parameter that exists to satisfy an
      // interface — provider adapters that ignore `location`, for instance.
      '@typescript-eslint/no-unused-vars': [
        'warn',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrorsIgnorePattern: '^_' },
      ],
    },
  },
];

export default config;
