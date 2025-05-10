#!/bin/bash
docker rmi machinima-llm:latest || true
rm -f machinima-llm.tar || true




POSTGRES_CONTAINER_NAME="temp-postgres"
docker stop "$POSTGRES_CONTAINER_NAME" > /dev/null || true
docker rm "$POSTGRES_CONTAINER_NAME" > /dev/null || true

# Function to find a free port within a specified range
find_free_port() {
    START_PORT=5432
    END_PORT=15432

    for PORT in $(seq "$START_PORT" "$END_PORT"); do
        # Check if the port is free by attempting to bind to it
        if ! nc -z localhost "$PORT"; then
            echo "$PORT"
            return
        fi
    done

    echo "Error: No free ports found in range $START_PORT-$END_PORT."
    exit 1
}

# Step 1: Find a free port
FREE_PORT=$(find_free_port)
echo "Found free port: $FREE_PORT"

# Step 2: Launch PostgreSQL in a Docker container
POSTGRES_IMAGE="pgvector/pgvector:pg16"  # Specify the desired PostgreSQL version
POSTGRES_PASSWORD="postgres"  # Set a password for PostgreSQL

echo "Launching PostgreSQL container on port $FREE_PORT..."
docker run --name "$POSTGRES_CONTAINER_NAME" \
           -e POSTGRES_USER="postgres" \
           -e POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
           -e POSTGRES_DB="postgres" \
           -e PGPASSWORD="$POSTGRES_PASSWORD" \
           -p "$FREE_PORT:5432" \
           -d "$POSTGRES_IMAGE"

# Wait for PostgreSQL to initialize (optional, but recommended)
echo "Waiting for PostgreSQL to initialize..."
sleep 5

# Step 3: Build another Docker image with the free port passed as an ARG
DOCKERFILE_PATH="./Dockerfile"  # Path to your Dockerfile
IMAGE_NAME="machinima-llm:latest"

echo "Building Docker image with port $FREE_PORT passed as ARG..."
docker build --add-host=host.docker.internal:host-gateway \
       --build-arg DB_URL="jdbc:postgresql://host.docker.internal:$FREE_PORT/postgres" \
       -t "$IMAGE_NAME" \
       -f "$DOCKERFILE_PATH" .

# Check if the build was successful
if [ $? -ne 0 ]; then
    echo "Error: Docker image build failed."
    exit 1
fi

echo "Docker image '$IMAGE_NAME' built successfully."

# Step 4: Terminate the PostgreSQL container
echo "Stopping and removing PostgreSQL container..."
docker stop "$POSTGRES_CONTAINER_NAME" > /dev/null
docker rm "$POSTGRES_CONTAINER_NAME" > /dev/null

echo "PostgreSQL container terminated."







#docker build --add-host=host.docker.internal:host-gateway -t machinima-llm:latest .
docker save -o machinima-llm.tar machinima-llm:latest