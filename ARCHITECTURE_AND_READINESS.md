# PersonaForge Studio — Architecture, Security & Production Readiness Guide

## 1. Executive Summary
**PersonaForge Studio** (`com.aistudio.personaforge.kxmpzq`) is an API-first, agent-operable AI creator workstation designed to operate seamlessly from both its **native Android app** (Kotlin, Jetpack Compose, Material 3, MVVM) and authorized **ChatGPT users** via a remote **Model Context Protocol (MCP) Server** over authenticated Streamable HTTP.

Both interfaces share the exact same Firestore multi-tenant schema (`ai-studio-android-46fd5efb-0292-450d-920c-6e66b3c8ab1f`), enforce server-derived `userId` isolation, require explicit owner approval before any external publish action, and store zero binary blobs inside Firestore documents.

---

## 2. System Architecture & Data Schema

### Tenant-Scoped Firestore Collections (`/users/{userId}/...`)
1. `personas/{personaId}`: Consented character identity records (`name`, `handle`, `status`, `profileVersion`, `facialIdentityDescriptor`, `referenceStorageUri`, `referenceSha256`, `consentSubjectName`, `consentVerified`, `retentionPolicyDays`).
2. `jobs/{jobId}`: Durable generation queue jobs (`idempotencyKey`, `personaId`, `personaVersion`, `jobType`, `provider`, `modelId`, `prompt`, `emotion`, `stylePreset`, `sourceReferenceUri`, `estimatedCostMicros`, `actualCostMicros`, `status`, `retryCount`, `maxRetries`, `outputAssetId`).
3. `assets/{assetId}`: Generated media provenance & review records (`personaId`, `personaName`, `personaVersion`, `jobId`, `idempotencyKey`, `mediaType`, `storageUri`, `sha256Hash`, `emotion`, `stylePreset`, `modelProvider`, `modelId`, `costMicros`, `sourceReferenceUri`, `versionNumber`, `reviewStatus`, `reviewerNotes`).
4. `schedules/{scheduleId}`: Content schedule drafts & queues (`assetId`, `personaId`, `personaName`, `destinationPlatform`, `caption`, `scheduledEpochMillis`, `status`, `activeApprovalId`, `bindingHash`).
5. `approvals/{approvalId}`: Cryptographically bound publish approvals (`scheduleId`, `assetId`, `destinationPlatform`, `scheduledEpochMillis`, `caption`, `bindingHash`, `requestedByRole`, `status`, `decisionNotes`, `expiresAtEpochMillis`).
6. `analytics/{entryId}`: Provenance-segmented telemetry (`assetId`, `personaId`, `platformName`, `sourceProvenance` in `VERIFIED_PLATFORM | IMPORTED_DATASET | MANUAL_ENTRY`, `impressions`, `engagements`, `conversions`, `revenueMicros`).
7. `quota/{docId}`: Spend & rate limit controls (`monthlyBudgetLimitMicros`, `currentMonthSpendMicros`, `dailyGenerationLimit`, `todayGenerationCount`, `alertThresholdPercent`, `falAiEnabled`, `replicateEnabled`).
8. `audit_logs/{logId}`: Immutable append-only audit trail (`actorRole`, `action`, `targetCollection`, `targetId`, `idempotencyKey`, `outcome`, `details`).

---

## 3. Complete 13-Tool ChatGPT MCP Plugin Contract
Implemented in `/backend/src/server.ts` and `/backend/src/mcpToolsEngine.js`, and documented in `/backend/openapi.yaml`:

| Tool Name | Mode | Mutates State? | Security & Policy Enforcement |
| :--- | :--- | :--- | :--- |
| `get_studio_status` | Read-Only | No | Returns tenant quota, budget usage, pending approvals, and provider states. |
| `list_personas` | Read-Only | No | Lists only personas owned by the authenticated bearer token's `uid`. |
| `get_persona` | Read-Only | No | Enforces tenant ownership; blocks cross-tenant reads (`FORBIDDEN_TENANT_ACCESS`). |
| `create_persona_draft` | Mutating | Yes | Requires `consentVerified == true`, metadata-only `referenceStorageUri`, and `idempotencyKey`. |
| `list_assets` | Read-Only | No | Lists tenant assets with optional `personaId` and `reviewStatus` filters. |
| `generate_image_draft` | Mutating | Yes | Enforces daily quota, monthly budget cap, provider enablement gate, and idempotency deduplication. |
| `get_generation_status` | Read-Only | No | Returns job status, retry count, model provenance, and output asset reference. |
| `review_asset` | Mutating | Yes | Transitions asset between `DRAFT`, `APPROVED`, and `REJECTED` with audit logging. |
| `prepare_content_schedule` | Mutating | Yes | Rejects `DRAFT` or `REJECTED` assets; only `APPROVED` assets can be scheduled. |
| `list_scheduled_posts` | Read-Only | No | Lists tenant schedules and their bound approval hashes. |
| `get_analytics_summary` | Read-Only | No | Returns strictly separated buckets for `VERIFIED_PLATFORM`, `IMPORTED_DATASET`, and `MANUAL_ENTRY`. |
| `request_publish_approval` | Mutating | Yes | Computes `SHA256(assetId|destinationPlatform|scheduledEpochMillis|caption)` and creates a `PENDING` ticket. Never publishes. |
| `publish_approved_post` | Mutating | Yes | Requires an owner-approved ticket (`status == APPROVED`), non-expired timestamp, and exact `bindingHash` match. |

---

## 4. Transparent Capability Matrix (Live vs Gated/Disabled by Default)

| Subsystem | Status | Implementation Details |
| :--- | :--- | :--- |
| **Firebase Auth (Google Sign-In)** | **LIVE** | Jetpack Credential Manager (`GetSignInWithGoogleOption`) + `Firebase.auth`. |
| **Firestore Multi-Tenant Database** | **LIVE** | Provisioned named database `ai-studio-android-46fd5efb-0292-450d-920c-6e66b3c8ab1f` with zero-trust rules. |
| **Gemini Image Generation** | **LIVE** | `gemini-nano-banana-2.1` & `gemini-3-pro-image` via Firebase AI Logic + App Check. |
| **Gemini Facial DNA & Video Storyboard** | **LIVE** | `gemini-3.8-flash` multimodal reference analysis & 4-scene structured JSON storyboard generation. |
| **Private Media Vault** | **LIVE** | Binary JPEGs stored in app-private storage (`vault://users/{uid}/...`) with SHA-256 hashes; zero binary blobs in Firestore. |
| **fal.ai & Replicate Backend Adapters** | **DEFAULT DISABLED** | Disabled by default (`falAiEnabled = false`, `replicateEnabled = false`) until Cloud Run secrets (`FAL_KEY`, `REPLICATE_API_TOKEN`) are configured and owner-enabled. |

---

## 5. Build, Verification & Deployment Instructions

### A. Run Firestore Security Rules Tests (Node.js)
```bash
node --test firestore.test.js
```

### B. Run ChatGPT MCP Backend Acceptance Tests
```bash
node --test backend/test/mcp_acceptance.test.js
```

### C. Run Android Robolectric Repository & Rule Tests
```bash
gradle :app:testDebugUnitTest --tests '*Rule*'
```

### D. Deploy Cloud Run Backend & MCP Server
1. Navigate to `/backend` and install dependencies:
   ```bash
   npm install && npm run build
   ```
2. Deploy to Google Cloud Run with Secret Manager bindings (never in client APK):
   ```bash
   gcloud run deploy personaforge-mcp \
     --source ./backend \
     --region us-central1 \
     --set-env-vars FIREBASE_DATABASE_ID=ai-studio-android-46fd5efb-0292-450d-920c-6e66b3c8ab1f \
     --set-secrets FAL_KEY=FAL_KEY:latest,REPLICATE_API_TOKEN=REPLICATE_API_TOKEN:latest
   ```

---

## 6. Production-Readiness & Owner Approval Checklist
- [x] **Zero Client Secrets**: `.env.example` and Android APK contain zero third-party API keys.
- [x] **Tenant Isolation Verified**: User A cannot read or mutate User B's personas, jobs, assets, schedules, approvals, analytics, quota, or audit logs.
- [x] **No Binary Media in Firestore**: Enforced in `SecurityHashUtil.requireMetadataOnlyUri` and `firestore.rules`.
- [x] **Two-Step Publish Approval**: `request_publish_approval` and `publish_approved_post` are cryptographically bound via SHA-256 to `(assetId, destinationPlatform, scheduledEpochMillis, caption)`.
- [x] **SSRF Protection**: `validateProviderUrl` blocks private/loopback/link-local IPs (`127.0.0.1`, `169.254.169.254`, `localhost`) and enforces an HTTPS host allowlist.
- [x] **Owner Sign-Off Required Before External Publication**: External publishing remains gated behind explicit owner approval in the Android Approval Vault.
