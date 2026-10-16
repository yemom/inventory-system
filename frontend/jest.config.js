const nextJest = require('next/jest');

const createJestConfig = nextJest({
  dir: './',
});

/*
 * These suites render whole pages in jsdom. A dashboard, POS or products screen
 * mounts several hundred elements before any assertion runs, and a suite that
 * finishes in a few hundred milliseconds on an idle machine takes past Jest's
 * 5s default once the machine is under load. That is not a slow test or a
 * defect — the work is real either way — so the timeout is set here once rather
 * than duplicated per file, and raised rather than the tests being split or
 * skipped. A genuine hang still fails.
 */
const TEST_TIMEOUT_MS = 30_000;

const customJestConfig = {
  // jsdom is the default so React + DOM APIs work in authFlow.test.ts
  // API-only tests that don't need a DOM can add @jest-environment node docblock
  testEnvironment: 'jest-environment-jsdom',
  testTimeout: TEST_TIMEOUT_MS,
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/src/$1',
  },
  setupFilesAfterEnv: ['@testing-library/jest-dom'],
};

module.exports = createJestConfig(customJestConfig);

