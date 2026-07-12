# solver-service (Cloud Run CP-SAT worker)

JVM HTTP service that claims Firestore program-synthesis jobs, runs
`CpSatProgramSynthesizer` (OR-Tools), writes result JSON to Cloud Storage, and
updates job status.

Dispatched by the Node function `processProgramSynthesisJob` (Phase 4):

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
| `src/main/kotlin/.../ProcessRequest.kt` | Body parse |
| `Dockerfile` | JRE 17 + libgomp + fat jar |

Depends on monorepo modules `:core:domain` (wire codec) and `:core:domain-solver`
(CP-SAT + OR-Tools natives).

## Process flow

1. Parse `{uid, jobId}`
2. Firestore doc `users/{uid}/synthesisJobs/{jobId}`
3. Transaction claim: only if `status == pending` → `running`, `progressPhase=solving`
4. Read `requestStoragePath`, download bytes from the default Storage bucket
5. `ProgramSynthesisWire.decodeRequest`
6. `CpSatProgramSynthesizer(deadlineMillis = 120_000).synthesize(...)` via `runBlocking`
7. `ProgramSynthesisWire.encodeResult` → upload `synthesis/{uid}/{jobId}/result.json`
8. Job → `status=ready`, `resultStoragePath`, `progressPhase=complete`
9. On failure → `status=failed`, sanitized `errorMessage` (≤500 chars), `progressPhase=failed`
10. Missing job doc (account deleted) → graceful no-op

HTTP responses for `/process` are **200** after claim handling (including synthesis
failure written to the job) so the Node dispatcher does not overwrite terminal state.
Invalid body → **400**. Unhandled crash before/without job update → **500**.

## Build

From monorepo root:

```bash
./gradlew :solver-service:compileKotlin
./gradlew :solver-service:test
./gradlew :solver-service:shadowJar
```

Fat jar output:

```
solver-service/build/libs/solver-service-all.jar
```

### Local run (needs ADC + project)

```bash
export GOOGLE_CLOUD_PROJECT=stratum-a1a5b
export FIREBASE_STORAGE_BUCKET=stratum-a1a5b.firebasestorage.app
export PORT=8080
# Application Default Credentials (gcloud auth application-default login)
java -jar solver-service/build/libs/solver-service-all.jar
```

Health:

```bash
curl -sS localhost:8080/health
```

## Docker

**Recommended build context = monorepo root** (matches `Dockerfile` `COPY` path):

```bash
./gradlew :solver-service:shadowJar
docker build -f solver-service/Dockerfile -t stratum-solver .
```

If you prefer module context only, adjust the `COPY` line in `Dockerfile` to:

```dockerfile
COPY build/libs/solver-service-all.jar /app/app.jar
```

then:

```bash
docker build -t stratum-solver solver-service/
```

## Environment variables

| Variable | Required | Description |
|----------|----------|-------------|
| `PORT` | Cloud Run sets | Listen port (default `8080`) |
| `GOOGLE_CLOUD_PROJECT` / `GCLOUD_PROJECT` | Recommended | Firebase / GCP project id |
| `FIREBASE_STORAGE_BUCKET` | Recommended | Default bucket, e.g. `stratum-a1a5b.firebasestorage.app` |
| ADC / Cloud Run SA | Yes | Firestore + Storage access |

Optional JVM system properties (see `CpSatProgramSynthesizer`):

- `stratum.solver.log_search=true` — verbose CP-SAT search logs
- `stratum.solver.warm_start=true` — experimental warm start

## Deploy notes (do not auto-deploy from this module)

Suggested Cloud Run settings:

| Setting | Value |
|---------|--------|
| CPU | **8** |
| Memory | **4 GiB** (Cloud Run minimum for 8 vCPU; 2 GiB is not allowed at this CPU) |
| Request timeout | **180 s** |
| Concurrency | **1** (one solve per instance) |
| Min instances | **0** |
| Max instances | **10–20** |
| Ingress / auth | **Require authentication** (not public) |
| Runtime SA | Access to Firestore + Storage for synthesis paths |

Grant the **Firebase Functions** service account **Cloud Run Invoker** on this
service so `processProgramSynthesisJob` can POST with an OIDC token.

Example (adjust project/region/service name):

```bash
gcloud run deploy stratum-solver \
  --image=stratum-solver \
  --region=us-central1 \
  --cpu=8 \
  --memory=4Gi \
  --timeout=180 \
  --concurrency=1 \
  --min-instances=0 \
  --max-instances=20 \
  --no-allow-unauthenticated \
  --set-env-vars=GOOGLE_CLOUD_PROJECT=stratum-a1a5b,FIREBASE_STORAGE_BUCKET=stratum-a1a5b.firebasestorage.app

# After deploy, set SOLVER_SERVICE_URL on the Node functions to the service URL.
```

## Logging

Stdout lines:

- `SolverApplication:` startup / config
- `ProcessHandler:` bad requests / unhandled
- `SynthesisJobWorker:` claim, solve summary hooks (via synthesizer `println`), upload, errors
- `CpSatProgramSynthesizer:` incumbent / final objective (from domain-solver)

## Security notes

- Service is intended **private** (Cloud Run IAM). OIDC is validated by Cloud Run;
  the app trusts the network boundary and validates the JSON body.
- Never ship OpenRouter or other LLM keys in this image — this worker is CP-SAT only.
