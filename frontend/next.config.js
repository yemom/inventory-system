// next.config.js
/** @type {import('next').NextConfig} */
const path = require('path');

const nextConfig = {
  reactStrictMode: true,
  output: 'standalone',

  // Pin the workspace root to the frontend package. Without this, Turbopack
  // walks up the tree, finds a stray root-level package-lock.json, picks the
  // repo root, and warns on every start ("Next.js inferred your workspace
  // root, but it may not be correct").
  turbopack: {
    root: path.resolve(__dirname),
  },

  // Next.js 16 generates AGENTS.md / CLAUDE.md on every dev start. These are
  // machine-local scaffolding, not project source, so don't create them.
  agentRules: false,

  // Next.js 16 blocks cross-origin access to dev resources (/_next/hmr, HMR
  // websocket, static dev chunks) unless the requesting host is allow-listed.
  // Without this, opening the dev server via the machine's LAN IP (for example
  // http://192.168.137.1:3001) fails with:
  //   "Blocked cross-origin request to Next.js dev resource /_next/hmr"
  // and the page never finishes loading / hot reload never connects.
  //
  // These entries are **development-only**; they have no effect on a
  // production build or on the containerised app.
  allowedDevOrigins: [
    // Localhost on any port (the dev server auto-selects a free port when
    // 3000 is taken, e.g. by the WSL relay).
    'localhost',
    '127.0.0.1',
    // WSL / Hyper-V host addresses.
    '0.0.0.0',
    '::1',
    // Host addresses on private ranges, so testing from a phone/tablet or a
    // second machine on the same network works.
    '192.168.137.1',
    'host.docker.internal',
  ],
};

module.exports = nextConfig;