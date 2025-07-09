# Lottery Service

This project implements a simple lottery system as a set of microservices built with Scala, Cats Effect, and Http4s. The entire application stack is containerized and can be run locally using Docker Compose.

The architecture consists of 4 main components:

* **`lottery-api`**: A HTTP service that handles user registration and ballot submissions. This service is designed to be horizontally scalable.
* **`lottery-draw`**: A single background worker service responsible for performing the daily lottery draw. It runs a cron job and also exposes a secure developer endpoint for manual draws.
* **`redis`**: The database used for persistence.
* **`ngnix`**: For DNS and routing.
* **`mailhog`**: Mock Email Inbox and routing.

***

### **Table of Contents**

1. [Prerequisites](#prerequisites)
2. [Getting Started](#getting-started)
    * [Step 1: Build the Project & Docker Images](#step-1-build-the-project--docker-images)
    * [Step 2: Start the Services](#step-2-start-the-services)
    * [Step 3: Verify and Test](#step-3-verify-and-test)
    * [Step 4: Shutting Down](#step-4-shutting-down)
3. [Key Features & Configuration](#key-features--configuration)
    * [Lottery API](#lottery-api)
    * [Scaling the API Service](#scaling-the-api-service)
    * [Lottery Draw](#lottery-draw)
    * [Email Notifications (MailHog)](#email-notifications-mailhog)
    * [Redis Persistence](#redis-persistence)

***

## Prerequisites

Before you begin, ensure you have the following installed:

* **SBT**
* **Docker** and **Docker Compose**

## Getting Started

Follow these steps to build the Docker images and run the entire application. All commands should be run from the root directory of the project.

### Step 1: Build the Project & Docker Images

This project uses *sbt-native-packager* to build Docker images for each service directly from SBT. 

First you need to build the project & docker images for all services. 
This can be done by running the following commands from the project Root directory

```bash
sbt update
sbt compile
sbt Docker / publishLocal
```

### Step 2: Start the Services

With the images built, you can now start all the services using Docker Compose.

```bash
# Start all services in detached mode
docker-compose up -d
```

### Step 3: Verify and Test

Once all containers have finished starting up, you can access the application on `localhost:8080`. You can check that all services are running correctly with `docker-compose ps`. You should see `lottery-api`, `lottery-draw`, `nginx`, and `redis` all in the `running` state.

You can now interact with the services through the Nginx gateway. Here are some example `curl` commands to test the endpoints.

### Step 4: Shutting Down

To stop and remove all the containers and networks created by Docker Compose, run:
```bash
docker-compose down
```

***

## Key Features & Configuration

### Scaling the API Service

The `lottery-api` service is designed to be horizontally scaled. You can run multiple instances of it to handle increased load. The Nginx gateway is configured to automatically load-balance traffic between all running instances in a round-robin fashion.

To scale up the service, use the `--scale` flag with `docker-compose up`. For example, to run 3 instances of the API:

```bash
docker-compose up -d --scale lottery-api={NO_INSTANCES}
```

### Lottery API
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

***

## Lottery Draw
### **Automatic Draw**

By default, the `lottery-draw` service will perform the daily lottery draw at 00:01 in the `Europe/Amsterdam` timezone and will draw a winner for the previous day's lottery.
You can customize this schedule by setting the following environment variables in the `docker-compose.yml` file for the `lottery-draw` service:

* `TIME_ZONE`: Sets the timezone for the cron job. Must be a valid Java ZoneId (e.g., `"Europe/Amsterdam"`, `"America/New_York"`).

* `DRAW_TIME`: Sets the time of day for the draw in `HH:mm` format (e.g., `"00:01"`).

* `DAY_OFFSET`: Sets which day's lottery to draw. `1` means draw yesterday's lottery (default), `0` means draw today's lottery.

### **Manual Draw (Developer Endpoint)**

This endpoint is useful for testing the draw logic without waiting for the cron job. Remember to replace API_SECRET with the secret-key defined in docker-compose.yml and use a valid date. (one that is not in the past)

```bash
curl --request POST 'localhost:8080/draw/api/v1/{{YYYY-MM-DD}}' \
--header 'X-Api-Secret: {{API_SECRET}}'
```

***

## Email Notifications (MailHog)

To test the email notification functionality for lottery winners, this project includes **MailHog**, a mock email server that catches and displays emails instead of sending them.

* **Accessing the Mailbox:** You can view the mock inbox by navigating to **`http://localhost:8080/mail/`** in your web browser.
* **How it Works:** When the `lottery-draw` service performs a draw and selects a winner, it sends a notification email. MailHog intercepts this email, and it will appear instantly in the web UI. This allows you to verify that the email sending logic is working correctly, view the content of the email, and confirm the recipient is correct.
* **Disclaimer:** MailHog is a development and testing tool only. It is not a real SMTP server. All emails sent by the application will be caught and displayed in this single, shared inbox.

***

## Redis Persistence

The Redis service is configured for durability to ensure that no participant or ballot data is lost.

* **AOF (Append Only File) Persistence:** The `docker-compose.yml` file starts Redis with the `--appendonly yes` flag. This instructs Redis to log every write operation to a file on disk, providing strong durability.

* **Docker Volume:** A named volume (`redis_data`) is configured to store this AOF file. This means the data lives on your host machine, outside the container. If you shut down and remove the containers with `docker-compose down`, the data will still be there. When you run `docker-compose up` again, Redis will automatically reload the data from the persisted file.