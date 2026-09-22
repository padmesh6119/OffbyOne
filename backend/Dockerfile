FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

FROM eclipse-temurin:21-jdk
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends g++ python3 bubblewrap && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/offbyone-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
