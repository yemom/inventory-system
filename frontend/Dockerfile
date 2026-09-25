# ============================================================
# Stage 1: Build Next.js application
# ============================================================
FROM node:20-alpine AS builder

WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

# Build-time environment variables
ARG NEXT_PUBLIC_API_BASE_URL=http://localhost:8081/api/v1
ARG NEXT_PUBLIC_APP_URL=http://localhost:3005

ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
ENV NEXT_PUBLIC_APP_URL=${NEXT_PUBLIC_APP_URL}

# Copy package files first
COPY package.json package-lock.json* ./

# Install dependencies
RUN npm ci

# Copy application source
COPY . .

# Build Next.js
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
COPY --from=builder /app/public ./public

# Copy standalone Next.js server
COPY --from=builder \
    --chown=nextjs:nodejs \
    /app/.next/standalone ./

# Copy static assets
COPY --from=builder \
    --chown=nextjs:nodejs \
    /app/.next/static ./.next/static

USER nextjs

EXPOSE 3000

CMD ["node", "server.js"]