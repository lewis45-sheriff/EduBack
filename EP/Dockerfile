# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy pom.xml first to cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
COPY JasperReports ./JasperReports
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Create non-root user for security
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

# Copy JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Copy JasperReports for report generation
COPY --from=build /app/JasperReports ./JasperReports

# Set ownership
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8085

# JAVA_OPTS lets us cap heap on small instances; SPRING_PROFILES_ACTIVE defaults to docker.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:InitialRAMPercentage=30"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-docker}"]
