# ============================================================
# StockFlow — Next.js Frontend
# Multi-stage build: builder produces the standalone server,
# runner copies only the runtime artifacts.
# ============================================================

# ============================================================
# Stage 1: Build Next.js application
# ============================================================
FROM node:20-alpine AS builder

WORKDIR /app

# NOTE: NODE_ENV must NOT be "production" during `npm ci`.
# npm treats NODE_ENV=production as `--omit=dev`, which silently
# drops devDependencies (typescript, @types/*, tailwindcss, ...).
# The resulting "successful" install then fails at `next build`
# with missing type declarations. `next build` sets its own
# production mode, so nothing here needs it.
ENV NEXT_TELEMETRY_DISABLED=1

# Build-time environment variables (inlined into the client bundle)
ARG NEXT_PUBLIC_API_BASE_URL=http://localhost:8081/api/v1
ARG NEXT_PUBLIC_APP_URL=http://localhost:3005

ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
ENV NEXT_PUBLIC_APP_URL=${NEXT_PUBLIC_APP_URL}

# Copy package files first so the dependency layer caches independently
# of application source changes.
COPY package.json package-lock.json ./

# Reproducible, lockfile-driven install.
# Must run with devDependencies included (see NODE_ENV note above).
RUN npm ci --no-audit --no-fund

# Copy application source
COPY . .

# Build Next.js (next.config.js sets output: 'standalone')
RUN npm run build


# ============================================================
# Stage 2: Production runner
# ============================================================
FROM node:20-alpine AS runner

WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

# Create non-root user
RUN addgroup --system --gid 1001 nodejs \
    && adduser --system --uid 1001 nextjs

# Copy public files
COPY --from=builder --chown=nextjs:nodejs /app/public ./public

# Copy standalone Next.js server (includes its own minimal node_modules)
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./

# Copy static assets
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs

EXPOSE 3000

CMD ["node", "server.js"]