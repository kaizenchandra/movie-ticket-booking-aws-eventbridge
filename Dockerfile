FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B -DskipTests package
FROM eclipse-temurin:21-jre
RUN groupadd -r cinema && useradd -r -g cinema cinema
WORKDIR /app
COPY --from=build /workspace/target/aws-eventbridge-demo-1.0.0-SNAPSHOT.jar app.jar
USER cinema
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java","-jar","app.jar"]
