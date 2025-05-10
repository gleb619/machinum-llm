FROM eclipse-temurin:21-jre as builder

ARG DB_URL=jdbc:postgresql://host.docker.internal:5432/postgres
ARG DB_USERNAME=postgres
ARG DB_PASSWORD=postgres

# Set environment variables from build args
ENV SPRING_DATASOURCE_URL=${DB_URL} \
    SPRING_DATASOURCE_USERNAME=${DB_USERNAME} \
    SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}

# Create an application directory and add a non-root user 'app'
RUN mkdir /app
#    addgroup --system app && \
#    adduser -S -s /bin/false -G app app

#RUN apk update --no-cache && \
#    apk add --no-cache libstdc++ libc6-compat

# Copy the application JAR file
COPY build/libs/app-0.0.1-SNAPSHOT.jar /app/app.jar
#COPY --chown=app:app build/libs/app-0.0.1-SNAPSHOT.jar /app/app.jar
#VOLUME /tmp/spring-ai-onnx-generative

# Set the working directory
#WORKDIR /

# Create a script to run the application and wait for specific log line
# Create a script to run the application and wait for specific log line
RUN echo '#!/bin/sh \n\
set -e \n\
\n\
java -jar /app/app.jar > app.log 2>&1 & \n\
PID=$! \n\
echo "Waiting for application to initialize..." \n\
\n\
# Function to show logs and exit on error \n\
show_logs_and_exit() { \n\
  echo "ERROR: $1" \n\
  echo "===== APPLICATION LOGS =====" \n\
  cat app.log \n\
  echo "============================" \n\
  exit 1 \n\
} \n\
\n\
# Wait for up to 10 minutes \n\
timeout=600 \n\
elapsed=0 \n\
while [ $elapsed -lt $timeout ]; do \n\
  if ! ps -p $PID > /dev/null; then \n\
    show_logs_and_exit "Application terminated unexpectedly" \n\
  fi \n\
  \n\
  if grep -q "Model input names" app.log; then \n\
    echo "Found target log line. Stopping application." \n\
    tail -10 app.log \n\
    #curl -X POST 127.0.0.1:8078/actuator/shutdown \n\
    kill $PID \n\
    wait $PID \n\
    echo "Application stopped successfully." \n\
    exit 0 \n\
  fi \n\
  \n\
  sleep 1 \n\
  elapsed=$((elapsed+1)) \n\
done \n\
\n\
show_logs_and_exit "Timeout waiting for initialization" \n\
' > /app/run.sh && chmod +x /app/run.sh

# Run the script during build
RUN /app/run.sh || true
RUN #/app/run.sh || exit 1

FROM eclipse-temurin:21-jre

# Set the JAVA_HOME environment variable to the default JRE path
#ENV JAVA_HOME=/opt/java/openjdk
#ENV PATH="$PATH:$JAVA_HOME/bin"

#ARG DB_URL=jdbc:postgresql://host.docker.internal:5432/postgres
#ARG DB_USERNAME=postgres
#ARG DB_PASSWORD=postgres
#
## Set environment variables from build args
#ENV SPRING_DATASOURCE_URL=${DB_URL} \
#    SPRING_DATASOURCE_USERNAME=${DB_USERNAME} \
#    SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}

# Create an application directory and add a non-root user 'app'
RUN mkdir /app
#    addgroup --system app && \
#    adduser -S -s /bin/false -G app app

#RUN apk update --no-cache && \
#    apk add --no-cache libstdc++ libc6-compat

# Copy the application JAR file
COPY --from=builder /app/app.jar /app/app.jar
COPY --from=builder /tmp/spring-ai-onnx-generative /tmp/spring-ai-onnx-generative
#COPY --chown=app:app build/libs/app-0.0.1-SNAPSHOT.jar /app/app.jar
VOLUME /tmp/spring-ai-onnx-generative

# Set the working directory
WORKDIR /app

# Use non-root user
#USER app

# Start the application using the default JRE 21
ENTRYPOINT ["java", "-jar", "/app/app.jar"]