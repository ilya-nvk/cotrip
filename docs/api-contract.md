# CoTrip API Contract (v1)

This contract matches the current Android UI screens and agreed rules. JSON over HTTPS. WebSocket is used for comments.

## Base

- Base path: `/v1`
- Auth: `Authorization: Bearer <jwt>`
- All IDs are `uuid` strings.
- All times are ISO 8601.

## Standard responses

Success list response:

```json
{
  "items": [],
  "nextCursor": null
}
```

Error response:

```json
{
  "error": {
    "code": "string",
    "message": "string",
    "details": {}
  }
}
```

## Access rules

- No admin role.
- Trip owner can approve/reject ideas, transfer ownership, archive/delete trip, manage members.
- Members can create/update their own ideas, comments, expenses, activities.
- Comments can be deleted only by their author.
- Joining a trip is possible only via invite link.

## DTOs (compact)

```json
// User
{
  "id": "uuid",
  "name": "string",
  "photoUrl": "string|null",
  "initials": "string"
}

// Trip
{
  "id": "uuid",
  "ownerId": "uuid",
  "title": "string",
  "description": "string|null",
  "startDate": "YYYY-MM-DD",
  "endDate": "YYYY-MM-DD",
  "locationLine": "string|null",
  "coverUrl": "string|null",
  "currencyCode": "string",
  "status": "active|archived",
  "updatedAt": "2025-01-01T10:00:00Z"
}

// Idea
{
  "id": "uuid",
  "tripId": "uuid",
  "authorId": "uuid",
  "title": "string",
  "city": "string|null",
  "costAmount": 123.45,
  "costType": "per_person|total|null",
  "website": "string|null",
  "notes": "string|null",
  "status": "pending|approved|rejected",
  "updatedAt": "2025-01-01T10:00:00Z"
}

// Activity (itinerary item)
{
  "id": "uuid",
  "dayId": "uuid",
  "sourceIdeaId": "uuid|null",
  "title": "string",
  "timeText": "string|null",
  "locationName": "string|null",
  "locationLink": "string|null",
  "costAmount": 50.0,
  "costType": "per_person|total|null",
  "website": "string|null",
  "notes": "string|null",
  "orderIndex": 0
}

// Expense
{
  "id": "uuid",
  "tripId": "uuid",
  "title": "string",
  "amount": 150.0,
  "currencyCode": "string",
  "status": "planned|paid",
  "paidById": "uuid|null",
  "date": "YYYY-MM-DD|null",
  "splitType": "equally|custom",
  "note": "string|null",
  "participants": [
    {
      "userId": "uuid",
      "shareAmount": 50.0,
      "isIncluded": true,
      "isPaid": false
    }
  ]
}
```

## Auth

`POST /v1/auth/google`

Request:

```json
{ "idToken": "string" }
```

Response:

```json
{ "accessToken": "jwt", "user": { "id": "uuid", "name": "string", "photoUrl": null, "initials": "IN" } }
```

## Users

`GET /v1/users/me`

`PATCH /v1/users/me`

Request:

```json
{ "name": "string", "photoUrl": "string|null" }
```

`DELETE /v1/users/me`

## Trips

`POST /v1/trips`

Request:

```json
{ "title": "string", "description": "string|null", "startDate": "YYYY-MM-DD", "endDate": "YYYY-MM-DD", "locationLine": "string|null", "coverUrl": "string|null", "currencyCode": "string" }
```

`GET /v1/trips?status=active|upcoming|past|archived`

`GET /v1/trips/{tripId}`

`PATCH /v1/trips/{tripId}`

`DELETE /v1/trips/{tripId}`

`POST /v1/trips/{tripId}/archive`

`POST /v1/trips/{tripId}/transfer-owner`

Request:

```json
{ "newOwnerId": "uuid" }
```

## Trip members

`GET /v1/trips/{tripId}/members`

`DELETE /v1/trips/{tripId}/members/{userId}`

## Invites

Invite link is rotated every 12 hours. Only one active link per trip.

`POST /v1/trips/{tripId}/invite`

Response:

```json
{ "token": "string", "url": "https://...", "expiresAt": "2025-01-01T22:00:00Z" }
```

`GET /v1/invites/{token}`

`POST /v1/invites/{token}/accept`

## Ideas

`GET /v1/trips/{tripId}/ideas?search=&status=&authorId=&city=`

`POST /v1/trips/{tripId}/ideas`

`GET /v1/ideas/{ideaId}`

`PATCH /v1/ideas/{ideaId}`

`DELETE /v1/ideas/{ideaId}`

`POST /v1/ideas/{ideaId}/approve`

`POST /v1/ideas/{ideaId}/reject`

`POST /v1/ideas/{ideaId}/convert-to-activity`

Request:

```json
{ "dayId": "uuid", "timeText": "string|null", "orderIndex": 0 }
```

## Comments (WebSocket required)

Comment history is fetched via REST, realtime updates go through WebSocket.

`GET /v1/ideas/{ideaId}/comments`

`DELETE /v1/comments/{commentId}`

WebSocket:

`wss://{host}/v1/ws/trips/{tripId}/comments?token=jwt`

Client message:

```json
{ "type": "comment.create", "payload": { "ideaId": "uuid", "body": "string" } }
```

Server broadcast:

```json
{ "type": "comment.created", "payload": { "id": "uuid", "ideaId": "uuid", "authorId": "uuid", "body": "string", "createdAt": "2025-01-01T10:00:00Z" } }
```

Delete message:

```json
{ "type": "comment.deleted", "payload": { "id": "uuid", "ideaId": "uuid" } }
```

## Itinerary

`GET /v1/trips/{tripId}/itinerary`

`PATCH /v1/itinerary/days/{dayId}`

Request:

```json
{ "city": "string|null" }
```

`POST /v1/itinerary/days/{dayId}/activities`

`PATCH /v1/itinerary/activities/{activityId}`

`DELETE /v1/itinerary/activities/{activityId}`

`POST /v1/itinerary/days/{dayId}/activities/reorder`

Request:

```json
{ "orderedIds": ["uuid", "uuid"] }
```

`POST /v1/trips/{tripId}/itinerary/trim-out-of-range`

Request:

```json
{ "action": "keep|remove", "dayIds": ["uuid"] }
```

## Expenses

Server stores only raw expenses and splits. Balances are computed on the client.

`GET /v1/trips/{tripId}/expenses`

`POST /v1/trips/{tripId}/expenses`

`GET /v1/expenses/{expenseId}`

`PATCH /v1/expenses/{expenseId}`

`DELETE /v1/expenses/{expenseId}`

## Weather

`GET /v1/trips/{tripId}/weather?city=&start=&end=`

`POST /v1/trips/{tripId}/weather/refresh`

## AI (Alice AI)

`POST /v1/trips/{tripId}/ai/suggestions`

Request:

```json
{ "city": "string", "description": "string", "typeOptions": ["string"], "timeOfDayOptions": ["string"], "budgetOptions": ["string"] }
```

Response:

```json
{ "items": [ { "id": "uuid", "title": "string", "description": "string", "typeLabel": "string", "durationLabel": "string", "budgetLabel": "string", "estimatedCost": 120.0, "isSaved": false } ] }
```

`POST /v1/ai/suggestions/{id}/save-to-ideas`

## Notifications

`GET /v1/notifications`

`PATCH /v1/notifications/{id}/read`

`GET /v1/users/me/notification-settings`

`PATCH /v1/users/me/notification-settings`

Request:

```json
{ "items": [ { "key": "string", "enabled": true } ] }
```

## Sync

`GET /v1/sync/changes?since=2025-01-01T10:00:00Z`

Response:

```json
{ "items": [ { "entity": "string", "id": "uuid", "updatedAt": "2025-01-01T10:00:00Z", "deletedAt": null, "payload": {} } ], "nextCursor": null }
```

`POST /v1/sync/changes`

Request:

```json
{ "items": [ { "entity": "string", "id": "uuid", "type": "upsert|delete", "payload": {} } ] }
```

Response:

```json
{ "applied": ["uuid"], "conflicts": [ { "id": "uuid", "reason": "string" } ] }
```
