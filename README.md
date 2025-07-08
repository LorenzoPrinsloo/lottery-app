# Lottery Service

This project implements a simple lottery system as a set of microservices built with Scala, Cats Effect, and Http4s. The entire application stack is containerized and can be run locally using Docker Compose.

The architecture consists of 4 main components:

* **`lottery-api`**: A HTTP service that handles user registration and ballot submissions. This service is designed to be horizontally scalable.
* **`lottery-draw`**: A background worker service responsible for performing the daily lottery draw. It runs a cron job and also exposes a secure developer endpoint for manual draws.
* **`redis`**: The database used for persistence.
* **`ngnix`**: For DNS and routing.

## Prerequisites

Before you begin, ensure you have the following installed:

* **SBT**
* **Docker** and **Docker Compose**

## Getting Started

Follow these steps to build the Docker images and run the entire application. All commands should be run from the root directory of the project.

### Step 1: Build the Docker Images

This project uses *sbt-native-packager* to build Docker images for each service directly from SBT. 

First you need to build the docker images for all services. 
This can be done by running `sbt Docker / publishLocal` from the project Root directory 

### Step 2: Start the Services

With the images built, you can now start all the services using Docker Compose.

```bash
# Start all services (lottery-api, draw-service, Nginx, Redis) in detached mode
docker-compose up -d
```

### Step 3: Verify and Test

Once all containers have finished starting up, you can access the application on `localhost:8080`. You can check that all services are running correctly with `docker-compose ps`. You should see `lottery-api`, `lottery-draw`, `nginx`, and `redis` all in the `running` state.

You can now interact with the services through the Nginx gateway. Here are some example `curl` commands to test the endpoints.

#### **Register a Participant**

  This command will register a new participant. On success, it will return the new participant's details, including their unique ID.

  ```bash
curl 'localhost:8080/service/api/v1/participants' \
--header 'Content-Type: application/json' \
--data-raw '{
    "name": "John",
    "email": "john.doe@example.com"
}'
  ```
  
#### **Submit Ballots for Today's Lottery**

This command submits 5 ballots for the participant with the given email. Note that you must register the participant first with their email.

```bash
curl 'localhost:8080/service/api/v1/lotteries/{{YYYY-MM-DD}}/ballots' \
--header 'Content-Type: application/json' \
--data-raw '{
    "email": "test@gmail.com",
    "count": 5
}'
```

#### **Check a previous Lottery's Result**

This command retrieves the winning result for a lottery on a specific date. If no winner has been drawn yet, it will return a 404 Not Found error.

```bash
curl 'localhost:8080/service/api/v1/lotteries/{{YYYY-MM-DD}}/winner'
```

#### **Configuring the Automatic Lottery Draw**

By default, the `lottery-draw` service will perform the daily lottery draw at 00:01 in the `Europe/Amsterdam` timezone and will draw a winner for the previous day's lottery.
You can customize this schedule by setting the following environment variables in the `docker-compose.yml` file for the `lottery-draw` service:

* `TIME_ZONE`: Sets the timezone for the cron job. Must be a valid Java ZoneId (e.g., `"Europe/Amsterdam"`, `"America/New_York"`).

* `DRAW_TIME`: Sets the time of day for the draw in `HH:mm` format (e.g., `"00:01"`).

* `DAY_OFFSET`: Sets which day's lottery to draw. `1` means draw yesterday's lottery (default), `0` means draw today's lottery.

#### **Trigger a Manual Draw (Developer Endpoint)**

This endpoint is useful for testing the draw logic without waiting for the cron job. Remember to replace API_SECRET with the secret-key defined in docker-compose.yml and use a valid date. (one that is not in the past)

```bash
curl --request POST 'localhost:8080/draw/api/v1/{{YYYY-MM-DD}}' \
--header 'X-Api-Secret: {{API_SECRET}}'
```

### Step 4: Shutting Down

To stop and remove all the containers and networks created by Docker Compose, run: 
```bash
docker-compose down
```