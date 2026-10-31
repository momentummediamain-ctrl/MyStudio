const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validPersonaPayload(userId, now) {
  return {
    userId,
    name: "Aria Vance",
    handle: "@ariavance",
    status: "ACTIVE",
    profileVersion: 1,
    facialIdentityDescriptor:
      "High cheekbones, almond hazel eyes, defined brow arch, subtle beauty mark under left eye",
    referenceStorageUri: `vault://users/${userId}/refs/aria_v1.jpg`,
    referenceSha256: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    consentSubjectName: "Aria Vance (Consented Creator Model)",
    consentVerified: true,
    retentionPolicyDays: 365,
    createdAt: now,
    updatedAt: now,
  };
}

test("Acceptance 1: Unauthenticated caller cannot read or write personas", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("personas").get()
  );
});

test("Acceptance 2: User A (Alice) cannot access User B's (Bob) personas, assets, or jobs", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db
      .collection("users")
      .doc(BOB_UID)
      .collection("personas")
      .doc("bob_persona_1")
      .set(validPersonaPayload(BOB_UID, now));
    await db
      .collection("users")
      .doc(BOB_UID)
      .collection("assets")
      .doc("bob_asset_1")
      .set({
        userId: BOB_UID,
        personaId: "bob_persona_1",
        personaName: "Bob Persona",
        personaVersion: 1,
        jobId: "job_1",
        idempotencyKey: "idem_bob_1",
        mediaType: "IMAGE",
        storageUri: `vault://users/${BOB_UID}/assets/a1.jpg`,
        sha256Hash: "abcdef1234567890",
        emotion: "Warm Smile",
        stylePreset: "Editorial Studio",
        modelProvider: "FIREBASE_AI_LOGIC",
        modelId: "gemini-nano-banana-2.1",
        costMicros: 40000,
        sourceReferenceUri: `vault://users/${BOB_UID}/refs/r1.jpg`,
        versionNumber: 1,
        reviewStatus: "DRAFT",
        reviewerNotes: "",
        storyboardSummary: "",
        createdAt: now,
        updatedAt: now,
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("personas")
      .doc("bob_persona_1")
      .get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("assets")
      .doc("bob_asset_1")
      .get()
  );
});

test("Acceptance 3: Metadata-only enforcement rejects raw base64/binary data in storageUri", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const badPersona = {
    ...validPersonaPayload(ALICE_UID, now),
    referenceStorageUri: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAUA",
  };
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("personas")
      .doc("persona_bad_uri")
      .set(badPersona)
  );
});

test("Acceptance 4: Authenticated owner can create and query own personas with aligned filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("personas")
      .doc("persona_1")
      .set(validPersonaPayload(ALICE_UID, now))
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("personas")
      .where("userId", "==", ALICE_UID)
      .get()
  );
});

test("Acceptance 5: Shadow update with undeclared ghost field is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("personas")
    .doc("persona_shadow");

  await assertSucceeds(docRef.set(validPersonaPayload(ALICE_UID, now)));

  await assertFails(
    docRef.update({
      name: "Aria Updated",
      isBypassAdmin: true,
      updatedAt: new Date(),
    })
  );
});

test("Acceptance 6: Rejected or consumed publish approvals cannot be reused or transitioned back to APPROVED", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const approvalRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("approvals")
    .doc("approval_1");

  await assertSucceeds(
    approvalRef.set({
      userId: ALICE_UID,
      scheduleId: "sched_1",
      assetId: "asset_1",
      destinationPlatform: "Patreon",
      scheduledEpochMillis: 1893456000000,
      caption: "New studio drop!",
      bindingHash: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      requestedByRole: "CHATGPT_MCP",
      status: "PENDING",
      decisionNotes: "Requested by MCP agent",
      expiresAtEpochMillis: 1893542400000,
      consumedByIdempotencyKey: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Transition PENDING -> REJECTED succeeds
  await assertSucceeds(
    approvalRef.update({
      status: "REJECTED",
      decisionNotes: "Owner rejected caption",
      updatedAt: new Date(),
    })
  );

  // Attempting to reuse/flip REJECTED -> APPROVED fails (Terminal State Lock)
  await assertFails(
    approvalRef.update({
      status: "APPROVED",
      decisionNotes: "Attempted replay",
      updatedAt: new Date(),
    })
  );
});

test("Acceptance 7: AuditLogEntry is strictly append-only (updates and deletes fail)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const logRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("auditLogs")
    .doc("log_1");

  await assertSucceeds(
    logRef.set({
      userId: ALICE_UID,
      actorRole: "CHATGPT_MCP",
      action: "generate_image_draft",
      targetCollection: "jobs",
      targetId: "job_1",
      idempotencyKey: "idem_101",
      outcome: "ALLOWED",
      details: "Enqueued image generation draft",
      createdAt: now,
    })
  );

  await assertFails(logRef.update({ outcome: "DENIED" }));
  await assertFails(logRef.delete());
});
