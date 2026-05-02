# ========== Build stage ==========
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B -q

# Build
COPY src ./src
RUN mvn package -DskipTests -B -q

# ========== Runtime stage ==========
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Git (needed for JGit clone)
RUN apk add --no-cache git

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
