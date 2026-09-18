// next.config.js
/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  ...(process.env.NODE_ENV === 'production' ? { output: 'standalone' } : {}),
};
module.exports = nextConfig;
