FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN sh ./mvnw -B -DskipTests clean package

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 gcmb \
    && useradd --uid 10001 --gid gcmb --no-create-home gcmb \
    && mkdir -p /app/data/attachments \
    && chown -R gcmb:gcmb /app
WORKDIR /app
COPY --from=build --chown=gcmb:gcmb /build/target/backend-0.0.1-SNAPSHOT.jar app.jar
USER gcmb
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
