# PersonaForge Studio — API-First AI Creator Workstation & MCP Server

PersonaForge Studio is a commercial-grade, API-first AI creator workstation operated seamlessly through both a native Android application (Kotlin, Jetpack Compose, Material 3, MVVM) and authorized ChatGPT clients via a remote Model Context Protocol (MCP) server over authenticated Streamable HTTP. Both interfaces share the same zero-trust backend contract, tenant isolation rules, durable job queue, and explicit human-in-the-loop approval gates.

### User Review & Critical Decisions

> [!IMPORTANT]
> Please review the updated architecture, security invariants, phased build plan (Phases 0–7), and MCP tool specifications below. No external deployments, live billing activations, or external publishing actions will occur without your explicit approval.

- **Confirmed Decision 1 — Phase 0 Baseline Inspection & Gap Analysis**:
  - *Current Workspace State*: The project currently contains the stock Android starter template (`MainActivity` rendering a `"Hello Android!"` greeting, default Material 3 theme tokens, unprovisioned Firebase configuration, and zero backend or MCP server components).
  - *Identified Gaps*: Requires full Firebase Auth + Firestore provisioning, custom database ID binding, metadata-only Firestore schema with security rules, private media storage abstraction (no binary blobs in Firestore), durable idempotent job queue, cost/quota ledger, two-step cryptographically bound publish approval workflow, Node.js/TypeScript Cloud Run API + Streamable HTTP MCP server (`13` tools), OpenAPI 3.1 contract, threat model, and automated acceptance tests.
- **Confirmed Decision 2 — Zero Client Secrets & Metadata-Only Firestore**:
  - Third-party provider keys (`fal.ai`, `Replicate`) are **never** embedded in the Android APK or `.env.example`. Native Android image generation and multimodal facial analysis run through App Check-protected **Firebase AI Logic** (`gemini-nano-banana-2.1`, `gemini-3-pro-image`, `gemini-3.8-flash`), while `fal.ai` and `Replicate` image/video jobs execute exclusively through protected backend adapters (defaulting to `DISABLED` until configured and verified).
  - Firestore documents store **only lightweight metadata, URIs, SHA-256 content hashes, and provenance records**—never raw base64 or binary media payloads. Binary media resides in private object storage (`gs://` Cloud Storage in production / private app-scoped vault with signed URI resolution locally).
- **Confirmed Decision 3 — Strict Two-Step Publish Approval Binding**:
  - Neither the Android scheduler nor the ChatGPT MCP plugin can publish directly. `request_publish_approval` generates a `PublishApprovalRequest` bound to the exact `(assetId, destinationPlatform, scheduledEpochMillis, captionSha256)` fingerprint. Only after an authenticated account owner approves that specific fingerprint can `publish_approved_post` execute—rejecting any modified parameter, expired approval, or duplicate call.

---

### 1. Overview & Core Concept

- **What It Does**: Provides an end-to-end studio for managing consented character personas with versioned facial identity descriptors, generating emotionally consistent images and storyboard-driven videos, tracking asset provenance and cost quotas, and coordinating multi-platform content schedules across both a native Android UI and ChatGPT via MCP.
- **Target Audience / Persona**: Professional digital creators, virtual talent studios, and creative agencies requiring strict tenant isolation, auditable AI spend controls, verifiable consent tracking, and agent-operable workflows with human approval guardrails.
- **Key Value**: Unifies mobile studio direction and conversational AI automation on a single source of truth where every generation, review decision, cost estimate, and publishing approval is idempotent, traceable, and policy-enforced.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Authenticated Studio Entry & Status Overview (`get_studio_status`)**: Users sign in with Google via Jetpack `CredentialManager`. The home command deck surfaces active budget consumption, remaining daily/monthly generation quotas, queued background jobs, pending publish approval requests, and external provider health (`Firebase AI Logic: Active`, `fal.ai: Disabled/Configured`, `Replicate: Disabled/Configured`).
  2. **Persona Vault & Consent Ledger (`list_personas`, `get_persona`, `create_persona_draft`)**:
     - Create, inspect, update, and archive character personas.
     - Attach reference portraits via the zero-permission Android Photo Picker with mandatory **Subject Consent Attestation** (subject name/alias, consent scope, timestamp, revocable retention policy).
     - Extract structured facial identity descriptors via `gemini-3.8-flash` and increment immutable `profileVersion` snapshots on every update.
  3. **Expression Studio & Side-by-Side Comparison (`generate_image_draft`, `get_generation_status`, `review_asset`)**:
     - Select an active persona version, emotion preset + intensity, cinematography style, and target provider.
     - View a **Pre-Flight Cost & Quota Estimate** before submitting an idempotent `GenerationJob`.
     - Compare generated drafts side-by-side against the persona's reference portrait and prior generations, then explicitly mark each asset `APPROVED` or `REJECTED` with review notes.
  4. **Asset Library & Provenance Inspector (`list_assets`)**:
     - Search and filter private assets by persona, approval status (`DRAFT`, `APPROVED`, `REJECTED`), emotion, and provider.
     - Inspect full provenance chains (input reference IDs, persona version, model ID, seed, cost USD, idempotency key, job history) and export only `APPROVED` assets.
  5. **Video Studio (Storyboard-First Pipeline)**:
     - Generate multi-scene video storyboards (keyframe prompts, camera motion vectors, pacing, and emotional arcs) using `gemini-3.8-flash`.
     - Dispatch actual video synthesis exclusively when a verified video-capable backend provider (`fal.ai` or `Replicate`) is enabled and within budget quota.
  6. **Scheduler & Two-Step Approval Gate (`prepare_content_schedule`, `list_scheduled_posts`, `request_publish_approval`, `publish_approved_post`)**:
     - Draft schedule slots for `APPROVED` assets, request owner publish approval bound to the exact `(assetId, destination, scheduledTime, caption)` tuple, review/approve or reject pending requests in the **Approval Vault**, and execute idempotent publishing.
  7. **Segmented Analytics & Billing Controls (`get_analytics_summary`)**:
     - View audience engagement metrics strictly segmented into three provenance tiers: **Verified Platform Metrics**, **Imported Metrics**, and **Manually Entered Data**.
     - Configure budget alerts, per-user spending caps, provider SSRF allowlists, and inspect the interactive **MCP Agent Activity & Audit Log**.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: **Dark Pro-Studio Editorial** — high-contrast cinema color-bay precision with status telemetry pills, provenance badges, and clear simulation/live indicators.
  - *Color Palette & Mood*:
    - `--background`: `#0B0C10` (Obsidian Studio Void)
    - `--surface`: `#141720` (Carbon Slate)
    - `--surface-variant`: `#1E2230` (Elevated Control Deck)
    - `--primary`: `#00F0FF` (Electric Studio Cyan)
    - `--secondary`: `#FF2A85` (Neon Editorial Magenta)
    - `--tertiary`: `#B8FF01` (Signal Lime for verified status & approved gates)
    - `--error`: `#FF4D6D` (Alert Crimson for rejected assets, quota blocks, & unapproved publish attempts)
  - *Typography & Hierarchy*:
    - **Display / Studio Headers**: `Syne` (bundled locally via `font-util`)
    - **Body & Controls**: `Plus Jakarta Sans` (bundled locally via `font-util`)
    - **Provenance / Cost / MCP Schemas**: `JetBrains Mono` (bundled locally via `font-util`)
  - *Component Styling & Layout*:
    - Adaptive Material 3 layout supporting `Compact`, `Medium`, and `Expanded` window size classes (`NavigationBar` on phones, `NavigationRail` + two-pane inspector on tablets/foldables) with full `enableEdgeToEdge()` safe-drawing insets.
- **Interactive Feedback & Motion**:
  - Real-time job polling/listener state transitions (`QUEUED` → `PROCESSING` → `COMPLETED` / `FAILED_RETRYABLE` / `FAILED_FINAL`), pre-flight cost breakdown badges, and prominent **"SIMULATED IN LOCAL SANDBOX"** vs **"LIVE PROVIDER"** badges so stubbed or unconfigured external adapters are never misrepresented as live.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1 — Dual-Surface API-First Architecture (Cloud Run Node.js/TypeScript + Android Sync Engine)**
  - *Chosen Approach*: Build the complete Node.js/TypeScript Cloud Run backend and official MCP Streamable HTTP server alongside the Android application, backed by the same Firestore schema (`/users/{userId}/...`) and strict security rules. In the Android streaming emulator environment, the Android app executes the exact same domain validation, idempotency checks, quota enforcement, and two-step approval binding against Firestore and Firebase AI Logic, while also providing an in-app **MCP Tool Runner & Contract Verifier** so all 13 MCP tools can be exercised interactively on-device as well as via `node --test` against the local emulator.
  - *Why*: Ensures 100% behavioral parity between the Android client and the ChatGPT MCP server while keeping the Android app immediately operable in the AI Studio emulator before external Cloud Run deployment.
- **Decision 2 — Metadata-Only Firestore & Private Object Storage Abstraction**
  - *Chosen Approach*: Enforce a strict size and schema rule on Firestore documents: media fields (`storageUri`, `thumbnailUri`, `sha256Hash`, `mimeType`, `byteSize`) store only pointers to private storage (`gs://<bucket>/users/{uid}/...` or app-private encrypted file vault `file://.../private_vault/users/{uid}/...`), never base64 image blobs.
  - *Why*: Prevents Firestore 1 MiB document bloat, enforces signed/authenticated media access, and supports clean retention/deletion lifecycle sweeps.
- **Decision 3 — Cryptographically Bound Two-Step Publish Approvals**
  - *Chosen Approach*: `request_publish_approval` computes a deterministic binding hash `sha256(assetId + "|" + destination + "|" + scheduledEpochMillis + "|" + caption.trim())` and creates a `PublishApproval` record with status `PENDING` and a 24-hour TTL. Only an explicit owner action transitions it to `APPROVED`. When `publish_approved_post` is invoked (from ChatGPT MCP or Android), the service recomputes the binding hash and verifies: (a) status is `APPROVED`, (b) current time < `expiresAt`, (c) `consumedAt == null`, and (d) the recomputed hash matches `bindingHash`. Upon execution, `consumedAt` and `idempotencyKey` are atomically recorded.
  - *Why*: Guarantees ChatGPT cannot bypass human approval, alter a caption or destination after approval, reuse a rejected/expired token, or double-publish on network retries.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌──────────────────────────────────────┐  ┌──────────────────────────────────────┐
│      Native Android Workstation      │  │     Authorized ChatGPT Client        │
│   (Kotlin, Compose, M3, Credential   │  │   (Remote MCP Plugin via OAuth 2.0   │
│    Manager, Firebase AI Logic)       │  │    Delegated Bearer Token)           │
└──────────────────┬───────────────────┘  └──────────────────┬───────────────────┘
                   │ Firebase Auth ID Token                  │ Streamable HTTP (/mcp)
                   │ + App Check                             │ + REST API (/api/v1/*)
┌──────────────────▼─────────────────────────────────────────▼───────────────────┐
│             PersonaForge Domain & API Boundary (Cloud Run / Shared Core)       │
│  ┌──────────────────────┐ ┌────────────────────────┐ ┌──────────────────────┐  │
│  │ Auth & Tenant Guard  │ │ Idempotency & Quota    │ │ Two-Step Approval    │  │
│  │ (uid from JWT only;  │ │ Ledger (Pre-flight USD │ │ Engine (Exact Hash   │  │
│  │  RBAC: OWNER/AGENT)  │ │ estimate, hard caps)   │ │ Binding & Single-Use)│  │
│  └──────────┬───────────┘ └───────────┬────────────┘ └──────────┬───────────┘  │
└─────────────┼─────────────────────────┼─────────────────────────┼──────────────┘
              │                         │                         │
┌─────────────▼─────────────────────────▼─────────────────────────▼──────────────┐
│                 Data, Queue & Protected Provider Layer                         │
├──────────────────────────┬──────────────────────────┬──────────────────────────┤
│   Firebase Firestore     │  Private Media Storage   │   AI Generation Router   │
│ (Named DB, Zero Binary)  │  (Cloud Storage / Vault) │  • Firebase AI Logic     │
│ • /users/{uid}/personas  │  • Signed URI Resolver   │    (Nano Banana 2.1/3.8) │
│ • /users/{uid}/assets    │  • Consent Attestations  │  • fal.ai Backend Proxy  │
│ • /users/{uid}/jobs      │  • Retention & Purge     │  • Replicate Proxy       │
│ • /users/{uid}/schedules │    Tombstone Sweeper     │    (SSRF Allowlisted,    │
│ • /users/{uid}/approvals │                          │     Default: DISABLED)   │
│ • /users/{uid}/analytics │                          │                          │
│ • /users/{uid}/auditLogs │                          │                          │
│ • /users/{uid}/quotas    │                          │                          │
└──────────────────────────┴──────────────────────────┴──────────────────────────┘
```

- **Data Model & Firestore Schema (Metadata-Only, Scoped under `/users/{userId}/...`)**:
  - `Persona` (`personas/{personaId}`): `id`, `userId`, `name`, `handle`, `status` (`DRAFT` | `ACTIVE` | `ARCHIVED`), `profileVersion` (Long), `facialIdentityDescriptor` (structured map: faceShape, eyeGeometry, browArch, noseBridge, lipContour, complexion, hairSignature, distinguishingMarks), `referenceMediaUris` (list of private storage URIs), `consentRecordId`, `consentVerified` (Boolean), `retentionPolicyDays` (Long), `deletedAt` (Timestamp?), `createdAt`, `updatedAt`.
  - `ConsentRecord` (`consents/{consentId}`): `id`, `userId`, `personaId`, `subjectIdentifier`, `consentStatement`, `referenceSha256List`, `isRevoked` (Boolean), `grantedAt`, `expiresAt`, `createdAt`, `updatedAt`.
  - `GenerationJob` (`jobs/{jobId}`): `id`, `userId`, `idempotencyKey`, `personaId`, `personaVersion` (Long), `jobType` (`IMAGE_DRAFT` | `VIDEO_STORYBOARD` | `VIDEO_SYNTHESIS`), `provider` (`FIREBASE_AI_LOGIC` | `FAL_AI` | `REPLICATE`), `modelId`, `prompt`, `emotion`, `stylePreset`, `sourceReferenceUris`, `estimatedCostMicros` (Long), `actualCostMicros` (Long), `status` (`QUEUED` | `PROCESSING` | `COMPLETED` | `FAILED_RETRYABLE` | `FAILED_FINAL`), `retryCount` (Long), `maxRetries` (Long), `outputAssetId`, `errorMessage`, `createdAt`, `updatedAt`.
  - `MediaAsset` (`assets/{assetId}`): `id`, `userId`, `personaId`, `personaVersion` (Long), `jobId`, `idempotencyKey`, `mediaType` (`IMAGE` | `VIDEO_STORYBOARD` | `VIDEO_CLIP`), `storageUri` (private object URI — never binary data), `sha256Hash`, `emotion`, `stylePreset`, `modelProvider`, `modelId`, `costMicros` (Long), `sourceReferenceIds`, `versionNumber` (Long), `parentAssetId`, `reviewStatus` (`DRAFT` | `APPROVED` | `REJECTED`), `reviewerNotes`, `reviewedBy`, `reviewedAt`, `storyboardJson` (for video keyframes/motion), `deletedAt`, `createdAt`, `updatedAt`.
  - `ScheduledPost` (`schedules/{scheduleId}`): `id`, `userId`, `assetId`, `personaId`, `destinationPlatform`, `caption`, `scheduledEpochMillis` (Long), `status` (`DRAFT` | `PENDING_APPROVAL` | `APPROVED` | `PUBLISHED` | `REJECTED` | `EXPIRED`), `activeApprovalId`, `lastPublishIdempotencyKey`, `publishedAt`, `createdAt`, `updatedAt`.
  - `PublishApproval` (`approvals/{approvalId}`): `id`, `userId`, `scheduleId`, `assetId`, `destinationPlatform`, `scheduledEpochMillis` (Long), `caption`, `bindingHash` (SHA-256 of exact tuple), `requestedByRole` (`ANDROID_OWNER` | `MCP_AGENT`), `status` (`PENDING` | `APPROVED` | `REJECTED` | `EXPIRED` | `CONSUMED`), `approvedByUid`, `decisionNotes`, `expiresAt`, `consumedAt`, `createdAt`, `updatedAt`.
  - `AnalyticsEntry` (`analytics/{entryId}`): `id`, `userId`, `assetId`, `personaId`, `platformName`, `sourceProvenance` (`VERIFIED_PLATFORM` | `IMPORTED_DATASET` | `MANUAL_ENTRY`), `impressions` (Long), `engagements` (Long), `conversions` (Long), `revenueMicros` (Long), `recordedAt`, `createdAt`, `updatedAt`.
  - `QuotaBudgetConfig` (`quotas/config`): `id`, `userId`, `monthlyBudgetLimitMicros` (Long), `currentMonthSpendMicros` (Long), `dailyGenerationLimit` (Long), `todayGenerationCount` (Long), `alertThresholdPercent` (Long), `falAiEnabled` (Boolean, default `false`), `replicateEnabled` (Boolean, default `false`), `allowedProviderHosts` (list of SSRF-safe hosts: `["queue.fal.run", "api.replicate.com"]`), `updatedAt`.
  - `AuditLogEntry` (`auditLogs/{logId}`): `id`, `userId`, `actorRole` (`OWNER_ANDROID` | `CHATGPT_MCP`), `action`, `targetCollection`, `targetId`, `idempotencyKey`, `outcome` (`ALLOWED` | `DENIED` | `ERROR`), `details`, `createdAt`.

- **MCP Server Tool Specification (All 13 Tools over Streamable HTTP)**:

| MCP Tool Name | Mode | Idempotency & Security Enforcement |
| :--- | :--- | :--- |
| `get_studio_status` | Read-Only | Returns tenant quota balance, active/queued job counts, provider enablement flags, and pending approval counts. Never mutates state. |
| `list_personas` | Read-Only | Lists non-deleted personas for the authenticated token's `uid` only, with optional status filter (`DRAFT`, `ACTIVE`, `ARCHIVED`). |
| `get_persona` | Read-Only | Retrieves a single persona's metadata, current `profileVersion`, facial identity descriptors, and consent status if owned by `uid`. |
| `create_persona_draft` | Mutation | Creates a new persona in `DRAFT` status (`profileVersion = 1`) with structured facial descriptors and consent record reference; enforces `idempotencyKey`. |
| `list_assets` | Read-Only | Queries assets scoped to `uid` filtered by `personaId`, `reviewStatus`, `mediaType`, or `emotion`, returning provenance and signed storage URI pointers. |
| `generate_image_draft` | Mutation | Validates persona ownership, consent status, daily/monthly budget quota, and `idempotencyKey`; records pre-flight cost estimate and enqueues a durable `GenerationJob`. |
| `get_generation_status` | Read-Only | Returns current state, retry count, cost micros, and output `assetId` for a `jobId` owned by `uid`. |
| `review_asset` | Mutation | Transitions an asset's `reviewStatus` to `APPROVED` or `REJECTED` with `reviewerNotes` and writes an immutable `AuditLogEntry`. |
| `prepare_content_schedule` | Mutation | Creates a `DRAFT` schedule entry linking an `APPROVED` asset (`DRAFT` or `REJECTED` assets are rejected) to a destination platform, time, and caption. |
| `list_scheduled_posts` | Read-Only | Lists scheduled posts for `uid` with current approval status and binding hash verification state. |
| `get_analytics_summary` | Read-Only | Aggregates engagement KPIs explicitly separated into `VERIFIED_PLATFORM`, `IMPORTED_DATASET`, and `MANUAL_ENTRY` buckets so unverified data never pollutes verified KPIs. |
| `request_publish_approval` | Mutation | Creates a `PENDING` `PublishApproval` bound to exact `(assetId, destinationPlatform, scheduledEpochMillis, caption)`. Does **not** approve or publish. |
| `publish_approved_post` | Mutation | Requires an owner-approved, non-expired, unconsumed `approvalId` whose `bindingHash` matches the exact `(assetId, destinationPlatform, scheduledEpochMillis, caption)` tuple and enforces `idempotencyKey` to prevent duplicate publishes. |

- **Threat Model & Security Mitigations**:
  1. **Cross-Tenant Data Access (IDOR)**: Mitigated by extracting `uid` exclusively from verified Firebase Auth JWTs (`request.auth.uid` in Firestore rules and `verifyIdToken` in Node.js middleware); any client-supplied `userId` mismatch is rejected and audited.
  2. **APK Secret Extraction**: Mitigated by omitting all third-party API keys (`fal.ai`, `Replicate`) from `.env.example`, `BuildConfig`, and Android source code. Native Android AI calls use App Check-attested `Firebase.ai`; external providers live exclusively behind Cloud Run environment secrets.
  3. **Unauthorized Autonomous Publishing by AI Agent**: Mitigated by separating `request_publish_approval` (available to MCP agent) from owner approval (requires `OWNER_ANDROID` role confirmation) and verifying the SHA-256 tuple binding hash inside `publish_approved_post`.
  4. **Server-Side Request Forgery (SSRF) via Provider URLs**: Mitigated by strict HTTPS hostname allowlisting (`queue.fal.run`, `api.replicate.com`), blocking private IPv4/IPv6 ranges (`127.0.0.0/8`, `10.0.0.0/8`, `169.254.169.254`, `172.16.0.0/12`, `192.168.0.0/16`, `localhost`), and rejecting custom arbitrary URLs unless validated against the allowlist.
  5. **Runaway Generation Spend & Replay Attacks**: Mitigated by mandatory `idempotencyKey` deduplication on `generate_image_draft` and `publish_approved_post`, pre-flight `estimatedCostMicros` reservation against `monthlyBudgetLimitMicros`, and hard circuit-breakers when budget reaches 100%.
  6. **Unconsented Likeness & Orphaned Media**: Mitigated by requiring an active, non-revoked `ConsentRecord` before `generate_image_draft` can execute for a persona, and cascading tombstone + storage purge markers upon persona or asset deletion.

- **Phased Execution & Acceptance Verification Plan (Phases 0–7)**:
  - **Phase 0 & 1 (Inspection, Architecture, Contracts)**: Finalize OpenAPI 3.1 specification, `firebase-blueprint.json`, hardened `firestore.rules`, and MCP tool schemas.
  - **Phase 2 (Backend, Auth, Storage & Security Rules Tests)**: Provision Firebase (`firestore` + `ai_logic`), build Node.js/TypeScript backend + durable job processor + SSRF/quota guards, and run local emulator rules & backend tests (`firestore.test.js`).
  - **Phase 3 (Native Android Workstation)**: Build the 7 studio modules in Jetpack Compose (Persona Vault with Consent Ledger, Expression Studio with Side-by-Side Compare & Review, Private Asset Library with Provenance, Storyboard-First Video Studio, Scheduler with Approval Vault, Segmented Analytics, and Billing/Quota & MCP Console).
  - **Phase 4 & 5 (MCP Server & Provider Adapters)**: Implement the Streamable HTTP MCP server (`13` tools) and protected provider adapters (real `Firebase.ai` on Android + spending-capped adapter harness for `fal.ai`/`Replicate` defaulted to disabled until configured).
  - **Phase 6 & 7 (10 Acceptance Tests & Production-Readiness Delivery)**: Execute automated tests verifying all 10 acceptance criteria (cross-user isolation, zero client secrets, generation provenance, MCP persona retrieval, safe retry handling, unapproved publish rejection, idempotent duplicate suppression, rejected/expired approval rejection, durable background jobs across app closure, and retention-compliant deletion) and deliver setup/deployment documentation.
