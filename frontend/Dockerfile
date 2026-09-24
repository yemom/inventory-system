# =====================================================================
# Stage 1: BUILDER — install deps and build the Next.js app
# =====================================================================
FROM node:20-alpine AS builder
WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

# Build-time env vars for Next.js NEXT_PUBLIC_ inlining
ARG NEXT_PUBLIC_API_BASE_URL=http://localhost:8081/api/v1
ARG NEXT_PUBLIC_APP_URL=http://localhost:3005
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_APP_URL=$NEXT_PUBLIC_APP_URL

# Install deps first (layer caching)
COPY package.json package-lock.json* ./
RUN npm install --prefer-offline

# Copy the rest of the source
COPY . .

# Build → output: standalone (set in next.config.js conditionally on NODE_ENV=production)
RUN npm run build

# =====================================================================
# Stage 2: RUNNER — minimal image that serves the standalone output
# =====================================================================
FROM node:20-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

RUN addgroup --system --gid 1001 nodejs
RUN adduser --system --uid 1001 nextjs

# Copy static public assets
COPY --from=builder /app/public ./public

# Copy the standalone server bundle (includes node_modules subset)
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./

# Copy the static Next.js assets into the correct sub-path
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs

EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

CMD ["node", "server.js"]
