# DevSentinel / CodeSure — Software Risk Intelligence Platform

> **Core Objective**: Analyze a developer's code change, collect multi-dimensional evidence about that change, predict release risk, and help the developer understand which detected issues deserve attention first.
>
> **Golden Principle**: **"Evidence before AI"** — Code changes feed into static analysis, historical churn, coverage, and dependency graphs before risk scoring and explainability.

---

## Architecture Overview

```
                            ┌─────────────────────┐
                            │  GitHub Repository  │
                            └──────────┬──────────┘
                                       │
                                  Pull Request / Commits
                                       │
                                       ▼
                            ┌─────────────────────┐
                            │   Change Analyzer   │
                            │ Files, Lines, Diffs │
                            └──────────┬──────────┘
                                       │
                ┌──────────────────────┼──────────────────────┐
                │                      │                      │
                ▼                      ▼                      ▼
         Static Analysis          Git History           Test Coverage
         (Security/Rules)       (Churn/Velocity)       (Affected Code)
                │                      │                      │
                └──────────────────────┼──────────────────────┘
                                       │
                                       ▼
                            ┌─────────────────────┐
                            │    CodeSure Risk    │
                            │       Engine        │
                            └──────────┬──────────┘
                                       │
                                       ▼
                            ┌─────────────────────┐
                            │ Risk Prioritization │
                            │ CRITICAL | HIGH     │
                            │ MEDIUM   | LOW      │
                            └──────────┬──────────┘
                                       │
                                       ▼
                            ┌─────────────────────┐
                            │  Evidence-Based AI  │
                            │     Explanation     │
                            └──────────┬──────────┘
                                       │
                                       ▼
                            ┌─────────────────────┐
                            │   React Dashboard   │
                            └─────────────────────┘
```

---

## Key Features

1. **GitHub Ingestion Engine**:
   - Seamlessly connect public and private GitHub repositories via URL.
   - Automatically sync metadata, commit histories, pull requests, and commit-to-commit diffs.

2. **Change-Aware Analysis**:
   - Focuses strictly on changed and added lines (`+`) in unified git diffs.
   - Maps file additions, deletions, renames, and modifications.

3. **Deterministic Static Rule Engine**:
   - Built-in security and quality rules covering SQL Injection, hardcoded credentials, insecure cryptography, path traversal, generic exception handling, and improper logging.
   - Categorized by severity: `CRITICAL`, `HIGH`, `MEDIUM`, and `LOW`.

4. **Multi-Factor Risk Scoring Engine**:
   - Evidence-weighted formula incorporating static finding severities, code churn volume (+/- lines), and commit velocity.
   - Categorizes release risk into `CRITICAL`, `HIGH`, `MEDIUM`, and `LOW`.

5. **Evidence-Based AI Reasoning**:
   - Formulates explainability narratives outlining the exact evidence factors detected and providing clear remediation guidance for developers.

6. **Interactive React Intelligence Dashboard**:
   - Live backend & database status indicators.
   - Real-time risk scorecard and risk level gauges.
   - Code churn and findings breakdown table with line numbers and rule details.

---

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Backend** | Java 21 LTS, Spring Boot 4, Spring Data JPA, Hibernate |
| **Database** | PostgreSQL 18 |
| **Frontend** | React 19, TypeScript, Vite |
| **Static Analysis** | Semgrep / Custom Deterministic Static Rule Engine |
| **Version Control** | Git, GitHub REST API |

---

## Project Structure

```
codesure/
├── backend/
│   ├── src/main/java/com/codesure/backend/
│   │   ├── analyzer/       # Static rule analyzer service and controller
│   │   ├── codechange/     # CodeChange entity and repository
│   │   ├── commit/         # Commit entity and repository
│   │   ├── config/         # Global CORS and web configurations
│   │   ├── finding/        # Static analysis finding entity and repository
│   │   ├── github/         # GitHub API client, DTOs, and sync service
│   │   ├── health/         # System health controller
│   │   ├── pullrequest/    # PullRequest entity and repository
│   │   ├── repository/     # Repository entity, DTOs, service, controller
│   │   └── risk/           # Risk intelligence engine and scoring service
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   └── application-local.properties (ignored from git)
│   └── pom.xml
│
├── frontend/
│   ├── src/
│   │   ├── App.tsx         # Risk Intelligence Dashboard
│   │   ├── App.css         # Modern dark-mode styling
│   │   └── main.tsx
│   ├── package.json
│   └── vite.config.ts
│
└── README.md
```

---

## Getting Started

### Prerequisites
- **Java 21 LTS**
- **Apache Maven 3.9+**
- **Node.js 20+ & npm**
- **PostgreSQL 16+**

### 1. Database Setup
Create the PostgreSQL database:
```sql
CREATE DATABASE codesure;
```

Configure `backend/src/main/resources/application-local.properties`:
```properties
spring.datasource.password=YOUR_POSTGRES_PASSWORD
```

### 2. Run the Backend
```bash
cd backend
mvn spring-boot:run
```
The backend API starts at `http://localhost:8080`.

### 3. Run the Frontend Dashboard
```bash
cd frontend
npm install
npm run dev
```
Open `http://localhost:5173` in your browser.

---

## REST API Reference

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/health` | Health check endpoint |
| `GET` | `/api/repositories` | List all tracked repositories |
| `POST` | `/api/repositories/sync-url` | Ingest repository metadata from GitHub URL |
| `POST` | `/api/repositories/{id}/sync-commits` | Sync commit history from GitHub |
| `POST` | `/api/repositories/{id}/sync-pulls` | Sync pull requests from GitHub |
| `GET` | `/api/repositories/{id}/changes` | Compare commit diffs (`?base=...&target=...`) |
| `GET` | `/api/repositories/{id}/findings` | Retrieve all static analysis findings |
| `GET` | `/api/repositories/{id}/risk-assessment` | Compute live risk score and AI explanation |
| `POST` | `/api/analyzer/scan-change/{id}` | Run static analyzer on a specific change patch |


---

## Deploying to Render

This repository includes a multi-stage `Dockerfile` and `render.yaml` Blueprint for 1-click or manual deployment to [Render](https://render.com).

### Option A: 1-Click Blueprint (Recommended)
1. In your Render Dashboard, click **New +** -> **Blueprint**.
2. Connect your GitHub repository (`Kishorerz/CodeSure`).
3. Render will read `render.yaml` and automatically configure:
   - A **PostgreSQL Database** (`codesure-postgres`)
   - A unified **Docker Web Service** (`codesure-app`) serving both the backend API and the React frontend on a single port.
4. Click **Apply**.

### Option B: Manual Web Service
1. On Render, click **New +** -> **Web Service**.
2. Select your repository.
3. Runtime: **Docker**.
4. Set the Health Check Path to: `/api/health`.
5. Environment Variables:
   - `SPRING_PROFILES_ACTIVE`: `prod`
   - `SPRING_JPA_HIBERNATE_DDL_AUTO`: `update`
   - `DATABASE_URL`: Your PostgreSQL connection string (`postgresql://user:pass@host/db`).
