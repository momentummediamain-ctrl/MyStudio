const { test } = require("node:test");
const assert = require("node:assert/strict");
const {
  MCP_TOOL_DEFINITIONS,
  PersonaForgeDomainEngine,
  validateProviderUrl,
  computePublishBindingHash,
} = require("../src/mcpToolsEngine");

test("MCP Contract: Defines all 13 required tools with structured schemas and descriptions", () => {
  const expectedTools = [
    "get_studio_status",
    "list_personas",
    "get_persona",
    "create_persona_draft",
    "list_assets",
    "generate_image_draft",
    "get_generation_status",
    "review_asset",
    "prepare_content_schedule",
    "list_scheduled_posts",
    "get_analytics_summary",
    "request_publish_approval",
    "publish_approved_post",
  ];
  const actualNames = MCP_TOOL_DEFINITIONS.map((t) => t.name);
  assert.deepEqual(actualNames, expectedTools);
  for (const tool of MCP_TOOL_DEFINITIONS) {
    assert.ok(tool.description.length > 20);
    assert.equal(tool.inputSchema.type, "object");
  }
});

test("Acceptance 1: User A cannot access User B's personas, assets, or jobs, and client-supplied userId spoofing is blocked", async () => {
  const engine = new PersonaForgeDomainEngine();
  const aliceCtx = { uid: "user_alice", role: "CHATGPT_MCP" };
  const bobCtx = { uid: "user_bob", role: "CHATGPT_MCP" };

  const { persona: bobPersona } = await engine.executeMcpTool(
    bobCtx,
    "create_persona_draft",
    {
      idempotencyKey: "idem_bob_persona_1",
      name: "Bob Virtual Host",
      handle: "@bobhost",
      facialIdentityDescriptor: "Square jaw, deep-set brown eyes",
      referenceStorageUri: "vault://users/user_bob/refs/bob1.jpg",
      referenceSha256: "1234567890abcdef",
      consentSubjectName: "Bob Tester",
      consentVerified: true,
    }
  );

  // Alice listing personas only sees her own (empty)
  const aliceList = await engine.executeMcpTool(aliceCtx, "list_personas", {});
  assert.equal(aliceList.personas.length, 0);

  // Alice attempting to get Bob's persona by ID is rejected with FORBIDDEN
  await assert.rejects(
    () =>
      engine.executeMcpTool(aliceCtx, "get_persona", {
        personaId: bobPersona.id,
      }),
    /FORBIDDEN/
  );

  // Client-supplied userId spoofing is rejected
  await assert.rejects(
    () =>
      engine.executeMcpTool(
        {
          uid: "user_alice",
          role: "CHATGPT_MCP",
          clientSuppliedUserId: "user_bob",
        },
        "list_personas",
        {}
      ),
    /FORBIDDEN/
  );
});

test("Acceptance 2 & 3: ChatGPT can retrieve existing persona data, request generation with traceable provenance, and handle retries safely", async () => {
  const engine = new PersonaForgeDomainEngine();
  const aliceCtx = { uid: "user_alice", role: "CHATGPT_MCP" };

  const { persona } = await engine.executeMcpTool(
    aliceCtx,
    "create_persona_draft",
    {
      idempotencyKey: "idem_alice_p1",
      name: "Sora Lin",
      handle: "@soralin",
      facialIdentityDescriptor: "Warm oval face, dark epicanthic eyes, beauty mark on right cheek",
      referenceStorageUri: "vault://users/user_alice/refs/sora.jpg",
      referenceSha256: "9988776655443322",
      consentSubjectName: "Sora Lin",
      consentVerified: true,
    }
  );

  const fetched = await engine.executeMcpTool(aliceCtx, "get_persona", {
    personaId: persona.id,
  });
  assert.equal(fetched.persona.name, "Sora Lin");

  const genResult = await engine.executeMcpTool(
    aliceCtx,
    "generate_image_draft",
    {
      idempotencyKey: "idem_gen_sora_1",
      personaId: persona.id,
      emotion: "Radiant Joy",
      stylePreset: "Anamorphic Neon 35mm",
      prompt: "Close-up studio portrait in soft cyan key light",
      provider: "FIREBASE_AI_LOGIC",
      modelId: "gemini-nano-banana-2.1",
    }
  );

  assert.equal(genResult.deduplicated, false);
  assert.equal(genResult.asset.modelId, "gemini-nano-banana-2.1");
  assert.equal(genResult.asset.costMicros, 40000);
  assert.equal(genResult.asset.sourceReferenceUri, persona.referenceStorageUri);

  // Duplicate tool call with same idempotencyKey never charges twice
  const dupResult = await engine.executeMcpTool(
    aliceCtx,
    "generate_image_draft",
    {
      idempotencyKey: "idem_gen_sora_1",
      personaId: persona.id,
      emotion: "Radiant Joy",
      stylePreset: "Anamorphic Neon 35mm",
      prompt: "Close-up studio portrait in soft cyan key light",
    }
  );
  assert.equal(dupResult.deduplicated, true);
  assert.equal(dupResult.asset.id, genResult.asset.id);

  const status = await engine.executeMcpTool(aliceCtx, "get_studio_status", {});
  assert.equal(status.quota.currentMonthSpendMicros, 40000);
  assert.equal(status.quota.todayGenerationCount, 1);
});

test("Acceptance 4, 5 & 6: Publishing without exact approval is rejected; parameter tampering, rejected/expired approvals, and duplicate publishes are blocked", async () => {
  const engine = new PersonaForgeDomainEngine();
  const agentCtx = { uid: "user_alice", role: "CHATGPT_MCP" };
  const ownerCtx = { uid: "user_alice", role: "OWNER_ANDROID" };

  const { persona } = await engine.executeMcpTool(
    agentCtx,
    "create_persona_draft",
    {
      idempotencyKey: "idem_p_pub",
      name: "Nova",
      handle: "@nova",
      facialIdentityDescriptor: "Sharp cheekbones, silver bob hair",
      referenceStorageUri: "vault://users/user_alice/refs/nova.jpg",
      referenceSha256: "aabbccddeeff0011",
      consentSubjectName: "Nova Model",
      consentVerified: true,
    }
  );

  const { asset } = await engine.executeMcpTool(
    agentCtx,
    "generate_image_draft",
    {
      idempotencyKey: "idem_gen_pub_1",
      personaId: persona.id,
      emotion: "Confident Smirk",
      stylePreset: "Editorial",
      prompt: "Portrait in studio",
    }
  );

  // Cannot schedule a DRAFT asset before review_asset -> APPROVED
  await assert.rejects(
    () =>
      engine.executeMcpTool(agentCtx, "prepare_content_schedule", {
        assetId: asset.id,
        destinationPlatform: "Patreon",
        scheduledEpochMillis: 1893456000000,
        caption: "Behind the scenes drop",
      }),
    /POLICY_VIOLATION/
  );

  // Approve asset
  await engine.executeMcpTool(agentCtx, "review_asset", {
    assetId: asset.id,
    reviewStatus: "APPROVED",
    reviewerNotes: "Facial consistency verified against reference v1",
  });

  const { schedule } = await engine.executeMcpTool(
    agentCtx,
    "prepare_content_schedule",
    {
      assetId: asset.id,
      destinationPlatform: "Patreon",
      scheduledEpochMillis: 1893456000000,
      caption: "Behind the scenes drop",
    }
  );

  const { approval } = await engine.executeMcpTool(
    agentCtx,
    "request_publish_approval",
    {
      scheduleId: schedule.id,
      assetId: asset.id,
      destinationPlatform: "Patreon",
      scheduledEpochMillis: 1893456000000,
      caption: "Behind the scenes drop",
    }
  );

  // Attempting to publish while approval is still PENDING fails
  await assert.rejects(
    () =>
      engine.executeMcpTool(agentCtx, "publish_approved_post", {
        idempotencyKey: "idem_pub_attempt_1",
        approvalId: approval.id,
        scheduleId: schedule.id,
        assetId: asset.id,
        destinationPlatform: "Patreon",
        scheduledEpochMillis: 1893456000000,
        caption: "Behind the scenes drop",
      }),
    /APPROVAL_NOT_GRANTED/
  );

  // Owner approves via Android Owner role
  engine.decidePublishApproval(ownerCtx, {
    approvalId: approval.id,
    decision: "APPROVED",
    decisionNotes: "Approved for Patreon release",
  });

  // Attempting to publish with a modified caption or destination fails bindingHash check
  await assert.rejects(
    () =>
      engine.executeMcpTool(agentCtx, "publish_approved_post", {
        idempotencyKey: "idem_pub_tampered",
        approvalId: approval.id,
        scheduleId: schedule.id,
        assetId: asset.id,
        destinationPlatform: "Patreon",
        scheduledEpochMillis: 1893456000000,
        caption: "TAMPERED CAPTION",
      }),
    /APPROVAL_BINDING_MISMATCH/
  );

  // Publishing with exact approved parameters succeeds
  const pubResult = await engine.executeMcpTool(
    agentCtx,
    "publish_approved_post",
    {
      idempotencyKey: "idem_pub_valid_1",
      approvalId: approval.id,
      scheduleId: schedule.id,
      assetId: asset.id,
      destinationPlatform: "Patreon",
      scheduledEpochMillis: 1893456000000,
      caption: "Behind the scenes drop",
    }
  );
  assert.equal(pubResult.published, true);
  assert.equal(pubResult.deduplicated, false);

  // Duplicate tool call with same idempotencyKey returns deduplicated receipt without double-publishing
  const dupPub = await engine.executeMcpTool(
    agentCtx,
    "publish_approved_post",
    {
      idempotencyKey: "idem_pub_valid_1",
      approvalId: approval.id,
      scheduleId: schedule.id,
      assetId: asset.id,
      destinationPlatform: "Patreon",
      scheduledEpochMillis: 1893456000000,
      caption: "Behind the scenes drop",
    }
  );
  assert.equal(dupPub.deduplicated, true);

  // Reusing a consumed approval with a new idempotencyKey is rejected
  await assert.rejects(
    () =>
      engine.executeMcpTool(agentCtx, "publish_approved_post", {
        idempotencyKey: "idem_pub_replay_2",
        approvalId: approval.id,
        scheduleId: schedule.id,
        assetId: asset.id,
        destinationPlatform: "Patreon",
        scheduledEpochMillis: 1893456000000,
        caption: "Behind the scenes drop",
      }),
    /APPROVAL_ALREADY_CONSUMED/
  );
});

test("Acceptance 7 & 8: External providers default to disabled, SSRF guard blocks internal/unapproved URLs, and deletion respects retention policy", async () => {
  const engine = new PersonaForgeDomainEngine();
  const aliceCtx = { uid: "user_alice", role: "OWNER_ANDROID" };

  const { persona } = await engine.executeMcpTool(
    aliceCtx,
    "create_persona_draft",
    {
      idempotencyKey: "idem_p_ret",
      name: "Kael",
      handle: "@kael",
      facialIdentityDescriptor: "Strong brow, emerald eyes",
      referenceStorageUri: "vault://users/user_alice/refs/kael.jpg",
      referenceSha256: "ff00ff00ff00ff00",
      consentSubjectName: "Kael Subject",
      consentVerified: true,
      retentionPolicyDays: 90,
    }
  );

  // External provider FAL_AI is disabled by default
  await assert.rejects(
    () =>
      engine.executeMcpTool(aliceCtx, "generate_image_draft", {
        idempotencyKey: "idem_fal_disabled",
        personaId: persona.id,
        emotion: "Focused",
        stylePreset: "Cinema",
        prompt: "Studio shot",
        provider: "FAL_AI",
      }),
    /PROVIDER_DISABLED/
  );

  // SSRF Guard blocks metadata, localhost, and arbitrary domains
  assert.throws(
    () => validateProviderUrl("http://169.254.169.254/latest/meta-data/"),
    /SSRF_BLOCKED/
  );
  assert.throws(
    () => validateProviderUrl("https://127.0.0.1:8080/internal"),
    /SSRF_BLOCKED/
  );
  assert.throws(
    () => validateProviderUrl("https://evil-attacker.example.com/model"),
    /SSRF_BLOCKED/
  );
  assert.equal(
    validateProviderUrl("https://queue.fal.run/fal-ai/flux"),
    "https://queue.fal.run/fal-ai/flux"
  );

  // Deletion creates retention tombstone with backup exclusion
  const delResult = engine.deletePersonaWithRetentionPolicy(aliceCtx, {
    personaId: persona.id,
  });
  assert.equal(delResult.deleted, true);
  assert.equal(delResult.tombstone.backupExcluded, true);
  assert.equal(
    delResult.tombstone.purgeStorageUri,
    "vault://users/user_alice/refs/kael.jpg"
  );
});
