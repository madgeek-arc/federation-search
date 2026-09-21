FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
COPY search-aggregator-service/target/search-aggregator-service-*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar", "--server.port=8090"]
