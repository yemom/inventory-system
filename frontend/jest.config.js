const nextJest = require('next/jest');

const createJestConfig = nextJest({
  dir: './',
});

const customJestConfig = {
  // jsdom is the default so React + DOM APIs work in authFlow.test.ts
  // API-only tests that don't need a DOM can add @jest-environment node docblock
  testEnvironment: 'jest-environment-jsdom',
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/src/$1',
  },
  setupFilesAfterEnv: ['@testing-library/jest-dom'],
};

module.exports = createJestConfig(customJestConfig);

