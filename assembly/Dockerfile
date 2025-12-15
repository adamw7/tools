FROM eclipse-temurin:25.0.1_8-jdk-alpine

COPY target/tools.assembly-*-jar-with-dependencies.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]