# API

Through the nginx gateway on `localhost:8080`, the participant API is under `/service/api/v1` and the draw service under `/draw/api/v1`. Dates are `YYYY-MM-DD`. Error responses have a short plain-text body explaining the problem.

## Register a participant

`POST /service/api/v1/participants`

```json
{ "name": "Ada", "email": "ada@example.com" }
```

| Status | When |
|---|---|
| `201 Created` | Registered; the body is the participant, with `registeredAt` |
| `400 Bad Request` | The body isn't valid JSON, the name is blank or over 100 characters, or the email isn't a valid address |
| `409 Conflict` | A participant with that email is already registered. Emails are compared trimmed and in lower case |

## Submit ballots

`POST /service/api/v1/lotteries/{date}/ballots`

```json
{ "email": "ada@example.com", "count": 5 }
```

```json
{ "lotteryDate": "2030-06-15", "drawDate": "2030-06-16", "ballotsSubmitted": 5, "totalBallotsForLottery": 8 }
```

`totalBallotsForLottery` is how many ballots this participant now holds in that lottery.

| Status | When |
|---|---|
| `200 OK` | The ballots were added |
| `400 Bad Request` | The date is in the past (in `TIME_ZONE`), `count` isn't between 1 and 1000, the email isn't registered, or the body isn't valid |
| `409 Conflict` | That lottery is being drawn or has already been drawn |

## Get a lottery's winner

`GET /service/api/v1/lotteries/{date}/winner`

```json
{ "lotteryDate": "2030-06-15", "winningBallot": "6f1c…", "winnerName": "Ada", "drawnAt": "2030-06-16T00:01:00" }
```

| Status | When |
|---|---|
| `200 OK` | The lottery has been drawn. `winnerName` is empty if the winner is no longer registered |
| `404 Not Found` | It hasn't been drawn yet |

## Draw a lottery now (developer endpoint)

`POST /draw/api/v1/{date}` with header `X-Api-Secret: <API_SECRET>`

| Status | When |
|---|---|
| `200 OK` | Drawn; the body has the winning ballot, the winner and the time of the draw |
| `401 Unauthorized` | The secret is missing or wrong |
| `404 Not Found` | That lottery has no ballots, or was already drawn |
| `409 Conflict` | Another draw for that date is in progress |
