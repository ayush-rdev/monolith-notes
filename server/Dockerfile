FROM node:22-alpine

WORKDIR /app

COPY server.js package.json ./

ENV PORT=8080
ENV DB_PATH=/app/data/vault.db

RUN mkdir -p /app/data

EXPOSE 8080

CMD ["node", "server.js"]
