[![Deploy Backend to Cloud Run](https://github.com/nanaki-93/hiragame/actions/workflows/backend-deploy.yml/badge.svg)](https://github.com/nanaki-93/hiragame/actions/workflows/backend-deploy.yml)
[![Deploy to Firebase](https://github.com/nanaki-93/hiragame/actions/workflows/firebase-deploy.yml/badge.svg)](https://github.com/nanaki-93/hiragame/actions/workflows/firebase-deploy.yml)
[![Qodana](https://github.com/nanaki-93/hiragame/actions/workflows/qodana_code_quality.yml/badge.svg)](https://github.com/nanaki-93/hiragame/actions/workflows/qodana_code_quality.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-7F52FF?logo=kotlin&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.2-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Kobweb](https://img.shields.io/badge/Kobweb-0.23.0-4B32C3)

# Hiragame

Hiragame is a full-stack Japanese learning game focused on Hiragana, romanization practice, and JLPT-level progression. It combines a Kotlin/Kobweb browser app, a Spring Boot API, shared Kotlin models, PostgreSQL persistence, JWT cookie authentication, and optional AI-assisted question generation.

## Features

- Practice Hiragana through `SIGN`, `WORD`, and `SENTENCE` game modes.
- Progress through JLPT-inspired levels from `N5` to `N1`.
- Track score, streaks, attempts, correct answers, and immediate feedback.
- Register, log in, refresh sessions, and log out using HTTP-only JWT cookies.
- Share API DTOs and enums between frontend and backend through the `shared` module.
- Generate and store additional questions through Spring AI integrations.
- Deploy the backend to Google Cloud Run and the frontend to Firebase Hosting.

## Tech Stack

| Area | Technology |
| --- | --- |
| Frontend | Kotlin/JS, Kobweb, Compose HTML, Silk |
| Backend | Kotlin, Spring Boot 3, Spring Security, Spring Data JPA |
| Shared code | Kotlin Multiplatform |
| Database | PostgreSQL |
| AI | Spring AI, Ollama, Gemini-compatible generation endpoint |
| Build | Gradle Kotlin DSL, Java 21 |
| Deployment | Docker, Google Cloud Run, Firebase Hosting |
| Quality | Qodana |

## Repository Layout

```text
.
├── backend/   # Spring Boot API, auth, game logic, AI generation, database schema
├── shared/    # Kotlin Multiplatform DTOs, enums, and shared models
├── site/      # Kobweb frontend served under /hiragame
├── Dockerfile # Backend container image
└── firebase.json
```

## Prerequisites

- JDK 21.
- Docker and Docker Compose for local PostgreSQL and optional Ollama.
- Kobweb CLI for frontend development.
- `psql` if you want to initialize the local database from the included SQL/CSV files.

## Local Development

### 1. Start local services

From the repository root:

```bash
docker compose -f backend/compose.yaml up -d postgres
```

Start Ollama too if you want to use local AI generation:

```bash
docker compose -f backend/compose.yaml up -d ollama
```

The Compose file exposes PostgreSQL on `localhost:5455`. It exposes Ollama on `localhost:11434`, while the checked-in backend default points to `http://localhost:1111/`, so override that value when using the Compose Ollama service.

### 2. Configure backend environment

From the repository root:

```bash
export DB_URL=localhost:5455/hiragame
export DB_USERNAME=hiradmin
export DB_PASSWORD=hiradmin
export JWT_TOKEN=replace-with-a-long-random-local-secret
export SPRING_USER_NAME=admin
export SPRING_USER_PASSWORD=admin
export SPRING_USER_ROLES=ADMIN
export FRONTEND_ORIGIN_ALLOWED=http://localhost:8081
export SPRING_AI_OLLAMA_BASE_URL=http://localhost:11434
```

Use real secrets outside local development.

### 3. Initialize local data

The local database helpers are split across `backend/schema.sql.bak`, `backend/schema.sql`, and `backend/migration/question.csv`.

```bash
psql "postgresql://hiradmin:hiradmin@localhost:5455/hiragame" -f backend/schema.sql.bak
psql "postgresql://hiradmin:hiradmin@localhost:5455/hiragame" -f backend/schema.sql
psql "postgresql://hiradmin:hiradmin@localhost:5455/hiragame" \
  -c "\copy question(id,japanese,romanization,translation,topic,level,game_mode,created_at,has_katakana,has_kanji) FROM 'backend/migration/question.csv' WITH (FORMAT csv)"
```

### 4. Run the backend

```bash
./gradlew :backend:bootRun
```

The API runs on `http://localhost:8080`.

### 5. Run the frontend

In a second terminal:

```bash
cd site
kobweb run
```

Open `http://localhost:8081/hiragame`. The frontend reads `/hiragame/config.json`, which points local API calls at `http://localhost:8080/api`.

## Common Commands

```bash
# Run backend tests
./gradlew :backend:test

# Build backend
./gradlew :backend:build

# Build frontend distribution
./gradlew :site:build

# Build all modules
./gradlew build

# Build backend container image
docker build -t hiragame-backend .
```

## API Overview

| Endpoint | Purpose |
| --- | --- |
| `POST /api/auth/register` | Create a user and set auth cookies |
| `POST /api/auth/login` | Authenticate and set auth cookies |
| `GET /api/auth/me` | Return the authenticated user |
| `GET /api/auth/refresh-token` | Refresh the access token |
| `GET /api/auth/logout` | Clear auth cookies |
| `POST /api/select-game-mode` | Return available levels for a mode |
| `POST /api/next-question` | Return the next practice question |
| `POST /api/process-answer` | Grade an answer and update progress |
| `POST /api/get-game-state` | Return current game statistics |
| `GET /api/ai/words` | Generate word questions |
| `GET /api/ai/sentences` | Generate sentence questions |
| `POST /api/ai/batch-generate` | Start bulk AI generation |
| `GET /api/ai/batch-status` | Read bulk generation status |

## Deployment

- Backend deployment is automated by `.github/workflows/backend-deploy.yml` and publishes the Dockerized Spring Boot app to Google Cloud Run.
- Frontend deployment is automated by `.github/workflows/firebase-deploy.yml` and publishes `site/build/dist/js/productionExecutable/public` to Firebase Hosting.
- Qodana runs through `.github/workflows/qodana_code_quality.yml`.

Production frontend configuration is stored in `site/src/jsMain/resources/config.prod.json` and copied to `config.json` during the frontend build output reorganization.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
