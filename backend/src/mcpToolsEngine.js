const crypto = require("node:crypto");

/**
 * Allowed external AI provider hosts (SSRF Defense-in-Depth).
 * All external providers default to DISABLED until explicitly configured and tested.
 */
const ALLOWED_PROVIDER_HOSTS = new Set([
  "queue.fal.run",
  "fal.run",
  "api.replicate.com",
  "firebasevertexai.googleapis.com",
]);

/**
 * Deterministic SHA-256 binding hash for Two-Step Publish Approval.
 * Binds exact (assetId, destinationPlatform, scheduledEpochMillis, caption).
 */
function computePublishBindingHash({
  assetId,
  destinationPlatform,
  scheduledEpochMillis,
  caption,
}) {
  const canonical = `${String(assetId).trim()}|${String(destinationPlatform).trim()}|${Number(
    scheduledEpochMillis
  )}|${String(caption).trim()}`;
  return crypto.createHash("sha256").update(canonical, "utf8").digest("hex");
}

/**
 * SSRF Validator for external AI provider or webhook URLs.
 * Rejects non-HTTPS schemes, private IP ranges, loopback, link-local metadata, and unapproved hosts.
 */
function validateProviderUrl(rawUrl, customAllowedHosts = ALLOWED_PROVIDER_HOSTS) {
  let parsed;
  try {
    parsed = new URL(rawUrl);
  } catch {
    throw new Error("SSRF_BLOCKED: Invalid URL syntax");
  }
  if (parsed.protocol !== "https:") {
    throw new Error("SSRF_BLOCKED: Only HTTPS provider endpoints are permitted");
  }
  const host = parsed.hostname.toLowerCase();
  if (
    host === "localhost" ||
    host === "127.0.0.1" ||
    host === "::1" ||
    host.startsWith("10.") ||
    host.startsWith("192.168.") ||
    host.startsWith("169.254.") ||
    /^172\.(1[6-9]|2[0-9]|3[0-1])\./.test(host) ||
    host.endsWith(".internal")
  ) {
    throw new Error(`SSRF_BLOCKED: Private or metadata host '${host}' is forbidden`);
  }
  if (!customAllowedHosts.has(host)) {
    throw new Error(`SSRF_BLOCKED: Host '${host}' is not in the verified provider allowlist`);
  }
  return parsed.toString();
}

/**
 * Validates that a storage reference is a metadata-only URI and never raw binary/base64 media.
 */
function assertMetadataOnlyUri(uri) {
  if (typeof uri !== "string" || uri.length < 7 || uri.length > 512) {
    throw new Error("STORAGE_POLICY_VIOLATION: Storage URI must be between 7 and 512 chars");
  }
  if (
    uri.startsWith("data:") ||
    uri.includes(";base64,") ||
    !(
      uri.startsWith("gs://") ||
      uri.startsWith("vault://") ||
      uri.startsWith("file://") ||
      uri.startsWith("content://")
    )
  ) {
    throw new Error(
      "STORAGE_POLICY_VIOLATION: Binary media or base64 data cannot be stored in Firestore metadata"
    );
  }
}

/**
 * Authoritative 13-Tool MCP Definitions (JSON Schema Draft-07 + Human-Readable Descriptions).
 */
const MCP_TOOL_DEFINITIONS = [
  {
    name: "get_studio_status",
    description:
      "Read-only tool. Returns tenant quota balance, monthly budget consumption, queued job count, pending approval count, and AI provider enablement status.",
    readOnlyHint: true,
    inputSchema: {
      type: "object",
      properties: {},
      additionalProperties: false,
    },
  },
  {
    name: "list_personas",
    description:
      "Read-only tool. Lists consented character personas owned by the authenticated account, optionally filtered by lifecycle status.",
    readOnlyHint: true,
    inputSchema: {
      type: "object",
      properties: {
        status: { type: "string", enum: ["DRAFT", "ACTIVE", "ARCHIVED"] },
      },
      additionalProperties: false,
    },
  },
  {
    name: "get_persona",
    description:
      "Read-only tool. Retrieves full identity descriptors, profile version, reference storage URI, and consent verification status for a single persona owned by the caller.",
    readOnlyHint: true,
    inputSchema: {
      type: "object",
      required: ["personaId"],
      properties: {
        personaId: { type: "string" },
      },
      additionalProperties: false,
    },
  },
  {
    name: "create_persona_draft",
    description:
      "Mutation tool. Creates a new persona in DRAFT status with consented reference storage URI and structured facial trait locks. Enforces idempotencyKey.",
    readOnlyHint: false,
    inputSchema: {
      type: "object",
      required: [
        "idempotencyKey",
        "name",
        "handle",
        "facialIdentityDescriptor",
        "referenceStorageUri",
        "referenceSha256",
        "consentSubjectName",
        "consentVerified",
      ],
      properties: {
        idempotencyKey: { type: "string" },
        name: { type: "string" },
        handle: { type: "string" },
        facialIdentityDescriptor: { type: "string" },
        referenceStorageUri: { type: "string" },
        referenceSha256: { type: "string" },
        consentSubjectName: { type: "string" },
        consentVerified: { type: "boolean" },
        retentionPolicyDays: { type: "integer", default: 365 },
      },
      additionalProperties: false,
    },
  },
  {
    name: "list_assets",
    description:
      "Read-only tool. Lists generated media assets owned by the caller with full model/cost/reference provenance and review status.",
    readOnlyHint: true,
    inputSchema: {
      type: "object",
      properties: {
        personaId: { type: "string" },
        reviewStatus: { type: "string", enum: ["DRAFT", "APPROVED", "REJECTED"] },
        mediaType: { type: "string", enum: ["IMAGE", "VIDEO_STORYBOARD", "VIDEO_CLIP"] },
      },
      additionalProperties: false,
    },
  },
  {
    name: "generate_image_draft",
    description:
      "Mutation tool. Enqueues a durable background image generation job after verifying persona consent, user quota, budget cap, provider readiness, and idempotencyKey.",
    readOnlyHint: false,
    inputSchema: {
      type: "object",
      required: ["idempotencyKey", "personaId", "emotion", "stylePreset", "prompt"],
      properties: {
        idempotencyKey: { type: "string" },
        personaId: { type: "string" },
        emotion: { type: "string" },
        stylePreset: { type: "string" },
        prompt: { type: "string" },
        provider: {
          type: "string",
          enum: ["FIREBASE_AI_LOGIC", "FAL_AI", "REPLICATE"],
          default: "FIREBASE_AI_LOGIC",
        },
        modelId: { type: "string", default: "gemini-nano-banana-2.1" },
      },
      additionalProperties: false,
    },
  },
  {
    name: "get_generation_status",
    description:
      "Read-only tool. Retrieves durable background job status, retry count, estimated/actual cost micros, and outputAssetId.",
    readOnlyHint: true,
    inputSchema: {
      type: "object",
      required: ["jobId"],
      properties: {
        jobId: { type: "string" },
      },
      additionalProperties: false,
    },
  },
  {
    name: "review_asset",
    description:
      "Mutation tool. Approves or rejects a generated media asset draft with reviewer notes and records an immutable audit log entry.",
    readOnlyHint: false,
    inputSchema: {
      type: "object",
      required: ["assetId", "reviewStatus", "reviewerNotes"],
      properties: {
        assetId: { type: "string" },
        reviewStatus: { type: "string", enum: ["APPROVED", "REJECTED"] },
        reviewerNotes: { type: "string" },
      },
      additionalProperties: false,
    },
  },
  {
    name: "prepare_content_schedule",
    description:
      "Mutation tool. Prepares a draft content schedule slot for an APPROVED asset. Rejects DRAFT or REJECTED assets.",
    readOnlyHint: false,
    inputSchema: {
      type: "object",
      required: ["assetId", "destinationPlatform", "scheduledEpochMillis", "caption"],
      properties: {
        assetId: { type: "string" },
        destinationPlatform: { type: "string" },
        scheduledEpochMillis: { type: "integer" },
        caption: { type: "string" },
      },
      additionalProperties: false,
    },
  },
  {
    name: "list_scheduled_posts",
    description:
      "Read-only tool. Lists scheduled posts owned by the caller along with their approval status and binding hash.",
    readOnlyHint: true,
    inputSchema: {
      type: "object",
      properties: {
        status: {
          type: "string",
          enum: ["DRAFT", "PENDING_APPROVAL", "APPROVED", "PUBLISHED", "REJECTED", "EXPIRED"],
        },
      },
      additionalProperties: false,
    },
  },
  {
    name: "get_analytics_summary",
    description:
      "Read-only tool. Returns audience engagement metrics strictly segmented into VERIFIED_PLATFORM, IMPORTED_DATASET, and MANUAL_ENTRY provenance buckets.",
    readOnlyHint: true,
    inputSchema: {
      type: "object",
      properties: {
        personaId: { type: "string" },
      },
      additionalProperties: false,
    },
  },
  {
    name: "request_publish_approval",
    description:
      "Mutation tool. Requests owner approval to publish a scheduled post. Computes a deterministic SHA-256 bindingHash over (assetId, destinationPlatform, scheduledEpochMillis, caption). Does NOT approve or publish.",
    readOnlyHint: false,
    inputSchema: {
      type: "object",
      required: ["scheduleId", "assetId", "destinationPlatform", "scheduledEpochMillis", "caption"],
      properties: {
        scheduleId: { type: "string" },
        assetId: { type: "string" },
        destinationPlatform: { type: "string" },
        scheduledEpochMillis: { type: "integer" },
        caption: { type: "string" },
      },
      additionalProperties: false,
    },
  },
  {
    name: "publish_approved_post",
    description:
      "Mutation tool. Executes publication ONLY if the referenced approvalId is in APPROVED status, not expired, not previously consumed, and its bindingHash matches the exact (assetId, destinationPlatform, scheduledEpochMillis, caption) parameters.",
    readOnlyHint: false,
    inputSchema: {
      type: "object",
      required: [
        "idempotencyKey",
        "approvalId",
        "scheduleId",
        "assetId",
        "destinationPlatform",
        "scheduledEpochMillis",
        "caption",
      ],
      properties: {
        idempotencyKey: { type: "string" },
        approvalId: { type: "string" },
        scheduleId: { type: "string" },
        assetId: { type: "string" },
        destinationPlatform: { type: "string" },
        scheduledEpochMillis: { type: "integer" },
        caption: { type: "string" },
      },
      additionalProperties: false,
    },
  },
];

/**
 * Tenant-Isolated Domain & MCP Execution Engine.
 * Operates on either a Firestore-compatible repository or an in-memory transactional store for fast acceptance testing.
 */
class PersonaForgeDomainEngine {
  constructor() {
    this.personas = new Map(); // key: `${uid}:${id}`
    this.jobs = new Map();
    this.assets = new Map();
    this.schedules = new Map();
    this.approvals = new Map();
    this.analytics = new Map();
    this.quotas = new Map(); // key: uid
    this.auditLogs = [];
    this.idempotencyLedger = new Map(); // key: `${uid}:${op}:${idempotencyKey}` -> result
    this.retentionTombstones = new Map(); // key: `${uid}:${type}:${id}` -> purge metadata
  }

  _requireAuth(authContext) {
    if (!authContext || typeof authContext.uid !== "string" || !authContext.uid.trim()) {
      throw new Error("UNAUTHORIZED: Valid authenticated token with uid is required");
    }
    if (
      authContext.clientSuppliedUserId &&
      authContext.clientSuppliedUserId !== authContext.uid
    ) {
      this._audit(
        authContext.uid,
        authContext.role || "CHATGPT_MCP",
        "TENANT_SPOOF_ATTEMPT",
        "security",
        authContext.clientSuppliedUserId,
        "none",
        "DENIED",
        "Client-supplied userId mismatched token uid"
      );
      throw new Error("FORBIDDEN: Client-supplied userId does not match authenticated token uid");
    }
    return {
      uid: authContext.uid,
      role: authContext.role || "CHATGPT_MCP",
    };
  }

  _getOrCreateQuota(uid) {
    if (!this.quotas.has(uid)) {
      this.quotas.set(uid, {
        userId: uid,
        monthlyBudgetLimitMicros: 25_000_000, // $25.00 default cap
        currentMonthSpendMicros: 0,
        dailyGenerationLimit: 50,
        todayGenerationCount: 0,
        alertThresholdPercent: 80,
        falAiEnabled: false, // Default disabled per security mandate
        replicateEnabled: false, // Default disabled per security mandate
      });
    }
    return this.quotas.get(uid);
  }

  _audit(uid, actorRole, action, targetCollection, targetId, idempotencyKey, outcome, details) {
    const entry = {
      id: `audit_${this.auditLogs.length + 1}`,
      userId: uid,
      actorRole,
      action,
      targetCollection,
      targetId,
      idempotencyKey: idempotencyKey || "read_only",
      outcome,
      details,
      createdAt: new Date().toISOString(),
    };
    this.auditLogs.push(entry);
    return entry;
  }

  estimateCostMicros(provider, modelId, jobType = "IMAGE_DRAFT") {
    if (jobType === "VIDEO_SYNTHESIS") return 350_000; // $0.35
    if (provider === "FAL_AI") return 65_000; // $0.065
    if (provider === "REPLICATE") return 55_000; // $0.055
    if (modelId === "gemini-3-pro-image") return 80_000; // $0.08
    return 40_000; // $0.04 default for gemini-nano-banana-2.1
  }

  async executeMcpTool(authContext, toolName, args = {}) {
    const { uid, role } = this._requireAuth(authContext);
    const quota = this._getOrCreateQuota(uid);

    switch (toolName) {
      case "get_studio_status": {
        const userJobs = [...this.jobs.values()].filter((j) => j.userId === uid);
        const userApprovals = [...this.approvals.values()].filter(
          (a) => a.userId === uid && a.status === "PENDING"
        );
        return {
          userId: uid,
          quota: {
            monthlyBudgetLimitMicros: quota.monthlyBudgetLimitMicros,
            currentMonthSpendMicros: quota.currentMonthSpendMicros,
            remainingBudgetMicros: Math.max(
              0,
              quota.monthlyBudgetLimitMicros - quota.currentMonthSpendMicros
            ),
            dailyGenerationLimit: quota.dailyGenerationLimit,
            todayGenerationCount: quota.todayGenerationCount,
            budgetAlertTriggered:
              quota.currentMonthSpendMicros * 100 >=
              quota.monthlyBudgetLimitMicros * quota.alertThresholdPercent,
          },
          jobs: {
            queuedOrProcessing: userJobs.filter(
              (j) => j.status === "QUEUED" || j.status === "PROCESSING"
            ).length,
            completed: userJobs.filter((j) => j.status === "COMPLETED").length,
            failed: userJobs.filter((j) => j.status.startsWith("FAILED")).length,
          },
          pendingPublishApprovals: userApprovals.length,
          providers: {
            FIREBASE_AI_LOGIC: { enabled: true, mode: "LIVE_APP_CHECK" },
            FAL_AI: {
              enabled: quota.falAiEnabled,
              mode: quota.falAiEnabled ? "CONFIGURED_BACKEND_PROXY" : "DISABLED_DEFAULT",
            },
            REPLICATE: {
              enabled: quota.replicateEnabled,
              mode: quota.replicateEnabled ? "CONFIGURED_BACKEND_PROXY" : "DISABLED_DEFAULT",
            },
          },
        };
      }

      case "list_personas": {
        const list = [...this.personas.values()].filter(
          (p) =>
            p.userId === uid &&
            !p.deletedAt &&
            (!args.status || p.status === args.status)
        );
        return { personas: list };
      }

      case "get_persona": {
        const record = this.personas.get(`${uid}:${args.personaId}`);
        if (!record || record.deletedAt) {
          // Check if it belongs to another user to log cross-tenant attempt
          for (const [k, v] of this.personas.entries()) {
            if (k.endsWith(`:${args.personaId}`) && v.userId !== uid) {
              this._audit(
                uid,
                role,
                "get_persona",
                "personas",
                args.personaId,
                "read_only",
                "DENIED",
                "Cross-tenant access denied"
              );
              throw new Error("FORBIDDEN: Persona belongs to another tenant");
            }
          }
          throw new Error("NOT_FOUND: Persona not found");
        }
        return { persona: record };
      }

      case "create_persona_draft": {
        if (!args.idempotencyKey || String(args.idempotencyKey).length < 4) {
          throw new Error("INVALID_ARGUMENT: idempotencyKey is required");
        }
        const idemKey = `${uid}:create_persona_draft:${args.idempotencyKey}`;
        if (this.idempotencyLedger.has(idemKey)) {
          return this.idempotencyLedger.get(idemKey);
        }
        assertMetadataOnlyUri(args.referenceStorageUri);
        if (!args.consentVerified) {
          throw new Error("CONSENT_REQUIRED: Explicit subject consent must be verified");
        }
        const id = `persona_${crypto.randomBytes(6).toString("hex")}`;
        const now = new Date().toISOString();
        const persona = {
          id,
          userId: uid,
          name: String(args.name).trim(),
          handle: String(args.handle).trim(),
          status: "DRAFT",
          profileVersion: 1,
          facialIdentityDescriptor: String(args.facialIdentityDescriptor).trim(),
          referenceStorageUri: args.referenceStorageUri,
          referenceSha256: args.referenceSha256,
          consentSubjectName: String(args.consentSubjectName).trim(),
          consentVerified: Boolean(args.consentVerified),
          retentionPolicyDays: Number(args.retentionPolicyDays || 365),
          createdAt: now,
          updatedAt: now,
        };
        this.personas.set(`${uid}:${id}`, persona);
        const response = { persona, deduplicated: false };
        this.idempotencyLedger.set(idemKey, { persona, deduplicated: true });
        this._audit(
          uid,
          role,
          "create_persona_draft",
          "personas",
          id,
          args.idempotencyKey,
          "ALLOWED",
          `Created persona draft ${persona.name} (v1)`
        );
        return response;
      }

      case "list_assets": {
        const list = [...this.assets.values()].filter(
          (a) =>
            a.userId === uid &&
            !a.deletedAt &&
            (!args.personaId || a.personaId === args.personaId) &&
            (!args.reviewStatus || a.reviewStatus === args.reviewStatus) &&
            (!args.mediaType || a.mediaType === args.mediaType)
        );
        return { assets: list };
      }

      case "generate_image_draft": {
        if (!args.idempotencyKey || String(args.idempotencyKey).length < 4) {
          throw new Error("INVALID_ARGUMENT: idempotencyKey is required");
        }
        const idemKey = `${uid}:generate_image_draft:${args.idempotencyKey}`;
        if (this.idempotencyLedger.has(idemKey)) {
          const cached = this.idempotencyLedger.get(idemKey);
          return { ...cached, deduplicated: true };
        }

        const persona = this.personas.get(`${uid}:${args.personaId}`);
        if (!persona || persona.deletedAt) {
          throw new Error("FORBIDDEN_OR_NOT_FOUND: Persona not found in caller tenant");
        }
        if (!persona.consentVerified) {
          throw new Error("CONSENT_REQUIRED: Persona lacks verified reference consent");
        }

        const provider = args.provider || "FIREBASE_AI_LOGIC";
        const modelId = args.modelId || "gemini-nano-banana-2.1";
        if (provider === "FAL_AI" && !quota.falAiEnabled) {
          throw new Error(
            "PROVIDER_DISABLED: fal.ai integration is disabled by default until configured and tested"
          );
        }
        if (provider === "REPLICATE" && !quota.replicateEnabled) {
          throw new Error(
            "PROVIDER_DISABLED: Replicate integration is disabled by default until configured and tested"
          );
        }

        const estimatedCostMicros = this.estimateCostMicros(provider, modelId, "IMAGE_DRAFT");
        if (quota.todayGenerationCount >= quota.dailyGenerationLimit) {
          throw new Error("QUOTA_EXCEEDED: Daily generation count limit reached");
        }
        if (
          quota.currentMonthSpendMicros + estimatedCostMicros >
          quota.monthlyBudgetLimitMicros
        ) {
          throw new Error("BUDGET_EXCEEDED: Generation exceeds monthly budget cap");
        }

        // Reserve quota atomically
        quota.todayGenerationCount += 1;
        quota.currentMonthSpendMicros += estimatedCostMicros;

        const jobId = `job_${crypto.randomBytes(6).toString("hex")}`;
        const assetId = `asset_${crypto.randomBytes(6).toString("hex")}`;
        const now = new Date().toISOString();

        const job = {
          id: jobId,
          userId: uid,
          idempotencyKey: args.idempotencyKey,
          personaId: persona.id,
          personaVersion: persona.profileVersion,
          jobType: "IMAGE_DRAFT",
          provider,
          modelId,
          prompt: `${args.prompt} | Locked Traits: ${persona.facialIdentityDescriptor}`,
          emotion: args.emotion,
          stylePreset: args.stylePreset,
          sourceReferenceUri: persona.referenceStorageUri,
          estimatedCostMicros,
          actualCostMicros: estimatedCostMicros,
          status: "COMPLETED",
          retryCount: 0,
          maxRetries: 3,
          outputAssetId: assetId,
          errorMessage: "",
          createdAt: now,
          updatedAt: now,
        };
        this.jobs.set(`${uid}:${jobId}`, job);

        const asset = {
          id: assetId,
          userId: uid,
          personaId: persona.id,
          personaName: persona.name,
          personaVersion: persona.profileVersion,
          jobId,
          idempotencyKey: args.idempotencyKey,
          mediaType: "IMAGE",
          storageUri: `vault://users/${uid}/assets/${assetId}.jpg`,
          sha256Hash: crypto
            .createHash("sha256")
            .update(`${jobId}:${args.prompt}`)
            .digest("hex"),
          emotion: args.emotion,
          stylePreset: args.stylePreset,
          modelProvider: provider,
          modelId,
          costMicros: estimatedCostMicros,
          sourceReferenceUri: persona.referenceStorageUri,
          versionNumber: 1,
          reviewStatus: "DRAFT",
          reviewerNotes: "",
          storyboardSummary: "",
          createdAt: now,
          updatedAt: now,
        };
        this.assets.set(`${uid}:${assetId}`, asset);

        const result = { job, asset, deduplicated: false };
        this.idempotencyLedger.set(idemKey, result);
        this._audit(
          uid,
          role,
          "generate_image_draft",
          "jobs",
          jobId,
          args.idempotencyKey,
          "ALLOWED",
          `Generated draft asset ${assetId} via ${provider}/${modelId}`
        );
        return result;
      }

      case "get_generation_status": {
        const job = this.jobs.get(`${uid}:${args.jobId}`);
        if (!job) {
          for (const [k, v] of this.jobs.entries()) {
            if (k.endsWith(`:${args.jobId}`) && v.userId !== uid) {
              throw new Error("FORBIDDEN: Job belongs to another tenant");
            }
          }
          throw new Error("NOT_FOUND: Generation job not found");
        }
        return { job };
      }

      case "review_asset": {
        const asset = this.assets.get(`${uid}:${args.assetId}`);
        if (!asset || asset.deletedAt) {
          throw new Error("FORBIDDEN_OR_NOT_FOUND: Asset not found in caller tenant");
        }
        if (!["APPROVED", "REJECTED"].includes(args.reviewStatus)) {
          throw new Error("INVALID_ARGUMENT: reviewStatus must be APPROVED or REJECTED");
        }
        asset.reviewStatus = args.reviewStatus;
        asset.reviewerNotes = String(args.reviewerNotes || "").trim();
        asset.updatedAt = new Date().toISOString();
        this._audit(
          uid,
          role,
          "review_asset",
          "assets",
          asset.id,
          `review_${asset.id}_${args.reviewStatus}`,
          "ALLOWED",
          `Asset marked ${args.reviewStatus}: ${asset.reviewerNotes}`
        );
        return { asset };
      }

      case "prepare_content_schedule": {
        const asset = this.assets.get(`${uid}:${args.assetId}`);
        if (!asset || asset.deletedAt) {
          throw new Error("FORBIDDEN_OR_NOT_FOUND: Asset not found in caller tenant");
        }
        if (asset.reviewStatus !== "APPROVED") {
          throw new Error(
            `POLICY_VIOLATION: Only APPROVED assets can be scheduled (current status: ${asset.reviewStatus})`
          );
        }
        const scheduleId = `sched_${crypto.randomBytes(6).toString("hex")}`;
        const now = new Date().toISOString();
        const schedule = {
          id: scheduleId,
          userId: uid,
          assetId: asset.id,
          personaId: asset.personaId,
          personaName: asset.personaName,
          destinationPlatform: String(args.destinationPlatform).trim(),
          caption: String(args.caption).trim(),
          scheduledEpochMillis: Number(args.scheduledEpochMillis),
          status: "DRAFT",
          activeApprovalId: "",
          bindingHash: "",
          lastPublishIdempotencyKey: "",
          createdAt: now,
          updatedAt: now,
        };
        this.schedules.set(`${uid}:${scheduleId}`, schedule);
        this._audit(
          uid,
          role,
          "prepare_content_schedule",
          "schedules",
          scheduleId,
          `sched_${scheduleId}`,
          "ALLOWED",
          `Prepared schedule draft for ${schedule.destinationPlatform}`
        );
        return { schedule };
      }

      case "list_scheduled_posts": {
        const list = [...this.schedules.values()].filter(
          (s) => s.userId === uid && (!args.status || s.status === args.status)
        );
        return { schedules: list };
      }

      case "get_analytics_summary": {
        const entries = [...this.analytics.values()].filter(
          (e) => e.userId === uid && (!args.personaId || e.personaId === args.personaId)
        );
        const summarize = (prov) => {
          const subset = entries.filter((e) => e.sourceProvenance === prov);
          return {
            count: subset.length,
            impressions: subset.reduce((s, x) => s + x.impressions, 0),
            engagements: subset.reduce((s, x) => s + x.engagements, 0),
            conversions: subset.reduce((s, x) => s + x.conversions, 0),
            revenueMicros: subset.reduce((s, x) => s + x.revenueMicros, 0),
          };
        };
        return {
          verifiedPlatformMetrics: summarize("VERIFIED_PLATFORM"),
          importedDatasetMetrics: summarize("IMPORTED_DATASET"),
          manualEntryMetrics: summarize("MANUAL_ENTRY"),
        };
      }

      case "request_publish_approval": {
        const schedule = this.schedules.get(`${uid}:${args.scheduleId}`);
        if (!schedule) {
          throw new Error("FORBIDDEN_OR_NOT_FOUND: Schedule not found in caller tenant");
        }
        const asset = this.assets.get(`${uid}:${args.assetId}`);
        if (!asset || asset.reviewStatus !== "APPROVED") {
          throw new Error("POLICY_VIOLATION: Referenced asset is not APPROVED");
        }
        const bindingHash = computePublishBindingHash({
          assetId: args.assetId,
          destinationPlatform: args.destinationPlatform,
          scheduledEpochMillis: args.scheduledEpochMillis,
          caption: args.caption,
        });
        const approvalId = `appr_${crypto.randomBytes(6).toString("hex")}`;
        const now = new Date().toISOString();
        const approval = {
          id: approvalId,
          userId: uid,
          scheduleId: schedule.id,
          assetId: args.assetId,
          destinationPlatform: String(args.destinationPlatform).trim(),
          scheduledEpochMillis: Number(args.scheduledEpochMillis),
          caption: String(args.caption).trim(),
          bindingHash,
          requestedByRole: role,
          status: "PENDING",
          decisionNotes: "Awaiting explicit account owner approval",
          expiresAtEpochMillis: Date.now() + 24 * 60 * 60 * 1000,
          consumedByIdempotencyKey: "",
          createdAt: now,
          updatedAt: now,
        };
        this.approvals.set(`${uid}:${approvalId}`, approval);
        schedule.status = "PENDING_APPROVAL";
        schedule.activeApprovalId = approvalId;
        schedule.bindingHash = bindingHash;
        schedule.updatedAt = now;

        this._audit(
          uid,
          role,
          "request_publish_approval",
          "approvals",
          approvalId,
          `req_${approvalId}`,
          "ALLOWED",
          `Created PENDING approval bound to hash ${bindingHash.slice(0, 12)}...`
        );
        return { approval };
      }

      case "publish_approved_post": {
        if (!args.idempotencyKey || String(args.idempotencyKey).length < 4) {
          throw new Error("INVALID_ARGUMENT: idempotencyKey is required");
        }
        const idemKey = `${uid}:publish_approved_post:${args.idempotencyKey}`;
        if (this.idempotencyLedger.has(idemKey)) {
          return { ...this.idempotencyLedger.get(idemKey), deduplicated: true };
        }

        const approval = this.approvals.get(`${uid}:${args.approvalId}`);
        if (!approval) {
          throw new Error("APPROVAL_REQUIRED: No matching PublishApproval found");
        }
        if (approval.status === "CONSUMED") {
          throw new Error("APPROVAL_ALREADY_CONSUMED: Approval token was already used");
        }
        if (approval.status === "REJECTED") {
          throw new Error("APPROVAL_REJECTED: Rejected approvals cannot be reused");
        }
        if (
          approval.status === "EXPIRED" ||
          Date.now() > Number(approval.expiresAtEpochMillis)
        ) {
          approval.status = "EXPIRED";
          throw new Error("APPROVAL_EXPIRED: Approval token has expired");
        }
        if (approval.status !== "APPROVED") {
          throw new Error(
            `APPROVAL_NOT_GRANTED: Approval status is '${approval.status}', owner must explicitly approve first`
          );
        }

        const expectedHash = computePublishBindingHash({
          assetId: args.assetId,
          destinationPlatform: args.destinationPlatform,
          scheduledEpochMillis: args.scheduledEpochMillis,
          caption: args.caption,
        });
        if (expectedHash !== approval.bindingHash) {
          this._audit(
            uid,
            role,
            "publish_approved_post",
            "approvals",
            approval.id,
            args.idempotencyKey,
            "DENIED",
            "Parameter tampering detected: bindingHash mismatch"
          );
          throw new Error(
            "APPROVAL_BINDING_MISMATCH: Publish parameters do not match the exact approved asset, destination, time, or caption"
          );
        }

        const schedule = this.schedules.get(`${uid}:${args.scheduleId}`);
        if (!schedule) {
          throw new Error("NOT_FOUND: Scheduled post not found");
        }

        const now = new Date().toISOString();
        approval.status = "CONSUMED";
        approval.consumedByIdempotencyKey = args.idempotencyKey;
        approval.updatedAt = now;

        schedule.status = "PUBLISHED";
        schedule.lastPublishIdempotencyKey = args.idempotencyKey;
        schedule.updatedAt = now;

        const result = {
          published: true,
          schedule,
          approval,
          deduplicated: false,
        };
        this.idempotencyLedger.set(idemKey, result);
        this._audit(
          uid,
          role,
          "publish_approved_post",
          "schedules",
          schedule.id,
          args.idempotencyKey,
          "ALLOWED",
          `Published post ${schedule.id} with verified approval ${approval.id}`
        );
        return result;
      }

      default:
        throw new Error(`UNKNOWN_MCP_TOOL: ${toolName}`);
    }
  }

  /**
   * Owner-only decision method (invoked from authenticated Android Owner UI, never exposed as an MCP agent tool).
   */
  decidePublishApproval(authContext, { approvalId, decision, decisionNotes }) {
    const { uid, role } = this._requireAuth(authContext);
    if (role !== "OWNER_ANDROID") {
      throw new Error("FORBIDDEN: Only the account owner can approve or reject publish requests");
    }
    const approval = this.approvals.get(`${uid}:${approvalId}`);
    if (!approval) {
      throw new Error("NOT_FOUND: Approval not found");
    }
    if (["REJECTED", "EXPIRED", "CONSUMED"].includes(approval.status)) {
      throw new Error(`TERMINAL_STATE_LOCKED: Cannot modify approval in ${approval.status} state`);
    }
    if (!["APPROVED", "REJECTED"].includes(decision)) {
      throw new Error("INVALID_ARGUMENT: decision must be APPROVED or REJECTED");
    }
    approval.status = decision;
    approval.decisionNotes = String(decisionNotes || "").trim();
    approval.updatedAt = new Date().toISOString();

    const schedule = this.schedules.get(`${uid}:${approval.scheduleId}`);
    if (schedule) {
      schedule.status = decision;
      schedule.updatedAt = approval.updatedAt;
    }
    this._audit(
      uid,
      role,
      `owner_${decision.toLowerCase()}_publish`,
      "approvals",
      approvalId,
      `owner_dec_${approvalId}`,
      "ALLOWED",
      `Owner marked approval ${decision}`
    );
    return approval;
  }

  /**
   * Retry handler for transient generation job failures.
   */
  retryGenerationJob(authContext, { jobId }) {
    const { uid } = this._requireAuth(authContext);
    const job = this.jobs.get(`${uid}:${jobId}`);
    if (!job) throw new Error("NOT_FOUND: Job not found");
    if (job.status === "COMPLETED") {
      return { job, retried: false };
    }
    if (job.retryCount >= job.maxRetries) {
      job.status = "FAILED_FINAL";
      throw new Error("MAX_RETRIES_EXCEEDED: Job has reached maximum retry limit");
    }
    job.retryCount += 1;
    job.status = "COMPLETED";
    job.errorMessage = "";
    job.updatedAt = new Date().toISOString();
    return { job, retried: true };
  }

  /**
   * Retention & Deletion Policy Handler: records tombstone, revokes active schedules, and schedules storage object purge.
   */
  deletePersonaWithRetentionPolicy(authContext, { personaId }) {
    const { uid, role } = this._requireAuth(authContext);
    const persona = this.personas.get(`${uid}:${personaId}`);
    if (!persona || persona.deletedAt) {
      throw new Error("NOT_FOUND: Persona not found");
    }
    const deletedAt = new Date().toISOString();
    persona.deletedAt = deletedAt;
    persona.status = "ARCHIVED";

    const purgeAtEpochMillis =
      Date.now() + persona.retentionPolicyDays * 24 * 60 * 60 * 1000;
    const tombstone = {
      entityType: "PERSONA",
      entityId: personaId,
      userId: uid,
      deletedAt,
      purgeStorageUri: persona.referenceStorageUri,
      backupExcluded: true,
      scheduledHardPurgeEpochMillis: purgeAtEpochMillis,
    };
    this.retentionTombstones.set(`${uid}:PERSONA:${personaId}`, tombstone);
    this._audit(
      uid,
      role,
      "delete_persona",
      "personas",
      personaId,
      `del_${personaId}`,
      "ALLOWED",
      `Soft-deleted persona and scheduled binary purge per ${persona.retentionPolicyDays}d retention policy`
    );
    return { deleted: true, tombstone };
  }
}

module.exports = {
  MCP_TOOL_DEFINITIONS,
  ALLOWED_PROVIDER_HOSTS,
  computePublishBindingHash,
  validateProviderUrl,
  assertMetadataOnlyUri,
  PersonaForgeDomainEngine,
};
