# CLAUDE.md

This file gives Claude Code project-level context for working on InnerFlow.

## Project Overview

InnerFlow is a full-stack AI emotional support and clinical assistant platform.

- Backend: Java 21, Spring Boot 3.2.5, Maven
- Frontend: Vue 3, TypeScript, Vite, Pinia, Vue Router
- Infrastructure: MySQL, Redis, Kafka, Elasticsearch, Nacos, Sentinel, Docker Compose
- AI/clinical modules: LangGraph4j-style emotion routing, ReAct tools, RAG, MCP/FHIR, multimodal emotion analysis

## Repository Layout

- `src/main/java/com/ling/linginnerflow/`: Spring Boot backend
- `src/test/java/com/ling/linginnerflow/`: backend tests
- `src/main/resources/`: backend configuration and static test pages
- `frontend/`: Vue 3 frontend
- `emotion-service/`: Python emotion service prototype/integration surface
- `docs/`: architecture, API contracts, ADRs, and agent workflow docs
- `skills/`: project-specific reusable agent instructions
- `.github/`: issue, PR, and CI configuration

## Common Commands

### Backend

```bash
./mvnw test
./mvnw spring-boot:run
./mvnw clean package
```

### Frontend

```bash
cd frontend
npm install
npm run dev
npm run type-check
npm run build
```

### Full Stack Local Services

```bash
docker compose up -d
```

## Coding Standards

### Backend Java

- Keep package names under `com.ling.linginnerflow`.
- Prefer feature-oriented packages that already exist, such as `agent`, `memory`, `rag`, `user`, `websocket`, `doctor`, and `checkin`.
- Keep controllers thin; business logic belongs in services.
- Use repositories only for persistence access.
- Use DTO/request/response classes for API boundaries when adding non-trivial endpoints.
- Do not hardcode secrets, API keys, hosts, or credentials.
- Keep medical/clinical user-facing claims conservative and safety-oriented.

### Frontend Vue

- Use Vue 3 Composition API and TypeScript.
- Keep API calls in `frontend/src/api/`.
- Keep route-level pages in `frontend/src/views/`.
- Keep shared state in Pinia stores under `frontend/src/stores/`.
- Keep UI copy empathetic, clear, and safety-aware.

### Tests

- Add or update focused tests when changing backend service behavior, controller behavior, auth, memory, RAG, or agent routing.
- Prefer small unit/service tests before broad integration tests.
- For frontend changes, run `npm run type-check` and `npm run build` when practical.

## Safety Rules

- Do not remove crisis-response behavior without explicit approval.
- Do not weaken authentication, authorization, rate limiting, or privacy protections.
- Do not log secrets, JWTs, raw credentials, or sensitive clinical text.
- Do not introduce external paid APIs or network dependencies without documenting configuration and fallback behavior.
- Do not make large unrelated refactors inside a task branch.

## Agent Workflow

- Work from an issue or clearly scoped task.
- Read `AGENTS.md` first, then relevant files in `docs/agent-workflows/` and `skills/`.
- If a task changes an API contract, update `docs/api-contracts/` first or in the same PR.
- Keep each branch/worktree focused on one issue.
- Use Conventional Commits.
- Include tests or a clear validation note in the PR.
