#
# Stage 1: Build the application with JDK 21
#
# Stage 1: Build with Gradle and JDK 21
FROM gradle:8.7.0-jdk21 AS build
WORKDIR /app
COPY gradlew gradlew
COPY gradle gradle
COPY . .
RUN chmod +x gradlew
RUN ./gradlew :backend:build

#
# Stage 2: Create the final runtime image with JRE 21
#
FROM eclipse-temurin:21-jre-jammy

# Set the working directory
WORKDIR /app

# Copy the built jar file from the build stage
# The 'backend' part in the path refers to the backend module's name
COPY --from=build /app/backend/build/libs/backend-1.0-SNAPSHOT.jar .

# Expose the port the application runs on
EXPOSE 8080

# Define the command to run the application
ENTRYPOINT ["java", "-jar", "backend-1.0-SNAPSHOT.jar"]