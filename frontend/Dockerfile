# syntax=docker/dockerfile:1
###############################################################################
# DeployForge AI dashboard
#
# Built with Node, served by nginx. The API base URL is a build argument because Vite
# inlines VITE_* values at build time.
###############################################################################

FROM node:20-alpine AS builder
WORKDIR /app

ENV CI=true
COPY package.json package-lock.json* ./
# npm ci when a lockfile exists (reproducible), npm install otherwise.
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi

COPY . .
ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# --------------------------------------------------------------------------- #

FROM nginx:1.27-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
