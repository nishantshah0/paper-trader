FROM node:22-alpine AS dashboard
WORKDIR /ui
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app
COPY pom.xml ./
RUN mvn -B dependency:go-offline
COPY src/ src/
COPY --from=dashboard /ui/dist/ src/main/resources/static/
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S trader && adduser -S trader -G trader
COPY --from=backend /app/target/paper-trader-*.jar app.jar
USER trader
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
