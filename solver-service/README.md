# solver-service (Cloud Run CP-SAT worker)

JVM HTTP service that claims Firestore program-synthesis jobs, runs
`CpSatProgramSynthesizer` (OR-Tools), writes result JSON to Cloud Storage, and
updates job status.

**Full build/deploy runbook (Functions + Cloud Build + IAM + smoke):**  
[`docs/cloud-program-synthesis-deploy.md`](../docs/cloud-program-synthesis-deploy.md)

## Protocol

Dispatched by Node `processProgramSynthesisJob`:

```http
POST ${SOLVER_SERVICE_URL}/process
Authorization: Bearer <OIDC ID token, audience = service URL>
Content-Type: application/json

{"uid":"<firebase-auth-uid>","jobId":"<client-job-id>"}
```

Also exposes `GET /health` → `200 ok`.

## Layout

| Path | Role |
|------|------|
| `src/main/kotlin/.../SolverApplication.kt` | Entrypoint, Firebase init, HttpServer |
| `src/main/kotlin/.../ProcessHandler.kt` | `POST /process` |
| `src/main/kotlin/.../SynthesisJobWorker.kt` | Claim → download → solve → upload → status |
| `src/main/kotlin/.../ProcessRequest.kt` | Body parse (domain `JsonReader`) |
| `Dockerfile` | JRE 17 + libgomp + fat jar |
| `build.gradle.kts` | Shadow fat jar + gRPC SPI patch |

Depends on `:core:domain` (wire codec) and `:core:domain-solver` (CP-SAT + host OR-Tools natives).

## Process flow

1. Parse `{uid, jobId}`
2. Firestore `users/{uid}/synthesisJobs/{jobId}`
3. Claim only if `status == pending` → `running`
4. Download `synthesis/{uid}/{jobId}/request.json` (path + size validated)
5. `ProgramSynthesisWire.decodeRequest` → `CpSatProgramSynthesizer` (≤120s)
6. Upload `result.json` → job `ready` (or user-safe `failed`)

HTTP: **200** after handling (including job-level failure) so the Node dispatcher can
avoid clobbering terminal state. **400** bad body; **500** unhandled crash.

## Quick build

```bash
# monorepo root
./gradlew :solver-service:test :solver-service:shadowJar
# → solver-service/build/libs/solver-service-all.jar
```

## Quick deploy (no local Docker)

See [docs/cloud-program-synthesis-deploy.md](../docs/cloud-program-synthesis-deploy.md)
sections **2–3** (Cloud Build staging + `gcloud run deploy`). Summary:

| Setting | Value |
|---------|--------|
| CPU | 8 |
| Memory | **4 GiB** (Cloud Run min for 8 vCPU) |
| Timeout | 180s |
| Concurrency | 1 |
| Auth | Require authentication |
| Env | `GOOGLE_CLOUD_PROJECT`, `FIREBASE_STORAGE_BUCKET` |

Then set Functions `SOLVER_SERVICE_URL` and grant **Cloud Run Invoker** to the
Functions runtime SA.

## Local run (optional)

```bash
export GOOGLE_CLOUD_PROJECT=stratum-a1a5b
export FIREBASE_STORAGE_BUCKET=stratum-a1a5b.firebasestorage.app
export PORT=8080
# gcloud auth application-default login
java -jar solver-service/build/libs/solver-service-all.jar
curl -sS localhost:8080/health
```

## Security

- Private Cloud Run; production OIDC only (`ALLOW_UNAUTH_SOLVER_DISPATCH` must stay off).
- No LLM keys in this image — CP-SAT only.
- User-facing job errors are sanitized; details stay in Cloud Run logs.
