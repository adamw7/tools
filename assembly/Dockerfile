FROM eclipse-temurin:21.0.9_10-jdk-alpine

COPY target/tools.assembly-*-jar-with-dependencies.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]