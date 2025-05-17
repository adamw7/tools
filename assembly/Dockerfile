FROM openjdk:21-jdk

COPY target/tools.assembly-1.1.0-SNAPSHOT-jar-with-dependencies.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]