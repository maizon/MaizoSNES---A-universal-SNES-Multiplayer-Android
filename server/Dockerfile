FROM node:22-alpine
WORKDIR /app
COPY relay.js wss-relay.js ice-directory.js ./
USER node
EXPOSE 8787/tcp 8788/tcp
CMD ["node", "relay.js"]
