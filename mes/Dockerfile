# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
# Tests need Docker (Testcontainers), so they run in CI and not in the image build.
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -q package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 mes
COPY --from=build /build/target/mes-lite-*.jar app.jar
USER mes
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
