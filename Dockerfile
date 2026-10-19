# Railway Docker build: root context, only the small Node relay is copied.
# No Android SDK, Gradle build or commercial ROMs are needed on the server.
FROM node:22-alpine
WORKDIR /app
COPY --chown=node:node server/relay.js server/wss-relay.js server/ice-directory.js ./
USER node
ENV MAIZO_HTTP_PORT=8787 MAIZO_TCP_PORT=8788 MAIZO_BIND=0.0.0.0
EXPOSE 8787/tcp 8788/tcp
CMD ["node", "relay.js"]
