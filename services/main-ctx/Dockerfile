FROM eclipse-temurin:21-jre-noble

RUN apt-get update && apt-get install -y ffmpeg && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
