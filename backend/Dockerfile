# Backend image for the hosted test environment (ADR-0014). Build context: backend/.
# Build stage compiles with the project's Maven wrapper; tests and formatting run in CI, not here.
FROM eclipse-temurin:21-jdk-noble AS build
# The Maven wrapper verifies the .zip distribution's SHA-256; without unzip it would fetch the
# .tar.gz instead and (correctly) fail that check.
RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -ntp -q package -DskipTests -Dspotless.check.skip=true \
 && cp target/smart-commerce-*.jar /app.jar

FROM eclipse-temurin:21-jre-noble
RUN groupadd --system app && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app \
 && mkdir -p /app && chown app:app /app
WORKDIR /app
COPY --from=build --chown=app:app /app.jar /app/app.jar
USER app
# Render (and most PaaS) route to $PORT on all interfaces; locally the default stays 8080.
# Sized for 512 MB / 0.1 CPU (Render free): bounded heap, serial GC and fast startup over peak speed.
ENV SERVER_ADDRESS=0.0.0.0 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java -jar /app/app.jar --server.port=${PORT:-8080}"]
