# text2sql-frontend — build the Vite bundle, then serve it with nginx.
#
# The API base URL stays "/api" (see src/api/http.ts), and nginx proxies /api to the backend. That
# keeps the bundle host-independent: the same image works whatever IP or domain the server has, so
# nothing needs rebuilding when a spot instance comes back on a different address.

# --- Build stage --------------------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# --- Runtime stage ------------------------------------------------------------------------------
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80
