
# 📝 Todo App – Spring Boot REST API

A **production-grade Spring Boot REST API** for managing todo lists and todo items with:

* JWT-based authentication
* Ownership-based authorization
* Proper HTTP status codes
* Centralized exception handling
* Clean layered architecture

This project demonstrates how a real-world backend should be designed, structured, and secured.

---

## Database Design (Version 1)

### Entity Relationship Overview

```
User (1) ──── (N) TodoList (1) ──── (N) TodoItem (1) ──── (N) TodoItemCompletion
```

---

## Tables

### User

| Column   | Type    | Constraints      |
| -------- | ------- | ---------------- |
| id       | BIGINT  | Primary Key      |
| email    | VARCHAR | UNIQUE, NOT NULL |
| username | VARCHAR | NOT NULL         |
| password | VARCHAR | NOT NULL         |

---

### TodoList

| Column  | Type    | Constraints             |
| ------- | ------- | ----------------------- |
| id      | BIGINT  | Primary Key             |
| name    | VARCHAR | NOT NULL                |
| user_id | BIGINT  | FK → User(id), NOT NULL |

Unique constraint:

* `(user_id, name)` — a user cannot have two todo lists with the same name

---

### TodoItem

| Column       | Type    | Constraints                 |
| ------------ | ------- | --------------------------- |
| id                     | BIGINT  | Primary Key                 |
| title                  | VARCHAR | NOT NULL                    |
| status                 | ENUM    | NOT NULL                    |
| deadline               | DATE    | NULLABLE                    |
| todo_list_id           | BIGINT  | FK → TodoList(id), NOT NULL |
| recurring              | BOOLEAN | NOT NULL, default false     |
| recurrence_type        | ENUM    | NULLABLE                    |
| scheduled_date         | DATE    | NULLABLE                    |
| next_occurrence        | DATE    | NULLABLE                    |
| recurrence_anchor_date | DATE    | NULLABLE                    |

Allowed status values:

```
CREATED
IN_PROGRESS
COMPLETED
NOT_APPLICABLE
```

Allowed recurrence type values:

```
DAILY
WEEKLY
MONTHLY
```

Check constraint `todo_items_recurrence_chk`: the recurrence columns are all set when
`recurring` is true and all null when it is false.

`recurrence_anchor_date` is internal (not exposed by the API). It is the date the current
schedule was set from, so monthly items keep their day of month (see Recurring Todo Items).

---

### TodoItemCompletion

Completion history of recurring todo items.

| Column          | Type     | Constraints                 |
| --------------- | -------- | --------------------------- |
| id              | BIGINT   | Primary Key                 |
| todo_item_id    | BIGINT   | FK → TodoItem(id), NOT NULL |
| occurrence_date | DATE     | NOT NULL                    |
| completed_at    | DATETIME | NOT NULL                    |

---

## Foreign Keys and Cascade Rules

* `TodoList.user_id → User.id`
* `TodoItem.todo_list_id → TodoList.id`
* `TodoItemCompletion.todo_item_id → TodoItem.id`

Cascade behavior:

* Deleting a User deletes all TodoLists and TodoItems
* Deleting a TodoList deletes all TodoItems
* Deleting a TodoItem deletes its completion history

---

## Tech Stack

* Java 17
* Spring Boot
* Spring Web
* Spring Data JPA
* Spring Security
* JWT (io.jsonwebtoken)
* MySQL
* Flyway
* Lombok

---

## Base URL

```
http://localhost:8080/api/v1
```

---

## Authentication & Security

### Authentication Model

* Stateless JWT authentication
* No sessions
* No refresh tokens (Version 1)
* JWT contains `userId`
* `userId` is extracted as `Authentication.getPrincipal()`

---

### Authorization Model

* Every TodoList belongs to exactly one user
* Every TodoItem belongs to exactly one TodoList
* Users can only access resources they own
* Ownership is enforced in the service layer
* Prevents IDOR vulnerabilities

---

## Auth APIs

### Register User

**POST** `/auth/register`
Bearer Token: NOT required

Request body:

```json
{
  "email": "user@example.com",
  "username": "john",
  "password": "password123"
}
```

Successful response – 201 Created:

```json
{
  "id": 1,
  "email": "user@example.com",
  "username": "john"
}
```

Possible errors:

* 409 Conflict – email already registered
* 400 Bad Request – invalid input

---

### Login User

**POST** `/auth/login`
Bearer Token: NOT required

Request body:

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

Successful response – 200 OK:

```json
{
  "accessToken": "jwt-token-here"
}
```

Possible errors:

* 401 Unauthorized – invalid email or password
* 400 Bad Request – invalid input

---

## User APIs

### Get Current User Profile

**GET** `/users/me`
Bearer Token: REQUIRED

Headers:

```
Authorization: Bearer <jwt-token>
```

Successful response – 200 OK:

```json
{
  "id": 1,
  "email": "user@example.com",
  "username": "john"
}
```

Possible errors:

* 401 Unauthorized – missing or invalid token
* 404 Not Found – user not found

---

## Todo List APIs

All TodoList APIs require authentication.

---

### Create Todo List

**POST** `/lists`
Bearer Token: REQUIRED

Request body:

```json
{
  "name": "Work"
}
```

Successful response – 201 Created:

```json
{
  "id": 1,
  "name": "Work"
}
```

Possible errors:

* 401 Unauthorized – missing or invalid token
* 409 Conflict – duplicate list name for user
* 400 Bad Request – invalid input

---

### Get All Todo Lists of Current User

**GET** `/lists`
Bearer Token: REQUIRED

Successful response – 200 OK:

```json
[
  {
    "id": 1,
    "name": "Work"
  },
  {
    "id": 2,
    "name": "Personal"
  }
]
```

Possible errors:

* 401 Unauthorized – missing or invalid token

---

### Update Todo List

**PUT** `/lists/{listId}`
Bearer Token: REQUIRED

Path variable:

* `listId` – ID of the todo list

Request body:

```json
{
  "name": "Updated Work"
}
```

Successful response – 200 OK:

```json
{
  "id": 1,
  "name": "Updated Work"
}
```

Possible errors:

* 401 Unauthorized
* 403 Forbidden – list belongs to another user
* 404 Not Found – list does not exist
* 409 Conflict – duplicate list name

---

### Delete Todo List

**DELETE** `/lists/{listId}`
Bearer Token: REQUIRED

Successful response – 200 OK:

```json
{
  "id": 1,
  "name": "Work"
}
```

Possible errors:

* 401 Unauthorized
* 403 Forbidden – list belongs to another user
* 404 Not Found – list does not exist

Cascade delete applies to todo items.

---

## Todo Item APIs

All TodoItem APIs are scoped under a TodoList.

Base path:

```
/lists/{listId}/items
```

Bearer Token: REQUIRED for all endpoints below.

---

### Create Todo Item

**POST** `/lists/{listId}/items`

Request body:

```json
{
  "title": "Buy milk",
  "status": "CREATED",
  "deadline": "2026-01-10"
}
```

Successful response – 201 Created:

```json
{
  "id": 10,
  "title": "Buy milk",
  "status": "CREATED",
  "deadline": "2026-01-10",
  "recurring": false,
  "recurrenceType": null,
  "scheduledDate": null,
  "nextOccurrence": null
}
```

Possible errors:

* 401 Unauthorized
* 403 Forbidden – list belongs to another user
* 404 Not Found – list does not exist
* 400 Bad Request – invalid status or input

Create a recurring todo item:

```json
{
  "title": "Pay rent",
  "status": "CREATED",
  "recurring": true,
  "recurrenceType": "MONTHLY",
  "scheduledDate": "2026-01-31"
}
```

Successful response – 201 Created:

```json
{
  "id": 12,
  "title": "Pay rent",
  "status": "CREATED",
  "deadline": null,
  "recurring": true,
  "recurrenceType": "MONTHLY",
  "scheduledDate": "2026-01-31",
  "nextOccurrence": "2026-02-28"
}
```

See [Recurring Todo Items](#recurring-todo-items) for the validation rules.

---

### Get All Todo Items in a List

**GET** `/lists/{listId}/items`

Successful response – 200 OK:

```json
[
  {
    "id": 10,
    "title": "Buy milk",
    "status": "CREATED",
    "deadline": "2026-01-10",
    "recurring": false,
    "recurrenceType": null,
    "scheduledDate": null,
    "nextOccurrence": null
  },
  {
    "id": 11,
    "title": "Pay bills",
    "status": "IN_PROGRESS",
    "deadline": null,
    "recurring": false,
    "recurrenceType": null,
    "scheduledDate": null,
    "nextOccurrence": null
  }
]
```

Possible errors:

* 401 Unauthorized
* 403 Forbidden
* 404 Not Found – list does not exist

---

### Get One Todo Item

**GET** `/lists/{listId}/items/{itemId}`

Successful response – 200 OK:

```json
{
  "id": 10,
  "title": "Buy milk",
  "status": "CREATED",
  "deadline": "2026-01-10",
  "recurring": false,
  "recurrenceType": null,
  "scheduledDate": null,
  "nextOccurrence": null
}
```

Possible errors:

* 401 Unauthorized
* 403 Forbidden
* 404 Not Found – item or list not found

---

### Update Todo Item

**PUT** `/lists/{listId}/items/{itemId}`

Partial updates allowed.

Request body:

```json
{
  "status": "COMPLETED"
}
```

Successful response – 200 OK:

```json
{
  "id": 10,
  "title": "Buy milk",
  "status": "COMPLETED",
  "deadline": "2026-01-10",
  "recurring": false,
  "recurrenceType": null,
  "scheduledDate": null,
  "nextOccurrence": null
}
```

Possible errors:

* 401 Unauthorized
* 403 Forbidden
* 404 Not Found
* 400 Bad Request

---

### Delete Todo Item

**DELETE** `/lists/{listId}/items/{itemId}`

Successful response – 200 OK:

```json
{
  "id": 10,
  "title": "Buy milk",
  "status": "COMPLETED",
  "deadline": "2026-01-10",
  "recurring": false,
  "recurrenceType": null,
  "scheduledDate": null,
  "nextOccurrence": null
}
```

Possible errors:

* 401 Unauthorized
* 403 Forbidden
* 404 Not Found

Deleting a recurring todo item also deletes its completion history.

---

### Get Completion History of a Todo Item

**GET** `/lists/{listId}/items/{itemId}/completions`

Returns the completed occurrences of a recurring todo item, oldest first
(an empty list for one-time items).

Successful response – 200 OK:

```json
[
  {
    "id": 1,
    "occurrenceDate": "2026-01-31",
    "completedAt": "2026-01-31T18:04:12.345Z"
  },
  {
    "id": 2,
    "occurrenceDate": "2026-02-28",
    "completedAt": "2026-03-01T09:30:00.000Z"
  }
]
```

Possible errors:

* 401 Unauthorized
* 403 Forbidden – item belongs to another user
* 404 Not Found – item or list not found

---

## Recurring Todo Items

A todo item is either one-time (default) or recurring. Recurring items use
`scheduledDate` (the current occurrence) instead of `deadline`.

### Fields

| Field            | Request       | Response | Description                                    |
| ---------------- | ------------- | -------- | ---------------------------------------------- |
| `recurring`      | optional      | always   | `true` for recurring items, defaults to `false` |
| `recurrenceType` | when recurring | always   | `DAILY`, `WEEKLY` or `MONTHLY`                 |
| `scheduledDate`  | when recurring | always   | Date of the current occurrence (`yyyy-MM-dd`)  |
| `nextOccurrence` | –             | always   | Date the item moves to once completed          |

### Completing a recurring item

Completing uses the existing update endpoint:

**PUT** `/lists/{listId}/items/{itemId}` with `{"status": "COMPLETED"}`

For a recurring item this:

1. Saves the current `scheduledDate` in the completion history
2. Moves `scheduledDate` to `nextOccurrence` and calculates a new `nextOccurrence`
3. Resets `status` to `CREATED`, so the recurring item is never permanently completed

One-time items are completed exactly as before. The next occurrence always follows the
current `scheduledDate`, even when an overdue occurrence is completed late.

### Recurrence rules

| Type      | Next occurrence                                                          |
| --------- | ------------------------------------------------------------------------ |
| `DAILY`   | Next calendar day                                                        |
| `WEEKLY`  | Same day of the following week                                           |
| `MONTHLY` | Same day of the following month, or its last day when that day is missing |

Monthly items keep the day of month they were scheduled on: an item scheduled on
January 31 moves to February 28 (February 29 in leap years), then back to March 31,
then April 30.

### Updating

Updates are partial, as for one-time items:

* Changing `recurrenceType` or `scheduledDate` recalculates `nextOccurrence`
* `{"recurring": true, "recurrenceType": ..., "scheduledDate": ...}` converts a one-time
  item into a recurring one (its `deadline` is cleared)
* `{"recurring": false}` converts a recurring item into a one-time item (its recurrence
  fields are cleared, the completion history is kept)

### Validation

Requests are rejected with 400 Bad Request when:

* `recurrenceType` is not `DAILY`, `WEEKLY` or `MONTHLY`
* A recurring item has no `recurrenceType` or no `scheduledDate`
* `recurrenceType` or `scheduledDate` is sent for a one-time item
* `deadline` is sent for a recurring item
* A recurring item is created with status `COMPLETED`
* A date is invalid or not in `yyyy-MM-dd` format (e.g. `2026-02-30`)

---

## Error Handling

All errors are handled via `GlobalExceptionHandler`.

Error response format:

```json
{
  "message": "Human readable error message",
  "status": 404,
  "timestamp": "2026-01-11T14:30:00Z"
}
```

Mapped exceptions:

| Exception             | HTTP Status |
| --------------------- | ----------- |
| NotFoundException     | 404         |
| ForbiddenException    | 403         |
| UnauthorizedException | 401         |
| ConflictException     | 409         |
| BadRequestException   | 400         |

---

## Database & Migrations

* Managed using Flyway
* Initial schema defined in `V1__init_schema.sql`
* Recurring todo items added in `V2__add_recurring_todo_items.sql`
* Migrations run before Hibernate validates the schema on application startup
* Safe, versioned migrations

---

## Architectural Principles

* Layered architecture (Controller → Service → Repository)
* DTO-based API (entities never exposed)
* Stateless security
* Ownership enforced in service layer
* Centralized exception handling
* Proper HTTP semantics
* Clean separation of concerns

---

## Current Status

* JWT authentication implemented
* All TodoList and TodoItem APIs secured
* Ownership enforced across all operations
* Proper HTTP status codes used
* Global exception handling in place
* Recurring (daily, weekly, monthly) todo items with completion history
* Backend is production-ready

## Running Tests

```
./mvnw test
```

Unit and integration tests run against an in-memory H2 database (`test` profile), so no
MySQL is needed. `TodoappApplicationTests` starts the application with its real
configuration and needs the `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and `JWT_SECRET`
environment variables (see `.env.example`).

## License

This project is licensed under the MIT License.
See the LICENSE file for details.

