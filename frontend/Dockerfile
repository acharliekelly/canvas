FROM node:24-bookworm AS dependencies
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM dependencies AS test
COPY . .
CMD ["npm", "test", "--", "--run"]

FROM dependencies AS runtime
COPY . .
EXPOSE 5173
CMD ["npm", "run", "dev"]
