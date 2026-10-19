# syntax=docker/dockerfile:1

FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
# src/shared/api/schema.d.ts is committed (generated from the backend's OpenAPI during development).
RUN npm run build

# One container serves the SPA and is the reverse proxy for the API, WebSocket and media (09-deployment §5).
FROM nginx:1.30-alpine
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
