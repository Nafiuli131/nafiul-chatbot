# Groq Chat App

A full-stack chat application built with **Spring Boot** (backend) and **React** (frontend), powered by the **Groq free API** using LLaMA 3.3 70B.

---

## Features

- Multiple independent chat sessions (like ChatGPT sidebar)
- Per-session conversation memory using `sessionId`
- Persistent chat history stored in **MySQL**
- Editable system prompt per session
- Markdown rendering — code blocks, tables, lists
- Animated loading indicator while waiting for response
- Auto-renames sessions from the first message
- Dark theme UI

---

## Project Structure

```
code/
├── backend/                              Spring Boot API
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/example/groqchat/
│       │   ├── GroqChatApplication.java  entry point
│       │   ├── config/
│       │   │   └── GroqConfig.java       reads yml, builds WebClient
│       │   ├── dto/
│       │   │   ├── Message.java          {role, content}
│       │   │   ├── ChatRequest.java      sent to Groq API
│       │   │   ├── ChatResponse.java     received from Groq API
│       │   │   └── UserMessage.java      received from frontend
│       │   ├── entity/
│       │   │   ├── ChatSession.java      JPA entity — chat_sessions table
│       │   │   └── ChatMessageEntity.java JPA entity — chat_messages table
│       │   ├── repository/
│       │   │   ├── ChatSessionRepository.java
│       │   │   └── ChatMessageRepository.java
│       │   ├── service/
│       │   │   └── LlmService.java       session memory + Groq calls
│       │   └── controller/
│       │       └── ChatController.java   REST endpoints
│       └── resources/
│           └── application.yml           config (API key, DB, model, etc.)
│
└── frontend/                             React + Vite
    ├── package.json
    ├── vite.config.js                    proxies /api → localhost:8080
    ├── index.html
    └── src/
        ├── main.jsx
        ├── App.jsx                       session state management
        ├── App.css
        ├── index.css
        └── components/
            ├── Sidebar.jsx               session list, new/delete
            ├── Sidebar.css
            ├── ChatWindow.jsx            messages + input box
            ├── ChatWindow.css
            ├── MessageBubble.jsx         markdown rendering per message
            ├── MessageBubble.css
            ├── SystemPromptBar.jsx       editable system prompt
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
export MYSQL_DB=groqchat
export MYSQL_USER=root
export MYSQL_PASSWORD=your_mysql_password
```

**Windows (Command Prompt):**
```cmd
set MYSQL_DB=groqchat
set MYSQL_USER=root
set MYSQL_PASSWORD=your_mysql_password
```

**Windows (PowerShell):**
```powershell
$env:MYSQL_DB="groqchat"
$env:MYSQL_USER="root"
$env:MYSQL_PASSWORD="your_mysql_password"
```

The database is created automatically on first startup. No manual SQL needed.

---

## Step 3 — Run the Backend

### Set the Groq API key

**Linux / macOS:**
```bash
export GROQ_API_KEY=gsk_your_actual_key_here
```

**Windows (Command Prompt):**
```cmd
set GROQ_API_KEY=gsk_your_actual_key_here
```

**Windows (PowerShell):**
```powershell
$env:GROQ_API_KEY="gsk_your_actual_key_here"
```

**IntelliJ IDEA:**
```
Run → Edit Configurations → Environment Variables
→ Add: GROQ_API_KEY=gsk_your_actual_key_here
       MYSQL_DB=groqchat
       MYSQL_USER=root
       MYSQL_PASSWORD=your_mysql_password
```

### Start the Spring Boot server

```bash
cd backend
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
cd frontend
npm install
npm run dev
```

You should see:
```
VITE v6.x.x  ready in 300ms
→  Local:   http://localhost:5173/
```

Open **http://localhost:5173** in your browser.

---

## How It Works

```
Browser (localhost:5173)
        │
        │  POST /api/chat/memory
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

Hibernate auto-creates these tables on startup:

```
chat_sessions
  id           VARCHAR(255)  PK
  system_prompt TEXT
  created_at   DATETIME

chat_messages
  id           BIGINT        PK (auto-increment)
  session_id   VARCHAR(255)  FK → chat_sessions.id
  role         VARCHAR(20)   "system" | "user" | "assistant"
  content      TEXT
  created_at   DATETIME
```

### Multi-user memory

Every chat session in the sidebar has a unique `sessionId` (UUID). The backend loads the full conversation history from MySQL per session and sends it to Groq on every request:

```
"session-uuid-a" → [system, user_1, assistant_1, user_2, ...]
"session-uuid-b" → [system, user_1, assistant_1, ...]
```

Sessions are completely isolated — users never see each other's messages. History survives server restarts.

---

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/chat` | One-shot chat, no memory |
| `POST` | `/api/chat/memory` | Chat with session memory (persisted) |
| `DELETE` | `/api/chat/session/{id}` | Clear one session |
| `GET` | `/api/chat/sessions` | List active sessions |
| `GET` | `/api/health` | Health check |

### Example request

```bash
curl -X POST http://localhost:8080/api/chat/memory \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "my-session",
    "message": "What is Spring Boot?",
    "systemPrompt": "You are a Java expert. Be concise."
  }'
```

### Example response

```json
{
  "sessionId": "my-session",
  "response": "Spring Boot is an opinionated framework...",
  "historySize": "3"
}
```

---

## Configuration

All config is in `backend/src/main/resources/application.yml`:

```yaml
groq:
  api-key: ${GROQ_API_KEY}              # from environment variable
  base-url: https://api.groq.com/openai/v1
  model: llama-3.3-70b-versatile        # change model here
  temperature: 0.7                       # 0 = precise, 1+ = creative
  max-tokens: 1024                       # max response length

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/${MYSQL_DB:groqchat}?createDatabaseIfNotExist=true
    username: ${MYSQL_USER:root}         # override via env variable
    password: ${MYSQL_PASSWORD:root}     # override via env variable
  jpa:
    hibernate:
      ddl-auto: update                   # auto-creates/updates tables
```

### Available Groq models (free)

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
| `401 Unauthorized` | API key is wrong or not set. Run `echo $GROQ_API_KEY` to verify. |
| `429 Too Many Requests` | Hit Groq free tier rate limit. Wait 1 minute. |
| `Connection refused` on port 8080 | Backend is not running. Start it with `mvn spring-boot:run`. |
| `Access denied for user` (MySQL) | Check `MYSQL_USER` and `MYSQL_PASSWORD` env variables. |
| `Unknown database 'groqchat'` | Add `?createDatabaseIfNotExist=true` to the JDBC URL (already set by default). |
| Lombok errors / missing getters | In IntelliJ: Settings → Build → Compiler → Annotation Processors → Enable ✓ |
| CORS error in browser | Make sure frontend runs on port 5173 (Vite default). |
| `GROQ_API_KEY` not found | Set the env variable before running `mvn spring-boot:run`. |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language (backend) | Java 21 |
| Framework | Spring Boot 3.4 |
| HTTP client | Spring WebFlux WebClient |
| ORM | Spring Data JPA + Hibernate |
| Database | MySQL 8 |
| Build tool | Maven |
| Language (frontend) | JavaScript (ES2022) |
| UI framework | React 18 |
| Build tool | Vite |
| Markdown rendering | react-markdown |
| LLM provider | Groq (free) |
| Model | LLaMA 3.3 70B |
# nafiul-chatbot
