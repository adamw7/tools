FROM openjdk:21-jdk

COPY target/tools.assembly-*-jar-with-dependencies.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]