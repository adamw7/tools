FROM eclipse-temurin:21-jdk-alpine

COPY target/tools.assembly-*-jar-with-dependencies.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]