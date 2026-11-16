# Front-end do OnCar: build com as dependencias completas e runtime so com o servidor enxuto.
FROM node:22-bookworm-slim AS dependencias
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:22-bookworm-slim AS build
WORKDIR /app
# Endereco do back-end visto de dentro da rede do compose. Os rewrites do /api sao
# gravados no build, por isso entra como argumento e nao como variavel de runtime.
ARG ONCAR_BACKEND_URL=http://backend:8080
ENV ONCAR_BACKEND_URL=$ONCAR_BACKEND_URL \
    ONCAR_STANDALONE=1 \
    NEXT_TELEMETRY_DISABLED=1
COPY --from=dependencias /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM node:22-bookworm-slim
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0
# o servidor enxuto nao serve public/ nem .next/static sozinho: entram ao lado dele
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static
COPY --from=build --chown=node:node /app/public ./public
USER node
EXPOSE 3000
HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=6 \
    CMD node -e "fetch('http://127.0.0.1:3000/login').then(r => process.exit(r.ok ? 0 : 1)).catch(() => process.exit(1))"
CMD ["node", "server.js"]
