# 🎙️ MeetingMind — AI Meeting Notes Tracker

An AI-powered meeting notes service that turns audio/video recordings or transcripts into structured summaries, action items, decisions, and follow-up email drafts — with real-time processing updates via WebSockets.

> Built with Java/Spring Boot, OpenAI Whisper, Groq LLaMA 3, and WebSockets.

---

## 🏗️ Architecture

```
Upload audio/video or transcript
         │
         ▼
┌─────────────────────────────────┐
│     Spring Boot API (8084)      │
│                                 │
│  ┌──────────────────────────┐   │
│  │   TranscriptionService   │   │
│  │   (OpenAI Whisper API)   │   │
│  └──────────┬───────────────┘   │
│             │ transcript        │
│  ┌──────────▼───────────────┐   │
│  │   @Async Processing      │   │
│  │   Pipeline               │   │
│  └──────────┬───────────────┘   │
│             │                   │
│  ┌──────────▼───────────────┐   │
│  │   GroqService            │   │
│  │   (LLaMA 3.3 70B)        │   │
│  │   - Summary              │   │
│  │   - Decisions            │   │
│  │   - Action Items         │   │
│  │   - Follow-up Email      │   │
│  └──────────┬───────────────┘   │
│             │                   │
│  ┌──────────▼───────────────┐   │
│  │   WebSocket Push         │   │
│  │   (real-time status)     │   │
│  └──────────────────────────┘   │
└─────────────────────────────────┘
         │
         ▼
┌─────────────────┐
│   PostgreSQL    │
│  meetings +     │
│  action_items   │
└─────────────────┘
```

---

## ✨ Features

**AI Processing Pipeline**
- Upload audio or video recordings (MP3, MP4, WAV, M4A, WEBM, MOV)
- Or paste a meeting transcript directly
- OpenAI Whisper transcribes audio with high accuracy across accents and speakers
- Groq LLaMA 3.3 70B extracts structured insights in seconds

**What AI Extracts**
- Bullet-point summary of key discussion points
- Decisions made during the meeting
- Action items with owner names and due dates
- Professional follow-up email draft ready to send

**Real-time Updates**
- WebSocket connection pushes live status updates to the browser
- Processing states: Pending → Processing → Done / Failed
- No need to refresh — results appear automatically

**Action Item Tracking**
- Click to cycle status: To Do → In Progress → Done
- Full audit log of every status change with timestamp
- Visual progress tracking per meeting

**Search**
- Full-text search across all meeting titles, summaries and transcripts

---

## 📊 Performance

| Metric | Result |
|---|---|
| Audio transcription | ~1 second per minute of audio |
| AI summarization | ~3-5 seconds per meeting |
| End-to-end processing | Under 30 seconds for a 1-hour meeting |
| WebSocket latency | Real-time (<100ms) |

---

## 🛠️ Tech Stack

| Component | Technology |
|---|---|
| API | Java 17, Spring Boot 3 |
| Database | PostgreSQL 15 |
| Real-time | WebSockets (STOMP over SockJS) |
| Transcription | OpenAI Whisper API |
| Summarization | Groq API (LLaMA 3.3 70B) |
| Async Processing | Spring @Async |
| Frontend | React 18 (single HTML file) |

---

## 🚀 Getting Started

### Prerequisites
- Java 17+
- Docker Desktop
- OpenAI API key (for Whisper transcription)
- Groq API key (free at console.groq.com)

### 1. Start PostgreSQL
```bash
docker run --name meeting-postgres \
  -e POSTGRES_PASSWORD=password \
  -e POSTGRES_DB=meetingnotes \
  -p 5433:5432 -d postgres:15
```

### 2. Configure API Keys
Copy `application.properties.example` to `application.properties` and fill in your keys:
```bash
cp src/main/resources/application.properties.example \
   src/main/resources/application.properties
```

```properties
spring.datasource.url=jdbc:postgresql://localhost:5433/meetingnotes
spring.datasource.username=postgres
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update
server.port=8084

groq.api.key=YOUR_GROQ_KEY_HERE
openai.api.key=YOUR_OPENAI_KEY_HERE

spring.servlet.multipart.max-file-size=500MB
spring.servlet.multipart.max-request-size=500MB
```

### 3. Run the App
```bash
./mvnw spring-boot:run
```

### 4. Open the Dashboard
Navigate to `http://localhost:8084`

---

## 📡 API Reference

### Upload Audio/Video
```
POST /api/meetings/upload
Content-Type: multipart/form-data

Parameters:
  file  — audio or video file (MP3, MP4, WAV, M4A, WEBM)
  title — meeting title (optional)
```

### Create from Transcript
```
POST /api/meetings
Content-Type: application/json

{
  "title": "Q2 Planning Sync",
  "transcript": "John: Let's kick off..."
}
```

### Get All Meetings
```
GET /api/meetings
```

### Get Meeting Details
```
GET /api/meetings/{id}
```

### Get Action Items
```
GET /api/meetings/{id}/actions
```

### Update Action Item Status
```
PATCH /api/meetings/actions/{id}
Content-Type: application/json

{
  "status": "IN_PROGRESS",
  "editor": "Shilpa"
}
```

### Search Meetings
```
GET /api/meetings/search?q=keyword
```

### WebSocket
```
Connect:   ws://localhost:8084/ws
Subscribe: /topic/meeting/{id}

Receives:
{
  "meetingId": 1,
  "status": "DONE",
  "message": "Analysis complete!"
}
```

---

## 🧠 Design Decisions

**Why async processing?**
Whisper transcription + LLM summarization takes 10-30 seconds. Doing this synchronously would block the HTTP request and time out. Using `@Async` the request returns immediately with a meeting ID, and the browser receives results via WebSocket when ready.

**Why WebSockets over polling?**
Polling every second wastes bandwidth and adds unnecessary load. WebSockets maintain a persistent connection and push updates only when the status changes — cleaner, faster, and more scalable.

**Why Groq over OpenAI for summarization?**
Groq's LLaMA 3.3 70B is free tier, extremely fast (typically under 3 seconds), and produces high-quality structured output for meeting analysis. OpenAI GPT-4 would also work but costs more and isn't meaningfully better for this use case.

**Why structured JSON output from LLM?**
Rather than asking the LLM for free-form text and parsing it, we prompt it to return a specific JSON schema. This makes parsing reliable and deterministic — the same structure every time regardless of meeting content.

---

## 📁 Project Structure

```
src/main/java/com/meetingnotes/
├── controller/
│   └── MeetingController.java      — REST endpoints
├── service/
│   ├── MeetingService.java         — async processing pipeline
│   ├── ClaudeService.java          — Groq LLM integration
│   └── TranscriptionService.java   — OpenAI Whisper integration
├── model/
│   ├── Meeting.java                — meeting entity
│   └── ActionItem.java             — action item entity
├── repository/
│   ├── MeetingRepository.java
│   └── ActionItemRepository.java
└── config/
    └── WebSocketConfig.java        — STOMP WebSocket config

src/main/resources/
├── static/index.html               — React frontend
└── application.properties.example
```

---

## 🔮 Future Improvements

- Speaker diarization — identify who said what
- Calendar integration — auto-create follow-up meetings
- Slack/email notifications when action items are due
- Multi-user support with team workspaces
- RAG search — ask questions across all past meetings
- Mobile app

---

## 👩‍💻 Author

**Shilpa Lingadal**
Master of Science in Software Engineering Systems — Northeastern University
