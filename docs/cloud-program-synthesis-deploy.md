# Cloud program synthesis — build & deploy

How to build the CP-SAT worker, deploy Cloud Run + Firebase Functions/rules, and wire the Android client. This is the runbook used for the cloud synthesis path (off-device OR-Tools).

**Project:** `stratum-a1a5b`  
**Region:** `us-central1` (match existing Functions)

---

## Architecture (who talks to what)

```text
Android app
  → Storage: put synthesis/{uid}/{jobId}/request.json
  → Callable startProgramSynthesis
  → poll getProgramSynthesisStatus
  → Storage: get synthesis/{uid}/{jobId}/result.json

Firebase Functions (Node)
  startProgramSynthesis     — auth, quota (10 starts/month), active-job gate, job doc
  processProgramSynthesisJob — on pending job, OIDC POST to Cloud Run (timeout 540s)
  getProgramSynthesisStatus — status for client poll
  onSynthesisJobWritten     — increment success count when status=ready

Cloud Run: stratum-solver (JVM)
  POST /process  — claim job, CP-SAT ≤120s, write result, mark ready|failed
  GET  /health
```

The app **never** calls Cloud Run. Only the Node dispatcher does, with an OIDC identity token.

| Resource | Name / path |
|----------|-------------|
| Cloud Run service | `stratum-solver` |
| Artifact Registry image | `us-central1-docker.pkg.dev/stratum-a1a5b/stratum/stratum-solver:latest` |
| Functions env | `SOLVER_SERVICE_URL` (e.g. `https://stratum-solver-….run.app`) |
| Storage | `stratum-a1a5b.firebasestorage.app` |
| Job docs | `users/{uid}/synthesisJobs/{jobId}` |
| Quota | `users/{uid}/synthesisQuota/current` |

---

## Prerequisites

```bash
gcloud auth login
gcloud config set project stratum-a1a5b
firebase login   # if needed
firebase use stratum-a1a5b
```

Enable APIs once per project (idempotent):

```bash
gcloud services enable \
  run.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  --project=stratum-a1a5b
```

Create the Artifact Registry Docker repo once if missing:

```bash
gcloud artifacts repositories create stratum \
  --repository-format=docker \
  --location=us-central1 \
  --description="Stratum container images" \
  --project=stratum-a1a5b
```

---

## 1. Build the fat jar (always)

From monorepo root:

```bash
./gradlew :solver-service:test :solver-service:shadowJar
```

Output:

```text
solver-service/build/libs/solver-service-all.jar
```

The Shadow task patches gRPC `META-INF/services` after package (required for Firestore Admin). Do not strip that post-step.

Host unit tests for the real solver (optional, slow):

```bash
./gradlew :core:domain-solver-tests:test -PrunSolverTests=true
```

---

## 2. Build & push the container image

**Preferred when local Docker is not installed:** Cloud Build with a minimal staging tree (same layout as monorepo root for the Dockerfile `COPY`).

```bash
PROJECT=stratum-a1a5b
REGION=us-central1
IMAGE_URI=${REGION}-docker.pkg.dev/${PROJECT}/stratum/stratum-solver:latest

DEPLOY_DIR=$(mktemp -d)
mkdir -p "${DEPLOY_DIR}/solver-service/build/libs"
cp solver-service/build/libs/solver-service-all.jar \
  "${DEPLOY_DIR}/solver-service/build/libs/"
cp solver-service/Dockerfile "${DEPLOY_DIR}/Dockerfile"

cat > "${DEPLOY_DIR}/cloudbuild.yaml" <<EOF
steps:
  - name: gcr.io/cloud-builders/docker
    args: ['build', '-f', 'Dockerfile', '-t', '${IMAGE_URI}', '.']
images:
  - '${IMAGE_URI}'
EOF

gcloud builds submit "${DEPLOY_DIR}" \
  --config="${DEPLOY_DIR}/cloudbuild.yaml" \
  --project=${PROJECT} \
  --timeout=20m

rm -rf "${DEPLOY_DIR}"
```

**If Docker is available locally:**

```bash
./gradlew :solver-service:shadowJar
docker build -f solver-service/Dockerfile -t stratum-solver .
# Tag and push to Artifact Registry (configure docker auth first):
# gcloud auth configure-docker us-central1-docker.pkg.dev
docker tag stratum-solver ${IMAGE_URI}
docker push ${IMAGE_URI}
```

Dockerfile **build context must be monorepo root** (or a staging dir with the same relative path `solver-service/build/libs/…`). See `solver-service/Dockerfile`.

---

## 3. Deploy Cloud Run

```bash
PROJECT=stratum-a1a5b
REGION=us-central1
IMAGE_URI=${REGION}-docker.pkg.dev/${PROJECT}/stratum/stratum-solver:latest

gcloud run deploy stratum-solver \
  --image=${IMAGE_URI} \
  --region=${REGION} \
  --project=${PROJECT} \
  --cpu=8 \
  --memory=4Gi \
  --timeout=180 \
  --concurrency=1 \
  --min-instances=0 \
  --max-instances=20 \
  --no-allow-unauthenticated \
  --cpu-boost \
  --set-env-vars=GOOGLE_CLOUD_PROJECT=${PROJECT},FIREBASE_STORAGE_BUCKET=stratum-a1a5b.firebasestorage.app \
  --port=8080
```

Notes:

- **4 GiB** is the Cloud Run minimum memory for **8 vCPU** (2 GiB is rejected).
- **concurrency=1** so one solve owns the CPUs.
- **timeout=180s** covers 120s CP-SAT deadline + load/I/O.

Capture the service URL:

```bash
SOLVER_URL=$(gcloud run services describe stratum-solver \
  --region=us-central1 --project=stratum-a1a5b \
  --format='value(status.url)')
echo "$SOLVER_URL"
```

### IAM: Functions → Cloud Run Invoker

Runtime SA for gen2 Functions is typically the default compute SA. Grant invoker (idempotent):

```bash
PROJECT_NUMBER=$(gcloud projects describe stratum-a1a5b --format='value(projectNumber)')
for SA in \
  "${PROJECT_NUMBER}-compute@developer.gserviceaccount.com" \
  "stratum-a1a5b@appspot.gserviceaccount.com"
do
  gcloud run services add-iam-policy-binding stratum-solver \
    --region=us-central1 \
    --project=stratum-a1a5b \
    --member="serviceAccount:${SA}" \
    --role="roles/run.invoker" \
    --quiet
done
```

Cloud Run’s own runtime SA also needs Firestore + Storage access (default compute SA usually has project-level rights on Firebase projects; tighten later if you use a custom SA).

---

## 4. Configure Functions env

`processProgramSynthesisJob` reads `SOLVER_SERVICE_URL` from the Functions environment (loaded from dotenv at deploy).

Create **gitignored** files (do not commit secrets or env):

```bash
# functions/.env.stratum-a1a5b  (and/or functions/.env)
SOLVER_SERVICE_URL=https://stratum-solver-XXXX.run.app
```

Rules:

- No trailing slash on the URL.
- Do **not** set `ALLOW_UNAUTH_SOLVER_DISPATCH=true` in production (OIDC fail-closed).

---

## 5. Deploy Firebase (Functions + rules)

From monorepo root, after `cd functions && npm run build` (or rely on predeploy):

```bash
# First time or after Node/solver contract changes:
firebase deploy --only functions,firestore:rules,storage --project stratum-a1a5b

# Hosting (Terms of Use fair-use section) when legal copy changes:
firebase deploy --only hosting --project stratum-a1a5b
```

Relevant exports (Node codebase `default`):

- `startProgramSynthesis`
- `getProgramSynthesisStatus`
- `processProgramSynthesisJob` (timeout **540s**)
- `onSynthesisJobWritten` (quota on `ready`)

Storage rules: client may write only `synthesis/{uid}/{jobId}/request.json`; `result.json` is client read-only (Admin/worker writes).

---

## 6. Android app

No separate deploy for the APK beyond your normal Play/debug install. The client is:

- `FirebaseProgramSynthesisAdapter` bound as `ProgramSynthesisPort`
- Upload → start → poll (≤5 min) → download result

Build/install:

```bash
./gradlew :app:assembleDebug
# install via Android Studio or adb
```

Generation requires **signed-in** Google user + network.

---

## 7. Smoke checklist

1. Sign in on device/emulator.
2. Run onboarding / create program generation.
3. Expect **up to ~2 minutes** of “synthesizing” (plus cold start on first hit).
4. On success, a program draft appears; Firestore job `status=ready` and `result.json` exists under Storage.

Logs:

```bash
# Cloud Run worker
gcloud run services logs read stratum-solver \
  --region=us-central1 --project=stratum-a1a5b --limit=50

# Node dispatcher
gcloud run services logs read processprogramsynthesisjob \
  --region=us-central1 --project=stratum-a1a5b --limit=50
```

Health (requires invoker identity; user ADC may 401 — Functions SA is the real caller):

```bash
# From a principal with run.invoker, or use Cloud Console → Cloud Run → Logs
```

---

## Operational constants (keep aligned)

| Constant | Value | Where |
|----------|--------|--------|
| CP-SAT deadline | 120s | `CpSatProgramSynthesizer` / worker |
| Cloud Run request timeout | 180s | `gcloud run deploy` |
| Node dispatcher timeout | 540s | `processProgramSynthesisJob` |
| Client poll ceiling | 5 min | `FirebaseProgramSynthesisAdapter` |
| Stale job reclaim | 6 min | `synthesisJobGuards.ts` (must be **>** client poll) |
| Monthly fair use | 10 **starts**/UTC month | `synthesisQuota.ts` |
| Request max size | 5 MiB | Storage rules + start metadata + worker |

---

## Typical “ship a solver fix” sequence

```bash
# 1. Code + tests
./gradlew :solver-service:test :solver-service:shadowJar
cd functions && npm test && npm run build && cd ..

# 2. Image + Cloud Run (Cloud Build path above)
# 3. Env already set unless URL changed
# 4. Functions/rules if Node or rules changed
firebase deploy --only functions,storage --project stratum-a1a5b

# 5. App if client changed
./gradlew :app:assembleDebug
```

If only the JVM worker changed, steps 2 + smoke are enough (no Functions redeploy).  
If only Node/quota/rules changed, step 4 + smoke.  
If only Android adapter changed, step 5 + smoke.

---

## Related files

| Path | Role |
|------|------|
| `solver-service/` | JVM worker, Dockerfile, module README |
| `functions/src/program-synthesis/` | Callables, quota, dispatch |
| `core/inference/.../FirebaseProgramSynthesisAdapter.kt` | Android client |
| `core/domain/.../synthesis/wire/` | Shared request/result JSON |
| `storage.rules` / `firestore.rules` | Client vs Admin access |
| `hosting/public/terms/index.html` | Fair-use language |
| `AGENTS.md` §4.6 | Product/intelligence policy |
