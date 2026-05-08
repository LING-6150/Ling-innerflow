# 🌊 InnerFlow — AI Emotional Support & Clinical Assistant Platform

> **North America Healthcare AI Hackathon — Agents Assemble 2026**
> Built on MCP · A2A · FHIR · LangGraph4j

[![Live Demo](https://img.shields.io/badge/Live%20Demo-35.170.192.217-blue?style=for-the-badge)](http://35.170.192.217)
[![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=java)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-green?style=for-the-badge&logo=springboot)](https://spring.io/)
[![Vue](https://img.shields.io/badge/Vue-3-4FC08D?style=for-the-badge&logo=vue.js)](https://vuejs.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)](LICENSE)

---

## 📖 Overview

**InnerFlow** is a full-stack AI emotional support and clinical assistance platform targeting the North American mental health market. It fuses multimodal emotion sensing, LangGraph4j stateful graph scheduling, a Planning Agent for intelligent routing, MCP tool protocol, and HL7 FHIR clinical standards into a complete closed loop — from user conversation to clinician dashboard.

🔗 **Live:** http://35.170.192.217  
📦 **GitHub:** https://github.com/LING-6150/Ling-innerflow

---

## 🎬 Demo

> 📹 **[Watch Demo Video]** *(insert your video link here)*

<!-- Add screenshots below -->
<!--
### User Chat Interface
![Chat](./docs/screenshots/chat.png)

### Doctor Dashboard
![Dashboard](./docs/screenshots/dashboard.png)

### Emotion Wall
![Wall](./docs/screenshots/wall.png)

### PHQ-9 Screening
![PHQ9](./docs/screenshots/phq9.png)
-->

---

## ✨ Key Features

### 🧠 Planning Agent + LangGraph4j Emotion Routing
Every message triggers a full reasoning pipeline:

```
EmotionAnalyzerNode → MultimodalFusion → PlannerNode → ReActAgent
     (raw level)                              ↑
                               levelHistory + UserMemory
                          (coreStruggles, emotionPattern)
```

PlannerNode synthesizes raw emotion level + session trajectory + long-term user memory to output one of four routing strategies:

| Strategy | Trigger | Effect |
|---|---|---|
| `pure` | No history or first session | Standard response for current level |
| `escalate` | Trajectory trending upward | More serious, engaged response |
| `de-escalate` | Trajectory improving | Softer tone, avoid jarring transitions |
| `blend` | Boundary ambiguous | Blend adjacent level response styles |

LangGraph4j conditional edges route to: **L1** Companionship → **L2** Guided → **L3** CBT Intervention → **L4** Professional Referral → **L5** Crisis Immediate Response

---

### 🎙️ Multimodal Emotion Sensing

- **Text**: Real-time emotion analysis of conversation content (L1–L5 quantification)
- **Voice**: Whisper API transcription + emotion scoring fusion
- **Image**: EmotionImage multimodal visual analysis
- **EmotionFusionService**: Weighted fusion of three signal streams into unified emotion level

---

### 🔧 ReAct Agent + 6 Clinical Tools

| Tool | Function |
|---|---|
| `PHQ9ScreeningTool` | Automated PHQ-9 depression scale assessment |
| `CBTSkillLibrary` | CBT technique knowledge base retrieval |
| `EmotionTrendAnalyzer` | Historical emotion trend analysis report |
| `HistoryContextRetriever` | Conversation history context recall |
| `WellnessResourceSearch` | Mental health resource recommendations |
| `FHIR Summary (MCP)` | Generate HL7 R4 Observation → push to EHR |

---

### 🗄️ Three-Layer Memory Architecture

| Layer | Technology | Spec |
|---|---|---|
| Short-term context | Redis | TTL 30 min · auto LLM summarization at 10+ turns (≤100 words) |
| Long-term UserMemory | MySQL | 9 dimensions: emotion pattern / core struggles / coping strategies / session summary / clinical reflection / persona preference |
| Clinical reflection | LLM-generated | Async write after session ends · doctor can one-click Regenerate |

---

### 🏥 Clinician Dashboard

| Feature | Description |
|---|---|
| Stats Cards | Total patients / High-risk count (L4+) / Average emotion level |
| Emotion Trend Chart | Daily average visualization, 7d / 30d / 90d toggle |
| **Planner Routing Panel** | **Real-time AI routing result: detected level → routed level + strategy badge (escalate / de-escalate / blend / pure)** |
| PHQ-9 Report | LLM auto-generated score range, severity, key indicators |
| Crisis Heatmap | 7×4 time-slot grid, highlights L3+ high-risk patterns |
| CBT Evidence Panel | Real-time clinical knowledge base retrieval based on patient profile |
| FHIR Round-trip | One-click HL7 R4 Observation generation + Sync to EHR |
| A2A Workflow Visualization | Collapsible end-to-end AI Agent workflow diagram |

---

### 🌟 User-Side Features

- **Virtual Pet**: Emotion score drives pet state with scheduled decay tasks (PetDecayTask)
- **Emotion Check-in Wall**: Kafka async CheckIn event consumption with Redis idempotent deduplication
- **Proactive Care Push**: ProactiveMessageService detects prolonged low emotional state and initiates conversation
- **Distributed Rate Limiting**: Bucket4j + Redisson token bucket, prevents API abuse
- **Persona System**: Three companionship styles — WARM / QUIET / RATIONAL, persisted to UserMemory

---

## 🏗️ Architecture

```
User Browser
     ↓
  Nginx (port 80, reverse proxy)
  ├── /api/*  → Spring Boot :8080
  └── /       → Vue 3 static files

Spring Boot (Java 21)
  ├── LangGraph4j Emotion Graph
  │   └── Analyzer → Planner → [L1~L5 Nodes]
  ├── ReAct Agent (6 tools)
  ├── MCP Server (6 endpoints)
  ├── Hybrid RAG (Pinecone + Elasticsearch)
  └── Three-Layer Memory (Redis + MySQL + LLM)

Infrastructure
  ├── MySQL 8  (persistent data)
  ├── Redis    (short-term context + rate limiting)
  ├── Kafka    (async check-in events, 3 partitions)
  └── Elasticsearch (keyword retrieval)
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 / TypeScript |
| Framework | Spring Boot 3.2.5 · Vue 3 · LangGraph4j 1.6.0 |
| AI | Spring AI · OpenAI GPT-4o · Whisper |
| Vector DB | Pinecone |
| Search | Elasticsearch 8.13 |
| Database | MySQL 8 · Redis |
| Message Queue | Apache Kafka (3 partitions × 3 consumers) |
| Security | Spring Security · JWT · Bucket4j · Redisson |
| Medical Standards | HL7 FHIR R4 · MCP Protocol · A2A |
| Frontend | Vue 3 · Tailwind CSS · Chart.js |
| Deployment | AWS EC2 (t3.small) · Docker Compose · Nginx |

---

## 🚀 Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 21+
- Node.js 22+

### Environment Setup

```bash
# Clone the repository
git clone https://github.com/LING-6150/Ling-innerflow.git
cd Ling-innerflow

# Create environment file
cp .env.example .env
# Fill in your API keys:
# OPENAI_API_KEY=your_key
# PINECONE_API_KEY=your_key
# DB_PASSWORD=your_password
```

### Run with Docker Compose

```bash
# Start all services
docker compose up -d

# Check status
docker compose ps

# View Spring Boot logs
docker compose logs -f app
```

### Access

| Service | URL |
|---|---|
| Frontend | http://localhost |
| API | http://localhost:8080/api |
| WebSocket | ws://localhost:8080/ws |

---

## 📡 MCP Server Endpoints

InnerFlow exposes 6 MCP-compliant tool endpoints for external agent invocation:

```
POST /mcp/phq9-screening
POST /mcp/cbt-skill-library
POST /mcp/emotion-trend-analyzer
POST /mcp/history-context-retriever
POST /mcp/wellness-resource-search
POST /mcp/fhir-summary
```

---

## 🏆 Hackathon Highlights

This project was built for the **Agents Assemble: Healthcare AI Endgame Challenge** by Prompt Opinion.

**Why InnerFlow stands out:**

1. **Complete A2A closed loop**: User input → Multimodal fusion → LangGraph4j routing → Planning Agent decision → ReAct tool invocation → FHIR output → Clinician dashboard
2. **Planning Agent beyond rule-based routing**: Considers emotion trajectory trends + long-term user memory across sessions, enabling escalate / de-escalate / blend / pure four-strategy intelligent dispatch — routing decisions visible in real-time on doctor dashboard
3. **Clinical-grade data standards**: HL7 FHIR R4 format output, ready for real EHR integration
4. **Precision mental health triage**: L1–L5 five-level quantification, L5 crisis bypasses Planner to directly trigger safety response with zero delay
5. **Enterprise-grade engineering**: JWT auth, Kafka async decoupling (3 partitions × 3 consumers, idempotent deduplication, dead letter queue), three-layer memory (Redis + MySQL + LLM reflection), Hybrid RAG dual-path retrieval

---

## 👩‍💻 Author

**Ling Duan**
- Graduate Student, Northeastern University (Information Systems, AI Backend)
- GitHub: [@LING-6150](https://github.com/LING-6150)
- Email: duan.lin@northeastern.edu

---

*Built with ❤️ for the North America Healthcare AI Hackathon — Agents Assemble 2026*
