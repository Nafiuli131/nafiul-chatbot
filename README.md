# Nafiul Chatbot

A full-stack AI chat application built with **Spring Boot** (backend) and **React** (frontend), powered by the **Groq free API** using LLaMA 3.3 70B.

---

## Features

- User registration and login with JWT authentication
- Per-user chat sessions — your history is private and scoped to your account
- Persistent chat history stored in **MySQL** — survives server restarts
- Multiple independent chat sessions (like ChatGPT sidebar)
- Starts a fresh new chat on every login; previous sessions visible in the sidebar
- Per-session conversation memory using `sessionId`
- Editable system prompt per session
- Markdown rendering — code blocks, tables, lists
- Animated loading indicator while waiting for response
- Auto-renames sessions from the first message
- Dark violet theme UI

---

## Project Structure

```
code/
├── backend/                                  Spring Boot API
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/example/groqchat/
│       │   ├── GroqChatApplication.java       entry point
│       │   ├── config/
│       │   │   └── GroqConfig.java            reads yml, builds WebClient
│       │   ├── dto/
│       │   │   ├── Message.java               {role, content}
│       │   │   ├── ChatRequest.java           sent to Groq API
│       │   │   ├── ChatResponse.java          received from Groq API
│       │   │   ├── UserMessage.java           received from frontend
│       │   │   ├── RegisterRequest.java       register payload
│       │   │   ├── LoginRequest.java          login payload
│       │   │   └── AuthResponse.java          {token, username}
│       │   ├── entity/
│       │   │   ├── User.java                  JPA entity — users table
│       │   │   ├── ChatSession.java           JPA entity — chat_sessions table
│       │   │   └── ChatMessageEntity.java     JPA entity — chat_messages table
│       │   ├── repository/
│       │   │   ├── UserRepository.java
│       │   │   ├── ChatSessionRepository.java
│       │   │   └── ChatMessageRepository.java
│       │   ├── security/
│       │   │   ├── JwtUtil.java               token generation & validation
│       │   │   ├── JwtAuthFilter.java         reads Bearer token from headers
│       │   │   └── SecurityConfig.java        Spring Security configuration
│       │   ├── service/
│       │   │   ├── AuthService.java           register / login logic
│       │   │   ├── UserDetailsServiceImpl.java
│       │   │   └── LlmService.java            session memory + Groq calls
│       │   └── controller/
│       │       ├── AuthController.java        /api/auth/**
│       │       └── ChatController.java        /api/chat/**
│       └── resources/
│           └── application.yml               config (API key, DB, JWT, model)
│
└── frontend/                                 React + Vite
    ├── package.json
    ├── vite.config.js                        proxies /api → localhost:8080
    ├── index.html
    └── src/
        ├── main.jsx
        ├── App.jsx                           auth state + session management
        ├── App.css
        ├── api.js                            authFetch() helper (Bearer token)
        ├── index.css
        └── components/
            ├── AuthPage.jsx                  login / register form
            ├── AuthPage.css
            ├── Sidebar.jsx                   session list, new/delete, logout
            ├── Sidebar.css
            ├── ChatWindow.jsx                messages + input box
            ├── ChatWindow.css
            ├── MessageBubble.jsx             markdown rendering per message
            ├── MessageBubble.css
            ├── SystemPromptBar.jsx           editable system prompt
            └── SystemPromptBar.css
```

---

## Prerequisites

| Tool | Version | Install |
|---|---|---|
| Java | 21+ | https://adoptium.net |
| Maven | 3.9+ | `sudo apt install maven` |
| Node.js | 18+ | https://nodejs.org |
| npm | 9+ | comes with Node.js |
| MySQL | 8.0+ | https://dev.mysql.com/downloads |
| Groq API key | free | https://console.groq.com |

---

## Step 1 — Get a Free Groq API Key

1. Go to **https://console.groq.com**
2. Sign up with Google or GitHub (no credit card needed)
3. Go to **API Keys** → **Create API Key**
4. Copy the key — it looks like: `gsk_xxxxxxxxxxxxxxxxxxxx`

---

## Step 2 — Set Up MySQL

Make sure MySQL is running, then set your credentials as environment variables:

**Linux / macOS:**
```bash
export MYSQL_DB=nafiulchat
export MYSQL_USER=root
export MYSQL_PASSWORD=your_mysql_password
```

**Windows (Command Prompt):**
```cmd
set MYSQL_DB=nafiulchat
set MYSQL_USER=root
set MYSQL_PASSWORD=your_mysql_password
```

**Windows (PowerShell):**
```powershell
$env:MYSQL_DB="nafiulchat"
$env:MYSQL_USER="root"
$env:MYSQL_PASSWORD="your_mysql_password"
```

The database is created automatically on first startup (`createDatabaseIfNotExist=true`). No manual SQL needed.

---

## Step 3 — Run the Backend

### Set environment variables

**Linux / macOS:**
```bash
export GROQ_API_KEY=gsk_your_actual_key_here
export MYSQL_DB=nafiulchat
export MYSQL_USER=root
export MYSQL_PASSWORD=your_mysql_password

# Optional — override the default JWT secret in production
export JWT_SECRET=YourSuperSecretKeyAtLeast32CharsLong!!
```

**Windows (Command Prompt):**
```cmd
set GROQ_API_KEY=gsk_your_actual_key_here
set MYSQL_DB=nafiulchat
set MYSQL_USER=root
set MYSQL_PASSWORD=your_mysql_password
```

**Windows (PowerShell):**
```powershell
$env:GROQ_API_KEY="gsk_your_actual_key_here"
$env:MYSQL_DB="nafiulchat"
$env:MYSQL_USER="root"
$env:MYSQL_PASSWORD="your_mysql_password"
```

**IntelliJ IDEA:**
```
Run → Edit Configurations → Environment Variables → Add:
  GROQ_API_KEY=gsk_your_actual_key_here
  MYSQL_DB=nafiulchat
  MYSQL_USER=root
  MYSQL_PASSWORD=your_mysql_password
```

### Start the Spring Boot server

```bash
cd code/backend
mvn spring-boot:run
```

You should see:
```
Started GroqChatApplication in 2.4 seconds
Tomcat started on port(s): 8080
```

Backend is now running at **http://localhost:8080**

---

## Step 4 — Run the Frontend

Open a **new terminal** (keep the backend running):

```bash
cd code/frontend
npm install
npm run dev
```

You should see:
```
VITE v6.x.x  ready in 300ms
  Local:   http://localhost:5173/
```

Open **http://localhost:5173** in your browser.

---

## Usage

1. **Register** — create an account with a username, email, and password
2. **Login** — sign in to access your personal chat history
3. Every login opens a **fresh new chat** automatically
4. Previous sessions are listed in the sidebar — click any to resume
5. Use the **+** button in the sidebar to start a new chat at any time
6. Click **⚙ System Prompt** to customize the AI's behaviour per session
7. Click **✕** next to a session to delete it permanently
8. Click **Logout** in the sidebar footer to sign out

---

## How It Works

```
Browser (localhost:5173)
        │
        │  POST /api/chat/memory
        │  Authorization: Bearer <jwt>
        │  { sessionId, message, systemPrompt }
        │
        ▼
Vite Dev Server (proxy)
        │
        │  forwards to localhost:8080
        │
        ▼
Spring Boot (localhost:8080)
        │
        │  validates JWT → resolves user
        │  loads conversation history from MySQL
        │  appends new message, calls Groq API
        │  saves assistant reply back to MySQL
        │
        ▼
Groq API (free)
        │
        │  returns LLaMA 3.3 70B response
        │
        ▼
Spring Boot → Vite → Browser ✓
```

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

### Session isolation

Every chat session belongs to exactly one user. Users cannot see or access each other's sessions. Each session has a unique UUID and carries the full conversation history sent to Groq on every request.

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
| `GET` | `/api/health` | Health check |

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
    url: jdbc:mysql://localhost:3306/${MYSQL_DB:nafiulchat}?createDatabaseIfNotExist=true
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root}
  jpa:
    hibernate:
      ddl-auto: update                    # auto-creates/updates tables

jwt:
  secret: ${JWT_SECRET:fallback-secret}  # override in production!
  expiration: 86400000                   # 24 hours in ms
```

### Available Groq models (free tier)

| Model | Best For |
|---|---|
| `llama-3.3-70b-versatile` | Best quality (default) |
| `llama-3.1-8b-instant` | Fastest responses |
| `mixtral-8x7b-32768` | Long context tasks |
| `gemma2-9b-it` | Lightweight tasks |

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `401 Unauthorized` on chat | JWT token missing or expired. Log out and log in again. |
| `401 Unauthorized` on Groq | `GROQ_API_KEY` is wrong or not set. Run `echo $GROQ_API_KEY`. |
| `429 Too Many Requests` | Hit Groq free tier rate limit. Wait ~1 minute. |
| `Connection refused` on port 8080 | Backend is not running. Run `mvn spring-boot:run`. |
| `Access denied for user` (MySQL) | Wrong `MYSQL_USER` or `MYSQL_PASSWORD`. Check env variables. |
| `Unknown database` (MySQL) | Ensure MySQL server is running — the DB is created automatically. |
| Blank screen after login | Hard-refresh the browser (Ctrl+Shift+R / Cmd+Shift+R). |
| Lombok errors in IntelliJ | Settings → Build → Compiler → Annotation Processors → Enable ✓ |
| CORS error in browser | Frontend must run on port 5173 (Vite default). |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language (backend) | Java 21 |
| Framework | Spring Boot 3.4 |
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
