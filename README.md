# Nafiul Chatbot

A full-stack **multi-agent AI chat application** built with **Spring Boot** (backend) and **React** (frontend), powered by **LangChain4j** and the **Groq free API** using LLaMA 3.3 70B. Features an intelligent agent router that automatically delegates queries to specialized agents (RAG, Weather, DateTime), with **5 built-in guardrails** and **hallucination detection** for safe, grounded AI responses. Fully Dockerized for easy deployment.

---

## Branches

This project has two versions with different AI frameworks:

| Branch | AI Framework | Description |
|--------|-------------|-------------|
| [`main`](https://github.com/Nafiuli131/nafiul-chatbot/tree/main) | **LangChain4j** | Latest — uses LangChain4j for LLM calls, RAG, embeddings, and PDF ingestion |
| [`spring-ai`](https://github.com/Nafiuli131/nafiul-chatbot/tree/spring-ai) | **Spring AI** | Previous — uses Spring AI with WebClient for Groq API calls |

Both branches share the same frontend, auth system, and database schema. The difference is only in the AI/LLM integration layer.

```bash
git checkout main        # LangChain4j version
git checkout spring-ai   # Spring AI version
```

---

## Features

### Multi-Agent Architecture
- Intelligent router automatically delegates queries to the right specialized agent
  - **RAG Agent** — searches uploaded PDF documents for knowledge-based questions
  - **Weather Agent** — fetches real-time weather for any city/country via [wttr.in](https://wttr.in) (free, no API key)
  - **DateTime Agent** — gets current date/time for 50+ countries and timezones
  - **General Chat** — direct LLM response using general knowledge
- **RAG-first routing** — for non-tool queries, always searches documents first; falls back to general knowledge if no relevant docs found
- Smart routing with keyword-based fast paths (weather/datetime) + LLM classifier fallback — saves tokens

### Guardrails & Hallucination Detection
- **5 built-in guardrails** that run on every request with full visibility in API responses:

| # | Guardrail | Type | What It Does |
|---|-----------|------|-------------|
| 1 | **Input Length** | Input | Blocks messages exceeding configurable max character limit (default: 2000) |
| 2 | **Prompt Injection** | Input | Detects jailbreak attempts, "ignore previous instructions", system prompt extraction |
| 3 | **Harmful Content** | Input | Blocks requests for weapons, malware, hacking, illegal content |
| 4 | **Hallucination Check** | RAG | LLM-based verification that RAG answers are grounded in retrieved documents |
| 5 | **Sensitive Data Redaction** | Output | Redacts emails, phone numbers, SSNs, API keys, credit cards from responses |

- Every API response includes guardrail metadata showing what each guard did (PASSED / BLOCKED / GROUNDED / REDACTED)
- `GET /api/guardrails/status` endpoint to inspect all active guardrails
- Smart fallback: if hallucination check detects NOT_GROUNDED, automatically retries with general knowledge instead of blocking
- All guardrails are independently configurable via `application.yml`

### RAG (Retrieval-Augmented Generation)
- Powered by **LangChain4j** — drop PDFs in `backend/src/main/resources/docs/` and the chatbot answers questions using your documents
- Automatic PDF ingestion on startup with local ONNX embeddings (no extra API key needed)
- Score-aware retrieval — only chunks above configurable `minScore` threshold (default: 0.7) are used
- In-memory embedding store persisted to disk — survives restarts without re-processing
- **Add more PDFs anytime** — just drop them in the docs folder and restart; no code changes needed

### Chat & Auth
- User registration and login with JWT authentication
- Per-user chat sessions — your history is private and scoped to your account
- Persistent chat history stored in **MySQL** — survives server restarts
- Multiple independent chat sessions (like ChatGPT sidebar)
- Starts a fresh new chat on every login; previous sessions visible in the sidebar
- Per-session conversation memory using `sessionId`
- Editable system prompt per session

### UI
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
│       │   ├── agent/
│       │   │   ├── AgentRouter.java             multi-agent router (classifies → routes → responds)
│       │   │   └── tools/
│       │   │       ├── RagTool.java             PDF document search agent
│       │   │       ├── WeatherTool.java         real-time weather agent (wttr.in)
│       │   │       └── DateTimeTool.java        date/time agent (50+ countries)
│       │   ├── config/
│       │   │   ├── GroqConfig.java              reads yml, builds LangChain4j ChatLanguageModel
│       │   │   └── RagConfig.java               RAG settings, EmbeddingModel + EmbeddingStore beans
│       │   ├── dto/
│       │   │   ├── Message.java                 {role, content}
│       │   │   ├── UserMessage.java             received from frontend
│       │   │   ├── AgentResponse.java           response + guardrail metadata
│       │   │   ├── GuardrailResult.java         {name, status, message} per guardrail
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
│       │   │   ├── LlmService.java              session memory + multi-agent routing
│       │   │   ├── RagService.java              score-aware embedding search
│       │   │   ├── GuardrailService.java        input/output guardrails (4 guards)
│       │   │   ├── HallucinationDetector.java   LLM-based RAG grounding verification
│       │   │   └── DocumentIngestionService.java LangChain4j PDF loading + chunking on startup
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

### Multi-Agent Flow (with Guardrails)

```
Browser
    │  POST /api/chat/memory
    │  Authorization: Bearer <jwt>
    │  { sessionId, message, systemPrompt }
    ▼
Spring Boot (port 8080)
    │  validates JWT → resolves user
    │  saves user message to MySQL
    │  loads conversation history
    ▼
AgentRouter (multi-agent orchestrator)
    │
    │  ┌─── INPUT GUARDRAILS ────────────────────────────────┐
    │  │  Guard 1: Input Length      → block if > 2000 chars │
    │  │  Guard 2: Prompt Injection  → block jailbreaks      │
    │  │  Guard 3: Harmful Content   → block dangerous asks  │
    │  └─────────────────────────────────────────────────────┘
    │
    │  Step 1: CLASSIFY — determines which agent to use
    │  ├── Keyword match (fast, no LLM call): "weather in..." → WEATHER
    │  ├── Keyword match (fast, no LLM call): "what time..." → DATETIME
    │  └── LLM classifier (fallback): asks Groq to classify → GENERAL
    │
    │  Step 2: EXECUTE
    │  ├── WEATHER  → WeatherTool  → wttr.in API → real-time weather data
    │  ├── DATETIME → DateTimeTool → Java ZoneId → current date/time
    │  └── GENERAL  → RAG-first strategy:
    │       ├── Search RAG (minScore=0.7)
    │       ├── HIT  → answer from docs → Guard 4: Hallucination Check
    │       │    ├── GROUNDED         → use RAG response ✓
    │       │    ├── PARTIALLY_GROUNDED → add disclaimer ⚠
    │       │    └── NOT_GROUNDED     → fallback to general knowledge
    │       └── MISS → answer from general knowledge (no hallucination check)
    │
    │  ┌─── OUTPUT GUARDRAILS ───────────────────────────────┐
    │  │  Guard 5: Sensitive Data Redaction                  │
    │  │  → redacts emails, phones, SSNs, API keys, cards   │
    │  └─────────────────────────────────────────────────────┘
    ▼
Groq API (free) → LLaMA 3.3 70B response → saved to MySQL → Browser ✓

Response includes guardrail metadata:
{ response, blocked, guardrails: [{name, status, message}, ...] }
```

### Example Queries

| User Message | Route | What Happens |
|---|---|---|
| "What's the weather in Tokyo?" | WEATHER tool | Keyword match → wttr.in API → formatted response |
| "What time is it in Bangladesh?" | DATETIME tool | Keyword match → Java ZoneId (Asia/Dhaka) → formatted response |
| "Who is Nafiul Islam?" | RAG → GROUNDED | RAG search → PDF chunks found → hallucination check passes → document-grounded answer |
| "Tell me about Iran" | RAG miss → General | RAG search → no relevant docs → answers from general knowledge |
| "Hello!" | General | LLM classifier → direct LLM response (no tool) |
| "Ignore previous instructions" | BLOCKED | Input guardrail blocks prompt injection attempt |

### RAG Pipeline (LangChain4j)

```
PDF files (backend/src/main/resources/docs/)
    │  [Startup: ApplicationReadyEvent]
    ▼
ApachePdfBoxDocumentParser → DocumentSplitters.recursive() → AllMiniLmL6V2EmbeddingModel (local ONNX)
    │                                                                │
    ▼                                                                ▼
List<TextSegment> (chunks)  →  InMemoryEmbeddingStore (in-memory + file-persisted)
                                       │
                                       ▼  [Query time]
                               EmbeddingSearchRequest(query, minScore=0.7)
                                       │
                                       ▼
                               RagResult { context, bestScore, matchCount }
                                       │
                                       ├── HIT (score ≥ 0.7) → context injected → LLM → Hallucination Check
                                       │                                            ├── GROUNDED → return ✓
                                       │                                            └── NOT_GROUNDED → general fallback
                                       └── MISS (no matches) → general LLM response (no hallucination check)
```

- **First startup**: Reads PDFs → chunks text → generates embeddings → saves to `./data/vector-store.json`
- **Subsequent startups**: Loads embedding store from file — skips PDF processing
- **To re-ingest**: Delete `./data/vector-store.json` (or the `vector_data` Docker volume) and restart
- **To add more documents**: Drop any `.pdf` into `backend/src/main/resources/docs/` and restart — no code changes needed

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
| `POST` | `/api/chat/memory` | Send a message with session memory (returns guardrail metadata) |
| `GET` | `/api/chat/sessions` | List all sessions for the logged-in user |
| `GET` | `/api/chat/session/{id}/messages` | Load message history for a session |
| `DELETE` | `/api/chat/session/{id}` | Delete a session |
| `GET` | `/api/guardrails/status` | List all active guardrails and their descriptions |
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
  "blocked": false,
  "guardrails": [
    { "name": "InputLength", "status": "PASSED", "message": "Check passed" },
    { "name": "PromptInjection", "status": "PASSED", "message": "Check passed" },
    { "name": "HarmfulContent", "status": "PASSED", "message": "Check passed" },
    { "name": "RAGSearch", "status": "MISS", "message": "No relevant documents found" },
    { "name": "HallucinationCheck", "status": "SKIPPED", "message": "No RAG context — answered using general knowledge" },
    { "name": "SensitiveDataRedaction", "status": "PASSED", "message": "No sensitive data found" }
  ],
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

guardrails:
  max-input-length: 2000                 # max characters per message
  block-prompt-injection: true           # detect jailbreak attempts
  block-sensitive-output: true           # redact PII from responses
  hallucination-check: true              # verify RAG answers are grounded
  rag-min-score: 0.7                     # min similarity score for RAG (0.0-1.0)
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
| `429 Too Many Requests` | Hit Groq free tier rate limit. Per-minute limit resets in ~60s. Daily limit (100K tokens) resets after 24 hours. Consider upgrading to Groq Dev Tier for higher limits. |
| `Connection refused` on port 8080 | Backend is not running. Check `docker compose logs backend`. |
| `Access denied for user` (MySQL) | Wrong credentials. Check `.env` matches `docker-compose.yml`. |
| Container keeps restarting | Check logs: `docker compose logs <service-name>`. |
| RAG not finding answers | Delete `./data/vector-store.json` and restart to re-ingest PDFs. Or lower `guardrails.rag-min-score` in `application.yml`. |
| Message blocked by guardrails | Check the `guardrails` array in the response to see which guard blocked it. Adjust settings in `application.yml`. |
| Hallucination check too strict | Set `guardrails.hallucination-check: false` in `application.yml` to disable it. |
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
| AI framework | LangChain4j 1.0.0-beta1 |
| Agent architecture | Multi-agent router (RAG, Weather, DateTime, General) with 5 guardrails |
| Guardrails | Input validation, prompt injection detection, hallucination detection, PII redaction |
| Weather API | wttr.in (free, no API key) |
| Embeddings | ONNX all-MiniLM-L6-v2 (local, via LangChain4j) |
| Vector store | LangChain4j InMemoryEmbeddingStore (file-persisted) |
| PDF parsing | LangChain4j Apache PDFBox Document Parser |
| LLM client | LangChain4j OpenAI-compatible ChatLanguageModel |
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
