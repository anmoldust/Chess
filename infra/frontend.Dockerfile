# syntax=docker/dockerfile:1
FROM node:22-alpine AS build
WORKDIR /app

COPY frontend/package.json frontend/package-lock.json ./
# This machine sets NODE_ENV=production globally, which makes npm skip devDependencies (vite,
# tsc, vitest) and breaks the build. Be explicit.
RUN npm ci --include=dev

COPY frontend/ ./
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY infra/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
