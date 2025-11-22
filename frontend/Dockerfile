FROM node:20-alpine AS build

WORKDIR /app

COPY package*.json ./
RUN npm install

COPY vite.config.ts ./
COPY certs ./certs
COPY . .

RUN npm run build

FROM node:20-alpine

WORKDIR /app

COPY --from=build /app/dist ./dist
COPY --from=build /app/vite.config.ts ./vite.config.ts
COPY --from=build /app/certs ./certs
COPY package*.json ./

RUN npm install --omit=dev && npm install vite

EXPOSE 4173

CMD ["npx", "vite", "preview", "--host", "0.0.0.0", "--port", "4173"]
