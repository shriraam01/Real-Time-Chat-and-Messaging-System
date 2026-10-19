# Real-Time Chat and Messaging System

A scalable real-time chat platform built with **Java, Spring Boot, WebSocket (STOMP), JWT authentication and MySQL**, with a lightweight browser client served by the same application.

Messages travel over persistent WebSocket connections instead of polling, so they appear on the recipient's screen the moment they are saved.

## Features

- Registration and login with BCrypt-hashed passwords and JWT tokens
- One-to-one chats and group chats (creator is admin: add members, remove members, anyone can leave)
- Live message delivery over WebSocket (STOMP)
- Message history with pagination (older messages load as you scroll up)
- Online / offline status and "last seen"
- Message ticks: sent, delivered, read (blue) - for groups, "read" means everyone has read it
- Unread message counters per conversation
- Messages sent while a user is offline are stored and delivered when they reconnect
- User search for starting chats and adding group members
- Light and dark theme, usable on mobile screens

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot, Spring Web, Spring Security, Spring Data JPA, Spring WebSocket (STOMP) |
| Auth | JWT (JJWT), BCrypt |
| Database | MySQL (Hibernate creates the tables) |
| Frontend | HTML, CSS and plain JavaScript, STOMP over WebSocket (`@stomp/stompjs`) |

## How it works

- **REST** handles things that do not need to be instant: register, login, user search, creating chats and groups, loading history.
- **WebSocket (STOMP)** handles everything live: sending messages, receiving messages, read receipts, presence.
- The JWT is sent in the STOMP `CONNECT` frame (browsers cannot set headers on a WebSocket handshake). An interceptor validates it and ties the user to the connection, so the client never says who it is.
- Each user subscribes to personal queues under `/user/queue/...` (`messages`, `receipts`, `presence`, `conversations`, `errors`). The server pushes to a specific user, and users can only subscribe to their own queues.
- **Ticks** are derived from two numbers stored per conversation member: the newest message they have received and the newest they have read. A message is *delivered* when all other members' delivered position has reached it, and *read* when all their read positions have.
- **Presence** is kept in memory (a user counts as online while at least one session is open); "last seen" is stored when their last session closes.

## Run it

Requirements: JDK 21, MySQL 8, Maven wrapper (included).

1. Create the database:
   ```sql
   CREATE DATABASE chatdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
2. Copy `chat-backend/src/main/resources/application.properties.example` to `application.properties` and fill in your MySQL password and a JWT secret. Generate a secret with:
   ```powershell
   $bytes = New-Object byte[] 32; [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes); [Convert]::ToBase64String($bytes)
   ```
3. Start the app from `chat-backend`:
   ```
   ./mvnw spring-boot:run
   ```
   (or run `ChatBackendApplication` from your IDE)
4. Open <http://localhost:8081>, register two users (use a normal window and a private window, or two browsers) and start chatting.

## REST API

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/register` | Create an account |
| POST | `/api/auth/login` | Log in, returns a JWT |
| GET | `/api/users/me` | Current user |
| GET | `/api/users?search=` | Search other users |
| GET | `/api/conversations` | My conversations (members, last message, unread count) |
| GET | `/api/conversations/{id}` | One conversation |
| POST | `/api/conversations` | Start a one-to-one chat `{username}` |
| POST | `/api/conversations/group` | Create a group `{name, usernames[]}` |
| POST | `/api/conversations/{id}/members` | Add a member (group creator only) |
| DELETE | `/api/conversations/{id}/members/{username}` | Remove a member, or leave the group |
| GET | `/api/conversations/{id}/messages?page=&size=` | Message history, newest first |

## WebSocket (STOMP) endpoint `/ws`

| Direction | Destination | Payload |
|---|---|---|
| client sends | `/app/chat.send` | `{conversationId, content}` |
| client sends | `/app/chat.read` | `{conversationId}` |
| client receives | `/user/queue/messages` | a saved message |
| client receives | `/user/queue/receipts` | `{conversationId, username, lastDeliveredMessageId, lastReadMessageId}` |
| client receives | `/user/queue/presence` | `{username, online, lastSeen}` |
| client receives | `/user/queue/conversations` | `{action: CHANGED or REMOVED, conversationId}` |
| client receives | `/user/queue/errors` | `{error}` |

## Project structure

```
chat-backend/src/main/java/com/chatapp/
  config/       SecurityConfig, WebSocketConfig
  controller/   AuthController, UserController, ConversationController, MessageController, ChatController (WebSocket)
  dto/          request / response / event records
  entity/       User, Conversation, ConversationMember, Message
  exception/    custom exceptions and the global error handler
  repository/   Spring Data JPA repositories
  security/     JwtService, JwtAuthFilter, CustomUserDetailsService, StompAuthChannelInterceptor
  service/      UserService, AuthService, ConversationService, MessageService,
                ReceiptService, PresenceService, NotificationService
  websocket/    PresenceEventListener
chat-backend/src/main/resources/static/   index.html, style.css, app.js (the web client)
```

## Possible next steps

- Redis pub/sub as the message broker so several server instances can share live connections
- Typing indicators, file sharing, message search
- React frontend, Docker Compose setup, automated tests
