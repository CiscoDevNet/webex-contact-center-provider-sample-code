# Multi-stage build for Java 17 gRPC application

# Build stage
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Install Maven
RUN apt-get update && \
    apt-get install -y maven && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Cache Maven dependencies by copying pom.xml first
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src/ ./src/

# Build the application
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# Set environment variables for Java optimization
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+DisableExplicitGC -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

# Create a non-root user
RUN groupadd --system --gid 1001 appuser && \
    useradd --system --uid 1001 --gid 1001 --create-home appuser

# Copy the shaded JAR from the build stage
COPY --from=build /app/target/*-allinone.jar /app/app.jar

# Change ownership to the non-root user
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose the gRPC server port
EXPOSE 8086

# Health check - verify the Java process is running
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD ps -ef | grep java | grep -v grep || exit 1

# Set the entry point with the main class specified (already defined in the JAR manifest)
ENTRYPOINT ["java", "-jar", "app.jar"]
