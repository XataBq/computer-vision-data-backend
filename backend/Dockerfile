FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN sed -i 's|http://|https://|g' /etc/apt/sources.list \
    && apt-get -o Acquire::Retries=3 update && apt-get -o Acquire::Retries=3 install -y --no-install-recommends ffmpeg curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app && useradd --uid 10001 --gid app --create-home app
WORKDIR /app
RUN mkdir -p /app/data/uploads /app/data/streams && chown -R app:app /app
COPY --from=build --chown=app:app /build/target/media-backend-0.0.1-SNAPSHOT.jar app.jar
USER app
ENV STORAGE_ROOT=/app/data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
