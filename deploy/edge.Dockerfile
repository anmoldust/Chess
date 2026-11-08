# syntax=docker/dockerfile:1
#
# The production edge image: the built SPA plus nginx with the production edge.conf.
# The origin certificate and key are NOT baked in — they are mounted at runtime from
# deploy/secrets/, so TLS material never lives in the image or the registry.
#
#   docker build -f deploy/edge.Dockerfile .
#
FROM node:22-alpine AS build
WORKDIR /app

COPY frontend/package.json frontend/package-lock.json ./
# This machine (and CI images) can set NODE_ENV=production globally, which makes npm skip
# devDependencies (vite, tsc) and breaks the build. Be explicit.
RUN npm ci --include=dev

COPY frontend/ ./
RUN npm run build

FROM nginx:1.27-alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY deploy/nginx/edge.conf /etc/nginx/conf.d/default.conf
COPY deploy/nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf

EXPOSE 80 443
