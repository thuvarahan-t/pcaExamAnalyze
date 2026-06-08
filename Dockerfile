FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn -f backend/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/backend/target/pcaExamAnalyze-0.0.1-SNAPSHOT.jar app.jar
CMD ["java", "-jar", "app.jar"]
