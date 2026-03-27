# Nafiul Chatbot

A full-stack AI chat application built with **Spring Boot** (backend) and **React** (frontend), powered by the **Groq free API** using LLaMA 3.3 70B. Fully Dockerized for easy deployment.

---

## Features

- User registration and login with JWT authentication
- Per-user chat sessions — your history is private and scoped to your account
- **RAG (Retrieval-Augmented Generation)** — drop PDFs in `backend/src/main/resources/docs/` and the chatbot answers questions using your documents
- Automatic PDF ingestion on startup with local ONNX embeddings (no extra API key needed)
- Vector store persisted to disk — survives restarts without re-processing
- Persistent chat history stored in **MySQL** — survives server restarts
- Multiple independent chat sessions (like ChatGPT sidebar)
- Starts a fresh new chat on every login; previous sessions visible in the sidebar
- Per-session conversation memory using `sessionId`
- Editable system prompt per session
- Markdown rendering — code blocks, tables, lists
- Animated loading indicator while waiting for response
- Auto-renames sessions from the first message
- Dark violet theme UI
- **Fully Dockerized** — one command to run everything

---

## Quick Start (Docker)

### Prerequisites

- [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/)
- [Groq API key](https://console.groq.com) (free)

### 1. Clone and configure

```bash
cd code
cp .env.example .env
```

Edit `.env` and set your Groq API key:
```
GROQ_API_KEY=gsk_your_actual_key_here
```

### 2. Start everything

```bash
docker compose up -d
```

This starts 4 services:

| Service | URL | Description |
|---------|-----|-------------|
| Frontend | http://localhost | React app served by Nginx |
| Backend API | http://localhost:8080 | Spring Boot REST API |
| phpMyAdmin | http://localhost:8081 | MySQL web UI |
| MySQL | localhost:3307 | Database (external access) |

### 3. Use the app

Open **http://localhost** in your browser. Register an account and start chatting.

### Stop / restart

```bash
docker compose down        # stop all services
docker compose up -d       # start again (data persists in volume)
docker compose down -v     # stop and delete all data
```

### Rebuild after code changes

```bash
docker compose up -d --build           # rebuild all
docker compose up -d --build backend   # rebuild backend only
docker compose up -d --build frontend  # rebuild frontend only
```

---

## Local Development (without Docker)

For faster development, run only MySQL in Docker and run backend/frontend locally:

### Prerequisites

| Tool | Version | Install |
|---|---|---|
| Java | 21+ | https://adoptium.net |
| Maven | 3.9+ | `sudo apt install maven` |
| Node.js | 18+ | https://nodejs.org |
| npm | 9+ | comes with Node.js |
| Docker | latest | https://docs.docker.com/get-docker/ |
| Groq API key | free | https://console.groq.com |

### 1. Start MySQL + phpMyAdmin in Docker

```bash
cd code
docker compose up -d mysql phpmyadmin
```

### 2. Run the backend

```bash
cd code/backend
export GROQ_API_KEY=gsk_your_actual_key_here
export MYSQL_HOST=localhost
export MYSQL_PORT=3307
export MYSQL_USER=groquser
export MYSQL_PASSWORD=groqpass
mvn spring-boot:run
```

Backend runs at **http://localhost:8080**

### 3. Run the frontend

Open a new terminal:

```bash
cd code/frontend
npm install
npm run dev
```

Frontend runs at **http://localhost:5173** (Vite proxies `/api` to backend automatically)

---

## Project Structure

```
code/
├── docker-compose.yml                           orchestrates all services
├── .env.example                                 environment variable template
├── backend/                                     Spring Boot API
│   ├── Dockerfile                               multi-stage build (Maven → JRE 21)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/example/groqchat/
│       │   ├── GroqChatApplication.java         entry point
│       │   ├── config/
│       │   │   ├── GroqConfig.java              reads yml, builds WebClient
│       │   │   └── RagConfig.java               RAG settings, SimpleVectorStore bean
│       │   ├── dto/
│       │   │   ├── Message.java                 {role, content}
│       │   │   ├── ChatRequest.java             sent to Groq API
│       │   │   ├── ChatResponse.java            received from Groq API
│       │   │   ├── UserMessage.java             received from frontend
│       │   │   ├── RegisterRequest.java         register payload
│       │   │   ├── LoginRequest.java            login payload
│       │   │   └── AuthResponse.java            {token, username}
│       │   ├── entity/
│       │   │   ├── User.java                    JPA entity — users table
│       │   │   ├── ChatSession.java             JPA entity — chat_sessions table
│       │   │   └── ChatMessageEntity.java       JPA entity — chat_messages table
│       │   ├── repository/
│       │   │   ├── UserRepository.java
│       │   │   ├── ChatSessionRepository.java
│       │   │   └── ChatMessageRepository.java
│       │   ├── security/
│       │   │   ├── JwtUtil.java                 token generation & validation
│       │   │   ├── JwtAuthFilter.java           reads Bearer token from headers
│       │   │   └── SecurityConfig.java          Spring Security configuration
│       │   ├── service/
│       │   │   ├── AuthService.java             register / login logic
│       │   │   ├── UserDetailsServiceImpl.java
│       │   │   ├── LlmService.java              session memory + RAG + Groq calls
│       │   │   ├── RagService.java              vector similarity search
│       │   │   └── DocumentIngestionService.java PDF loading + chunking on startup
│       │   └── controller/
│       │       ├── AuthController.java          /api/auth/**
│       │       └── ChatController.java          /api/chat/**
│       └── resources/
│           ├── application.yml                  config (API key, DB, JWT, model, RAG)
│           └── docs/                            drop PDF files here for RAG
│
└── frontend/                                    React + Vite
    ├── Dockerfile                               multi-stage build (Node → Nginx)
    ├── nginx.conf                               proxies /api to backend
    ├── package.json
    ├── vite.config.js                           dev proxy /api → localhost:8080
    ├── index.html
    └── src/
        ├── main.jsx
        ├── App.jsx                              auth state + session management
        ├── App.css
        ├── api.js                               authFetch() helper (Bearer token)
        ├── index.css
        └── components/
            ├── AuthPage.jsx/css                 login / register form
            ├── Sidebar.jsx/css                  session list, new/delete, logout
            ├── ChatWindow.jsx/css               messages + input box
            ├── MessageBubble.jsx/css             markdown rendering per message
            └── SystemPromptBar.jsx/css           editable system prompt
```

---

## Architecture

### Docker (production)

```
Browser → :80 (Nginx / Frontend)
              ├── static files (React build)
              └── /api/* → :8080 (Spring Boot Backend)
                                  └── :3306 (MySQL container)

phpMyAdmin → :8081 → MySQL container
```

### Local development

```
Browser → :5173 (Vite Dev Server)
              ├── React hot-reload
              └── /api/* (proxy) → :8080 (Spring Boot locally)
                                       └── :3307 (MySQL in Docker)
```

---

## How It Works

```
Browser
    │  POST /api/chat/memory
    │  Authorization: Bearer <jwt>
    │  { sessionId, message, systemPrompt }
    ▼
Spring Boot (port 8080)
    │  validates JWT → resolves user
    │  searches vector store for relevant document chunks (RAG)
    │  enriches system prompt with document context (if found)
    │  loads conversation history from MySQL
    │  appends new message, calls Groq API
    │  saves assistant reply back to MySQL
    ▼
Groq API (free) → LLaMA 3.3 70B response → Browser ✓
```

### RAG Pipeline

```
PDF files (backend/src/main/resources/docs/)
    │  [Startup: ApplicationReadyEvent]
    ▼
PagePdfDocumentReader → TokenTextSplitter → ONNX EmbeddingModel (local)
    │                                              │
    ▼                                              ▼
List<Document> (chunks)  →  SimpleVectorStore (in-memory + file-persisted)
                                    │
                                    ▼  [Query time]
                            similaritySearch(query)
                                    │
                                    ▼
                    Context injected into system prompt → Groq API → Response
```

- **First startup**: Reads PDFs → chunks text → generates embeddings → saves to `./data/vector-store.json`
- **Subsequent startups**: Loads vectors from file — skips PDF processing
- **To re-ingest**: Delete `./data/vector-store.json` (or the `vector_data` Docker volume) and restart

### MySQL Schema

Hibernate auto-creates all tables on startup:

```
users
  id           BIGINT        PK (auto-increment)
  username     VARCHAR(255)  UNIQUE
  email        VARCHAR(255)  UNIQUE
  password     VARCHAR(255)  BCrypt hashed
  created_at   DATETIME

chat_sessions
  id           VARCHAR(255)  PK (UUID)
  user_id      BIGINT        FK → users.id
  system_prompt TEXT
  created_at   DATETIME

chat_messages
  id           BIGINT        PK (auto-increment)
  session_id   VARCHAR(255)  FK → chat_sessions.id
  role         VARCHAR(20)   "system" | "user" | "assistant"
  content      TEXT
  created_at   DATETIME
```

---

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `GROQ_API_KEY` | Yes | — | Groq API key from https://console.groq.com |
| `MYSQL_ROOT_PASSWORD` | No | `root` | MySQL root password |
| `MYSQL_DB` | No | `groqchat` | MySQL database name |
| `MYSQL_USER` | No | `groquser` | MySQL application user |
| `MYSQL_PASSWORD` | No | `groqpass` | MySQL application password |
| `JWT_SECRET` | No | (built-in default) | JWT signing key (min 32 chars) |

---

## API Endpoints

### Auth (public)

| Method | Endpoint | Body | Description |
|---|---|---|---|
| `POST` | `/api/auth/register` | `{username, email, password}` | Create a new account |
| `POST` | `/api/auth/login` | `{username, password}` | Get a JWT token |

### Chat (requires `Authorization: Bearer <token>`)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/chat/memory` | Send a message with session memory |
| `GET` | `/api/chat/sessions` | List all sessions for the logged-in user |
| `GET` | `/api/chat/session/{id}/messages` | Load message history for a session |
| `DELETE` | `/api/chat/session/{id}` | Delete a session |
| `GET` | `/api/health` | Health check (public) |

### Example — Login

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "nafiul", "password": "secret"}'
```

```json
{ "token": "eyJhbGci...", "username": "nafiul" }
```

### Example — Chat

```bash
curl -X POST http://localhost:8080/api/chat/memory \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGci..." \
  -d '{
    "sessionId": "550e8400-e29b-41d4-a716-446655440000",
    "message": "What is Spring Boot?",
    "systemPrompt": "You are a Java expert. Be concise."
  }'
```

```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "response": "Spring Boot is an opinionated framework...",
  "historySize": "3"
}
```

---

## Configuration

All config is in `backend/src/main/resources/application.yml`:

```yaml
groq:
  api-key: ${GROQ_API_KEY}               # required — set as env variable
  base-url: https://api.groq.com/openai/v1
  model: llama-3.3-70b-versatile         # change model here
  temperature: 0.7                        # 0 = precise, 1+ = creative
  max-tokens: 1024                        # max response length

spring:
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/${MYSQL_DB:groqchat}
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root}
  jpa:
    hibernate:
      ddl-auto: update                    # auto-creates/updates tables

jwt:
  secret: ${JWT_SECRET:fallback-secret}  # override in production!
  expiration: 86400000                   # 24 hours in ms

rag:
  docs-path: classpath:docs/             # PDF files directory
  vector-store-path: ./data/vector-store.json  # persisted vector store
  chunk-size: 800                        # tokens per chunk
  chunk-overlap: 200                     # overlap between chunks
```

### Available Groq models (free tier)

| Model | Best For |
|---|---|
| `llama-3.3-70b-versatile` | Best quality (default) |
| `llama-3.1-8b-instant` | Fastest responses |
| `mixtral-8x7b-32768` | Long context tasks |
| `gemma2-9b-it` | Lightweight tasks |

---

## phpMyAdmin

Access at **http://localhost:8081** after running `docker compose up -d`.

| Field | Value |
|-------|-------|
| Username | `root` |
| Password | `root` |

Or use `groquser` / `groqpass` (limited to `groqchat` database only).

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `401 Unauthorized` on chat | JWT token missing or expired. Log out and log in again. |
| `401 Unauthorized` on Groq | `GROQ_API_KEY` is wrong or not set. Check `.env` file. |
| `429 Too Many Requests` | Hit Groq free tier rate limit. Wait ~1 minute. |
| `Connection refused` on port 8080 | Backend is not running. Check `docker compose logs backend`. |
| `Access denied for user` (MySQL) | Wrong credentials. Check `.env` matches `docker-compose.yml`. |
| Container keeps restarting | Check logs: `docker compose logs <service-name>`. |
| RAG not finding answers | Delete `./data/vector-store.json` and restart to re-ingest PDFs. |
| No PDF files found warning | Place at least one `.pdf` file in `backend/src/main/resources/docs/`. |
| First startup is slow | ONNX model (~80MB) downloads on first run. Cached after that. |
| Frontend can't reach backend | Nginx proxies `/api` to `backend:8080`. Ensure backend is healthy. |
| Blank screen after login | Hard-refresh the browser (Ctrl+Shift+R / Cmd+Shift+R). |
| Lombok errors in IntelliJ | Settings → Build → Compiler → Annotation Processors → Enable |
| MySQL data lost | Don't use `docker compose down -v` — the `-v` flag deletes volumes. |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language (backend) | Java 21 |
| Framework | Spring Boot 3.4 |
| AI framework | Spring AI 1.0 |
| Embeddings | ONNX all-MiniLM-L6-v2 (local) |
| Vector store | SimpleVectorStore (file-persisted) |
| PDF parsing | Spring AI PDF Document Reader |
| HTTP client | Spring WebFlux WebClient |
| ORM | Spring Data JPA + Hibernate |
| Database | MySQL 8 |
| Security | Spring Security + JWT (jjwt 0.12.6) |
| Password hashing | BCrypt |
| Build tool | Maven |
| Language (frontend) | JavaScript (ES2022) |
| UI framework | React 18 |
| Build tool | Vite 6 |
| Markdown rendering | react-markdown |
| LLM provider | Groq (free) |
| Model | LLaMA 3.3 70B |
| Containerization | Docker + Docker Compose |
| Web server | Nginx (frontend) |
| DB admin | phpMyAdmin |
