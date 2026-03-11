FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
COPY JasperReports ./JasperReportsRUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

COPY --from=build /app/target/*.jar app.jar
COPY --from=build /app/JasperReports ./JasperReports
RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]