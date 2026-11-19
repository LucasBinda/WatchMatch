# Build do site estático já apontando para o backend real (sem o mock)
# Debian (glibc), não Alpine: o TypeScript 7 e o Vite usam binários nativos
FROM node:22-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ENV VITE_USE_MOCKS=false
RUN npm run build

# Nginx só serve o site; a /api passa pelo gateway (backend/gateway)
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
