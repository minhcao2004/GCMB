FROM node:22-bookworm-slim AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY public public
COPY src src
ENV REACT_APP_API_BASE_URL=/api/v1
ENV GENERATE_SOURCEMAP=false
RUN npm run build

FROM caddy:2-alpine
COPY --from=build /build/build /srv
