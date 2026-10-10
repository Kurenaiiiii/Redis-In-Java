# Multi-stage build: compile with Maven on Temurin 21, run on a slim JRE.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B verify -Puberjar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/redis-in-java-0.1.0.jar app.jar
EXPOSE 6379
ENTRYPOINT ["java", "-jar", "app.jar"]
