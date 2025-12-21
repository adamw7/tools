FROM eclipse-temurin:25

COPY target/tools.assembly-*-jar-with-dependencies.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]