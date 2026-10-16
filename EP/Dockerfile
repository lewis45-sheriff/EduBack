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

# JasperReports renders PDFs with the AWT font system, which needs real fonts AND
# fontconfig present in the image. The slim JRE base has neither, so report downloads
# fail with: Font "Arial" is not available to the JVM.
#
# We install fontconfig plus the Liberation fonts (Liberation Sans is metric-compatible
# with Arial) and register a fontconfig alias so any request for "Arial" resolves to
# Liberation Sans. This keeps the existing .jrxml templates (which use fontName="Arial")
# working without editing every template.
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-liberation \
    && printf '%s\n' \
        '<?xml version="1.0"?>' \
        '<!DOCTYPE fontconfig SYSTEM "fonts.dtd">' \
        '<fontconfig>' \
        '  <alias><family>Arial</family><prefer><family>Liberation Sans</family></prefer></alias>' \
        '  <alias><family>Helvetica</family><prefer><family>Liberation Sans</family></prefer></alias>' \
        '</fontconfig>' \
        > /etc/fonts/conf.d/99-arial-alias.conf \
    && fc-cache -f \
    && rm -rf /var/lib/apt/lists/*

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
